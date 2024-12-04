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

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.Util;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

public abstract class FolderPropertyDescriptor extends AbstractFolderPropertyDescriptor {

    @Override
    public FolderProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
        if (Util.isOverridden(FolderPropertyDescriptor.class, getClass(), "newInstance", StaplerRequest.class, JSONObject.class)) {
            return newInstance(req != null ? StaplerRequest.fromStaplerRequest2(req) : null, formData);
        } else {
            return (FolderProperty<?>) super.newInstance(req, formData);
        }
    }

    /**
     * @deprecated use {@link #newInstance(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    @Override
    public FolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return (FolderProperty<?>) super.newInstance(req, formData);
    }

    /**
     * @param containerType the type of folder
     * @return all {@link AbstractFolderPropertyDescriptor} applicable to the supplied type of folder.
     * @deprecated Use {@link AbstractFolderPropertyDescriptor#getApplicableDescriptors} instead.
     */
    @Deprecated
    public static List<FolderPropertyDescriptor> getPropertyDescriptors(Class<? extends Folder> containerType) {
        List<FolderPropertyDescriptor> r = new ArrayList<>();
        for (FolderPropertyDescriptor p : ExtensionList.lookup(FolderPropertyDescriptor.class))
            if(p.isApplicable(containerType))
                r.add(p);
        return r;
    }

    /**
     * @return all {@link AbstractFolderPropertyDescriptor}
     * @deprecated Directly look up {@link AbstractFolderPropertyDescriptor}s from {@link ExtensionList#lookup} instead.
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DescriptorExtensionList all() {
        return Jenkins.get().getDescriptorList(FolderProperty.class);
    }
}
