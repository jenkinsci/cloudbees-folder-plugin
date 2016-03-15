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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.kohsuke.stapler.HttpResponse;

/**
 * Provides some kind of specialized handling for a move.
 * Handlers are chained in extension registration order, so that some can decorate other handlers.
 * It is also possible for a handler to send a placeholder response and schedule a move for later (keeping the rest of the handlers ready).
 */
public abstract class RelocationHandler implements ExtensionPoint {

    public enum HandlingMode {
        /** Can in general handle the actual move.  and  may both be called. */
        HANDLE,
        /** May only delegate to another handler.  may be called, but not . */
        DELEGATE,
        /** Skips this item entirely. Neither  nor  should be called. */
        SKIP
    }

    /**
     * Checks quickly whether this handler might be able to move a given item.
     
     *
     */
    public abstract HandlingMode applicability(Item item);

    /**
     * Possibly handles redirecting an item.
     
     
     
     
     *
     
     
     */
    public abstract HttpResponse handle(Item item, ItemGroup<?> destination, AtomicReference<Item> newItem, List<? extends RelocationHandler> chain) throws IOException, InterruptedException;

    /**
     * Gathers a list of possible destinations to which an item may be moved.
     * The union of all destinations from various handlers is used.
     
     *
     */
    public abstract List<? extends ItemGroup<?>> validDestinations(Item item);

}
