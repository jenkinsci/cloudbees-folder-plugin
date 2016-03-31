/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Restricts additions to a folder via {@code View/newJob.jelly}.
 * @see Folder#getItemDescriptors
 */
@Extension public class FolderAddFilter extends DescriptorVisibilityFilter {

    @Override public boolean filter(Object context, Descriptor descriptor) {
        StaplerRequest req = Stapler.getCurrentRequest();
        // View/newJob.jelly for 1.x, View.doCategories for 2.x
        if (req == null || !req.getRequestURI().matches(".*/(newJob|categories)")) {
            return true;
        }
        if (!(descriptor instanceof TopLevelItemDescriptor)) {
            return true;
        }
        Folder d;
        if (context instanceof Folder) {
            d = ((Folder) context);
        } else if (context instanceof View && ((View) context).getOwnerItemGroup() instanceof Folder) {
            d = (Folder) ((View) context).getOwnerItemGroup();
        } else {
            return true;
        }
        return d.isAllowedChildDescriptor((TopLevelItemDescriptor) descriptor);
    }

}
