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

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import hudson.model.TopLevelItemDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * Category of {@link AbstractFolder}.
 * @since 4.11-beta-1
 */
public abstract class AbstractFolderDescriptor extends TopLevelItemDescriptor {
    
    @Override
    public String getDisplayName() {
        return Messages.Folder_DisplayName();
    }

    /**
     * Needed if it wants Folders are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     * TODO: Replace to {@code NestedProjectsCategory.ID}
     *
     * @return A string it represents a ItemCategory identifier.
     */
    public String getCategoryId() {
        return "nestedprojects";
    }

    /**
     * Needed if it wants Folders are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     *
     * @return A string with the Item description.
     */
    public String getDescription() {
        return Messages.Folder_Description();
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

}
