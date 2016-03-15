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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

public abstract class FolderProperty<C extends Folder> extends AbstractFolderProperty<C> {

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

    /**
     * {@link hudson.model.Action}s to be displayed in the folder page.
     * <p>
     * Returning actions from this method allows a folder property to add them
     * to the left navigation bar in the folder page.
     *
     * @return can be empty but never null.
     * @since 3.14
     * @deprecated Use {@link TransientActionFactory} instead.
     */
    @NonNull
    public Collection<? extends Action> getFolderActions() {
        return Collections.emptyList();
    }
}
