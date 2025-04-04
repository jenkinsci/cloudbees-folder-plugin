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

import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Descriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Category of {@link AbstractFolderProperty}.
 * @since 4.11-beta-1
 */
public abstract class AbstractFolderPropertyDescriptor extends Descriptor<AbstractFolderProperty<?>> {

    /**
     * {@inheritDoc}
     *
     * @return
     *      null to avoid setting an instance of {@link AbstractFolderProperty} to the target folder.
     */
    @Override
    public AbstractFolderProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
        if (Util.isOverridden(AbstractFolderPropertyDescriptor.class, getClass(), "newInstance", StaplerRequest.class, JSONObject.class)) {
            return newInstance(req != null ? StaplerRequest.fromStaplerRequest2(req) : null, formData);
        } else {
            // Analogous to hack in JobPropertyDescriptor.
            if (formData.isNullObject()) {
                formData = new JSONObject();
            }
            return super.newInstance(req, formData);
        }
    }

    /**
     * @deprecated use {@link #newInstance(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    @Override
    public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        // Analogous to hack in JobPropertyDescriptor.
        if (formData.isNullObject()) {
            formData = new JSONObject();
        }
        return super.newInstance(req, formData);
    }

    /**
     * Returns true if this {@link AbstractFolderProperty} type is applicable to the
     * given folder type.
     *
     * <p>
     * The default implementation of this method checks if the given folder type is assignable to the type parameter of
     * {@link AbstractFolderProperty}, but subtypes can extend this to change this behavior.
     *
     * @param containerType the type of folder.
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this folder.
     */
    @SuppressWarnings("rawtypes") // erasure
    public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
        Type parameterization = Types.getBaseClass(clazz, AbstractFolderProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class<?> applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(containerType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize AbstractFolderProperty. The isApplicable() method must be overridden.");
        }
    }

    /**
     * Gets the {@link FolderPropertyDescriptor}s applicable for a given folder type.
     *
     * @param containerType the type of folder.
     * @return the applicable descriptors.
     */
    @SuppressWarnings("rawtypes") // erasure
    public static List<AbstractFolderPropertyDescriptor> getApplicableDescriptors(Class<? extends AbstractFolder> containerType) {
        List<AbstractFolderPropertyDescriptor> r = new ArrayList<>();
        for (AbstractFolderPropertyDescriptor p : ExtensionList.lookup(AbstractFolderPropertyDescriptor.class)) {
            if (p.isApplicable(containerType)) {
                r.add(p);
            }
        }
        return r;
    }

}
