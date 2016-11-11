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
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;

/**
 * Callback for {@link ComputedFolder}.
 * Methods may be called only inside the scope of {@link ComputedFolder#computeChildren} or an out-of-band event handler.
 * @see ComputedFolder#computeChildren(ChildObserver, TaskListener)
 * @see ComputedFolder#createEventsChildObserver()
 */
public abstract class ChildObserver<I extends TopLevelItem> {

    /** Not implementable outside package. */
    ChildObserver() {}

    /**
     * Checks whether there is an existing child which should be updated.
     * @param name a proposed {@link Item#getName}
     * @return the existing child to update, if there is one (in which case go ahead and update it as needed); else null, in which case continue by checking {@link #mayCreate}
     */
    public abstract @CheckForNull I shouldUpdate(String name);

    /**
     * Checks whether we may create a new child of the given name.
     * @param name a proposed {@link Item#getName}
     * @return true if you may go ahead and call {@link #created} (though you are not obliged to do so); false if you may not
     */
    public abstract boolean mayCreate(String name);

    /**
     * Notify the observer that you did create a new child.
     * @param child a newly constructed child item; do not call {@link Item#onCreatedFromScratch} and try to avoid calls to {@link Item#save}
     */
    public abstract void created(I child);

    /**
     * Returns a copy of the item names that have been observed.
     *
     * @return a copy of the item names that have been observed.
     * @since FIXME
     */
    public abstract Set<String> observed();

    /**
     * Returns a copy of the map of orphaned items keyed by name.
     *
     * @return a copy of the map of orphaned items keyed by name.
     * @since FIXME
     */
    public abstract Map<String,I> orphaned();

}
