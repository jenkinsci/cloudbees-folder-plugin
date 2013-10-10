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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor.FormException;
import hudson.model.HealthReport;
import hudson.model.ReconfigurableDescribable;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class FolderProperty<C extends Folder> extends AbstractDescribableImpl<FolderProperty<?>>
        implements ExtensionPoint, ReconfigurableDescribable<FolderProperty<?>> {
    /**
     * The {@link Folder} object that owns this property.
     * This value will be set by the Hudson code.
     * Derived classes can expect this value to be always set.
     */
    protected transient C owner;

    /**
     * Hook for performing post-initialization action.
     */
    protected void setOwner(C owner) {
        this.owner = owner;
    }

    /**
     * Provides stapler override objects to {@link Folder} so that its URL space can be partially
     * overridden by properties.
     *
     * @see StaplerOverridable
     */
    public Collection<?> getItemContainerOverrides() {
        return Collections.emptyList();
    }

    /**
     * Determines if the parent container is allowed to create a new item of the given type, or copy from
     * an existing item of the given type.
     *
     * @param candidate The type of the item being considered.
     * @return false to prevent the container to create the children of the said type.
     */
    public boolean allowsParentToCreate(TopLevelItemDescriptor candidate) {
        return true;
    }

    public boolean allowsParentToHave(TopLevelItem candidate) {
        return true;
    }

    public FolderProperty reconfigure(StaplerRequest req, JSONObject form) throws FormException {
        return form == null ? null : getDescriptor().newInstance(req, form);
    }

    /**
     * Folder properties can optionally contribute health reports for the folder. These should be reports of the folder
     * directly, where a report requires iteration of the items in the folder use the {@link #getHealthMetrics()}
     * in order to prevent multiple iterations of the items in the folder.
     *
     * @return the list of health reports.
     */
    @NonNull
    public List<HealthReport> getHealthReports() {
        return Collections.emptyList();
    }

    /**
     * Returns the health metrics contributed by this property.
     *
     * @return the health metrics contributed by this property.
     */
    @NonNull
    public List<FolderHealthMetric> getHealthMetrics() {
        return Collections.emptyList();
    }

    /**
     * {@link hudson.model.Action}s to be displayed in the folder page.
     * <p/>
     * <p/>
     * Returning actions from this method allows a folder property to add them
     * to the left navigation bar in the folder page.
     *
     * @return can be empty but never null.
     * @since 3.14
     */
    @NonNull
    public Collection<? extends Action> getFolderActions() {
        return Collections.emptyList();
    }
}
