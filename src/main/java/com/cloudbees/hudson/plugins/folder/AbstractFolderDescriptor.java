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
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.jelly.DefaultScriptInvoker;
import org.kohsuke.stapler.jelly.JellyClassTearOff;

/**
 * Category of {@link AbstractFolder}.
 * @since 4.11-beta-1
 */
public abstract class AbstractFolderDescriptor extends TopLevelItemDescriptor implements IconSpec {

    /**
     * Explicit constructor.
     *
     * @param clazz the explicit {@link AbstractFolder} sub-class that this descriptor is for.
     * @see TopLevelItemDescriptor#TopLevelItemDescriptor(Class)
     */
    protected AbstractFolderDescriptor(Class<? extends AbstractFolder> clazz) {
        super(clazz);
    }

    /**
     * Default constructor.
     *
     * @see TopLevelItemDescriptor#TopLevelItemDescriptor()
     */
    protected AbstractFolderDescriptor() {
    }

    @Override
    public String getDisplayName() {
        return Messages.Folder_DisplayName();
    }

    /**
     * Needed if it wants AbstractFolderDescriptor implementations are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     * TODO: Replace to {@code NestedProjectsCategory.ID}
     *
     * @return A string it represents a ItemCategory identifier.
     */
    public String getCategoryId() {
        return "nested-projects";
    }

    /**
     * Properties that can be configured for this type of {@link AbstractFolder} subtype.
     */
    public List<AbstractFolderPropertyDescriptor> getPropertyDescriptors() {
        return AbstractFolderPropertyDescriptor.getApplicableDescriptors(clazz.asSubclass(AbstractFolder.class));
    }

