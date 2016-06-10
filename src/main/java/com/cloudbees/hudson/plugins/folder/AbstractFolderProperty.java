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

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.ReconfigurableDescribable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Property potentially applicable to any {@link AbstractFolder}.
 * @since 4.11-beta-1
 */
public abstract class AbstractFolderProperty<C extends AbstractFolder<?>> extends AbstractDescribableImpl<AbstractFolderProperty<?>> implements ExtensionPoint, ReconfigurableDescribable<AbstractFolderProperty<?>> {

    /**
     * The {@link AbstractFolder} object that owns this property.
     * This value will be set by the folder.
     * Derived classes can expect this value to be always set.
     */
    protected transient C owner;

    /**
     * Hook for performing post-initialization action.
     */
    protected void setOwner(@NonNull C owner) {
        this.owner = owner;
    }

    /**
     * Gets an owner of the property.
     * @return Owner of the property.
     *         It should be always non-null if the property initialized correctly.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public C getOwner() {
        return owner;
    }
    
    @Override
    public AbstractFolderPropertyDescriptor getDescriptor() {
        return (AbstractFolderPropertyDescriptor) super.getDescriptor();
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

    @Override
    public AbstractFolderProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
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

}
