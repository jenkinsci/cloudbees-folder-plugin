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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Default implementation of {@link RelocationUI}
 * 
 * @since 4.9
 */
@Extension(ordinal = -1000.0)
public class DefaultRelocationUI extends RelocationUI {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicableTo(Class<? extends Item> itemClass) {
        return true;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable(Item item) {
        for (RelocationHandler handler : ExtensionList.lookup(RelocationHandler.class)) {
            if (handler.applicability(item) == RelocationHandler.HandlingMode.HANDLE) {
                return true;
            }
        }
        // No actual handler, so not available.
        return false;
    }
    
    /**
     * List of destinations that the item can be moved to by the current user.
     *
     * @param item the item.
     * @return the list of destinations that the item can be moved to by the current user.
     */
    public Collection<ItemGroup<?>> listDestinations(Item item) {
        Collection<ItemGroup<?>> result = new LinkedHashSet<>();
        for (RelocationHandler handler : ExtensionList.lookup(RelocationHandler.class)) {
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
     * @param item        the item
     * @param destination the destination.
     * @return the response.
     * @throws IOException          if things go wrong.
     * @throws InterruptedException if interrupted.
     */
    @RequirePOST
    public HttpResponse doMove(StaplerRequest2 req, @AncestorInPath Item item, @QueryParameter String destination)
            throws IOException, InterruptedException {
        item.checkPermission(RelocationAction.RELOCATE);
        ItemGroup dest = null;
        for (ItemGroup itemGroup : listDestinations(item)) {
            if (("/" + itemGroup.getFullName()).equals(destination)) {
                dest = itemGroup;
                break;
            }
        }
        if (dest == null || dest == item.getParent()) {
            return HttpResponses.forwardToPreviousPage();
        }
        List<RelocationHandler> chain = new ArrayList<>();
        for (RelocationHandler handler : ExtensionList.lookup(RelocationHandler.class)) {
            if (handler.applicability(item) != RelocationHandler.HandlingMode.SKIP) {
                chain.add(handler);
            }
        }
        if (chain.isEmpty()) {
            return new Failure("no known way to handle " + item);
        }
        HttpResponse response = chain.get(0).handle(item, dest, new AtomicReference<>(), chain.subList(1, chain.size()));
        if (response != null) {
            return response;
        } else {
            return HttpResponses.forwardToPreviousPage();
        }
    }

}
