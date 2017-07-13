/*
 * The MIT License
 *
 * Copyright 2015-2016 CloudBees, Inc.
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
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import com.cloudbees.hudson.plugins.folder.views.DefaultFolderViewHolder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.AllView;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Failure;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.ModifiableViewGroup;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.WorkUnit;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.security.ACL;
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
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ProjectNamingStrategy;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static hudson.Util.fixEmpty;
import static hudson.model.queue.Executables.getParentOf;

/**
 * A general-purpose {@link ItemGroup}.
 * Base for {@link Folder} and {@link ComputedFolder}.
 * <p>
 * <b>Extending Folders UI</b><br>
 * As any other {@link Item} type, folder types support extension of UI via {@link Action}s.
 * These actions can be persisted or added via {@link TransientActionFactory}.
 * See <a href="https://wiki.jenkins-ci.org/display/JENKINS/Action+and+its+family+of+subtypes">this page</a>
 * for more details about actions.
 * In folders actions provide the following features:
 * <ul>
 *  <li>Left sidepanel hyperlink, which opens the page specified by action's {@code index.jelly}.</li>
 *  <li>Optional summary boxes on the main panel, which may be defined by {@code summary.jelly}.</li>
 * </ul>
 * @since 4.11-beta-1
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public abstract class AbstractFolder<I extends TopLevelItem> extends AbstractItem implements TopLevelItem, ItemGroup<I>, ModifiableViewGroup, StaplerFallback, ModelObjectWithChildren, StaplerOverridable {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AbstractFolder.class.getName());
    private static final Random ENTROPY = new Random();
    private static final int HEALTH_REPORT_CACHE_REFRESH_MIN = Math.max(10, Math.min(1440, Integer.getInteger(
            AbstractFolder.class.getName()+".healthReportCacheRefreshMin", 60
    )));

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

    private /*almost final*/ AbstractFolderViewHolder folderViews;

    /**
     * {@link View}s.
     */
    @Deprecated
    private transient /*almost final*/ CopyOnWriteArrayList<View> views;

    /**
     * Currently active Views tab bar.
     */
    @Deprecated
    private transient volatile ViewsTabBar viewsTabBar;

    /**
     * Name of the primary view.
     */
    @Deprecated
    private transient volatile String primaryView;

    private transient /*almost final*/ ViewGroupMixIn viewGroupMixIn;

    private DescribableList<FolderHealthMetric,FolderHealthMetricDescriptor> healthMetrics;

    private FolderIcon icon;

    private transient volatile long nextHealthReportsRefreshMillis;
    private transient volatile List<HealthReport> healthReports;

    /**
     * Subclasses should also call {@link #init}.
     *
     * @param parent the parent of this folder.
     * @param name   the name
     */
    protected AbstractFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected void init() {
        if (properties == null) {
            properties = new DescribableList<AbstractFolderProperty<?>,AbstractFolderPropertyDescriptor>(this);
        } else {
            properties.setOwner(this);
        }
        for (AbstractFolderProperty p : properties) {
            p.setOwner(this);
        }
        if (icon == null) {
            icon = newDefaultFolderIcon();
        }
        icon.setOwner(this);

        if (folderViews == null) {
            if (views != null && !views.isEmpty()) {
                if (primaryView != null) {
                    // TODO replace reflection with direct access once baseline core has JENKINS-38606 fix merged
                    try {
                        Method migrateLegacyPrimaryAllViewLocalizedName = AllView.class
                                .getMethod("migrateLegacyPrimaryAllViewLocalizedName", List.class, String.class);
                        primaryView =
                                (String) migrateLegacyPrimaryAllViewLocalizedName.invoke(null, views, primaryView);
                    } catch (NoSuchMethodException e) {
                        // ignore, Jenkins core does not have JENKINS-38606 fix merged
                    } catch (IllegalAccessException e) {
                        // ignore, Jenkins core does not have JENKINS-38606 fix merged
                    } catch (InvocationTargetException e) {
                        // ignore, Jenkins core does not have JENKINS-38606 fix merged
                    }
                }
                folderViews = new DefaultFolderViewHolder(views, primaryView, viewsTabBar == null ? newDefaultViewsTabBar()
                        : viewsTabBar);
            } else {
                folderViews = newFolderViewHolder();
            }
            views = null;
            primaryView = null;
            viewsTabBar = null;
        }
        viewGroupMixIn = new ViewGroupMixIn(this) {
            @Override
            protected List<View> views() {
                return folderViews.getViews();
            }
            @Override
            protected String primaryView() {
                String primaryView = folderViews.getPrimaryView();
                return primaryView == null ? folderViews.getViews().get(0).getViewName() : primaryView;
            }
            @Override
            protected void primaryView(String name) {
                folderViews.setPrimaryView(name);
            }
            @Override
            public void addView(View v) throws IOException {
                if (folderViews.isViewsModifiable()) {
                    super.addView(v);
                }
            }
            @Override
            public boolean canDelete(View view) {
                return folderViews.isViewsModifiable() && super.canDelete(view);
            }
            @Override
            public synchronized void deleteView(View view) throws IOException {
                if (folderViews.isViewsModifiable()) {
                    super.deleteView(view);
                }
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

    protected DefaultViewsTabBar newDefaultViewsTabBar() {
        return new DefaultViewsTabBar();
    }

    protected AbstractFolderViewHolder newFolderViewHolder() {
        CopyOnWriteArrayList views = new CopyOnWriteArrayList<View>();
        try {
            initViews(views);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to set up the initial view", e);
        }
        return new DefaultFolderViewHolder(views, null, newDefaultViewsTabBar());
    }

    protected FolderIcon newDefaultFolderIcon() {
        return new StockFolderIcon();
    }

    protected void initViews(List<View> views) throws IOException {
        AllView v = new AllView("All", this);
        views.add(v);
    }

    /**
     * {@inheritDoc}
     */
    // TODO remove once baseline has JENKINS-39404
    @Override
    @SuppressWarnings("deprecation")
    public void addAction(Action a) {
        super.getActions().add(a);
    }

    /**
     * {@inheritDoc}
     */
    // TODO remove once baseline has JENKINS-39404
    @Override
    @SuppressWarnings("deprecation")
    public void replaceAction(Action a) {
        addOrReplaceAction(a);
    }

    /**
     * Add an action, replacing any existing actions of the (exact) same class.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     *
     * @param a an action to add/replace
     * @return {@code true} if this actions changed as a result of the call
     * @since FIXME
     */
    // TODO remove once baseline has JENKINS-39404
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public boolean addOrReplaceAction(@Nonnull Action a) {
        if (a == null) {
            throw new IllegalArgumentException("Action must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<Action>(1);
        List<Action> current = super.getActions();
        boolean found = false;
        for (Action a2 : current) {
            if (!found && a.equals(a2)) {
                found = true;
            } else if (a2.getClass() == a.getClass()) {
                old.add(a2);
            }
        }
        current.removeAll(old);
        if (!found) {
            addAction(a);
        }
        return !found || !old.isEmpty();
    }

    /**
     * Remove an action.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     *
     * @param a an action to remove (if {@code null} then this will be a no-op)
     * @return {@code true} if this actions changed as a result of the call
     * @since FIXME
     */
    // TODO remove once baseline has JENKINS-39404
    @SuppressWarnings("deprecation")
    public boolean removeAction(@Nullable Action a) {
        if (a == null) {
            return false;
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        return super.getActions().removeAll(Collections.singleton(a));
    }

    /**
     * Removes any actions of the specified type.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     *
     * @param clazz the type of actions to remove
     * @return {@code true} if this actions changed as a result of the call
     * @since FIXME
     */
    // TODO remove once baseline has JENKINS-39404
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public boolean removeActions(@Nonnull Class<? extends Action> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Action type must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<Action>();
        List<Action> current = super.getActions();
        for (Action a : current) {
            if (clazz.isInstance(a)) {
                old.add(a);
            }
        }
        return current.removeAll(old);
    }

    /**
     * Replaces any actions of the specified type by the supplied action.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     *
     * @param clazz the type of actions to replace (note that the action you are replacing this with need not extend
     *              this class)
     * @param a     the action to replace with
     * @return {@code true} if this actions changed as a result of the call
     * @since FIXME
     */
    // TODO remove once baseline has JENKINS-39404
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public boolean replaceActions(@Nonnull Class<? extends Action> clazz, Action a) {
        if (clazz == null) {
            throw new IllegalArgumentException("Action type must be non-null");
        }
        if (a == null) {
            throw new IllegalArgumentException("Action must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<Action>();
        List<Action> current = super.getActions();
        boolean found = false;
        for (Action a1 : current) {
            if (!found && a.equals(a1)) {
                found = true;
            } else if (clazz.isInstance(a1) && !a.equals(a1)) {
                old.add(a1);
            }
        }
        current.removeAll(old);
        if (!found) {
            addAction(a);
        }
        return !(old.isEmpty() && found);
    }

    /**
     * Loads all the child {@link Item}s.
     *
     * @param parent     the parent of the children.
     * @param modulesDir Directory that contains sub-directories for each child item.
     * @param key        the key generating function.
     * @param <K>        the key type
     * @param <V>        the child type.
     * @return a map of the children keyed by the generated keys.
     */
    // TODO replace with ItemGroupMixIn.loadChildren once baseline core has JENKINS-41222 merged
    public static <K, V extends TopLevelItem> Map<K, V> loadChildren(AbstractFolder<V> parent, File modulesDir,
                                                             Function1<? extends K, ? super V> key) {
        CopyOnWriteMap.Tree<K, V> configurations = new CopyOnWriteMap.Tree<K, V>();
        if (!modulesDir.isDirectory() && !modulesDir.mkdirs()) { // make sure it exists
            LOGGER.log(Level.SEVERE, "Could not create {0} for folder {1}",
                    new Object[]{modulesDir, parent.getFullName()});
            return configurations;
        }

        File[] subdirs = modulesDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        if (subdirs == null) {
            return configurations;
        }
        final ChildNameGenerator<AbstractFolder<V>,V> childNameGenerator = parent.childNameGenerator();
        Map<String,V> byDirName = new HashMap<String, V>();
        if (parent.items != null) {
            if (childNameGenerator == null) {
                for (V item : parent.items.values()) {
                    byDirName.put(item.getName(), item);
                }
            } else {
                for (V item : parent.items.values()) {
                    String itemName = childNameGenerator.dirNameFromItem(parent, item);
                    if (itemName == null) {
                        itemName = childNameGenerator.dirNameFromLegacy(parent, item.getName());
                    }
                    byDirName.put(itemName, item);
                }
            }
        }
        for (File subdir : subdirs) {
            try {
                boolean legacy;
                String childName;
                if (childNameGenerator == null) {
                    // the directory name is the item name
                    childName = subdir.getName();
                    legacy = false;
                } else {
                    File nameFile = new File(subdir, ChildNameGenerator.CHILD_NAME_FILE);
                    if (nameFile.isFile()) {
                        childName = StringUtils.trimToNull(FileUtils.readFileToString(nameFile, "UTF-8"));
                        if (childName == null) {
                            LOGGER.log(Level.WARNING, "{0} was empty, assuming child name is {1}",
                                            new Object[]{nameFile, subdir.getName()});
                            legacy = true;
                            childName = subdir.getName();
                        } else {
                            legacy = false;
                        }
                    } else {
                        // this is a legacy name
                        legacy = true;
                        childName = subdir.getName();
                    }
                }
                // Try to retain the identity of an existing child object if we can.
                V item = byDirName.get(childName);
                boolean itemNeedsSave = false;
                if (item == null) {
                    XmlFile xmlFile = Items.getConfigFile(subdir);
                    if (xmlFile.exists()) {
                        item = (V) xmlFile.read();
                        String name;
                        if (childNameGenerator == null) {
                            name = subdir.getName();
                        } else {
                            String dirName = childNameGenerator.dirNameFromItem(parent, item);
                            if (dirName == null) {
                                dirName = childNameGenerator.dirNameFromLegacy(parent, childName);
                                BulkChange bc = new BulkChange(item); // suppress any attempt to save as parent not set
                                try {
                                    childNameGenerator.recordLegacyName(parent, item, childName);
                                    itemNeedsSave = true;
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Ignoring {0} as could not record legacy name",
                                            subdir);
                                    continue;
                                } finally {
                                    bc.abort();
                                }
                            }
                            if (!subdir.getName().equals(dirName)) {
                                File newSubdir = parent.getRootDirFor(dirName);
                                if (newSubdir.exists()) {
                                    LOGGER.log(Level.WARNING, "Ignoring {0} as folder naming rules collide with {1}",
                                                    new Object[]{subdir, newSubdir});
                                    continue;

                                }
                                LOGGER.log(Level.INFO, "Moving {0} to {1} in accordance with folder naming rules",
                                        new Object[]{subdir, newSubdir});
                                if (!subdir.renameTo(newSubdir)) {
                                    LOGGER.log(Level.WARNING, "Failed to move {0} to {1}. Ignoring this item",
                                            new Object[]{subdir, newSubdir});
                                    continue;
                                }
                            }
                            File nameFile = new File(parent.getRootDirFor(dirName), ChildNameGenerator.CHILD_NAME_FILE);
                            name = childNameGenerator.itemNameFromItem(parent, item);
                            if (name == null) {
                                name = childNameGenerator.itemNameFromLegacy(parent, childName);
                                FileUtils.writeStringToFile(nameFile, name, "UTF-8");
                                BulkChange bc = new BulkChange(item); // suppress any attempt to save as parent not set
                                try {
                                    childNameGenerator.recordLegacyName(parent, item, childName);
                                    itemNeedsSave = true;
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Ignoring {0} as could not record legacy name",
                                            subdir);
                                    continue;
                                } finally {
                                    bc.abort();
                                }
                            } else if (!childName.equals(name) || legacy) {
                                FileUtils.writeStringToFile(nameFile, name, "UTF-8");
                            }
                        }
                        item.onLoad(parent, name);
                    } else {
                        LOGGER.log(Level.WARNING, "could not find file " + xmlFile.getFile());
                        continue;
                    }
                } else {
                    String name;
                    if (childNameGenerator == null) {
                        name = subdir.getName();
                    } else {
                        File nameFile = new File(subdir, ChildNameGenerator.CHILD_NAME_FILE);
                        name = childNameGenerator.itemNameFromItem(parent, item);
                        if (name == null) {
                            name = childNameGenerator.itemNameFromLegacy(parent, childName);
                            FileUtils.writeStringToFile(nameFile, name, "UTF-8");
                            BulkChange bc = new BulkChange(item); // suppress any attempt to save as parent not set
                            try {
                                childNameGenerator.recordLegacyName(parent, item, childName);
                                itemNeedsSave = true;
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Ignoring {0} as could not record legacy name",
                                        subdir);
                                continue;
                            } finally {
                                bc.abort();
                            }
                        } else if (!childName.equals(name) || legacy) {
                            FileUtils.writeStringToFile(nameFile, name, "UTF-8");
                        }
                        if (!subdir.getName().equals(name) && item instanceof AbstractItem
                                && ((AbstractItem) item).getDisplayNameOrNull() == null) {
                            BulkChange bc = new BulkChange(item);
                            try {
                                ((AbstractItem) item).setDisplayName(childName);
                            } finally {
                                bc.abort();
                            }
                        }
                    }
                    item.onLoad(parent, name);
                }
                if (itemNeedsSave) {
                    try {
                        item.save();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Could not update {0} after applying folder naming rules",
                                item.getFullName());
                    }
                }
                configurations.put(key.call(item), item);
            } catch (Exception e) {
                Logger.getLogger(ItemGroupMixIn.class.getName()).log(Level.WARNING, "could not load " + subdir, e);
            }
        }

        return configurations;
    }


    protected final I itemsPut(String name, I item) {
        ChildNameGenerator<AbstractFolder<I>, I> childNameGenerator = childNameGenerator();
        if (childNameGenerator != null) {
            File nameFile = new File(getRootDirFor(item), ChildNameGenerator.CHILD_NAME_FILE);
            String oldName;
            if (nameFile.isFile()) {
                try {
                    oldName = StringUtils.trimToNull(FileUtils.readFileToString(nameFile, "UTF-8"));
                } catch (IOException e) {
                    oldName = null;
                }
            } else {
                oldName = null;
            }
            if (!name.equals(oldName)) {
                try {
                    FileUtils.writeStringToFile(nameFile, name, "UTF-8");
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not create " + nameFile);
                }
            }
        }
        return items.put(name, item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        init();
        final Thread t = Thread.currentThread();
        String n = t.getName();
        try {
            if (items == null) {
                // When Jenkins is getting reloaded, we want children being loaded to be able to find existing items that they will be overriding.
                // This is necessary for them to correctly keep the running builds, for example.
                // ItemGroupMixIn.loadChildren handles the rest of this logic.
                Item current = parent.getItem(name);
                if (current != null && current.getClass() == getClass()) {
                    this.items = ((AbstractFolder) current).items;
                }
            }

            final ChildNameGenerator<AbstractFolder<I>,I> childNameGenerator = childNameGenerator();
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
                    if (childNameGenerator == null) {
                        return item.getName();
                    } else {
                        String name = childNameGenerator.itemNameFromItem(AbstractFolder.this, item);
                        if (name == null) {
                            return childNameGenerator.itemNameFromLegacy(AbstractFolder.this, item.getName());
                        }
                        return name;
                    }
                }
            });
        } finally {
            t.setName(n);
        }
    }

    private ChildNameGenerator<AbstractFolder<I>,I> childNameGenerator() {
        return getDescriptor().childNameGenerator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractFolderDescriptor getDescriptor() {
        return (AbstractFolderDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    /**
     * May be used to enumerate or remove properties.
     * To add properties, use {@link #addProperty}.
     * @return the list of properties.
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

    /**
     * May be overridden, but {@link #loadJobTotal} will be inaccurate in that case.
     * @return the jobs directory.
     */
    protected File getJobsDir() {
        return new File(getRootDir(), "jobs");
    }

    protected final File getRootDirFor(String name) {
        return new File(getJobsDir(), name);
    }

    @Override
    public File getRootDirFor(I child) {
        ChildNameGenerator<AbstractFolder<I>,I> childNameGenerator = childNameGenerator();
        if (childNameGenerator == null) {
            return getRootDirFor(child.getName());
        }
        String name = childNameGenerator.dirNameFromItem(this, child);
        if (name == null) {
            name = childNameGenerator.dirNameFromLegacy(this, child.getName());
        }
        return getRootDirFor(name);
    }

    /**
     * It is unwise to override this, lest links to children from nondefault {@link View}s break.
     * TODO remove this warning if and when JENKINS-35243 is fixed in the baseline.
     * {@inheritDoc}
     */
    @Override
    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * For URL binding.
     *
     * @param name the name of the child.
     * @return the job or {@code null} if there is no such child.
     * @see #getUrlChildPrefix
     */
    public I getJob(String name) {
        return getItem(name);
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void addView(View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    /**
     * {@inheritDoc}
     */
    @Exported
    @Override
    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    public AbstractFolderViewHolder getFolderViews() {
        return folderViews;
    }

    public void resetFolderViews() {
        folderViews = newFolderViewHolder();
    }

    /**
     * {@inheritDoc}
     */
    @Exported
    @Override
    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
    }

    public void setPrimaryView(View v) {
        if (folderViews.isPrimaryModifiable()) {
            folderViews.setPrimaryView(v.getViewName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ViewsTabBar getViewsTabBar() {
        return folderViews.getTabBar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new CollectionSearchIndex<TopLevelItem>() {
            /**
             * {@inheritDoc}
             */
            @Override
            protected SearchItem get(String key) {
                return Jenkins.getActiveInstance().getItem(key, grp());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected Collection<TopLevelItem> all() {
                return Items.getAllItems(grp(), TopLevelItem.class);
            }

            /**
             * {@inheritDoc}
             */
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

    /**
     * {@inheritDoc}
     */
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
     *
     * @param value the name of the child.
     * @return the validation results.
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

    /**
     * Invalidates the cache of build health reports.
     *
     * @since FIXME
     */
    public void invalidateBuildHealthReports() {
        healthReports = null;
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        if (healthMetrics == null || healthMetrics.isEmpty()) {
            return Collections.<HealthReport>emptyList();
        }
        List<HealthReport> reports = healthReports;
        if (reports != null && nextHealthReportsRefreshMillis > System.currentTimeMillis()) {
            // cache is still valid
            return reports;
        }
        // ensure we refresh on average once every HEALTH_REPORT_CACHE_REFRESH_MIN but not all at once
        nextHealthReportsRefreshMillis = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(HEALTH_REPORT_CACHE_REFRESH_MIN * 3 / 4)
                + ENTROPY.nextInt((int)TimeUnit.MINUTES.toMillis(HEALTH_REPORT_CACHE_REFRESH_MIN / 2));
        reports = new ArrayList<HealthReport>();
        List<FolderHealthMetric.Reporter> reporters = new ArrayList<FolderHealthMetric.Reporter>(healthMetrics.size());
        boolean recursive = false;
        boolean topLevelOnly = true;
        for (FolderHealthMetric metric : healthMetrics) {
            recursive = recursive || metric.getType().isRecursive();
            topLevelOnly = topLevelOnly && metric.getType().isTopLevelItems();
            reporters.add(metric.reporter());
        }
        for (AbstractFolderProperty<?> p : getProperties()) {
            for (FolderHealthMetric metric : p.getHealthMetrics()) {
                recursive = recursive || metric.getType().isRecursive();
                topLevelOnly = topLevelOnly && metric.getType().isTopLevelItems();
                reporters.add(metric.reporter());
            }
        }
        if (recursive) {
            Stack<Iterable<? extends Item>> stack = new Stack<Iterable<? extends Item>>();
            stack.push(getItems());
            if (topLevelOnly) {
                while (!stack.isEmpty()) {
                    for (Item item : stack.pop()) {
                        if (item instanceof TopLevelItem) {
                            for (FolderHealthMetric.Reporter reporter : reporters) {
                                reporter.observe(item);
                            }
                            if (item instanceof Folder) {
                                stack.push(((Folder) item).getItems());
                            }
                        }
                    }
                }
            } else {
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
            }
        } else {
            for (Item item: getItems()) {
                for (FolderHealthMetric.Reporter reporter : reporters) {
                    reporter.observe(item);
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
        healthReports = reports; // idempotent write
        return reports;
    }

    public DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor> getHealthMetrics() {
        return healthMetrics;
    }

    public HttpResponse doLastBuild(StaplerRequest req) {
        return HttpResponses.redirectToDot();
    }

    /**
     * Gets the icon used for this folder.
     *
     * @return the icon.
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>();
        for (Item i : getItems()) {
            jobs.addAll(i.getAllJobs());
        }
        return jobs;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onRenamed(I item, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, item);
        // For compatibility with old views:
        for (View v : folderViews.getViews()) {
            v.onJobRenamed(item, oldName, newName);
        }
        save();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onDeleted(I item) throws IOException {
        ItemListener.fireOnDeleted(item);
        items.remove(item.getName());
        // For compatibility with old views:
        for (View v : folderViews.getViews()) {
            v.onJobRenamed(item, item.getName(), null);
        }
        save();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete() throws IOException, InterruptedException {
        // Some parts copied from AbstractItem.
        checkPermission(DELETE);
        // TODO remove once baseline core has JENKINS-35160
        boolean responsibleForAbortingBuilds = !ItemDeletion.contains(this);
        boolean ownsRegistration = ItemDeletion.register(this);
        if (!ownsRegistration && ItemDeletion.isRegistered(this)) {
            // we are not the owning thread and somebody else is concurrently deleting this exact item
            throw new Failure(Messages.AbstractFolder_BeingDeleted(getPronoun()));
        }
        try {
            // if a build is in progress. Cancel it.
            if (responsibleForAbortingBuilds || ownsRegistration) {
                Queue queue = Queue.getInstance();
                if (this instanceof Queue.Task) {
                    // clear any items in the queue so they do not get picked up
                    queue.cancel((Queue.Task) this);
                }
                // now cancel any child items - this happens after ItemDeletion registration, so we can use a snapshot
                for (Queue.Item i : queue.getItems()) {
                    Item item = ItemDeletion.getItemOf(i.task);
                    while (item != null) {
                        if (item == this) {
                            queue.cancel(i);
                            break;
                        }
                        if (item.getParent() instanceof Item) {
                            item = (Item) item.getParent();
                        } else {
                            break;
                        }
                    }
                }
                // interrupt any builds in progress (and this should be a recursive test so that folders do not pay
                // the 15 second delay for every child item). This happens after queue cancellation, so will be
                // a complete set of builds in flight
                Map<Executor, Queue.Executable> buildsInProgress = new LinkedHashMap<>();
                for (Computer c : Jenkins.getActiveInstance().getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        WorkUnit workUnit = e.getCurrentWorkUnit();
                        if (workUnit != null) {
                            Item item = ItemDeletion.getItemOf(getParentOf(workUnit.getExecutable()));
                            if (item != null) {
                                while (item != null) {
                                    if (item == this) {
                                        buildsInProgress.put(e, e.getCurrentExecutable());
                                        e.interrupt(Result.ABORTED);
                                        break;
                                    }
                                    if (item.getParent() instanceof Item) {
                                        item = (Item) item.getParent();
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    for (Executor e : c.getOneOffExecutors()) {
                        WorkUnit workUnit = e.getCurrentWorkUnit();
                        if (workUnit != null) {
                            Item item = ItemDeletion.getItemOf(getParentOf(workUnit.getExecutable()));
                            if (item != null) {
                                while (item != null) {
                                    if (item == this) {
                                        buildsInProgress.put(e, e.getCurrentExecutable());
                                        e.interrupt(Result.ABORTED);
                                        break;
                                    }
                                    if (item.getParent() instanceof Item) {
                                        item = (Item) item.getParent();
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!buildsInProgress.isEmpty()) {
                    // give them 15 seconds or so to respond to the interrupt
                    long expiration = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    // comparison with executor.getCurrentExecutable() == computation currently should always be true
                    // as we no longer recycle Executors, but safer to future-proof in case we ever revisit recycling
                    while (!buildsInProgress.isEmpty() && expiration - System.nanoTime() > 0L) {
                        // we know that ItemDeletion will prevent any new builds in the queue
                        // ItemDeletion happens-before Queue.cancel so we know that the Queue will stay clear
                        // Queue.cancel happens-before collecting the buildsInProgress list
                        // thus buildsInProgress contains the complete set we need to interrupt and wait for
                        for (Iterator<Map.Entry<Executor, Queue.Executable>> iterator =
                             buildsInProgress.entrySet().iterator();
                             iterator.hasNext(); ) {
                            Map.Entry<Executor, Queue.Executable> entry = iterator.next();
                            // comparison with executor.getCurrentExecutable() == executable currently should always be
                            // true as we no longer recycle Executors, but safer to future-proof in case we ever
                            // revisit recycling.
                            if (!entry.getKey().isAlive()
                                    || entry.getValue() != entry.getKey().getCurrentExecutable()) {
                                iterator.remove();
                            }
                            // I don't know why, but we have to keep interrupting
                            entry.getKey().interrupt(Result.ABORTED);
                        }
                        Thread.sleep(50L);
                    }
                    if (!buildsInProgress.isEmpty()) {
                        throw new Failure(Messages.AbstractFolder_FailureToStopBuilds(
                                buildsInProgress.size(), getFullDisplayName()
                        ));
                    }
                }
            }
            // END remove once baseline core has JENKINS-35160

            // delete individual items first
            // (disregard whether they would be deletable in isolation)
            // JENKINS-34939: do not hold the monitor on this folder while deleting them
            // (thus we cannot do this inside performDelete)
            SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
            try {
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
            } finally {
                SecurityContextHolder.setContext(orig);
            }
            synchronized (this) {
                performDelete();
            }
            // TODO remove once baseline core has JENKINS-35160
        } finally {
            if (ownsRegistration) {
                ItemDeletion.deregister(this);
            }
        }
        // END remove once baseline core has JENKINS-35160
        getParent().onDeleted(AbstractFolder.this);
        Jenkins.getActiveInstance().rebuildDependencyGraphAsync();
    }

    /**
     * Is this folder disabled. A disabled folder should have all child items disabled.
     *
     * @return {@code true} if and only if the folder is disabled.
     * @since 6.1.0
     * @see FolderJobQueueDecisionHandler
     */
    public boolean isDisabled() {
        return false;
    }

    /**
     * Sets the folder as disabled.
     *
     * @param disabled {@code true} if and only if the folder is to be disabled.
     * @since 6.1.0
     */
    protected void setDisabled(boolean disabled) {
        throw new UnsupportedOperationException("must be implemented if supportsMakeDisabled is overridden");
    }

    /**
     * Determines whether the folder supports being made disabled.
     * @return {@code true} if and only if {@link #setDisabled(boolean)} is implemented
     * @since 6.1.0
     */
    protected boolean supportsMakeDisabled() {
        return false;
    }

    /**
     * Makes the folder disabled. Will have no effect if the folder type does not {@linkplain #supportsMakeDisabled()}.
     * @param disabled {@code true} if the folder should be disabled.
     * @throws IOException if the operation could not complete.
     * @since 6.1.0
     */
    public void makeDisabled(boolean disabled) throws IOException {
        if (isDisabled() == disabled) {
            return; // noop
        }
        if (disabled && !supportsMakeDisabled()) {
            return; // do nothing if the disabling is unsupported
        }
        setDisabled(disabled);
        if (disabled && this instanceof Queue.Task) {
            Jenkins.getActiveInstance().getQueue().cancel((Queue.Task)this);
        }
        save();
        ItemListener.fireOnUpdated(this);
    }

    /**
     * Stapler action method to disable the folder.
     *
     * @return the response.
     * @throws IOException      if the folder could not be disabled.
     * @throws ServletException if something goes wrong.
     * @since 6.1.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler action method
    public HttpResponse doDisable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        makeDisabled(true);
        return new HttpRedirect(".");
    }

    /**
     * Stapler action method to enable the folder.
     *
     * @return the response.
     * @throws IOException      if the folder could not be disabled.
     * @throws ServletException if something goes wrong.
     * @since 6.1.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler action method
    public HttpResponse doEnable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        makeDisabled(false);
        return new HttpRedirect(".");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void save() throws IOException {
        if (folderViews != null) {
            folderViews.invalidateCaches();
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        JSONObject json = req.getSubmittedForm();

        BulkChange bc = new BulkChange(this);
        try {
            description = json.getString("description");
            displayName = Util.fixEmpty(json.optString("displayNameOrNull"));
            if (folderViews.isTabBarModifiable() && json.has("viewsTabBar")) {
                folderViews.setTabBar(req.bindJSON(ViewsTabBar.class, json.getJSONObject("viewsTabBar")));
            }

            if (folderViews.isPrimaryModifiable() && json.has("primaryView")) {
                folderViews.setPrimaryView(json.getString("primaryView"));
            }

            properties.rebuild(req, json, getDescriptor().getPropertyDescriptors());
            for (AbstractFolderProperty p : properties) {
                p.setOwner(this);
            }

            healthMetrics.replaceBy(req.bindJSONToList(FolderHealthMetric.class, json.get("healthMetrics")));

            icon = req.bindJSON(FolderIcon.class, req.getSubmittedForm().getJSONObject("icon"));
            icon.setOwner(this);

            submit(req, rsp);
            makeDisabled(json.optBoolean("disable"));

            save();
            bc.commit();
        } finally {
            bc.abort();
        }

        // TODO boilerplate
        String newName = json.getString("name");
        ProjectNamingStrategy namingStrategy = Jenkins.getActiveInstance().getProjectNamingStrategy();
        if (newName != null && !newName.equals(name)) {
            // check this error early to avoid HTTP response splitting.
            Jenkins.checkGoodName(newName);
            namingStrategy.checkName(newName);
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
        } else {
            if (namingStrategy.isForceExistingJobs()) {
                namingStrategy.checkName(name);
            }
            FormApply.success(getSuccessfulDestination()).generateResponse(req, rsp, this);
        }
    }

    /**
     * Where user will be redirected after creating or reconfiguring a {@code AbstractFolder}.
     *
     * @return A string that represents the redirect location URL.
     *
     * @see javax.servlet.http.HttpServletResponse#sendRedirect(String)
     */
    @Restricted(NoExternalUse.class)
    @Nonnull
    protected String getSuccessfulDestination() {
        return ".";
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {}

    // TODO boilerplate like this should not be necessary: JENKINS-22936
    @Restricted(DoNotUse.class)
    @RequirePOST
    public void doDoRename(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

        if (!hasPermission(CONFIGURE)) {
            // rename is essentially delete followed by a create
            checkPermission(CREATE);
            checkPermission(DELETE);
        }

        String newName = req.getParameter("newName");
        Jenkins.checkGoodName(newName);

        String blocker = renameBlocker();
        if (blocker != null) {
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8") + "&blocker=" + URLEncoder.encode(blocker, "UTF-8"));
            return;
        }

        renameTo(newName);
        rsp.sendRedirect2("../" + newName);
    }

    /**
     * Allows a subclass to block renames under dynamic conditions.
     * @return a message if rename should currently be prohibited, or null to allow
     */
    @CheckForNull
    protected String renameBlocker() {
        for (Job<?,?> job : getAllJobs()) {
            if (job.isBuilding()) {
                return "Unable to rename a folder while a job inside it is building.";
            }
        }
        return null;
    }

    private static void invalidateBuildHealthReports(Item item) {
        while (item != null) {
            if (item instanceof AbstractFolder) {
                ((AbstractFolder) item).invalidateBuildHealthReports();
            }
            if (item.getParent() instanceof Item) {
                item = (Item) item.getParent();
            } else {
                break;
            }
        }
    }

    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onCreated(Item item) {
            invalidateBuildHealthReports(item);
        }

        @Override
        public void onCopied(Item src, Item item) {
            invalidateBuildHealthReports(item);
        }

        @Override
        public void onDeleted(Item item) {
            invalidateBuildHealthReports(item);
        }

        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            invalidateBuildHealthReports(item);
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            invalidateBuildHealthReports(item);
        }

        @Override
        public void onUpdated(Item item) {
            invalidateBuildHealthReports(item);
        }

    }

    @Extension
    public static class RunListenerImpl extends RunListener<Run> {
        @Override
        public void onCompleted(Run run, @Nonnull TaskListener listener) {
            invalidateBuildHealthReports(run.getParent());
        }

        @Override
        public void onFinalized(Run run) {
            invalidateBuildHealthReports(run.getParent());
        }

        @Override
        public void onStarted(Run run, TaskListener listener) {
            invalidateBuildHealthReports(run.getParent());
        }

        @Override
        public void onDeleted(Run run) {
            invalidateBuildHealthReports(run.getParent());
        }
    }

}
