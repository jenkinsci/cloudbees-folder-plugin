package com.cloudbees.hudson.plugins.folder.computed;

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.QueueTaskFuture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

public class ThrottleComputationQueueTaskDispatcherTest {
    private static final Logger LOGGER = Logger.getLogger(ThrottleComputationQueueTaskDispatcherTest.class.getName());

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void acceptOne() throws Exception {
        SlowComputedFolder d = r.jenkins.createProject(SlowComputedFolder.class, "acceptOne");
        d.recompute(Result.SUCCESS);
    }

    @Test
    public void acceptLimit() throws Exception {
        SlowComputedFolder[] d = new SlowComputedFolder[ThrottleComputationQueueTaskDispatcher.LIMIT];
        Queue.Item[] q = new Queue.Item[ThrottleComputationQueueTaskDispatcher.LIMIT];
        QueueTaskFuture[] f = new QueueTaskFuture[ThrottleComputationQueueTaskDispatcher.LIMIT];
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch[] started = new CountDownLatch[ThrottleComputationQueueTaskDispatcher.LIMIT];
        for (int i = 0; i < d.length; i++) {
            d[i] = r.jenkins.createProject(SlowComputedFolder.class, "acceptLimit-" + i);
            d[i].started = started[i] = new CountDownLatch(1);
            d[i].finish = finished;
            q[i] = d[i].scheduleBuild2(0);
            f[i] = q[i].getFuture();
        }
        Random entropy = new Random();
        long startNanoTime = System.nanoTime();
        Future<?> maint = Queue.getInstance().scheduleMaintenance();
        long waitForConditionNanos = TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() - startNanoTime < waitForConditionNanos) {
            int startedCount = 0;
            int notStartedIndex = -1;
            for (int i = 0; i < started.length; i++) {
                if (started[i].getCount() == 0) {
                    startedCount++;
                } else if (notStartedIndex == -1 || entropy.nextBoolean()) {
                    notStartedIndex = i;
                }
            }
            if (startedCount >= ThrottleComputationQueueTaskDispatcher.LIMIT) {
                assertThat(startedCount, is(ThrottleComputationQueueTaskDispatcher.LIMIT));
                assertThat(notStartedIndex, is(-1));
                break;
            }
            if (maint.isDone()) {
                maint = Queue.getInstance().scheduleMaintenance();
            }
            if (notStartedIndex != -1) {
                // faster to wait on one randomly chosen non-started one than just wait unconditionally
                // as that way we have at least a 50:50 chance we are waiting on one that starts
                started[notStartedIndex].await(10, TimeUnit.MILLISECONDS);
            } else {
                maint.get(10, TimeUnit.MILLISECONDS);
            }
        }
        finished.countDown();
        for (int i = 0; i < f.length; i++) {
            f[i].get();
            FolderComputation<?> computation = d[i].getComputation();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            computation.writeWholeLogTo(baos);
            String log = baos.toString();
            assertEquals(log, Result.SUCCESS, computation.getResult());
        }
    }

    @Test
    public void blockOneAboveLimit() throws Exception {
        SlowComputedFolder[] d = new SlowComputedFolder[ThrottleComputationQueueTaskDispatcher.LIMIT + 1];
        Queue.Item[] q = new Queue.Item[ThrottleComputationQueueTaskDispatcher.LIMIT + 1];
        QueueTaskFuture[] f = new QueueTaskFuture[ThrottleComputationQueueTaskDispatcher.LIMIT + 1];
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch[] started = new CountDownLatch[ThrottleComputationQueueTaskDispatcher.LIMIT + 1];
        for (int i = 0; i < d.length; i++) {
            d[i] = r.jenkins.createProject(SlowComputedFolder.class, "blockOneAboveLimit-" + i);
            d[i].started = started[i] = new CountDownLatch(1);
            d[i].finish = finished;
            q[i] = d[i].scheduleBuild2(0);
            f[i] = q[i].getFuture();
        }
        Random entropy = new Random();
        long startNanoTime = System.nanoTime();
        long maintNanoTime = System.nanoTime();
        Future<?> maint = Queue.getInstance().scheduleMaintenance();
        long waitForConditionNanos = TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() - startNanoTime < waitForConditionNanos) {
            int startedCount = 0;
            int notStartedIndex = -1;
            for (int i = 0; i < started.length; i++) {
                if (started[i].getCount() == 0) {
                    startedCount++;
                } else if (notStartedIndex == -1 || entropy.nextBoolean()) {
                    notStartedIndex = i;
                }
            }
            if (startedCount >= ThrottleComputationQueueTaskDispatcher.LIMIT) {
                assertThat(startedCount, is(ThrottleComputationQueueTaskDispatcher.LIMIT));
                LOGGER.log(Level.INFO, "All {0} started", startedCount);
                assertThat(notStartedIndex, not(is(-1)));
                assertThat(q[notStartedIndex].getCauseOfBlockage(), notNullValue());
                break;
            }
            if (maint.isDone() && maintNanoTime - System.nanoTime() > TimeUnit.MILLISECONDS.toNanos(500)) {
                maint = Queue.getInstance().scheduleMaintenance();
            }
            if (notStartedIndex != -1) {
                // faster to wait on one randomly chosen non-started one than just wait unconditionally
                // as that way we have at least a 50:50 chance we are waiting on one that starts
                started[notStartedIndex].await(10, TimeUnit.MILLISECONDS);
            } else {
                maint.get(10, TimeUnit.MILLISECONDS);
            }
        }
        finished.countDown();
        for (int i = 0; i < f.length; i++) {
            f[i].get();
            FolderComputation<?> computation = d[i].getComputation();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            computation.writeWholeLogTo(baos);
            String log = baos.toString();
            assertEquals(log, Result.SUCCESS, computation.getResult());
        }
    }

    @Test
    public void blockManyAboveLimit() throws Exception {
        // The queue could pick them up in any random order, so we need to leave at least one slot free in the
        // second set in order to ensure that the first set can complete (even eventually) once we release
        // it's finished latch, hence 2*LIMIT-1 and not 2*LIMIT. If we used 2*LIMIT then every so
        // often we would expect a random case where all the scheduled items are in the second tranch
        // and hence the first tranch can never be unblocked.
        SlowComputedFolder[] d = new SlowComputedFolder[ThrottleComputationQueueTaskDispatcher.LIMIT * 2 - 1];
        Queue.Item[] q = new Queue.Item[ThrottleComputationQueueTaskDispatcher.LIMIT * 2 - 1];
        QueueTaskFuture[] f = new QueueTaskFuture[ThrottleComputationQueueTaskDispatcher.LIMIT * 2 - 1];
        CountDownLatch[] finished = new CountDownLatch[]{new CountDownLatch(1), new CountDownLatch(1)};
        CountDownLatch[] started = new CountDownLatch[ThrottleComputationQueueTaskDispatcher.LIMIT * 2 - 1];
        for (int i = 0; i < d.length; i++) {
            d[i] = r.jenkins.createProject(SlowComputedFolder.class, "blockManyAboveLimit-" + i);
            d[i].started = started[i] = new CountDownLatch(1);
            d[i].finish = finished[i / ThrottleComputationQueueTaskDispatcher.LIMIT];
            q[i] = d[i].scheduleBuild2(0);
            f[i] = q[i].getFuture();
        }
        Random entropy = new Random();
        // now wait for the started count to reach the limit
        {
            int expectedStartCount = ThrottleComputationQueueTaskDispatcher.LIMIT;
            long startNanoTime = System.nanoTime();
            long maintNanoTime = System.nanoTime();
            Future<?> maint = Queue.getInstance().scheduleMaintenance();
            long waitForConditionNanos = TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() - startNanoTime < waitForConditionNanos) {
                int startedCount = 0;
                int notStartedIndex = -1;
                for (int i = 0; i < started.length; i++) {
                    if (started[i].getCount() == 0) {
                        startedCount++;
                    } else if (notStartedIndex == -1 || entropy.nextBoolean()) {
                        notStartedIndex = i;
                    }
                }
                if (startedCount >= expectedStartCount) {
                    assertThat(startedCount, is(expectedStartCount));
                    LOGGER.log(Level.INFO, "All {0} for tranch 1 started", startedCount);
                    assertThat(notStartedIndex, not(is(-1)));
                    assertThat(q[notStartedIndex].getCauseOfBlockage(), notNullValue());
                    break;
                }
                if (maint.isDone() && maintNanoTime - System.nanoTime() > TimeUnit.MILLISECONDS.toNanos(500)) {
                    maintNanoTime = System.nanoTime();
                    maint = Queue.getInstance().scheduleMaintenance();
                }
                if (notStartedIndex != -1) {
                    // faster to wait on one randomly chosen non-started one than just wait unconditionally
                    // as that way we have at least a 50:50 chance we are waiting on one that starts
                    started[notStartedIndex].await(10, TimeUnit.MILLISECONDS);
                } else {
                    maint.get(10, TimeUnit.MILLISECONDS);
                }
            }
            finished[0].countDown();
        }
        // now wait for everything to start
        {
            int expectedStartCount = ThrottleComputationQueueTaskDispatcher.LIMIT * 2 - 1;
            long startNanoTime = System.nanoTime();
            long maintNanoTime = System.nanoTime();
            Future<?> maint = Queue.getInstance().scheduleMaintenance();
            long waitForConditionNanos = TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() - startNanoTime < waitForConditionNanos) {
                int startedCount = 0;
                int notStartedIndex = -1;
                for (int i = 0; i < started.length; i++) {
                    if (started[i].getCount() == 0) {
                        startedCount++;
                    } else if (notStartedIndex == -1 || entropy.nextBoolean()) {
                        notStartedIndex = i;
                    }
                }
                if (startedCount >= expectedStartCount) {
                    assertThat(startedCount, is(expectedStartCount));
                    LOGGER.log(Level.INFO, "All {0} for tranches 1 and 2 started", startedCount);
                    assertThat(notStartedIndex, is(-1));
                    break;
                }
                if (maint.isDone() && maintNanoTime - System.nanoTime() > TimeUnit.MILLISECONDS.toNanos(500)) {
                    maint = Queue.getInstance().scheduleMaintenance();
                }
                if (notStartedIndex != -1) {
                    // faster to wait on one randomly chosen non-started one than just wait unconditionally
                    // as that way we have at least a 50:50 chance we are waiting on one that starts
                    started[notStartedIndex].await(10, TimeUnit.MILLISECONDS);
                } else {
                    maint.get(10, TimeUnit.MILLISECONDS);
                }
            }
            finished[1].countDown();
        }
        for (int i = 0; i < f.length; i++) {
            f[i].get();
            FolderComputation<?> computation = d[i].getComputation();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            computation.writeWholeLogTo(baos);
            String log = baos.toString();
            assertEquals(log, Result.SUCCESS, computation.getResult());
        }
    }

    static String doRecompute(ComputedFolder<?> d, Result result) throws Exception {
        if (d.isDisabled()) {
            assertEquals("Folder " + d.getFullName() + " is disabled", result, Result.NOT_BUILT);
            return "DISABLED";
        }
        d.scheduleBuild2(0).getFuture().get();
        FolderComputation<?> computation = d.getComputation();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        computation.writeWholeLogTo(baos);
        String log = baos.toString();
        assertEquals(log, result, computation.getResult());
        return log;
    }

    public static class SlowComputedFolder extends ComputedFolder<FreeStyleProject> {

        private transient CountDownLatch started;
        private transient CountDownLatch finish;

        public SlowComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener)
                throws IOException, InterruptedException {
            LOGGER.log(Level.INFO, "Starting {0}", getFullName());
            try {
                if (started != null) {
                    started.countDown();
                }
                listener.getLogger().printf("[%tc] Started...%n", new Date());
                if (finish != null) {
                    if (!finish.await(60, TimeUnit.SECONDS)) {
                        throw new IOException("Timeout");
                    }
                }
                listener.getLogger().printf("[%tc] Finished!%n", new Date());
            } finally {
                LOGGER.log(Level.INFO, "Finished {0}", getFullName());
            }
        }

        String recompute(Result result) throws Exception {
            return doRecompute(this, result);
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SlowComputedFolder(parent, name);
            }

        }
    }
}
