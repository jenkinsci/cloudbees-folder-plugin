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

package com.cloudbees.hudson.plugins.folder;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Queue;
import java.util.List;

/**
 * A decision handler to prevent building jobs in a disabled folder.
 *
 * @since 6.1.0
 */
@Extension
public class FolderJobQueueDecisionHandler extends Queue.QueueDecisionHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        if (p instanceof Item) {
            Item i = (Item) p;
            while (i != null) {
                if (i instanceof AbstractFolder && ((AbstractFolder) i).isDisabled()) {
                    return false;
                }
                if (i.getParent() instanceof Item) {
                    i = (Item) i.getParent();
                } else {
                    break;
                }
            }
        }
        return true;
    }
}
