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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import static hudson.model.Item.CONFIGURE;
import static hudson.model.Item.CREATE;
import static hudson.model.Item.DELETE;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.security.ACL;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.CopyOnWriteMap;
import hudson.util.FormApply;
import hudson.util.Function1;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A folder-like item whose children are computed.
 * Users cannot directly add or remove (or rename) children.
 * The children should also not offer {@link Item#CONFIGURE} to anyone.
 * Generalizes some techniques originally written in {@code MultiBranchProject} and {@link Folder}.
 * @param <I> the child item type
 * @since FIXME
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public abstract class ComputedFolder<I extends TopLevelItem> extends AbstractItem implements TopLevelItem, ItemGroup<I>, Saveable, ViewGroup, StaplerFallback, ModelObjectWithChildren, BuildableItem, Queue.FlyweightTask {

    private static final Logger LOGGER = Logger.getLogger(ComputedFolder.class.getName());

    private transient Map<String,I> items;
    private transient ViewGroupMixIn viewGroupMixIn;
    private CopyOnWriteArrayList<View> views;
    private volatile ViewsTabBar viewsTabBar;
    private volatile String primaryView;
    private transient @CheckForNull FolderComputation<I> computation;

    protected ComputedFolder(ItemGroup parent, String name) {
        super(parent, name);
        init();
        items = new CopyOnWriteMap.Tree<String,I>(CaseInsensitiveComparator.INSTANCE);
    }

    @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        init();
        items = ItemGroupMixIn.loadChildren(this, getJobsDir(), new Function1<String,I>() {
            @Override public String call(I child) {
                // TODO Folder.onLoad prints progress here
                return child.getName(); // TODO loadChildren does not support decoding folder names
            }
        });
    }

    private void init() {
        viewGroupMixIn = new ViewGroupMixIn(this) {
            @Override protected List<View> views() {
                return views;
            }
            @Override protected String primaryView() {
                return primaryView;
            }
            @Override protected void primaryView(String newName) {
                primaryView = newName;
            }
        };
        if (views == null) {
            views = new CopyOnWriteArrayList<View>();
        }
        if (views.isEmpty()) {
            ListView lv = new ListView("All", this);
            views.add(lv);
            lv.setIncludeRegex(".*");
        }
        if (viewsTabBar == null) {
            viewsTabBar = new DefaultViewsTabBar();
        }
        if (primaryView == null) {
            primaryView = views.get(0).getViewName();
        }
        loadComputation();
    }

    protected abstract Map<String,I> computeChildren(TaskListener listener) throws IOException, InterruptedException;

    protected abstract void updateExistingItem(I existing, I replacement);

    /**
     * Recreates children synchronously.
     */
    final void updateChildren(TaskListener listener) throws IOException, InterruptedException {
        Set<String> deadItems = new HashSet<String>(items.keySet());
        for (Map.Entry<String,I> entry : computeChildren(listener).entrySet()) {
            String childName = entry.getKey();
            I replacement = entry.getValue();
            I existing = items.get(childName);
            if (existing != null) {
                updateExistingItem(existing, replacement);
                deadItems.remove(childName);
            } else {
                replacement.onCreatedFromScratch();
                replacement.save();
                deadItems.remove(childName);
                items.put(childName, replacement);
            }
        }
        for (String childName : deadItems) {
            I existing = items.get(childName);
            // TODO we probably to define a DeadSourceStrategy and this needs to become a protected method somehow
            existing.delete();
            items.remove(childName);
        }
    }

    private synchronized void loadComputation() {
        try {
            FileUtils.forceMkdir(getComputationDir());
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        computation = new FolderComputation<I>(this, null);
        XmlFile file = computation.getDataFile();
        if (file != null && file.exists()) {
            try {
                file.unmarshal(computation);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
            }
        } else if (isBuildable()) { // first time
            scheduleBuild();
        }
    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        JSONObject json = req.getSubmittedForm();

        description = json.getString("description");
        displayName = Util.fixEmpty(json.optString("displayNameOrNull"));
        if (json.has("viewsTabBar")) {
            viewsTabBar = req.bindJSON(ViewsTabBar.class, json.getJSONObject("viewsTabBar"));
        }

        if (json.has("primaryView")) {
            primaryView = json.getString("primaryView");
        }

        submit(req, rsp);

        save();

        // TODO boilerplate; need to consider ProjectNamingStrategy
        String newName = json.getString("name");
        if (newName != null && !newName.equals(name)) {
            Jenkins.checkGoodName(newName);
            // TODO need to create rename.jelly
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
        } else {
            FormApply.success(".").generateResponse(req, rsp, this);
        }
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {}

    @Override public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    // TODO boilerplate like this should not be necessary: JENKINS-22936
    @RequirePOST
    public void doDoRename(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (!hasPermission(CONFIGURE)) {
            checkPermission(CREATE);
            checkPermission(DELETE);
        }
        String newName = req.getParameter("newName");
        Jenkins.checkGoodName(newName);
        // TODO reject if isBuilding
        renameTo(newName);
        rsp.sendRedirect2("../" + newName);
    }

    @Override public synchronized void save() throws IOException {
        super.save();
        // TODO should this not just be done in AbstractItem?
        ItemListener.fireOnUpdated(this);
        // TODO we could perhaps be more discriminating here.
        scheduleBuild();
    }

    @Override public I getItem(String name) throws AccessDeniedException {
        if (items == null) {
            return null;
        }
        I item = items.get(name);
        if (item == null) {
            return null;
        }
        if (!item.hasPermission(Item.READ)) {
            if (item.hasPermission(Item.DISCOVER)) {
                throw new AccessDeniedException("Please log in to access " + name);
            }
            return null;
        }
        return item;
    }

    @Override public Collection<I> getItems() {
        List<I> viewableItems = new ArrayList<I>();
        for (I item : items.values()) {
            if (item.hasPermission(Item.READ)) {
                viewableItems.add(item);
            }
        }
        return viewableItems;
    }

    @Override public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>();
        for (I i : getItems()) {
            jobs.addAll(i.getAllJobs());
        }
        return jobs;
    }

    protected abstract File getJobsDir();

    @Override public File getRootDirFor(I child) {
        File dir = new File(getJobsDir(), /* TODO see comment regarding loadChildren and encoding */child.getName());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.log(Level.WARNING, "Could not create directory {0}", dir);
        }
        return dir;
    }

    @Override public void onRenamed(I item, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override public void onDeleted(I item) throws IOException {
        ItemListener.fireOnDeleted(item);
        items.remove(item.getName());
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        for (I i : new ArrayList<I>(items.values())) {
            i.delete();
        }
        super.performDelete();
    }

    @Override public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    @Override public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    @Override public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    @Override public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    @Override public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
    }

    @Override public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    // TODO perhaps need doCreateView (+ newView.jelly) etc. to add new views

    @Override public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    @Override public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    @Override public List<Action> getViewActions() {
        return Collections.emptyList();
    }

    @Override public Object getStaplerFallback() {
        return getPrimaryView();
    }

    @Override public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) {
        ContextMenu menu = new ContextMenu();
        for (View view : getViews()) {
            menu.add(view.getAbsoluteUrl(), view.getDisplayName());
        }
        return menu;
    }

    @Override protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new CollectionSearchIndex<TopLevelItem>() {
            @Override protected SearchItem get(String key) {
                Jenkins j = Jenkins.getInstance();
                return j != null ? j.getItem(key, grp()) : null;
            }
            @Override protected Collection<TopLevelItem> all() {
                return Items.getAllItems(grp(), TopLevelItem.class);
            }
            @Override protected String getName(TopLevelItem j) {
                return j.getRelativeNameFrom(grp());
            }
            /** Disambiguates calls that otherwise would match {@link Item} too. */
            private ItemGroup<?> grp() {
                return ComputedFolder.this;
            }
        });
    }

    // TODO handle rename of this folder (cf. JENKINS-22936)

    // TODO public HealthReport getBuildHealth() & public List<HealthReport> getBuildHealthReports()

    // TODO List<Trigger> and Cron

    public boolean isBuildable() {
        return true;
    }

    @RequirePOST
    public HttpResponse doBuild(@QueryParameter TimeDuration delay) {
        checkPermission(BUILD);
        if (!isBuildable()) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, new IOException(getFullName() + " cannot be indexed"));
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

    @Override public boolean scheduleBuild() {
        return scheduleBuild2(0) != null;
    }

    @Override public boolean scheduleBuild(Cause c) {
        return scheduleBuild2(0, new CauseAction(c)) != null;
    }

    @Override public boolean scheduleBuild(int quietPeriod) {
        return scheduleBuild2(quietPeriod) != null;
    }

    @Override public boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, new CauseAction(c)) != null;
    }

    @Override public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Deprecated
    @Override public String getWhyBlocked() {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
        return causeOfBlockage == null ? null : causeOfBlockage.getShortDescription();
    }

    @Override public CauseOfBlockage getCauseOfBlockage() {
        final FolderComputation<I> c = computation;
        if (c != null && c.isBuilding()) {
            return CauseOfBlockage.fromMessage(Messages._ComputedFolder_already_computing());
        }
        return null;
    }

    @Override public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Override public boolean isConcurrentBuild() {
        return false;
    }

    @Override public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    @Override public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    @Override public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

    @Override public Label getAssignedLabel() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getSelfLabel();
    }

    @Override public Node getLastBuiltOn() {
        return Jenkins.getInstance();
    }

    @Override public long getEstimatedDuration() {
        return computation == null ? -1 : computation.getEstimatedDuration();
    }

    @Override public final FolderComputation<I> createExecutable() throws IOException {
        FolderComputation<I> c = createComputation(computation);
        computation = c;
        return c;
    }

    protected @Nonnull FolderComputation<I> createComputation(@CheckForNull FolderComputation<I> previous) {
        return new FolderComputation<I>(this, previous);
    }

    @Override public Queue.Task getOwnerTask() {
        return this;
    }

    @Override public Object getSameNodeConstraint() {
        return null;
    }

    @Override public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    File getComputationDir() {
        return new File(getRootDir(), "computation");
    }

    /** URL binding and other purposes. */
    public @CheckForNull FolderComputation<I> getComputation() {
        return computation;
    }

}
