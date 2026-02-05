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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.ListView;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import hudson.views.ListViewColumn;
import hudson.views.ViewJobFilter;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

/**
 * A mutable folder.
 */
public class Folder extends AbstractFolder<TopLevelItem> implements DirectlyModifiableTopLevelItemGroup {

    /**
     * @see #getNewPronoun
     * @since 4.0
     */
    public static final AlternativeUiTextProvider.Message<Folder> NEW_PRONOUN = new AlternativeUiTextProvider.Message<>();

    /**
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    @Deprecated
    private transient DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    @Deprecated
    private transient DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> filters;

    private transient /*final*/ ItemGroupMixIn mixin;

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link Folder}, such as from properties.
     * <p>
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     * @deprecated Use {@link TransientActionFactory} instead.
     */
    @CopyOnWrite
    @Deprecated
    protected transient volatile List<Action> transientActions = new Vector<>();

    public Folder(ItemGroup parent, String name) {
        super(parent, name);
        init();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        updateTransientActions();
    }

    @Override
    protected final void init() {
        super.init();
        mixin = new MixInImpl(this);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void initViews(List<View> views) throws IOException {
        if (columns != null || filters != null) {
            // we're loading an ancient config
            if (columns == null) {
                columns = new DescribableList<>(this,
                        ListViewColumn.createDefaultInitialColumnList());
            }
            if (filters == null) {
                filters = new DescribableList<>(this);
            }
            ListView lv = new ListView("All", this);
            views.add(lv);
            lv.getColumns().replaceBy(columns.toList());
            lv.getJobFilters().replaceBy(filters.toList());
            lv.setIncludeRegex(".*");
            lv.save();
        } else {
            super.initViews(views);
        }
    }

    @Override
    public void onCreatedFromScratch() {
        updateTransientActions();
    }

    @Restricted(DoNotUse.class)
    @Deprecated
    @Extension
    public static class DeprecatedTransientActions extends TransientActionFactory<Folder> {

        @Override
        public Class<Folder> type() {
            return Folder.class;
        }

        @Override
        public Collection<? extends Action> createFor(Folder target) {
            return target.transientActions;
        }

    }

    /**
     * @deprecated Use {@link TransientActionFactory} instead.
     */
    @Deprecated
    protected void updateTransientActions() {
        transientActions = createTransientActions();
    }

    @Deprecated
    protected List<Action> createTransientActions() {
        Vector<Action> ta = new Vector<>();

        for (TransientFolderActionFactory tpaf : TransientFolderActionFactory.all()) {
            ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
        }
        for (FolderProperty<?> p: getProperties().getAll(FolderProperty.class)) {
            ta.addAll(Util.fixNull(p.getFolderActions()));
        }

        return ta;
    }

    /**
     * Used in "New Job" side menu.
     * @return the pronoun for new item creation.
     * @see #NEW_PRONOUN
     */
    public String getNewPronoun() {
        return AlternativeUiTextProvider.get(NEW_PRONOUN, this, Messages.Folder_DefaultPronoun());
    }

    /**
     * @return the columns.
     * @deprecated as of 1.7
     *             Folder is no longer a view by itself.
     */
    @Deprecated
    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return new DescribableList<>(this,
                ListViewColumn.createDefaultInitialColumnList());
    }

    /**
     * Legacy binary compatibility method.
     * @param p the property.
     * @throws IOException if the folder could not be saved.
     * @deprecated use {@link #addProperty(AbstractFolderProperty)} instead
     */
    @Deprecated
    public void addProperty(FolderProperty<?> p) throws IOException {
        addProperty((AbstractFolderProperty) p);
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

    @POST
    public TopLevelItem doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Folder.class, getClass(), "doCreateItem", StaplerRequest.class, StaplerResponse.class)) {
            try {
                return doCreateItem(req != null ? StaplerRequest.fromStaplerRequest2(req) : null,  rsp != null ? StaplerResponse.fromStaplerResponse2(rsp) : null);
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            return doCreateItemImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doCreateItem(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public TopLevelItem doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            return doCreateItemImpl(req != null ? StaplerRequest.toStaplerRequest2(req) : null, rsp != null ? StaplerResponse.toStaplerResponse2(rsp) : null);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private TopLevelItem doCreateItemImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        TopLevelItem nue = mixin.createTopLevelItem(req, rsp);
        if (!isAllowedChild(nue)) {
            // TODO would be better to intercept it before creation, if mode is set
            try {
                nue.delete();
            } catch (InterruptedException x) {
                throw new IOException(x.toString(), x);
            }
            throw new IOException("forbidden child type");
        }
        return nue;
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
                throw new IOException(x.toString(), x);
            }
            throw new IOException("forbidden child type");
        }
        return nue;
    }

    public <T extends TopLevelItem> T createProject(@NonNull Class<T> type, @NonNull String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor) Jenkins.get().getDescriptorOrDie(type), name));
    }

    public TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name) throws IOException {
        return createProject(type, name, true);
    }

    @Override
    public TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name, boolean notify) throws IOException {
        if (!isAllowedChildDescriptor(type)) {
            throw new IOException("forbidden child type");
        }
        return mixin.createProject(type, name, notify);
    }

    @Override
    protected void submit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        if (Util.isOverridden(Folder.class, getClass(), "submit", StaplerRequest.class, StaplerResponse.class)) {
            try {
                submit(req != null ? StaplerRequest.fromStaplerRequest2(req) : null, rsp != null ? StaplerResponse.fromStaplerResponse2(rsp) : null);
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            submitImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #submit(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, FormException {
        try {
            submitImpl(req != null ? StaplerRequest.toStaplerRequest2(req) : null, rsp != null ? StaplerResponse.toStaplerResponse2(rsp) : null);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void submitImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        updateTransientActions();
    }

    /**
     * Items that can be created in this {@link Folder}.
     * @return the descriptors of items that can be created within this folder.
     * @see FolderAddFilter
     */
    public List<TopLevelItemDescriptor> getItemDescriptors() {
        List<TopLevelItemDescriptor> r = new ArrayList<>();
        for (TopLevelItemDescriptor tid : Items.all()) {
            if (isAllowedChildDescriptor(tid)) {
                r.add(tid);
            }
        }
        return r;
    }

    /**
     * Returns true if the specified descriptor type is allowed for this container.
     *
     * @param tid the type of child item.
     * @return {@code true} if it can be added.
     */
    public boolean isAllowedChildDescriptor(TopLevelItemDescriptor tid) {
        for (FolderProperty<?> p : getProperties().getAll(FolderProperty.class)) {
            if (!p.allowsParentToCreate(tid)) {
                return false;
            }
        }
        if (!getACL().hasCreatePermission2(Jenkins.getAuthentication2(), this, tid)) {
            return false;
        }
        return tid.isApplicableIn(this);
    }

    /**
     * Historical synonym for {@link #canAdd}.
     *
     * @param tid the potential child item.
     * @return {@code true} if it can be added.
     */
    public boolean isAllowedChild(TopLevelItem tid) {
        for (FolderProperty<?> p : getProperties().getAll(FolderProperty.class)) {
            if (!p.allowsParentToHave(tid)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override public boolean canAdd(TopLevelItem item) {
        return isAllowedChild(item);
    }

    @Override public <I extends TopLevelItem> I add(I item, String name) throws IOException, IllegalArgumentException {
        if (!canAdd(item)) {
            throw new IllegalArgumentException();
        }
        addLoadedChild(item, name);
        return item;
    }

    @Override public void remove(TopLevelItem item) throws IOException, IllegalArgumentException {
        items.remove(item.getName());
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderDescriptor {

        /**
         * Needed if it wants Folders are categorized in Jenkins 2.x.
         *
         * @return A string with the Item description.
         */
        @Override
        public String getDescription() {
            return Messages.Folder_Description();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new Folder(parent, name);
        }

        static {
            // Modern symbol- icons (preferred)
            IconSet.icons.addIcon(new Icon("symbol-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder icon-md", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder icon-lg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder icon-xlg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_XLARGE_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-md", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-lg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_XLARGE_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-store icon-sm", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-store icon-md", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-store icon-lg", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("symbol-folder-store icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_XLARGE_STYLE));

            // Deprecated icon- classes (backward compatibility for branch-api-plugin and dependent plugins)
            // TODO: Remove these after branch-api-plugin migrates to symbol- format
            // See: https://github.com/jenkinsci/branch-api-plugin (MetadataActionFolderIcon.java lines 74, 111)
            IconSet.icons.addIcon(new Icon("icon-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder icon-md", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder icon-lg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder icon-xlg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_XLARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-md", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-lg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_XLARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-store icon-sm", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-store icon-md", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-store icon-lg", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(new Icon("icon-folder-store icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-store.svg", Icon.ICON_XLARGE_STYLE));
        }
    }

    private class MixInImpl extends ItemGroupMixIn {
        private MixInImpl(Folder parent) {
            super(parent, parent);
        }

        @Override
        protected void add(TopLevelItem item) {
            itemsPut(item.getName(), item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return Folder.this.getRootDirFor(name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Folder.class.getName());
}
