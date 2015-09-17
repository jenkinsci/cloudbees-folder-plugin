/*
 * The MIT License
 *
 * Copyright 2013 CloudBees.
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

package com.cloudbees.hudson.plugins.folder;

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon;
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty;
import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Failure;
import hudson.model.HealthReport;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.ItemVisitor;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.AllView;
import hudson.model.ListView;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.Function1;
import hudson.util.HttpResponses;
import hudson.util.IOException2;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ListViewColumn;
import hudson.views.ViewJobFilter;
import hudson.views.ViewsTabBar;
import java.lang.reflect.InvocationTargetException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import static hudson.model.ItemGroupMixIn.loadChildren;
import hudson.model.TransientProjectActionFactory;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.util.FormApply;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link Item} that contains other {@link Item}s, without modeling dependency.
 */
public class Folder extends AbstractItem
        implements DirectlyModifiableTopLevelItemGroup, ViewGroup, TopLevelItem, StaplerOverridable, StaplerFallback, ModelObjectWithChildren {

    /**
     * @see #getNewPronoun
     * @since 4.0
     */
    public static final AlternativeUiTextProvider.Message<Folder> NEW_PRONOUN = new AlternativeUiTextProvider.Message<Folder>();

    private DescribableList<FolderProperty<?>, FolderPropertyDescriptor> properties;

    /**
     * All {@link Item}s keyed by their {@link Item#getName() name} s.
     */
    /*package*/ transient Map<String, TopLevelItem> items =
            new CopyOnWriteMap.Tree<String, TopLevelItem>(CaseInsensitiveComparator.INSTANCE);


    /**
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    private transient DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    private transient DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> filters;

    private FolderIcon icon;

    private transient /*final*/ ItemGroupMixIn mixin;

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link Folder}, such as from properties.
     * <p/>
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<Action>();

    /**
     * {@link View}s.
     */
    private /*almost final*/ CopyOnWriteArrayList<View> views;

    /**
     * Currently active Views tab bar.
     */
    private volatile ViewsTabBar viewsTabBar;

    /**
     * Name of the primary view.
     */
    private volatile String primaryView;

    private transient /*almost final*/ ViewGroupMixIn viewGroupMixIn;

    private DescribableList<FolderHealthMetric,FolderHealthMetricDescriptor> healthMetrics;

    public Folder(ItemGroup parent, String name) {
        super(parent, name);
        init();
    }

    private static long loadingTick;
    private static final AtomicInteger jobTotal = new AtomicInteger();
    private static final AtomicInteger jobEncountered = new AtomicInteger();
    private static final AtomicBoolean loadJobTotalRan = new AtomicBoolean();
    private static final int TICK_INTERVAL = 15000;

    @Initializer(before=InitMilestone.JOB_LOADED, fatal=false)
    public static void loadJobTotal() {
        if (!loadJobTotalRan.compareAndSet(false, true)) {
            return; // TODO why does Jenkins run the initializer many times?!
        }
        scan(new File(Jenkins.getInstance().getRootDir(), "jobs"), 0);
        // TODO reset count after reload config from disk (otherwise goes up to 200% etc.)
    }
    private static void scan(File d, int depth) {
        File[] projects = d.listFiles();
        if (projects == null) {
            return;
        }
        for (File project : projects) {
            if (!new File(project, "config.xml").isFile()) {
                continue;
            }
            if (depth > 0) {
                jobTotal.incrementAndGet();
            }
            File jobs = new File(project, "jobs");
            if (jobs.isDirectory()) {
                scan(jobs, depth + 1);
            }
        }
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        init();
        final Thread t = Thread.currentThread();
        String n = t.getName();
        try {
            if (items ==null) {
                // when Jenkins is getting reloaed, we want children being loaded to be able to find
                // existing items that they will be overriding. This is necessary for  them to correctly
                // keep the running builds, for example. See ZD-13067
                Item current = parent.getItem(name);
                if (current!=null && current.getClass()==getClass()) {
                    this.items = ((Folder)current).items;
                }
            }

            items = loadChildren(this, getJobsDir(), new Function1<String, Item>() {
                public String call(Item item) {
                    String fullName = item.getFullName();
                    t.setName("Loading job " + fullName);
                    float percentage = 100.0f * jobEncountered.incrementAndGet() / Math.max(1, jobTotal.get());
                    long now = System.currentTimeMillis();
                    if (loadingTick == 0) {
                        loadingTick = now;
                    } else if (now - loadingTick > TICK_INTERVAL) {
                        LOGGER.log(Level.INFO, String.format("Loading job %s (%.1f%%)", fullName, percentage));
                        loadingTick = now;
                    }
                    return item.getName();
                }
            });
        } finally {
            t.setName(n);
        }
        updateTransientActions();
    }

    private void init() {
        if (icon == null) {
            icon = new StockFolderIcon();
        }
        if (properties == null) {
            properties = new DescribableList<FolderProperty<?>, FolderPropertyDescriptor>(this);
        }
        for (FolderProperty p : properties) {
            p.setOwner(this);
        }
        if (views == null) {
            views = new CopyOnWriteArrayList<View>();
        }
        if (views.size() == 0) {
            try {
                if (columns != null || filters != null) {
                    // we're loading an ancient config
                    if (columns == null) {
                        columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,
                                ListViewColumn.createDefaultInitialColumnList());
                    }
                    if (filters == null) {
                        filters = new DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>>(this);
                    }
                    ListView lv = new ListView("All", this);
                    views.add(lv);
                    lv.getColumns().replaceBy(columns.toList());
                    lv.getJobFilters().replaceBy(filters.toList());
                    lv.setIncludeRegex(".*");
                    lv.save();
                } else {
                    AllView v = new AllView("All", this);
                    views.add(v);
                    v.save();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to set up the initial view", e);
            }
        }
        if (viewsTabBar == null) {
            viewsTabBar = new DefaultViewsTabBar();
        }
        if (primaryView == null) {
            primaryView = views.get(0).getViewName();
        }
        mixin = new MixInImpl(this);
        viewGroupMixIn = new ViewGroupMixIn(this) {
            protected List<View> views() {
                return views;
            }

            protected String primaryView() {
                return primaryView;
            }

            protected void primaryView(String name) {
                primaryView = name;
            }
        };
        if (healthMetrics == null) {
            List<FolderHealthMetric> metrics = new ArrayList<FolderHealthMetric>();
            for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
                FolderHealthMetric metric = d.createDefault();
                if (metric != null) {
                    metrics.add(metric);
                }
            }
            healthMetrics = new DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor>(this, metrics);
        }
    }

    public void addView(View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    @Exported
    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    @Exported
    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
    }

    public void setPrimaryView(View v) {
        primaryView = v.getViewName();
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    public List<Action> getViewActions() {
        return Collections.emptyList();
    }

    @Override
    public void onCreatedFromScratch() {
        updateTransientActions();
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, getDescriptor().getDisplayName());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p/>
     * Note that this method returns a read-only view of {@link Action}s.
     *
     * @see TransientProjectActionFactory
     */
    @SuppressWarnings("deprecation")
    @Override
    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    /**
     * effectively deprecated. Since using updateTransientActions correctly
     * under concurrent environment requires a lock that can too easily cause deadlocks.
     * <p/>
     * <p/>
     * Override {@link #createTransientActions()} instead.
     */
    protected void updateTransientActions() {
        transientActions = createTransientActions();
    }

    protected List<Action> createTransientActions() {
        Vector<Action> ta = new Vector<Action>();

        for (TransientFolderActionFactory tpaf : TransientFolderActionFactory.all()) {
            ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
        }
        for (FolderProperty<?> p: properties) {
            ta.addAll(Util.fixNull(p.getFolderActions()));
        }

        return ta;
    }

    @Override
    public ACL getACL() {
        AuthorizationStrategy as = Hudson.getInstance().getAuthorizationStrategy();
        // TODO this should be an extension point, or ideally matrix-auth would have an optional dependency on cloudbees-folder
        if (as.getClass().getName().equals("hudson.security.ProjectMatrixAuthorizationStrategy")) {
            AuthorizationMatrixProperty p = getProperties().get(AuthorizationMatrixProperty.class);
            if (p != null) {
                return p.getACL().newInheritingACL(((ProjectMatrixAuthorizationStrategy) as).getACL(getParent()));
            }
        }
        return super.getACL();
    }

    /**
     * Used in "New Job" side menu.
     * @see #NEW_PRONOUN
     */
    public String getNewPronoun() {
        return AlternativeUiTextProvider.get(NEW_PRONOUN, this, Messages.Folder_DefaultPronoun());
    }

    /**
     * Gets the icon used for this folder.
     */
    public FolderIcon getIcon() {
        return icon;
    }

    public void setIcon(FolderIcon icon) {
        this.icon = icon;
        icon.setFolder(this);
    }

    public FolderIcon getIconColor() {
        return icon;
    }

    @Override
    public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>();
        for (Item i : getItems()) {
            jobs.addAll(i.getAllJobs());
        }
        return jobs;
    }

    /**
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,
                ListViewColumn.createDefaultInitialColumnList());
    }

    /**
     * Renames this item container.
     */
    @Override
    public void renameTo(String newName) throws IOException {
        super.renameTo(newName);
    }

    public DescribableList<FolderProperty<?>, FolderPropertyDescriptor> getProperties() {
        return properties;
    }

    public void addProperty(FolderProperty p) throws IOException {
        p.setOwner(this);
        properties.add(p);
    }

    @Exported(name="jobs")
    public Collection<TopLevelItem> getItems() {
        List<TopLevelItem> viewableItems = new ArrayList<TopLevelItem>();
        for (TopLevelItem item : items.values()) {
            if (item.hasPermission(Item.READ)) {
                viewableItems.add(item);
            }
        }

        return viewableItems;
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    public TopLevelItem getItem(String name) {
        if (items == null) {
            return null;
        }
        TopLevelItem item = items.get(name);
        if (item == null) {
            return null;
        }
        if (!item.hasPermission(Item.READ)) {
            if (item.hasPermission(Item.DISCOVER)) {
                throw new AccessDeniedException("Please login to access job " + name);
            }
            return null;
        }
        return item;
    }

    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    private File getRootDirFor(String name) {
        return new File(getJobsDir(), name);
    }

    private File getJobsDir() {
        return new File(getRootDir(), "jobs");
    }

    public void onRenamed(TopLevelItem item, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, item);
        // For compatibility with old views:
        for (View v : views) {
            v.onJobRenamed(item, oldName, newName);
        }
        save();
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        // delete individual items first
        for (Item i : new ArrayList<Item>(items.values())) {
            try {
                i.delete();
            } catch (AbortException e) {
                throw (AbortException) new AbortException(
                        "Failed to delete " + i.getFullDisplayName() + " : " + e.getMessage()).initCause(e);
            } catch (IOException e) {
                throw new IOException2("Failed to delete " + i.getFullDisplayName(), e);
            }
        }
        super.performDelete();
    }

    /**
     * If copied, copy folder contents.
     */
    @Override
    public void onCopiedFrom(Item _src) {
        Folder src = (Folder) _src;
        for (TopLevelItem item : src.getItems()) {
            try {
                copy(item, item.getName());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to copy " + src + " into " + this, e);
            }
        }
    }

    public void onDeleted(TopLevelItem item) throws IOException {
        ItemListener.fireOnDeleted(item);
        items.remove(item.getName());
        // For compatibility with old views:
        for (View v : views) {
            v.onJobRenamed(item, item.getName(), null);
        }
        save();
    }

    public TopLevelItem doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        TopLevelItem nue = mixin.createTopLevelItem(req, rsp);
        if (!isAllowedChild(nue)) {
            // TODO would be better to intercept it before creation, if mode is set
            try {
                nue.delete();
            } catch (InterruptedException x) {
                throw (IOException) new IOException(x.toString()).initCause(x);
            }
            throw new IOException("forbidden child type");
        }
        return nue;
    }

    public synchronized void doCreateView(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, ParseException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req, rsp, this));
    }

    /**
     * Checks if a top-level view with the given name exists.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        if (view == null) {
            return FormValidation.ok();
        }

        if (getView(view) == null) {
            return FormValidation.ok();
        } else {
            return FormValidation.error(jenkins.model.Messages.Hudson_ViewAlreadyExists(view));
        }
    }

    public FormValidation doCheckJobName(@QueryParameter String value) {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        checkPermission(Item.CREATE);

        if (fixEmpty(value) == null) {
            return FormValidation.ok();
        }

        try {
            Hudson.checkGoodName(value);
            value = value.trim();
            if (getItem(value) != null) {
                throw new Failure(hudson.model.Messages.Hudson_JobAlreadyExists(value));
            }

            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) {
        ContextMenu menu = new ContextMenu();
        for (View view : getViews()) {
            menu.add(view.getAbsoluteUrl(),view.getDisplayName());
        }
        return menu;
    }

    /**
     * Copies an existing {@link TopLevelItem} to into this folder with a new name.
     */
    public <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        if (!isAllowedChild(src)) {
            throw new IOException("forbidden child type");
        }
        return mixin.copy(src, name);
    }

    public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        TopLevelItem nue = mixin.createProjectFromXML(name, xml);
        if (!isAllowedChild(nue)) {
            try {
                nue.delete();
            } catch (InterruptedException x) {
                throw (IOException) new IOException(x.toString()).initCause(x);
            }
            throw new IOException("forbidden child type");
        }
        return nue;
    }

    public <T extends TopLevelItem> T createProject(Class<T> type, String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor) Hudson.getInstance().getDescriptor(type), name));
    }

    public TopLevelItem createProject(TopLevelItemDescriptor type, String name) throws IOException {
        return createProject(type, name, true);
    }

    public TopLevelItem createProject(TopLevelItemDescriptor type, String name, boolean notify) throws IOException {
        if (!isAllowedChildDescriptor(type)) {
            throw new IOException("forbidden child type");
        }
        return mixin.createProject(type, name, notify);
    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        JSONObject json = req.getSubmittedForm();

        description = json.getString("description");
        displayName = Util.fixEmpty(json.optString("displayNameOrNull"));
        if (json.has("viewsTabBar")) {
            viewsTabBar = req.bindJSON(ViewsTabBar.class, json.getJSONObject("viewsTabBar"));
        }

        if (json.has("primaryView")) {
            setPrimaryView(viewGroupMixIn.getView(json.getString("primaryView")));
        }

        properties.rebuild(req, json, getDescriptor().getPropertyDescriptors());
        for (FolderProperty p : properties) // TODO: push this to the subtype of property descriptors
        {
            p.setOwner(this);
        }

        healthMetrics.replaceBy(req.bindJSONToList(FolderHealthMetric.class, json.get("healthMetrics")));

        icon = req.bindJSON(FolderIcon.class, json.getJSONObject("icon"));
        icon.setFolder(this);

        updateTransientActions();

        save();

        String newName = json.getString("name");
        if (newName != null && !newName.equals(name)) {
            // check this error early to avoid HTTP response splitting.
            Hudson.checkGoodName(newName);
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
        } else {
            FormApply.success(".").generateResponse(req, rsp, this);
        }
    }

    @Override public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    // TODO boilerplate like this should not be necessary: JENKINS-22936
    @RequirePOST
    public void doDoRename(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

        if (!hasPermission(CONFIGURE)) {
            // rename is essentially delete followed by a create
            checkPermission(CREATE);
            checkPermission(DELETE);
        }

        String newName = req.getParameter("newName");
        Jenkins.checkGoodName(newName);

        // wouldn't building job inside folder be impacted here? Shall we then check as Job.doDoRename does

        renameTo(newName);
        rsp.sendRedirect2("../" + newName);
    }

    /**
     * Overrides from job properties.
     */
    public Collection<?> getOverrides() {
        List<Object> r = new ArrayList<Object>();
        for (FolderProperty<?> p : properties) {
            r.addAll(p.getItemContainerOverrides());
        }
        return r;
    }

    /**
     * Items that can be created in this {@link Folder}.
     * @see FolderAddFilter
     */
    public List<TopLevelItemDescriptor> getItemDescriptors() {
        List<TopLevelItemDescriptor> r = new ArrayList<TopLevelItemDescriptor>();
        for (TopLevelItemDescriptor tid : Items.all()) {
            if (isAllowedChildDescriptor(tid)) {
                r.add(tid);
            }
        }
        return r;
    }

    /**
     * Returns true if the specified descriptor type is allowed for this container.
     */
    public boolean isAllowedChildDescriptor(TopLevelItemDescriptor tid) {
        for (FolderProperty<?> p : properties) {
            if (!p.allowsParentToCreate(tid)) {
                return false;
            }
        }
        // TODO Jenkins 1.607+ use method directly
        try {
            return Boolean.TRUE.equals(tid.getClass().getMethod("isApplicableIn", ItemGroup.class).invoke(this));
        } catch (NoSuchMethodException e) {
            // ignore
        } catch (InvocationTargetException e) {
            // ignore
        } catch (IllegalAccessException e) {
            // ignore
        }
        return true;
    }

    /** Historical synonym for {@link #canAdd}. */
    public boolean isAllowedChild(TopLevelItem tid) {
        for (FolderProperty<?> p : properties) {
            if (!p.allowsParentToHave(tid)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fallback to the primary view.
     */
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    @Override protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new CollectionSearchIndex<TopLevelItem>() {
            @Override protected SearchItem get(String key) {
                return Jenkins.getInstance().getItem(key, grp());
            }
            @Override protected Collection<TopLevelItem> all() {
                return Items.getAllItems(grp(), TopLevelItem.class);
            }
            @Override protected String getName(TopLevelItem j) {
                return j.getRelativeNameFrom(grp());
            }
            /** Disambiguates calls that otherwise would match {@link Item} too. */
            private ItemGroup<?> grp() {
                return Folder.this;
            }
        });
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor> getHealthMetrics() {
        return healthMetrics;
    }

    /**
     * Get the current health report for a folder.
     *
     * @return the health report. Never returns null
     */
    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport() : reports.get(0);
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        if (healthMetrics == null || healthMetrics.isEmpty()) return Collections.<HealthReport>emptyList();
        List<HealthReport> reports = new ArrayList<HealthReport>();
        List<FolderHealthMetric.Reporter> reporters = new ArrayList<FolderHealthMetric.Reporter>(healthMetrics.size());
        for (FolderHealthMetric metric: healthMetrics) {
            reporters.add(metric.reporter());
        }
        for (FolderProperty<?> p: getProperties()) {
            for (FolderHealthMetric metric: p.getHealthMetrics()) {
                reporters.add(metric.reporter());
            }
        }
        Stack<Iterable<? extends Item>> stack = new Stack<Iterable<? extends Item>>();
        stack.push(getItems());
        while (!stack.isEmpty()) {
            for (Item item : stack.pop()) {
                for (FolderHealthMetric.Reporter reporter: reporters) {
                    reporter.observe(item);
                }
                if (item instanceof Folder) {
                    stack.push(((Folder) item).getItems());
                }
            }
        }
        for (FolderHealthMetric.Reporter reporter: reporters) {
            reports.addAll(reporter.report());
        }
        for (FolderProperty<?> p: getProperties()) {
            reports.addAll(p.getHealthReports());
        }

        Collections.sort(reports);
        return reports;
    }

    public HttpResponse doLastBuild(StaplerRequest req) {
        return HttpResponses.redirectToDot();
    }

    @Override public boolean canAdd(TopLevelItem item) {
        return isAllowedChild(item);
    }

    @Override public <I extends TopLevelItem> I add(I item, String name) throws IOException, IllegalArgumentException {
        if (!canAdd(item)) {
            throw new IllegalArgumentException();
        }
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("already an item '" + name + "'");
        }
        items.put(item.getName(), item);
        return item;
    }

    @Override public void remove(TopLevelItem item) throws IOException, IllegalArgumentException {
        items.remove(item.getName());
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.Folder_DisplayName();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new Folder(parent, name);
        }

        /**
         * Properties that can be configured for this type of {@link Folder} subtype.
         */
        public List<FolderPropertyDescriptor> getPropertyDescriptors() {
            List<FolderPropertyDescriptor> r = new ArrayList<FolderPropertyDescriptor>();
            for (FolderPropertyDescriptor d : FolderPropertyDescriptor.all()) {
                if (d.isApplicable((Class) clazz)) {
                    r.add(d);
                }
            }
            return r;
        }

        /**
         * Properties that can be configured for this type of {@link Folder} subtype.
         */
        public List<FolderHealthMetricDescriptor> getHealthMetricDescriptors() {
            List<FolderHealthMetricDescriptor> r = new ArrayList<FolderHealthMetricDescriptor>();
            for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
                if (d.isApplicable((Class) clazz)) {
                    r.add(d);
                }
            }
            return r;
        }

        /**
         * Auto-completion for the "copy from" field in the new job page.
         */
        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@AncestorInPath final Folder f,
                                                                      @QueryParameter final String value) {
            // TODO use ofJobNames but filter by isAllowedChild
            final AutoCompletionCandidates r = new AutoCompletionCandidates();

            abstract class VisitorImpl extends ItemVisitor {
                abstract String getPathNameOf(Item i);

                @Override
                public void onItemGroup(ItemGroup<?> group) {
                    super.onItemGroup(group);
                }

                @Override
                public void onItem(Item i) {
                    String name = getPathNameOf(i);
                    if (name.startsWith(value)) {
                        if (i instanceof TopLevelItem) {
                            TopLevelItem tli = (TopLevelItem) i;
                            if (f.isAllowedChild(tli)) {
                                r.add(name);
                            }
                        }
                    }

                    if (value.startsWith(name)) {
                        super.onItem(i);
                    }
                }
            }

            // absolute path name "/foo/bar/zot"
            new VisitorImpl() {
                String getPathNameOf(Item i) {
                    return '/' + i.getFullName();
                }
            }.walk();

            // relative path name "foo/bar/zot" from this folder
            // TODO: support ".." notation
            new VisitorImpl() {
                String baseName = f.getFullName();

                String getPathNameOf(Item i) {
                    return i.getFullName().substring(baseName.length() + 1);
                }
            }.onItemGroup(f);

            return r;
        }
    }

    private class MixInImpl extends ItemGroupMixIn {
        private MixInImpl(Folder parent) {
            super(parent, parent);
        }

        @Override
        protected void add(TopLevelItem item) {
            items.put(item.getName(), item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return Folder.this.getRootDirFor(name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Folder.class.getName());
}
