/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.Collection;

/**
 * A strategy for removing children after they are no longer indexed by an owning {@link ComputedFolder}.
 */
public abstract class OrphanedItemStrategy extends AbstractDescribableImpl<OrphanedItemStrategy> implements ExtensionPoint {

    /** parameters and return value as in {@link ComputedFolder#orphanedItems} */
    public abstract <I extends TopLevelItem> Collection<I> orphanedItems(ComputedFolder<I> owner, Collection<I> orphaned, TaskListener listener) throws IOException,  InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    public OrphanedItemStrategyDescriptor getDescriptor() {
        return (OrphanedItemStrategyDescriptor) super.getDescriptor();
    }

}
