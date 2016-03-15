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

package com.cloudbees.hudson.plugins.folder.health;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class FolderHealthMetricDescriptor extends Descriptor<FolderHealthMetric> {
    /**
     * Returns true if this {@link FolderHealthMetric} type is applicable to the
     * given folder type.
     */
    @SuppressWarnings("rawtypes") // erasure
    public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
        return true;
    }

    /**
     * Optionally create a default health metric to be used in new folders.
     * @return a default configuration of the associated metric, or null to not include by default
     * @since 4.0
     */
    @CheckForNull public FolderHealthMetric createDefault() {
        return null;
    }

    @SuppressWarnings({"unchecked"})
    public static DescriptorExtensionList<FolderHealthMetric, FolderHealthMetricDescriptor> all() {
        return (DescriptorExtensionList) Jenkins.getActiveInstance().getDescriptorList(FolderHealthMetric.class);
    }
}
