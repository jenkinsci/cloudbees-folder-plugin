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
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Does the actual work of relocating an item.
 */
@Restricted(NoExternalUse.class)
public class RelocationAction implements Action {

    /**
     * The permission required to move an item.
     */
    public static final Permission RELOCATE = new Permission(Item.PERMISSIONS, "Move", Messages._RelocateAction_permission_desc(), Permission.CREATE, PermissionScope.ITEM);

    /**
     * The item that would be moved.
     */
    private final Item item;

    /**
     * Creates an instance of this action.
     *
     * @param item the item that would be moved.
     */
    public RelocationAction(Item item) {
        this.item = item;
    }

    @Override public String getIconFileName() {
        for (RelocationHandler handler : Jenkins.getInstance().getExtensionList(RelocationHandler.class)) {
            if (handler.applicability(item) == RelocationHandler.HandlingMode.HANDLE) {
                return "/plugin/cloudbees-folder/images/24x24/move.png";
            }
        }
        // No actual handler, so just hide.
        // TODO this is not working for MatrixConfiguration
        return null;
    }

    @Override public String getDisplayName() {
        return Messages.RelocateAction_displayName();
    }

    @Override public String getUrlName() {
        return "move";
    }

    /**
     * Gets the item that would be moved.
     *
     * @return the item that would be moved.
     */
    public Item getItem() {
        return item;
    }

    /**
     * Avoids a CCE caused by return type ambiguity in script access.
     * @return {@link Item#getParent} of {@link #getItem}
     */
    public ItemGroup<?> getItemParent() {
        return item.getParent();
    }

    /**
     * Gets the list of destinations that the item can be moved to by the current user.
     *
     * @return the list of destinations that the item can be moved to by the current user.
     */
    public Collection<ItemGroup<?>> getDestinations() {
        Collection<ItemGroup<?>> result = new LinkedHashSet<ItemGroup<?>>();
        for (RelocationHandler handler : Jenkins.getInstance().getExtensionList(RelocationHandler.class)) {
            if (handler.applicability(item) == RelocationHandler.HandlingMode.HANDLE) {
                result.addAll(handler.validDestinations(item));
            }
        }
        return result;
    }

    /**
     * Does the move.
     *
     * @param req         the request.
     * @param destination the destination.
     * @return the response.
     */
    @RequirePOST
    public HttpResponse doMove(StaplerRequest req, @QueryParameter String destination) throws IOException, InterruptedException {
        item.checkPermission(RELOCATE);
        ItemGroup dest = null;
        for (ItemGroup itemGroup : getDestinations()) {
            if (("/" + itemGroup.getFullName()).equals(destination)) {
                dest = itemGroup;
                break;
            }
        }
        if (dest == null || dest == item.getParent()) {
            return HttpResponses.forwardToPreviousPage();
        }
        List<RelocationHandler> chain = new ArrayList<RelocationHandler>();
        for (RelocationHandler handler : Jenkins.getInstance().getExtensionList(RelocationHandler.class)) {
            if (handler.applicability(item) != RelocationHandler.HandlingMode.SKIP) {
                chain.add(handler);
            }
        }
        if (chain.isEmpty()) {
            return new Failure("no known way to handle " + item);
        }
        HttpResponse response = chain.get(0).handle(item, dest, new AtomicReference<Item>(), chain.subList(1, chain.size()));
        if (response != null) {
            return response;
        } else {
            return HttpResponses.forwardToPreviousPage();
        }
    }

    /**
     * Makes sure that {@link Item}s have the action.
     */
    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<Item> {

        static {
            RELOCATE.getId(); // ensure loaded eagerly
        }

        @Override public Class<Item> type() {
            return Item.class;
        }

        @Override
        public Collection<? extends Action> createFor(Item target) {
            return Collections.singleton(new RelocationAction(target));
        }
    }

}
