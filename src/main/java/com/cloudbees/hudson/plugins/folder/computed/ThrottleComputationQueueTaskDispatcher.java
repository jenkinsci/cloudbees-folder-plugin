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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;

/**
 * We need to be able to limit concurrent indexing.
 */
@SuppressWarnings("unused") // instantiated by Jenkins
@Extension
public class ThrottleComputationQueueTaskDispatcher extends QueueTaskDispatcher {

    private static final long ONE_SECOND_OF_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static int LIMIT = Math.max(1,
            Math.min(
                    Integer.getInteger(ThrottleComputationQueueTaskDispatcher.class.getName() + ".LIMIT", 5),
                    Runtime.getRuntime().availableProcessors() * 4
            )
    );
    private final List<NonBlockedDetails> nonBlocked = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        if (item.task instanceof ComputedFolder) {
            long now = System.nanoTime();
            int approvedCount;
            boolean found;
            synchronized (nonBlocked) {
                approvedCount = 0;
                found = false;
                for (Iterator<NonBlockedDetails> i = nonBlocked.iterator(); i.hasNext(); ) {
                    NonBlockedDetails details = i.next();
                    Queue.Item task = details.task.get();
                    if (task == null) {
                        i.remove();
                    } else if (now - details.when > ONE_SECOND_OF_NANOS) {
                        i.remove();
                    } else {
                        approvedCount++;
                        found = found || task == item;
                    }
                }
            }
            if (!found && indexingCount() + approvedCount > LIMIT) {
                // TODO make the limit configurable
                return CauseOfBlockage.fromMessage(Messages._ThrottleComputationQueueTaskDispatcher_MaxConcurrentIndexing());
            }
            if (!found) {
                synchronized (nonBlocked) {
                    nonBlocked.add(new NonBlockedDetails(now, item));
                }
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

    private static class NonBlockedDetails {
        private final long when;
        private final WeakReference<Queue.Item> task;

        public NonBlockedDetails(long when, Queue.Item task) {
            this.when = when;
            this.task = new WeakReference<Queue.Item>(task);
        }
    }

}
