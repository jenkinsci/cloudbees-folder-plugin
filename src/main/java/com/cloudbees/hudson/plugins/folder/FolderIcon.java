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
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.Stapler;

/**
 * Renders {@link StatusIcon} for a folder.
 *
 * <p>
 * Possible subtypes can range from dumb icons that always render the same thing to smarter icons
 * that change its icon based on the properties/contents of the folder. 
 */
public abstract class FolderIcon extends AbstractStatusIcon implements Describable<FolderIcon>, ExtensionPoint,
        IconSpec {
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

    @Override
    public String getIconClassName() {
        return null;
    }

    protected String iconClassNameImageOf(String size) {
        String iconClassName = getIconClassName();
        if (StringUtils.isNotBlank(iconClassName)) {
            String spec = null;
            if ("16x16".equals(size)) {
                spec = "icon-sm";
            } else if ("24x24".equals(size)) {
                spec = "icon-md";
            } else if ("32x32".equals(size)) {
                spec = "icon-lg";
            } else if ("48x48".equals(size)) {
                spec = "icon-xlg";
            }
            if (spec != null) {
                Icon icon = IconSet.icons.getIconByClassSpec(iconClassName + " " + spec);
                if (icon != null) {
                    JellyContext ctx = new JellyContext();
                    ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
                    return icon.getQualifiedUrl(ctx);
                }
            }
        }
        return null;
    }

    /** @deprecated */
    protected void setFolder(Folder folder) {}

    @Override
    public FolderIconDescriptor getDescriptor() {
        return (FolderIconDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }
}
