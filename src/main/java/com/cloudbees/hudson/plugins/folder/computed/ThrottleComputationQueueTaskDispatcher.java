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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import jenkins.model.Jenkins;

/**
 * We need to be able to limit concurrent indexing.
 */
@SuppressWarnings("unused") // instantiated by Jenkins
@Extension
public class ThrottleComputationQueueTaskDispatcher extends QueueTaskDispatcher {
    /**
     * {@inheritDoc}
     */
    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        if (item.task instanceof ComputedFolder) {
            if (indexingCount() > 5) {
                // TODO make the limit configurable
                return CauseOfBlockage.fromMessage(Messages._ThrottleComputationQueueTaskDispatcher_MaxConcurrentIndexing());
            }
        }
        return null;
    }
    /**
     * Gets the number of current indexing tasks.
     *
     * @return number of current indexing tasks.
     */
    public int indexingCount() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return 0;
        }
        int result = indexingCount(j);
        for (Node n : j.getNodes()) {
            result += indexingCount(n);
        }
        return result;
    }
    /**
     * Gets the number of current indexing tasks on the specified node.
     *
     * @param node the node.
     * @return number of current indexing tasks on the specified node.
     */
    public int indexingCount(@CheckForNull Node node) {
        int result = 0;
        @CheckForNull
        Computer computer = node == null ? null : node.toComputer();
        if (computer != null) {
            // not all nodes will have a computer
            for (Executor e : computer.getExecutors()) {
                if (e.getCurrentExecutable() instanceof FolderComputation) {
                    result++;
                }
            }
            for (Executor e : computer.getOneOffExecutors()) {
                if (e.getCurrentExecutable() instanceof FolderComputation) {
                    result++;
                }
            }
        }
        return result;
    }

}
