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

package com.cloudbees.hudson.plugins.folder;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon;
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Util;
import static hudson.Util.fixEmpty;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.AllView;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.Item;
import static hudson.model.Item.CONFIGURE;
import static hudson.model.Item.CREATE;
import static hudson.model.Item.DELETE;
import hudson.model.ItemGroup;
import static hudson.model.ItemGroupMixIn.loadChildren;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.Function1;
import hudson.util.HttpResponses;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A general-purpose {@link ItemGroup}.
 * Base for {@link Folder} and {@link ComputedFolder}.
 * @since FIXME
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public abstract class AbstractFolder<I extends TopLevelItem> extends AbstractItem implements TopLevelItem, ItemGroup<I>, ViewGroup, StaplerFallback, ModelObjectWithChildren, StaplerOverridable {

    private static final Logger LOGGER = Logger.getLogger(AbstractFolder.class.getName());

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
        scan(new File(Jenkins.getActiveInstance().getRootDir(), "jobs"), 0);
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
            File jobs = new File(project, "jobs"); // cf. getJobsDir
            if (jobs.isDirectory()) {
                scan(jobs, depth + 1);
            }
        }
    }

    /** Child items, keyed by {@link Item#getName}. */
    protected transient Map<String,I> items = new CopyOnWriteMap.Tree<String,I>(CaseInsensitiveComparator.INSTANCE);

    private DescribableList<AbstractFolderProperty<?>,AbstractFolderPropertyDescriptor> properties;

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

    private FolderIcon icon;

    /** Subclasses should also call {@link #init}. */
    protected AbstractFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected void init() {
        if (properties == null) {
            properties = new DescribableList<AbstractFolderProperty<?>,AbstractFolderPropertyDescriptor>(this);
        }
        for (AbstractFolderProperty p : properties) {
            p.setOwner(this);
        }
        if (icon == null) {
            icon = new StockFolderIcon();
        }
        if (views == null) {
            views = new CopyOnWriteArrayList<View>();
        }
        if (views.isEmpty()) {
            try {
                initViews(views);
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
        viewGroupMixIn = new ViewGroupMixIn(this) {
            @Override
            protected List<View> views() {
                return views;
            }
            @Override
            protected String primaryView() {
                return primaryView;
            }
            @Override
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

    protected void initViews(List<View> views) throws IOException {
        AllView v = new AllView("All", this);
        views.add(v);
        v.save();
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
                    this.items = ((AbstractFolder) current).items;
                }
            }

            items = loadChildren(this, getJobsDir(), new Function1<String,I>() {
                @Override
                public String call(I item) {
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
                    // TODO loadChildren does not support decoding folder names
                    return item.getName();
                }
            });
        } finally {
            t.setName(n);
        }
    }

    @Override
    public AbstractFolderDescriptor getDescriptor() {
        // Seems to work even though AbstractFolder is not Describable, hmm
        return (AbstractFolderDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    /**
     * May be used to enumerate or remove properties.
     * To add properties, use {@link #addProperty}.
     */
    public DescribableList<AbstractFolderProperty<?>,AbstractFolderPropertyDescriptor> getProperties() {
        return properties;
    }

    @SuppressWarnings("rawtypes") // else setOwner will not compile
    public void addProperty(AbstractFolderProperty p) throws IOException {
        if (!p.getDescriptor().isApplicable(getClass())) {
            throw new IllegalArgumentException(p.getClass().getName() + " cannot be applied to " + getClass().getName());
        }
        p.setOwner(this);
        properties.add(p);
    }

    /** May be overridden, but {@link #loadJobTotal} will be inaccurate in that case. */
    protected File getJobsDir() {
        return new File(getRootDir(), "jobs");
    }

    protected final File getRootDirFor(String name) {
        return new File(getJobsDir(), name);
    }

    @Override
    public File getRootDirFor(I child) {
        File dir = getRootDirFor(child.getName()); // TODO see comment regarding loadChildren and encoding
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.log(Level.WARNING, "Could not create directory {0}", dir);
        }
        return dir;
    }

    @Override
    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * For URL binding.
     * @see #getUrlChildPrefix
     */
    public I getJob(String name) {
        return getItem(name);
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, getDescriptor().getDisplayName());
    }

    /**
     * Overrides from job properties.
     */
    @Override
    public Collection<?> getOverrides() {
        List<Object> r = new ArrayList<Object>();
        for (AbstractFolderProperty<?> p : properties) {
            r.addAll(p.getItemContainerOverrides());
        }
        return r;
    }

    public void addView(View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    @Override
    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    @Override
    public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    @Override
    public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    @Exported
    @Override
    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    @Exported
    @Override
    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
    }

    public void setPrimaryView(View v) {
        primaryView = v.getViewName();
    }

    @Override
    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    @Override
    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    @Override
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    @Override
    public List<Action> getViewActions() {
        return Collections.emptyList();
    }

    /**
     * Fallback to the primary view.
     */
    @Override
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new CollectionSearchIndex<TopLevelItem>() {
            @Override
            protected SearchItem get(String key) {
                return Jenkins.getActiveInstance().getItem(key, grp());
            }
            @Override
            protected Collection<TopLevelItem> all() {
                return Items.getAllItems(grp(), TopLevelItem.class);
            }
            @Override
            protected String getName(TopLevelItem j) {
                return j.getRelativeNameFrom(grp());
            }
            /** Disambiguates calls that otherwise would match {@link Item} too. */
            private ItemGroup<?> grp() {
                return AbstractFolder.this;
            }
        });
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) {
        ContextMenu menu = new ContextMenu();
        for (View view : getViews()) {
            menu.add(view.getAbsoluteUrl(),view.getDisplayName());
        }
        return menu;
    }

    public synchronized void doCreateView(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, ParseException, Descriptor.FormException {
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
        if (healthMetrics == null || healthMetrics.isEmpty()) {
            return Collections.<HealthReport>emptyList();
        }
        List<HealthReport> reports = new ArrayList<HealthReport>();
        List<FolderHealthMetric.Reporter> reporters = new ArrayList<FolderHealthMetric.Reporter>(healthMetrics.size());
        for (FolderHealthMetric metric : healthMetrics) {
            reporters.add(metric.reporter());
        }
        for (AbstractFolderProperty<?> p : getProperties()) {
            for (FolderHealthMetric metric : p.getHealthMetrics()) {
                reporters.add(metric.reporter());
            }
        }
        Stack<Iterable<? extends Item>> stack = new Stack<Iterable<? extends Item>>();
        stack.push(getItems());
        while (!stack.isEmpty()) {
            for (Item item : stack.pop()) {
                for (FolderHealthMetric.Reporter reporter : reporters) {
                    reporter.observe(item);
                }
                if (item instanceof Folder) {
                    stack.push(((Folder) item).getItems());
                }
            }
        }
        for (FolderHealthMetric.Reporter reporter : reporters) {
            reports.addAll(reporter.report());
        }
        for (AbstractFolderProperty<?> p : getProperties()) {
            reports.addAll(p.getHealthReports());
        }

        Collections.sort(reports);
        return reports;
    }

    public DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor> getHealthMetrics() {
        return healthMetrics;
    }

    public HttpResponse doLastBuild(StaplerRequest req) {
        return HttpResponses.redirectToDot();
    }

    @Override
    public ACL getACL() {
        AuthorizationStrategy as = Jenkins.getActiveInstance().getAuthorizationStrategy();
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
     * Gets the icon used for this folder.
     */
    public FolderIcon getIcon() {
        return icon;
    }

    public void setIcon(FolderIcon icon) {
        this.icon = icon;
        icon.setOwner(this);
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

    @Exported(name="jobs")
    @Override
    public Collection<I> getItems() {
        List<I> viewableItems = new ArrayList<I>();
        for (I item : items.values()) {
            if (item.hasPermission(Item.READ)) {
                viewableItems.add(item);
            }
        }
        return viewableItems;
    }

    @Override
    public I getItem(String name) throws AccessDeniedException {
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

    @SuppressWarnings("deprecation")
    @Override
    public void onRenamed(TopLevelItem item, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, (I) item);
        // For compatibility with old views:
        for (View v : views) {
            v.onJobRenamed(item, oldName, newName);
        }
        save();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDeleted(TopLevelItem item) throws IOException {
        ItemListener.fireOnDeleted(item);
        items.remove(item.getName());
        // For compatibility with old views:
        for (View v : views) {
            v.onJobRenamed(item, item.getName(), null);
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
                throw new IOException("Failed to delete " + i.getFullDisplayName(), e);
            }
        }
        super.performDelete();
    }

    @Override public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        super.save();
        // TODO should this not just be done in AbstractItem?
        ItemListener.fireOnUpdated(this);
    }

    /**
     * Renames this item container.
     */
    @Override
    public void renameTo(String newName) throws IOException {
        super.renameTo(newName);
    }

    @Override
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        JSONObject json = req.getSubmittedForm();

        BulkChange bc = new BulkChange(this);
        try {
            description = json.getString("description");
            displayName = Util.fixEmpty(json.optString("displayNameOrNull"));
            if (json.has("viewsTabBar")) {
                viewsTabBar = req.bindJSON(ViewsTabBar.class, json.getJSONObject("viewsTabBar"));
            }

            if (json.has("primaryView")) {
                primaryView = json.getString("primaryView");
            }

            properties.rebuild(req, json, getDescriptor().getPropertyDescriptors());
            for (AbstractFolderProperty p : properties) {
                p.setOwner(this);
            }

            healthMetrics.replaceBy(req.bindJSONToList(FolderHealthMetric.class, json.get("healthMetrics")));

            icon = req.bindJSON(FolderIcon.class, req.getSubmittedForm().getJSONObject("icon"));
            icon.setOwner(this);

            submit(req, rsp);

            save();
            bc.commit();
        } finally {
            bc.abort();
        }

        // TODO boilerplate; need to consider ProjectNamingStrategy
        String newName = json.getString("name");
        if (newName != null && !newName.equals(name)) {
            // check this error early to avoid HTTP response splitting.
            Jenkins.checkGoodName(newName);
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
        } else {
            FormApply.success(".").generateResponse(req, rsp, this);
        }
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {}

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

        // wouldn't building job inside Folder (or isBuilding, for ComputedFolder) be impacted here? Shall we then check as Job.doDoRename does

        renameTo(newName);
        rsp.sendRedirect2("../" + newName);
    }

}
