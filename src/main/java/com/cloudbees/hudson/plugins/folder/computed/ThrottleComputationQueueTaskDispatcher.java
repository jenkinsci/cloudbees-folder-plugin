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
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;

/**
 * We need to be able to limit concurrent indexing.
 */
@SuppressWarnings("unused") // instantiated by Jenkins
@Extension
public class ThrottleComputationQueueTaskDispatcher extends QueueTaskDispatcher {

    /**
     * One second expressed as nanoseconds.
     */
    private static final long ONE_SECOND_OF_NANOS = TimeUnit.SECONDS.toNanos(1);
    /**
     * How many concurrent computations to permit.
     */
    static int LIMIT = Math.max(1,
            Math.min(
                    Integer.getInteger(ThrottleComputationQueueTaskDispatcher.class.getName() + ".LIMIT", 5),
                    Runtime.getRuntime().availableProcessors() * 4
            )
    );
    /**
     * A list of recently permitted computations. This list should never grow above {@link #LIMIT} entries.
     */
    @GuardedBy("self")
    private final List<TimestamppedWeakReference> recent = new ArrayList<>(LIMIT);

    /**
     * {@inheritDoc}
     */
    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        if (item.task instanceof ComputedFolder) {
            long now = System.nanoTime();
            int approvedCount;
            boolean found;
            Queue queue = Queue.getInstance();
            synchronized (recent) {
                approvedCount = 0;
                found = false;
                for (Iterator<TimestamppedWeakReference> i = recent.iterator(); i.hasNext(); ) {
                    TimestamppedWeakReference details = i.next();
                    Queue.Item task = details.get();
                    if (task == null) {
                        i.remove();
                    } else {
                        if (now - details.when > ONE_SECOND_OF_NANOS
                                || queue.getItem(task.getId()) instanceof Queue.LeftItem) {
                            i.remove();
                        } else {
                            approvedCount++;
                            found = found || task == item;
                        }
                    }
                }
            }
            if (!found && computationCount() + approvedCount >= LIMIT) {
                return CauseOfBlockage.fromMessage(Messages._ThrottleComputationQueueTaskDispatcher_MaxConcurrentIndexing());
            }
            if (!found) {
                synchronized (recent) {
                    // use the nanoTime we pruned recent with in order to ensure we don't have more than LIMIT entries
                    // note that this is not a constraint, just nice to know the list will not grow too big.
                    recent.add(new TimestamppedWeakReference(now, item));
                }
            }
        }
        return null;
    }

    /**
     * Gets the number of current computation tasks.
     *
     * @return number of current computation tasks.
     * @deprecated use {@link #computationCount()}
     */
    @Deprecated
    public int indexingCount() {
        return computationCount();
    }

    /**
     * Gets the number of current computation tasks.
     *
     * @return number of current computation tasks.
     */
    public int computationCount() {
        Jenkins j = Jenkins.getInstance();
        int result = computationCount(j);
        for (Node n : j.getNodes()) {
            result += computationCount(n);
        }
        return result;
    }

    /**
     * Gets the number of current computation tasks on the specified node.
     *
     * @param node the node.
     * @return number of current computation tasks on the specified node.
     * @deprecated use {@link #computationCount(Node)}
     */
    @Deprecated
    public int indexingCount(@CheckForNull Node node) {
        return computationCount(node);
    }

    /**
     * Gets the number of current computation tasks on the specified node.
     *
     * @param node the node.
     * @return number of current computation tasks on the specified node.
     */
    public int computationCount(@CheckForNull Node node) {
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

    /**
     * Extend {@link WeakReference} to allow association of secondary data without requiring excessive boxing of
     * {@link Long}s
     */
    private static class TimestamppedWeakReference extends WeakReference<Queue.Item> {
        /**
         * The {@link System#nanoTime()} comparable timestamp.
         */
        final long when;

        TimestamppedWeakReference(long when, Queue.Item task) {
            super(task);
            this.when = when;
        }
    }

}
