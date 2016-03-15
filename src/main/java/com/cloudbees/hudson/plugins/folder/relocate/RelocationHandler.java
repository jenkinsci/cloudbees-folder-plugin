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
        /** Can in general handle the actual move. {@link #handle} and {@link #validDestinations} may both be called. */
        HANDLE,
        /** May only delegate to another handler. {@link #handle} may be called, but not {@link #validDestinations}. */
        DELEGATE,
        /** Skips this item entirely. Neither {@link #handle} nor {@link #validDestinations} should be called. */
        SKIP
    }

    /**
     * Checks quickly whether this handler might be able to move a given item.
     * @param item an item which the user wishes to move
     * @return how this handler might handle the given item
     */
    public abstract @NonNull HandlingMode applicability(@NonNull Item item);

    /**
     * Possibly handles redirecting an item.
     * @param item an item which the user wishes to move
     * @param destination the location the user wishes to move it to
     * @param newItem if moving succeeds, set this to the new item (typically same object as {@code item})
     * @param chain zero or more remaining handlers which could be delegated to (may call this method on the first and pass in the rest of the chain)
     * @return {@link Failure} if the move is known to not be able to proceed, or a custom response such as a redirect, or a delegated response from the first handler in the chain, or null if no HTTP response is warranted or possible
     * @throws IOException if the move was attempted but failed
     * @throws InterruptedException if the move was attempted but was interrupted
     */
    public abstract @CheckForNull HttpResponse handle(@NonNull Item item, @NonNull ItemGroup<?> destination, @NonNull AtomicReference<Item> newItem, @NonNull List<? extends RelocationHandler> chain) throws IOException, InterruptedException;

    /**
     * Gathers a list of possible destinations to which an item may be moved.
     * The union of all destinations from various handlers is used.
     * @param item an item which the user wishes to move
     * @return potential destinations (may be empty if this handler does not need to add new kinds of destinations)
     */
    public abstract @NonNull List<? extends ItemGroup<?>> validDestinations(@NonNull Item item);

}
