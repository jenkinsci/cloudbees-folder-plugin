/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

/**
 * Handler which can move items which are both {@link AbstractItem} and {@link TopLevelItem} into a {@link DirectlyModifiableTopLevelItemGroup}.
 */
@SuppressWarnings("rawtypes")
@Restricted(NoExternalUse.class)
@Extension(ordinal=-1000) public final class StandardHandler extends RelocationHandler {

    @Override
    public HandlingMode applicability(Item item) {
        if (item instanceof TopLevelItem
                && item instanceof AbstractItem
                && item.getParent() instanceof DirectlyModifiableTopLevelItemGroup
                && hasValidDestination(item)) {
            return HandlingMode.HANDLE;
        } else {
            return HandlingMode.SKIP;
        }
    }

    @Override public HttpResponse handle(Item item, ItemGroup<?> destination, AtomicReference<Item> newItem, List<? extends RelocationHandler> chain) throws IOException, InterruptedException {
        if (!(destination instanceof DirectlyModifiableTopLevelItemGroup)) {
            return chain.isEmpty() ? null : chain.get(0).handle(item, destination, newItem, chain.subList(1, chain.size()));
        }
        Item result = doMove(item, (DirectlyModifiableTopLevelItemGroup) destination);
        newItem.set(result);
        // AbstractItem.getUrl does weird magic here which winds up making it redirect to the old location, so inline the correct part of this method.
        return HttpResponses.redirectViaContextPath(result.getParent().getUrl() + result.getShortUrl());
    }

    @SuppressWarnings("unchecked")
    private static <I extends AbstractItem & TopLevelItem> I doMove(Item item, DirectlyModifiableTopLevelItemGroup destination) throws IOException {
        return Items.move((I) item, destination);
    }

    public boolean hasValidDestination(Item item) {
        Jenkins instance = Jenkins.get();
        if (permitted(item, instance) && instance.getItem(item.getName()) == null) {
            // we can move to the root if there is none with the same name.
            return true;
        }
        ITEM: for (Item g : Items.allItems2(ACL.SYSTEM2, instance, Item.class)) {
            if (g instanceof DirectlyModifiableTopLevelItemGroup) {
                DirectlyModifiableTopLevelItemGroup itemGroup = (DirectlyModifiableTopLevelItemGroup) g;
                if (!permitted(item, itemGroup) || /* unlikely since we just checked CREATE, but just in case: */ !g.hasPermission(Item.READ)) {
                    continue;
                }
                // Cannot move a folder into itself or a descendant
                if (g == item) {
                    continue;
                }
                // Cannot move an item into a Folder if there is already an item with the same name
                if (g instanceof Folder) {
                    // NOTE: test for Folder not AbstractFolder as Folder is mutable by users
                    Folder folder = (Folder) g;
                    if (folder.getItem(item.getName()) != null) {
                        continue;
                    }
                }
                // Cannot move a folder into a descendant
                // Cannot move d1/ into say d1/d2/d3/
                ItemGroup itemGroupSubElement = g.getParent();
                while (itemGroupSubElement != instance) {
                    if (item == itemGroupSubElement) {
                        continue ITEM;
                    }
                    if (itemGroupSubElement instanceof Item) {
                        itemGroupSubElement = ((Item) itemGroupSubElement).getParent();
                    } else {
                        // should never get here as this is an ItemGroup resolved from the root of Jenkins,
                        // so there should be a parent chain ending at Jenkins but *if* we do end up here,
                        // safer to say this one is not safe to move into and break the infinite loop
                        continue ITEM;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public List<? extends ItemGroup<?>> validDestinations(Item item) {
        List<DirectlyModifiableTopLevelItemGroup> result = new ArrayList<>();
        Jenkins instance = Jenkins.get();
        // ROOT context is only added in case there is not any item with the same name
        // But we add it in case the one is there is the item itself and not a different job with the same name
        // No-op by default
        if (permitted(item, instance) && (instance.getItem(item.getName()) == null) || instance.getItem(item.getName()) == item) {
            result.add(instance);
        }
        ITEM: for (Item g : instance.getAllItems()) {
            if (g instanceof DirectlyModifiableTopLevelItemGroup) {
                DirectlyModifiableTopLevelItemGroup itemGroup = (DirectlyModifiableTopLevelItemGroup) g;
                if (!permitted(item, itemGroup)) {
                    continue;
                }
                // Cannot move a folder into itself or a descendant
                if (g == item) {
                    continue;
                }
                // By default the move is a no-op in case you hit it by mistake
                if (item.getParent() == g) {
                    result.add(itemGroup);
                }
                // Cannot move an item into a Folder if there is already an item with the same name
                if (g instanceof Folder) {
                    // NOTE: test for Folder not AbstractFolder as Folder is mutable by users
                    Folder folder = (Folder) g;
                    if (folder.getItem(item.getName()) != null) {
                        continue;
                    }
                }
                // Cannot move a folder into a descendant
                // Cannot move d1/ into say d1/d2/d3/
                ItemGroup itemGroupSubElement = g.getParent();
                while (itemGroupSubElement != instance) {
                    if (item == itemGroupSubElement) {
                        continue ITEM;
                    }
                    if (itemGroupSubElement instanceof Item) {
                        itemGroupSubElement = ((Item) itemGroupSubElement).getParent();
                    } else {
                        // should never get here as this is an ItemGroup resolved from the root of Jenkins,
                        // so there should be a parent chain ending at Jenkins but *if* we do end up here,
                        // safer to say this one is not safe to move into and break the infinite loop
                        continue ITEM;
                    }
                }
                result.add(itemGroup);
            }
        }
        return result;
    }

    private boolean permitted(Item item, DirectlyModifiableTopLevelItemGroup itemGroup) {
        return itemGroup == item.getParent() || itemGroup.canAdd((TopLevelItem) item) && ((AccessControlled) itemGroup).hasPermission(Job.CREATE);
    }

}
