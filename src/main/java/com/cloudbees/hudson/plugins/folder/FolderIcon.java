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

import hudson.ExtensionPoint;
import hudson.model.AbstractStatusIcon;
import hudson.model.Describable;
import hudson.model.StatusIcon;
import jenkins.model.Jenkins;

/**
 * Renders {@link StatusIcon} for a folder.
 *
 * <p>
 * Possible subtypes can range from dumb icons that always render the same thing to smarter icons
 * that change its icon based on the properties/contents of the folder. 
 */
public abstract class FolderIcon extends AbstractStatusIcon implements Describable<FolderIcon>, ExtensionPoint {
    /**
     * Called by {@link AbstractFolder} to set the owner that this icon is used for.
     * <p>
     * If you are implementing {@link FolderIcon} that changes the behaviour based on the contents/properties
     * of the folder, store the folder object to a field and use that. 
     */
    protected void setOwner(AbstractFolder<?> folder) {
        if (folder instanceof Folder) {
            setFolder((Folder) folder);
        }
    }

    /** @deprecated */
    protected void setFolder(Folder folder) {}

    @Override
    public FolderIconDescriptor getDescriptor() {
        return (FolderIconDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }
}
