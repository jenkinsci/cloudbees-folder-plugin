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
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.StaplerRequest;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

public abstract class FolderPropertyDescriptor extends Descriptor<FolderProperty<?>> {
    /**
     * {@inheritDoc}
     *
     * @return
     *      null to avoid setting an instance of {@link FolderProperty} to the target folder.
     */
    @Override
    public FolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        // JobPropertyDescriptors are bit different in that we allow them even without any user-visible configuration parameter,
        // so replace the lack of form data by an empty one.
        if(formData.isNullObject()) formData=new JSONObject();

        return super.newInstance(req, formData);
    }

    /**
     * Returns true if this {@link FolderProperty} type is applicable to the
     * given folder type.
     *
     * <p>
     * The default implementation of this method checks if the given folder type is assignable to the type parameter of
     * {@link FolderProperty}, but subtypes can extend this to change this behavior.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this folder.
     */
    public boolean isApplicable(Class<? extends Folder> containerType) {
        Type parameterization = Types.getBaseClass(clazz, FolderProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class<?> applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(containerType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize FolderProperty. The isApplicable() method must be overriden.");
        }
    }

    /**
     * Gets the {@link FolderPropertyDescriptor}s applicable for a given folder type.
     */
    public static List<FolderPropertyDescriptor> getPropertyDescriptors(Class<? extends Folder> containerType) {
        List<FolderPropertyDescriptor> r = new ArrayList<FolderPropertyDescriptor>();
        for (FolderPropertyDescriptor p : all())
            if(p.isApplicable(containerType))
                r.add(p);
        return r;
    }

    @SuppressWarnings({"unchecked"})
    public static DescriptorExtensionList<FolderProperty<?>, FolderPropertyDescriptor> all() {
        return Jenkins.getActiveInstance().getDescriptorList(FolderProperty.class);
    }
}
