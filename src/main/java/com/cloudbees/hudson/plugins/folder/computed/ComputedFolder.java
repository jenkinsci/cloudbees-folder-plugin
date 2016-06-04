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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A folder-like item whose children are computed.
 * Users cannot directly add or remove (or rename) children.
 * The children should also not offer {@link Item#CONFIGURE} to anyone.
 * @param <I> the child item type
 * @since 4.11-beta-1
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"}) // generics mistakes in various places; BuildableItem defines deprecated methods (and @SW on those overrides does not seem to work)
public abstract class ComputedFolder<I extends TopLevelItem> extends AbstractFolder<I> implements BuildableItem, Queue.FlyweightTask {

    private static final Logger LOGGER = Logger.getLogger(ComputedFolder.class.getName());

    private OrphanedItemStrategy orphanedItemStrategy;

    private DescribableList<Trigger<?>,TriggerDescriptor> triggers;
    // TODO p:config-triggers also expects there to be a BuildAuthorizationToken authToken option. Do we want one?

    private transient @Nonnull FolderComputation<I> computation;

    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    protected ComputedFolder(ItemGroup parent, String name) {
        super(parent, name);
        init();
    }

    @Override
    protected final void init() {
        super.init();
        if (orphanedItemStrategy == null) {
            orphanedItemStrategy = new DefaultOrphanedItemStrategy(true, "0", "0");
        }
        if (triggers == null) {
            triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
        } else {
            triggers.setOwner(this);
        }
        for (Trigger t : triggers) {
            t.start(this, Items.currentlyUpdatingByXml());
        }
        loadComputation();
    }

    /**
     * Called to (re-)compute the set of children of this folder.
     * @param observer how to indicate which children should be seen
     * @param listener a way to report progress
     */
    protected abstract void computeChildren(ChildObserver<I> observer, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Hook called when some items are no longer in the list.
     * Do not call {@link Item#delete} or {@link ItemGroup#onDeleted} or {@link ItemListener#fireOnDeleted} yourself.
     * By default, uses {@link #getOrphanedItemStrategy}.
     * @param orphaned a nonempty set of items which no longer are supposed to be here
     * @return any subset of {@code orphaned}, representing those children which ought to be removed from the folder now; items not listed will be left alone for the time being
     */
    protected Collection<I> orphanedItems(Collection<I> orphaned, TaskListener listener) throws IOException, InterruptedException {
        return getOrphanedItemStrategy().orphanedItems(this, orphaned, listener);
    }

    public void setOrphanedItemStrategy(@Nonnull OrphanedItemStrategy strategy) {
        this.orphanedItemStrategy = strategy;
    }

    /**
     * Recreates children synchronously.
     */
    final void updateChildren(final TaskListener listener) throws IOException, InterruptedException {
        final String fullName = getFullName();
        LOGGER.log(Level.FINE, "updating {0}", fullName);
        final Map<String,I> orphaned = new HashMap<String,I>(items);
        final Set<String> observed = new HashSet<String>();
        computeChildren(new ChildObserver<I>() {
            @Override
            public I shouldUpdate(String name) {
                I existing = orphaned.remove(name);
                if (existing != null) {
                    observed.add(name);
                }
                LOGGER.log(Level.FINE, "{0}: existing {1}: {2}", new Object[] {fullName, name, existing});
                return existing;
            }
            @Override
            public boolean mayCreate(String name) {
                boolean r = observed.add(name);
                LOGGER.log(Level.FINE, "{0}: may create {1}? {2}", new Object[] {fullName, name, r});
                return r;
            }
            @Override
            public void created(I child) {
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
                items.put(name, child);
                Jenkins j = Jenkins.getInstance();
                if (j != null) {
                    j.rebuildDependencyGraphAsync();
                }
                ItemListener.fireOnCreated(child);
            }
        }, listener);
        if (!orphaned.isEmpty()) {
            LOGGER.log(Level.FINE, "{0}: orphaned {1}", new Object[] {fullName, orphaned});
            for (I existing : orphanedItems(orphaned.values(), listener)) {
                LOGGER.log(Level.FINE, "{0}: deleting {1}", new Object[] {fullName, existing});
                existing.delete();
                // super.onDeleted handles removal from items
            }
        }
        LOGGER.log(Level.FINE, "finished updating {0}", fullName);
    }

    private synchronized void loadComputation() {
        try {
            FileUtils.forceMkdir(getComputationDir());
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        computation = createComputation(null);
        XmlFile file = computation.getDataFile();
        if (file != null && file.exists()) {
            try {
                file.unmarshal(computation);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
            }
        }
    }

    @RequirePOST
    @Override
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        super.doConfigSubmit(req, rsp);
        scheduleBuild();
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
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
    }

    @Override
    protected @Nonnull String getSuccessfulDestination() {
        return computation.getSearchUrl() + "console";
    }

    public Map<TriggerDescriptor,Trigger<?>> getTriggers() {
        return triggers.toMap();
    }

    public void addTrigger(Trigger trigger) {
        Trigger old = triggers.get(trigger.getDescriptor());
        if (old != null) {
            old.stop();
            triggers.remove(old);
        }
        triggers.add(trigger);
        trigger.start(this, true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<Action> getActions() {
        List<Action> actions = new ArrayList<Action>(super.getActions());
        for (Trigger<?> trigger : triggers) {
            actions.addAll(trigger.getProjectActions());
        }
        return actions;
    }

    /** Whether it is permissible to recompute this folder at this time. */
    public boolean isBuildable() {
        return true;
    }

    @RequirePOST
    public HttpResponse doBuild(@QueryParameter TimeDuration delay) {
        checkPermission(BUILD);
        if (!isBuildable()) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, new IOException(getFullName() + " cannot be recomputed"));
        }
        scheduleBuild2(delay == null ? 0 : delay.getTime(), new CauseAction(new Cause.UserIdCause()));
        return HttpResponses.forwardToPreviousPage();
    }

    /** Duck-types {@link ParameterizedJobMixIn#scheduleBuild2(int, Action...)}. */
    public @CheckForNull Queue.Item scheduleBuild2(int quietPeriod, Action... actions) {
        if (!isBuildable()) {
            return null;
        }
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getQueue().schedule2(this, quietPeriod, Arrays.asList(actions)).getItem();
    }

    @Override
    public boolean scheduleBuild() {
        return scheduleBuild2(0) != null;
    }

    @Override
    public boolean scheduleBuild(Cause c) {
        return scheduleBuild2(0, new CauseAction(c)) != null;
    }

    @Override
    public boolean scheduleBuild(int quietPeriod) {
        return scheduleBuild2(quietPeriod) != null;
    }

    @Override
    public boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, new CauseAction(c)) != null;
    }

    @Override
    public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Deprecated
    @Override
    public String getWhyBlocked() {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
        return causeOfBlockage == null ? null : causeOfBlockage.getShortDescription();
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        if (computation.isBuilding()) {
            return CauseOfBlockage.fromMessage(Messages._ComputedFolder_already_computing());
        }
        return null;
    }

    @Override
    public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override
    public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Override
    public boolean isConcurrentBuild() {
        return false;
    }

    @Override
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    @Override
    public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    @Override
    public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

    @Override
    public Label getAssignedLabel() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getSelfLabel();
    }

    @Override
    public Node getLastBuiltOn() {
        return Jenkins.getInstance();
    }

    @Override
    public long getEstimatedDuration() {
        return computation.getEstimatedDuration();
    }

    @Override
    public final FolderComputation<I> createExecutable() throws IOException {
        FolderComputation<I> c = createComputation(computation);
        computation = c;
        return c;
    }

    protected @Nonnull FolderComputation<I> createComputation(@CheckForNull FolderComputation<I> previous) {
        return new FolderComputation<I>(this, previous);
    }

    @Override
    public Queue.Task getOwnerTask() {
        return this;
    }

    @Override
    public Object getSameNodeConstraint() {
        return null;
    }

    @Override
    public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    protected File getComputationDir() {
        return new File(getRootDir(), "computation");
    }

    /**
     * URL binding and other purposes.
     * It may be null temporarily inside the constructor, so beware if you extend this class.
     */
    public @Nonnull FolderComputation<I> getComputation() {
        return computation;
    }

    @Override
    protected String renameBlocker() {
        if (computation.isBuilding()) {
            return "Recomputation is currently in progress";
        }
        return super.renameBlocker();
    }

    public @NonNull OrphanedItemStrategy getOrphanedItemStrategy() {
        return orphanedItemStrategy;
    }

    /**
     * Gets the {@link OrphanedItemStrategyDescriptor}s applicable to this folder.
     */
    @Restricted(DoNotUse.class) // used by Jelly
    public @NonNull List<OrphanedItemStrategyDescriptor> getOrphanedItemStrategyDescriptors() {
        List<OrphanedItemStrategyDescriptor> result = new ArrayList<OrphanedItemStrategyDescriptor>();
        for (OrphanedItemStrategyDescriptor descriptor : ExtensionList.lookup(OrphanedItemStrategyDescriptor.class)) {
            if (descriptor.isApplicable(getClass())) {
                result.add(descriptor);
            }
        }
        return result;
    }

}
