/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package com.cloudbees.hudson.plugins.folder.computed;

import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;

/**
 * Callback for {@link ComputedFolder}. Methods may be called only inside the scope of
 * {@link ComputedFolder#computeChildren} or an out-of-band event handler.
 *
 * @see ComputedFolder#computeChildren(ChildObserver, TaskListener)
 * @see ComputedFolder#openEventsChildObserver()
 */
public abstract class ChildObserver<I extends TopLevelItem> implements AutoCloseable {

    /**
     * Not implementable outside package.
     */
    ChildObserver() {
    }

    /**
     * Checks whether there is an existing child which should be updated. It is <strong>stronly </strong>recommended to
     * call {@link #completed(String)} after completion of processing the proposed {@link Item#getName()} as otherwise
     * no other {@link ChildObserver} will be able to proceed with this {@link Item#getName()}.
     *
     * @param name a proposed {@link Item#getName}
     * @return the existing child to update, if there is one (in which case go ahead and update it as needed); else
     * {@code null}, in which case continue by checking {@link #mayCreate}
     * @throws InterruptedException if interrupted.
     */
    @CheckForNull
    public abstract I shouldUpdate(String name) throws InterruptedException;

    /**
     * Checks whether we may create a new child of the given name.
     *
     * @param name a proposed {@link Item#getName}
     * @return true if you may go ahead and call {@link #created} (though you are not obliged to do so); {@code false}
     * if you may not
     */
    public abstract boolean mayCreate(String name);

    /**
     * Notify the observer that you did create a new child.
     *
     * @param child a newly constructed child item; do not call {@link Item#onCreatedFromScratch} and try to avoid
     *              calls to {@link Item#save}
     */
    public abstract void created(I child);

    /**
     * Notify the observer that you have completed with the named child and other threads are now permitted to proceed
     * with observations of the {@link Item#getName()}.
     *
     * @param name the {@link Item#getName()}.
     * @since 6.0.0
     */
    public abstract void completed(String name);

    /**
     * Returns a copy of the item names that have been observed.
     *
     * @return a copy of the item names that have been observed.
     * @since 5.14
     */
    public abstract Set<String> observed();

    /**
     * Returns a copy of the map of orphaned items keyed by name.
     *
     * @return a copy of the map of orphaned items keyed by name.
     * @since 5.14
     */
    public abstract Map<String, I> orphaned();

    /**
     * Closes the {@link ChildObserver} completing any observations that were not {@link #completed(String)}.
     * This method is idempotent.
     */
    @Override
    public abstract void close();
}
