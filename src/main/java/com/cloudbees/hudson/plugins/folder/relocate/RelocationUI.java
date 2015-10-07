/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

package com.cloudbees.hudson.plugins.folder.relocate;

import com.cloudbees.hudson.plugins.folder.Messages;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Item;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Extension point to provide a plugable UI for moving {@link Item} instances.
 *
 * @since 4.9
 */
public abstract class RelocationUI implements ExtensionPoint {

    /**
     * The {@link Action#getDisplayName()} to present for this UI.
     *
     * @return the {@link Action#getDisplayName()} to present for this UI, never {@code null}
     * @see Action#getDisplayName()
     */
    @Nonnull
    public String getDisplayName() {
        return Messages.RelocateAction_displayName();
    }

    /**
     * The {@link Action#getUrlName()} to present for this UI.
     *
     * @return the {@link Action#getUrlName()} to present for this UI, never {@code null}
     * @see Action#getUrlName()
     */
    @Nonnull
    public String getUrlName() {
        return "move";
    }

    /**
     * The {@link Action#getIconFileName()} to present for this UI.
     *
     * @return the {@link Action#getIconFileName()} to present for this UI, never {@code null}
     * @see Action#getIconFileName()
     */
    @Nonnull
    public String getIconFileName() {
        return "/plugin/cloudbees-folder/images/24x24/move.png";
    }

    /**
     * Checks if the relocation operation is currently available for the specific item 
     * (as {@link Jenkins#getAuthentication()} if the user is a factor). You can assume that the current user
     * has {@link RelocationAction#RELOCATE} permission.
     * 
     * @param item the item being checked.
     * @return {@code true} if the UI is available for the current user on the specified item.
     */
    public abstract boolean isAvailable(Item item);

    /**
     * Checks if this {@link RelocationUI} is applicable to the specified type of item.
     * @param itemClass the type of item.
     * @return {@code true} if this UI is applicable to the specified type of item.
     * @see ExtensionList
     * @see Extension#ordinal()
     */
    public abstract boolean isApplicableTo(Class<? extends Item> itemClass);

    /**
     * Retrieves the {@link RelocationUI} to use for the specified item.
     * @param item the item.
     * @return the {@link RelocationUI} to use or {@code null}
     */
    @CheckForNull
    public static RelocationUI for_(@Nonnull Item item) {
        final Class<? extends Item> itemClass = item.getClass();
        for (final RelocationUI ui : ExtensionList.lookup(RelocationUI.class)) {
            if (ui.isApplicableTo(itemClass)) {
                return ui;
            }
        }
        return null;
    }


}
