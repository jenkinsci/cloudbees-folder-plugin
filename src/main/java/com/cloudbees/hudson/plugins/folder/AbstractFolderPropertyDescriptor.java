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
import hudson.model.Descriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Category of .
 
 */
public abstract class AbstractFolderPropertyDescriptor extends Descriptor<AbstractFolderProperty<?>> {

    /**
     * 
     *
     
     *      null to avoid setting an instance of  to the target folder.
     */
    
    public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        // Analogous to hack in JobPropertyDescriptor.
        if (formData.isNullObject()) {
            formData = new JSONObject();
        }
        return super.newInstance(req, formData);
    }

    /**
     * Returns true if this  type is applicable to the
     * given folder type.
     *
     * <p>
     * The default implementation of this method checks if the given folder type is assignable to the type parameter of
     * , but subtypes can extend this to change this behavior.
     *
     
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this folder.
     */
     // erasure
    public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
        Type parameterization = Types.getBaseClass(clazz, AbstractFolderProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class<?> applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(containerType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize AbstractFolderProperty. The isApplicable() method must be overriden.");
        }
    }

    /**
     * Gets the s applicable for a given folder type.
     */
     // erasure
    public static List<AbstractFolderPropertyDescriptor> getApplicableDescriptors(Class<? extends AbstractFolder> containerType) {
        List<AbstractFolderPropertyDescriptor> r = new ArrayList<AbstractFolderPropertyDescriptor>();
        for (AbstractFolderPropertyDescriptor p : ExtensionList.lookup(AbstractFolderPropertyDescriptor.class)) {
            if (p.isApplicable(containerType)) {
                r.add(p);
            }
        }
        return r;
    }

}