    /**
     * Health metrics that can be configured for this type of {@link AbstractFolder} subtype.
     */
    public List<FolderHealthMetricDescriptor> getHealthMetricDescriptors() {
        List<FolderHealthMetricDescriptor> r = new ArrayList<FolderHealthMetricDescriptor>();
        for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
            if (d.isApplicable(clazz.asSubclass(AbstractFolder.class))) {
                r.add(d);
            }
        }
        return r;
    }

    /**
     * Gets the {@link FolderIconDescriptor}s applicable for this folder type.
     *
     * @since FIXME
     */
    public List<FolderIconDescriptor> getIconDescriptors() {
        List<FolderIconDescriptor> r = new ArrayList<FolderIconDescriptor>();
        for (FolderIconDescriptor p : FolderIconDescriptor.all()) {
            if (p.isApplicable(clazz.asSubclass(AbstractFolder.class))) {
                r.add(p);
            }
        }
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            AbstractFolder folder = request.findAncestorObject(AbstractFolder.class);
            if (folder != null) {
                return DescriptorVisibilityFilter.apply(folder, r);
            }
        }
        return r;
    }

    // TODO remove once baseline 2.0
    public String getDescription() {
        Stapler stapler = Stapler.getCurrent();
        if (stapler != null) {
            try {
                WebApp webapp = WebApp.getCurrent();
                MetaClass meta = webapp.getMetaClass(this);
                Script s = meta.loadTearOff(JellyClassTearOff.class).findScript("newInstanceDetail");
                if (s == null) {
                    return "";
                }
                DefaultScriptInvoker dsi = new DefaultScriptInvoker();
                StringWriter sw = new StringWriter();
                XMLOutput xml = dsi.createXMLOutput(sw, true);
                dsi.invokeScript(Stapler.getCurrentRequest(), Stapler.getCurrentResponse(), s, this, xml);
                return sw.toString();
            } catch (Exception e) {
                Logger.getLogger(clazz.getName()).log(Level.WARNING, e.getMessage(), e);
                return "";
            }
        } else {
            return "";
        }
    }

    /**
     * Needed if it wants Folder are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     *
     * @return A string it represents a URL pattern to get the Item icon in different sizes.
     */
    public String getIconFilePathPattern() {
        return "plugin/cloudbees-folder/images/:size/folder.png";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return "icon-folder";
    }

    public boolean isIconConfigurable() {
        return getIconDescriptors().size() > 1;
    }

    public boolean isTabBarConfigurable() {
        return Jenkins.getActiveInstance().getDescriptorList(ViewsTabBar.class).size() > 1;
    }

    public boolean isLookAndFeelConfigurable(AbstractFolder<?> folder) {
        return isIconConfigurable() || (isTabBarConfigurable() && folder.getFolderViews().isTabBarModifiable()) || (folder.getViews().size() > 1 && folder.getFolderViews().isPrimaryModifiable());
    }

    /**
     * Folders, especially computed folders, may have requirements for using a different on-disk file name for child
     * items than the url-segment name. Typically this is to work around filesystem naming rules.
     * Regular folders typically would leave the naming of child items to {@link ProjectNamingStrategy} and thereby
     * prevent users from creating child items with names that do not comply with the {@link ProjectNamingStrategy}.
     * <p>
     * <strong>However,</strong> {@link ComputedFolder} instances may not have that luxury. The children of a
     * {@link ComputedFolder} may have names that come from an external system and the matching item must be created,
     * always. The obvious solution is that the {@link ComputedFolder} should mangle the supplied {@link Item#getName()}
     * but the side-effect is that the URLs of the child items will now be mangled. Additionally the filename
     * requirements can be rather onerous. Here is the most portable list of filename specification:
     * <ul>
     *     <li>Assume case insensitive</li>
     *     <li>Assume no filename can be longer than 32 characters</li>
     *     <li>Assume that only the characters {@code A-Za-z0-9_.-} are available</li>
     *     <li>Assume that there are some special reserved names such as {@code .} and {@code ..}</li>
     *     <li>Assume that there are some problematic names to avoid such as {@code AUX}, {@code CON}, {@code NUL}, etc.
     *         See <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx">
     *         Microsoft's page on "Naming Files, Paths, and Namespaces"</a>
     *         (What's that you say, "Oh but we only run on Linux", perhaps but users may want to migrate from one OS
     *         to an other by <strong>moving</strong> their {@code JENKINS_HOME} (or even parts of it) so if you are
     *         mangling names, be sure to ensure that the mangled name is the same on all OSes
     *     </li>
     *     <li><a href="http://unicode.org/reports/tr15/">NFC vs. NFD</a> may be a concern as different filesystems
     *     apply different rules and normalization to the filenames. This is primarily a concern when migrating from
     *     before having a {@link ChildNameGenerator} to having a {@link ChildNameGenerator} as the migration will
     *     require inference of the un-mangled name from the filesystem, which may or may not match the un-mangled
     *     name from the source of the computation. Now POSIX does not specify how the filesystem is supposed to handle
     *     encoding of filenames and there can be strange behaviours, e.g.
     *     <a href="http://stackoverflow.com/a/32663908/1589700">{@link File#listFiles()} is rumoured to always return
     *     NFC on OS-X</a>
     *     </li>
     * </ul>
     * The {@link ChildNameGenerator} at least allows an {@link AbstractFolder} to apply an on-disk naming strategy
     * that differs from the names used for the URLs.
     * <p>
     * If you implement a {@link ChildNameGenerator} it is strongly recommended to return a singleton for performance
     * reasons.
     *
     * @param <I> A wildcard parameter to assist type matching.
     * @return a (ideally singleton) instance of {@link ChildNameGenerator} for all instances of the concrete
     * {@link AbstractFolder} class or {@code null} if no name mangling will be performed.
     * @since 5.17
     */
    // TODO figure out how one could un-wind name mangling if one ever wanted to
    // TODO move the name mangling code from branch-api to this plugin so that everyone can use it
    @CheckForNull
    public <I extends TopLevelItem> ChildNameGenerator<AbstractFolder<I>,I> childNameGenerator() {
        return null;
    }

}
