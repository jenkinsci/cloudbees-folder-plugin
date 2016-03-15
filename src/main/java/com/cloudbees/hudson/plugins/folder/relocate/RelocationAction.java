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

package com.cloudbees.hudson.plugins.folder.relocate;

import com.cloudbees.hudson.plugins.folder.Messages;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerFallback;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * Does the actual work of relocating an item.
 */

public class RelocationAction implements Action, StaplerFallback {

    /**
     * The permission required to move an item.
     */
    public static final Permission RELOCATE = new Permission(Item.PERMISSIONS, "Move", Messages._RelocateAction_permission_desc(), Permission.CREATE, PermissionScope.ITEM);

    /**
     * The item that would be moved.
     */
    private final Item item;

    /**
     * The UI to use for the relocation action.
     *
     
     */
    
    private final RelocationUI ui;

    /**
     * Creates an instance of this action.
     *
     
     */
    public RelocationAction(Item item) {
        this.item = item;
        this.ui = RelocationUI.for_(item);
    }

    public RelocationAction(Item item, RelocationUI ui) {
        this.item = item;
        this.ui = ui;
    }


    /**
     * 
     */
     
    public String getIconFileName() {
        return !item.hasPermission(RELOCATE) || ui == null || !ui.isAvailable(item) ? null : ui.getIconFileName();
    }

    /**
     * 
     */
     
    public String getDisplayName() {
        return ui == null ? null : ui.getDisplayName();
    }

    /**
     * 
     */
     
    public String getUrlName() {
        return ui == null ? null : ui.getUrlName();
    }

    /**
     * Gets the item that would be moved.
     *
     *
     */
    public Item getItem() {
        return item;
    }

    /**
     * Getter for the UI to display in this action.
     *
     *
     
     */
    
    public RelocationUI getUi() {
        return ui;
    }

    /**
     * 
     */
    
    public Object getStaplerFallback() {
        return ui;
    }

    /**
     * Avoids a CCE caused by return type ambiguity in script access.
     *
     */
    public ItemGroup<?> getItemParent() {
        return item.getParent();
    }

    /**
     * Makes sure that s have the action.
     */
    
    
    public static class TransientActionFactoryImpl extends TransientActionFactory<Item> {

        static {
            RELOCATE.getId(); // ensure loaded eagerly
        }

         public Class<Item> type() {
            return Item.class;
        }

        
        public Collection<? extends Action> createFor(Item target) {
            return Collections.singleton(new RelocationAction(target));
        }
    }

}
