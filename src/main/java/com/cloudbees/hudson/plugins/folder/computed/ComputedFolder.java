/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.hudson.plugins.folder.computed;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.thoughtworks.xstream.XStreamException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.TriggeredItem;
import jenkins.util.TimeDuration;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * A folder-like item whose children are computed.
 * Users cannot directly add or remove (or rename) children.
 * The children should also not offer {@link Item#CONFIGURE} to anyone.
 * @param <I> the child item type
 * @since 4.11-beta-1
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // generics mistakes in various places
public abstract class ComputedFolder<I extends TopLevelItem> extends AbstractFolder<I> implements BuildableItem, TriggeredItem, Queue.FlyweightTask {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(ComputedFolder.class.getName());

    /**
     * Our {@link OrphanedItemStrategy}
     */
    private OrphanedItemStrategy orphanedItemStrategy;

    /**
     * Our {@link Trigger}s.
     */
    private DescribableList<Trigger<?>,TriggerDescriptor> triggers;

    /**
     * Is this folder disabled.
     *
     * @since 6.1.0
     */
    private volatile boolean disabled;

    /**
     * Our {@link FolderComputation}.
     */
    @NonNull
    private transient FolderComputation<I> computation;

    /**
     * Lock to guard {@link #currentObservations}.
     *
     * @since 6.0.0
     */
    @NonNull
    private transient /* almost final */ ReentrantLock currentObservationsLock;
    /**
     * Condition to flag whenever the {@link #currentObservations} has had elements removed.
     *
     * @since 6.0.0
     */
    private transient /* almost final */ Condition currentObservationsChanged;
    /**
     * The names of the child items that are currently being observed.
     *
     * @since 6.0.0
     */
    @GuardedBy("#computationLock")
    private transient /* almost final */ Set<String> currentObservations;
    /**
     * Flag set when the implementation uses {@link #createEventsChildObserver()} and not
     * {@link #openEventsChildObserver()}, when {@code true} then the {@link #currentObservations} is ignored
     * as we cannot rely on the implementation to call {@link ChildObserver#close()}.
     *
     * @since 6.0.0
     */
    @GuardedBy("#computationLock")
    private transient boolean currentObservationsLockDisabled;

    /**
     * Tracks recalculation requirements in {@link #doConfigSubmit(StaplerRequest, StaplerResponse)}.
     *
     * @see #recalculateAfterSubmitted(boolean)
     * @see #submit(StaplerRequest, StaplerResponse)
     * @see #doConfigSubmit(StaplerRequest, StaplerResponse)
     */
    private transient Recalculation recalculate;

    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    protected ComputedFolder(ItemGroup parent, String name) {
        super(parent, name);
        init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init() {
        super.init();
        if (orphanedItemStrategy == null) {
            orphanedItemStrategy = new DefaultOrphanedItemStrategy(true, "", "");
        }
        if (triggers == null) {
            triggers = new DescribableList<>(this);
        } else {
            triggers.setOwner(this);
        }
        for (Trigger t : triggers) {
            t.start(this, Items.currentlyUpdatingByXml());
        }
        synchronized (this) {
            computation = createComputation(null);
        }
        currentObservationsLock = new ReentrantLock();
        currentObservationsChanged = currentObservationsLock.newCondition();
        currentObservations = new HashSet<>();
    }

    @Override
    public void onCreatedFromScratch() {
        try {
            FileUtils.forceMkdir(getComputationDir());
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        super.onCreatedFromScratch();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (reloadingThis.get()) {
            LOGGER.fine(() -> this + " skipping the rest of onLoad");
            return;
        }
        try {
            FileUtils.forceMkdir(getComputationDir());
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        synchronized (this) {
            try {
                computation.load();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + computation, e);
            }
        }
    }

    /**
     * Called to (re-)compute the set of children of this folder. It is recommended that the computation checks the
     * {@link Thread#interrupted()} status and throws a {@link InterruptedException} if set at least once every 5
     * seconds to allow the user to interrupt a computation..
     *
     * @param observer how to indicate which children should be seen
     * @param listener a way to report progress
     * @throws IOException if there was an {@link IOException} during the computation.
     * @throws InterruptedException if the computation was interrupted.
     */
    protected abstract void computeChildren(ChildObserver<I> observer, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Hook called when some items are no longer in the list.
     * Do not call {@link Item#delete} or {@link ItemGroup#onDeleted} or {@link ItemListener#fireOnDeleted} yourself.
     * By default, uses {@link #getOrphanedItemStrategy}.
     * @param orphaned a nonempty set of items which no longer are supposed to be here
     * @param listener the listener to report decisions to.
     * @return any subset of {@code orphaned}, representing those children which ought to be removed from the folder
     * now; items not listed will be left alone for the time
     * @throws IOException          if there was an I/O issue processing the items.
     * @throws InterruptedException if interrupted while processing the items.
     */
    protected Collection<I> orphanedItems(Collection<I> orphaned, TaskListener listener) throws IOException, InterruptedException {
        return getOrphanedItemStrategy().orphanedItems(this, orphaned, listener);
    }

    public void setOrphanedItemStrategy(@NonNull OrphanedItemStrategy strategy) {
        this.orphanedItemStrategy = strategy;
    }

    /**
     * Recreates children synchronously.
     */
    final void updateChildren(final TaskListener listener) throws IOException, InterruptedException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "updating {0}", getFullName());
        }
        try (FullReindexChildObserver observer = new FullReindexChildObserver()) {
            computeChildren(observer, listener);
            Map<String, I> orphaned = observer.orphaned();
            if (!orphaned.isEmpty()) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "{0}: orphaned {1}",
                            new Object[]{getFullName(), orphaned.keySet()});
                }
                Collection<I> forRemoval = orphanedItems(orphaned.values(), listener);

                List<IOException> deletingExceptions = new LinkedList<>();
                for (I existing : orphaned.values()) {
                    if (forRemoval.contains(existing)) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.log(Level.FINE, "{0}: deleting {1}", new Object[]{getFullName(), existing});
                        }
                        try {
                            existing.delete();
                            // super.onDeleted handles removal from items
                        } catch (IOException x) { // InterruptedException is still propagated
                            deletingExceptions.add(x);
                        }
                    } else {
                        applyDisabled(existing, true);
                    }
                }

                if (!deletingExceptions.isEmpty()) {
                    throw new DeletingChildrenException(deletingExceptions);
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "finished updating {0}", getFullName());
        }
    }

    private void applyDisabled(I item, boolean disabled) {
        try {
            if (item instanceof AbstractFolder) {
                ((AbstractFolder) item).makeDisabled(disabled);
            } else if (item instanceof ParameterizedJobMixIn.ParameterizedJob) {
                ((ParameterizedJobMixIn.ParameterizedJob) item).makeDisabled(disabled);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Could not " + (disabled ? "disable " : "enable ") + item.getFullName(), e);
        }
    }

    /**
     * Creates a {@link ChildObserver} that subclasses can use when handling events that might create new / update
     * existing child items. The handling of orphaned items is a responsibility of the {@link OrphanedItemStrategy}
     * which is only applied as part of a full computation.
     *
     * @return a {@link ChildObserver} for event handling.
     * @deprecated use {@link #openEventsChildObserver()}
     */
    @Deprecated
    @Restricted(NoExternalUse.class) // cause a compilation error to force implementations to switch
    protected final ChildObserver<I> createEventsChildObserver() {
        LOGGER.log(Level.WARNING, "The {0} implementation of ComputedFolder has not been updated to use "
                + "openEventsChildObserver(), this may result in 'java.lang.IllegalStateException: JENKINS-23152 ... "
                + "already existed; will not overwrite with ...' being thrown when processing events",
                getClass().getName());
        currentObservationsLock.lock();
        try {
            if (!currentObservationsLockDisabled) {
                currentObservationsLockDisabled = true;
                currentObservationsChanged.signalAll();
            }
        } finally {
            currentObservationsLock.unlock();
        }
        return new EventChildObserver();
    }

    /**
     * Opens a new {@link ChildObserver} that subclasses can use when handling events that might create new / update
     * existing child items. The handling of orphaned items is a responsibility of the {@link OrphanedItemStrategy}
     * which is only applied as part of a full computation.
     *
     * @return a {@link ChildObserver} for event handling. The caller must {@link ChildObserver#close()} when done.
     * @since 6.0.0
     */
    protected final ChildObserver<I> openEventsChildObserver() {
        return new EventChildObserver();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setDisabled(boolean disabled) {
        this.disabled = disabled;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsMakeDisabled() {
        return true;

    }

    /**
     * {@inheritDoc}
     */
    @RequirePOST
    @Override
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        try {
            recalculate = Recalculation.UNKNOWN;
            super.doConfigSubmit(req, rsp);
            if (recalculate != Recalculation.NO_RECALCULATION && isBuildable()) {
                scheduleBuild(new Cause.UserIdCause());
            }
        } finally {
            recalculate = null;
        }
    }

    /**
     * Method for child classes to use if they want to suppress/confirm the automatic recalculation provided in
     * {@link #doConfigSubmit(StaplerRequest, StaplerResponse)}. This method should only be called from
     * {@link #submit(StaplerRequest, StaplerResponse)}. If called multiple times from
     * {@link #submit(StaplerRequest, StaplerResponse)} then all calls must be with the {@code false} parameter
     * to suppress recalculation.
     *
     * @param recalculate {@code true} to require recalculation, {@code false} to suppress recalculation.
     * @see #submit(StaplerRequest, StaplerResponse)
     */
    /*
     * Note: it would have been much nicer to have submit(req,rsp) return a boolean... but that would have required
     * doConfigSubmit(req,rsp) to also return a boolean which would have required the @WebMethod annotation to get
     * Stapler to detect the method as a web method...
     */
    protected final void recalculateAfterSubmitted(boolean recalculate) {
        if (this.recalculate == null) {
            return;
        }
        if (this.recalculate == Recalculation.RECALCULATE) {
            return;
        }
        this.recalculate = recalculate ? Recalculation.RECALCULATE : Recalculation.NO_RECALCULATION;
    }

    /**
     * {@inheritDoc}
     *
     * @see #recalculateAfterSubmitted(boolean)
     */
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        String oisDigest = null;
        try {
            oisDigest = Util.getDigestOf(Items.XSTREAM2.toXML(orphanedItemStrategy));
        } catch (XStreamException e) {
            // ignore
        }
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        orphanedItemStrategy = req.bindJSON(OrphanedItemStrategy.class, json.getJSONObject("orphanedItemStrategy"));
        for (Trigger t : triggers) {
            t.stop();
        }
        triggers.rebuild(req, json, Trigger.for_(this));
        for (Trigger t : triggers) {
            t.start(this, true);
        }
        try {
            if (oisDigest == null || !oisDigest.equals(Util.getDigestOf(Items.XSTREAM2.toXML(orphanedItemStrategy)))) {
                // force a recalculation if orphanedItemStrategy has changed as recalculation is when we find orphans
                recalculateAfterSubmitted(true);
            }
        } catch(XStreamException e){
            // force a recalculation anyway in this case
            recalculateAfterSubmitted(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Restricted(NoExternalUse.class)
    @Override
    @NonNull
    protected final String getSuccessfulDestination() {
        if (recalculate != Recalculation.NO_RECALCULATION && isBuildable()) {
            return computation.getSearchUrl() + "console";
        } else {
            return super.getSuccessfulDestination();
        }
    }

    @Override
    public Map<TriggerDescriptor,Trigger<?>> getTriggers() {
        return triggers.toMap();
    }

    @Restricted(NoExternalUse.class) // currently used only by jelly / stapler
    public List<TriggerDescriptor> getTriggerDescriptors() {
        // TODO remove this once core has support for DescriptorVisibilityFilter on Trigger.for_(Item)
        List<TriggerDescriptor> result = new ArrayList<>();
        for (TriggerDescriptor d: Trigger.for_(this)) {
            if (d instanceof TimerTrigger.DescriptorImpl) {
                continue;
            }
            result.add(d);
        }
        return result;
    }

    /**
     * Update an existing trigger or add a new one.
     * @param trigger Target trigger instance.
     */
    public void addTrigger(Trigger trigger) {
        removeTrigger(trigger);
        triggers.add(trigger);
        trigger.start(this, true);
    }

    /**
     * Remove an existing trigger.
     * @param trigger Target trigger instance.
     */
    public void removeTrigger(Trigger trigger) {
        Trigger old = triggers.get(trigger.getDescriptor());
        if (old != null) {
            old.stop();
            triggers.remove(old);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public List<Action> getActions() {
        List<Action> actions = new ArrayList<>(super.getActions());
        for (Trigger<?> trigger : triggers) {
            actions.addAll(trigger.getProjectActions());
        }
        return actions;
    }

    /**
     * Whether it is permissible to recompute this folder at this time.
     *
     * @return {@code true} if this folder can currently be recomputed.
     */
    @Exported
    public boolean isBuildable() {
        if (isDisabled()) {
            return false;
        }
        // TODO revisit logic when Jenkins core allows ItemGroups to expose their disabled state
        // until then we just need to check if a parent is disabled
        for (ItemGroup p = getParent(); p instanceof Item; p = (((Item) p).getParent())) {
            if (p instanceof AbstractFolder && ((AbstractFolder) p).isDisabled()) {
                return false;
            }
        }
        return true;
    }

    @RequirePOST
    public HttpResponse doBuild(@QueryParameter TimeDuration delay) {
        checkPermission(BUILD);
        if (!isBuildable()) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, new IOException(getFullName() + " cannot be recomputed"));
        }
        scheduleBuild2(delay == null ? 0 : delay.getTimeInSeconds(), new CauseAction(new Cause.UserIdCause()));
        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Duck-types {@link ParameterizedJobMixIn#scheduleBuild2(int, Action...)}.
     * @param quietPeriod seconds to wait before starting (normally 0)
     * @param actions various actions to associate with the scheduling, such as {@link ParametersAction} or
     * {@link CauseAction}
     * @return a handle by which you may wait for the build to complete (or just start); or null if the build was not
     * actually scheduled for some reason
     */
    @CheckForNull
    public Queue.Item scheduleBuild2(int quietPeriod, Action... actions) {
        if (!isBuildable()) {
            return null;
        }
        return Queue.getInstance().schedule2(this, quietPeriod, Arrays.asList(actions)).getItem();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean scheduleBuild(Cause c) {
        return scheduleBuild2(0, new CauseAction(c)) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, new CauseAction(c)) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        if (computation.isBuilding()) {
            return CauseOfBlockage.fromMessage(Messages._ComputedFolder_already_computing());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Label getAssignedLabel() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return null;
        }
        return j.getSelfLabel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getLastBuiltOn() {
        return Jenkins.getInstanceOrNull();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEstimatedDuration() {
        return computation.getEstimatedDuration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final FolderComputation<I> createExecutable() throws IOException {
        if (isDisabled()) {
            return null;
        }
        FolderComputation<I> c = createComputation(computation);
        computation = c;
        LOGGER.log(Level.FINE, "Recording {0} @{1}", new Object[] {c, c.getTimestamp()});
        return c;
    }

    @NonNull
    protected FolderComputation<I> createComputation(@CheckForNull FolderComputation<I> previous) {
        return new FolderComputation<>(this, previous);
    }

    protected File getComputationDir() {
        return new File(getRootDir(), "computation");
    }

    /**
     * Identifies if this {@link ComputedFolder} has a separate out of band events log. Default implementation
     * just checks if the events log has content. Subclasses can override this method to force the events log
     * always present in the UI.
     *
     * @return {@code true} if this {@link ComputedFolder} has a separate out of band events log.
     */
    public boolean isHasEvents() {
        return getComputation().getEventsFile().length() > 0;
    }

    /**
     * URL binding and other purposes.
     * It may be {@code null} temporarily inside the constructor, so beware if you extend this class.
     *
     * @return the computation.
     */
    @NonNull
    public FolderComputation<I> getComputation() {
        return computation;
    }

    @Restricted(NoExternalUse.class) // used by stapler only
    public PseudoRun<I> getLastSuccessfulBuild() {
        FolderComputation<I> computation = getComputation();
        Result result = computation.getResult();
        if (result != null && Result.UNSTABLE.isWorseOrEqualTo(result)) {
            return new PseudoRun(computation);
        }
        return null;
    }

    @Restricted(NoExternalUse.class) // used by stapler only
    public PseudoRun<I> getLastStableBuild() {
        FolderComputation<I> computation = getComputation();
        Result result = computation.getResult();
        if (result != null && Result.SUCCESS.isWorseOrEqualTo(result)) {
            return new PseudoRun(computation);
        }
        return null;
    }

    @Restricted(NoExternalUse.class) // used by stapler only
    public PseudoRun<I> getLastFailedBuild() {
        FolderComputation<I> computation = getComputation();
        Result result = computation.getResult();
        if (result != null && Result.UNSTABLE.isBetterThan(result)) {
            return new PseudoRun(computation);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkRename(String newName) {
        if (computation.isBuilding()) {
            throw new Failure(Messages.ComputedFolder_ComputationInProgress());
        }
        super.checkRename(newName);
    }

    @NonNull
    public OrphanedItemStrategy getOrphanedItemStrategy() {
        return orphanedItemStrategy;
    }

    /**
     * Gets the {@link OrphanedItemStrategyDescriptor}s applicable to this folder.
     *
     * @return the {@link OrphanedItemStrategyDescriptor}s applicable to this folder.
     */
    @Restricted(DoNotUse.class) // used by Jelly
    @NonNull
    public List<OrphanedItemStrategyDescriptor> getOrphanedItemStrategyDescriptors() {
        List<OrphanedItemStrategyDescriptor> result = new ArrayList<>();
        for (OrphanedItemStrategyDescriptor descriptor : ExtensionList.lookup(OrphanedItemStrategyDescriptor.class)) {
            if (descriptor.isApplicable(getClass())) {
                result.add(descriptor);
            }
        }
        return result;
    }

    private class FullReindexChildObserver extends ChildObserver<I> {
        private final Map<String, I> orphaned = new HashMap<>(items);
        private final Set<String> observed = new HashSet<>();
        private final Set<String> locked = new HashSet<>();
        private final String fullName = getFullName();

        @Override
        public void close() {
            if (!locked.isEmpty()) {
                currentObservationsLock.lock();
                try {
                    currentObservations.removeAll(locked);
                    currentObservationsChanged.signalAll();
                } finally {
                    currentObservationsLock.unlock();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public I shouldUpdate(String name) throws InterruptedException {
            currentObservationsLock.lock();
            try {
                while (!currentObservations.add(name) && !currentObservationsLockDisabled) {
                    currentObservationsChanged.await();
                }
                locked.add(name);
            } finally {
                currentObservationsLock.unlock();
            }
            I existing = orphaned.remove(name);
            if (existing == null) {
                // may have been created by a parallel event
                existing = items.get(name);
            }
            if (existing != null) {
                observed.add(name);
                applyDisabled(existing, false);
            }
            LOGGER.log(Level.FINE, "{0}: existing {1}: {2}", new Object[] {fullName, name, existing});
            return existing;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean mayCreate(String name) {
            boolean r = observed.add(name);
            LOGGER.log(Level.FINE, "{0}: may create {1}? {2}", new Object[] {fullName, name, r});
            if (!r) {
                completed(name);
            }
            return r;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void created(I child) {
            if (child.getParent() != ComputedFolder.this) {
                throw new IllegalStateException("Tried to notify " + ComputedFolder.this + " of creation of " + child + " with a different parent");
            }
            LOGGER.log(Level.FINE, "{0}: created {1}", new Object[] {fullName, child});
            String name = child.getName();
            if (!observed.contains(name)) {
                throw new IllegalStateException("Did not call mayCreate, or used the wrong Item.name for " + child + " with name " + name + " among " + observed);
            }
            child.onCreatedFromScratch();
            try {
                child.save();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "Failed to save " + child, x);
            }
            itemsPut(name, child);
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j != null) {
                j.rebuildDependencyGraphAsync();
            }
            ItemListener.fireOnCreated(child);
            // signal this name early
            completed(name);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completed(String name) {
            if (locked.contains(name)) {
                currentObservationsLock.lock();
                try {
                    locked.remove(name);
                    currentObservations.remove(name);
                    currentObservationsChanged.signalAll();
                } finally {
                    currentObservationsLock.unlock();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<String> observed() {
            return new HashSet<>(observed);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, I> orphaned() {
            return new HashMap<>(orphaned);
        }
    }

    private class EventChildObserver extends ChildObserver<I> {
        private final String fullName = getFullName();
        private final Set<String> observed = new HashSet<>();
        private final Set<String> locked = new HashSet<>();

        @Override
        public void close() {
            if (!locked.isEmpty()) {
                currentObservationsLock.lock();
                try {
                    currentObservations.removeAll(locked);
                    currentObservationsChanged.signalAll();
                } finally {
                    currentObservationsLock.unlock();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public I shouldUpdate(String name) throws InterruptedException {
            currentObservationsLock.lock();
            try {
                while (!currentObservations.add(name) && !currentObservationsLockDisabled) {
                    currentObservationsChanged.await();
                }
                locked.add(name);
            } finally {
                currentObservationsLock.unlock();
            }
            I existing = items.get(name);
            if (existing != null) {
                observed.add(name);
                applyDisabled(existing, false);
            }
            LOGGER.log(Level.FINE, "{0}: existing {1}: {2}", new Object[]{fullName, name, existing});
            return existing;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean mayCreate(String name) {
            boolean r = !items.containsKey(name) && observed.add(name);
            LOGGER.log(Level.FINE, "{0}: may create {1}? {2}", new Object[]{fullName, name, r});
            if (!r) {
                completed(name);
            }
            return r;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void created(I child) {
            if (child.getParent() != ComputedFolder.this) {
                throw new IllegalStateException(
                        "Tried to notify " + ComputedFolder.this + " of creation of " + child
                                + " with a different parent");
            }
            LOGGER.log(Level.FINE, "{0}: created {1}", new Object[]{fullName, child});
            String name = child.getName();
            if (!observed.contains(name)) {
                throw new IllegalStateException(
                        "Did not call mayCreate, or used the wrong Item.name for " + child + " with name " + name
                                + " among " + observed);
            }
            child.onCreatedFromScratch();
            try {
                child.save();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "Failed to save " + child, x);
            }
            itemsPut(name, child);
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j != null) {
                j.rebuildDependencyGraphAsync();
            }
            ItemListener.fireOnCreated(child);
            // signal this name early
            completed(name);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void completed(String name) {
            if (locked.contains(name)) {
                currentObservationsLock.lock();
                try {
                    locked.remove(name);
                    currentObservations.remove(name);
                    currentObservationsChanged.signalAll();
                } finally {
                    currentObservationsLock.unlock();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<String> observed() {
            return new HashSet<>(observed);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, I> orphaned() {
            return new HashMap<>(); // always empty as we never orphan items from events
        }
    }

    /**
     * Records the recalculation requirements of a call to {@link #submit(StaplerRequest, StaplerResponse)}.
     */
    private enum Recalculation {
        /**
         * We don't know if recalculation is required... assume it will be.
         */
        UNKNOWN,
        /**
         * We know recalculation is required.
         */
        RECALCULATE,
        /**
         * We know recalculation is not required.
         */
        NO_RECALCULATION
    }
}
