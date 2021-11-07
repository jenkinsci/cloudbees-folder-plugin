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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.tasks.LogRotator;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The default {@link OrphanedItemStrategy}.
 * Trims dead items by the # of days or the # of builds much like {@link LogRotator}.
 *
 * @author Stephen Connolly
 */
public class DefaultOrphanedItemStrategy extends OrphanedItemStrategy {

    /**
     * Should old branches be removed at all
     */
    private final boolean pruneDeadBranches;

    /**
     * If not -1, dead branches are only kept up to this days.
     */
    private final int daysToKeep;

    /**
     * If not -1, only this number of dead branches are kept.
     */
    private final int numToKeep;

    /**
     * Should pending or ongoing builds be aborted
     */
    private final boolean abortBuilds;

    /**
     * @deprecated Use {@link #DefaultOrphanedItemStrategy(boolean, String, String, boolean)} instead.
     */
    @Deprecated
    public DefaultOrphanedItemStrategy(boolean pruneDeadBranches, @CheckForNull String daysToKeepStr,
                                     @CheckForNull String numToKeepStr) {
        this(pruneDeadBranches, daysToKeepStr, numToKeepStr, false);
    }

    /**
     * Stapler's constructor.
     *
     * @param pruneDeadBranches remove dead branches.
     * @param daysToKeepStr     how old a branch must be to remove.
     * @param numToKeepStr      how many branches to keep.
     * @param abortBuilds       abort pending or ongoing builds
     */
    @DataBoundConstructor
    public DefaultOrphanedItemStrategy(boolean pruneDeadBranches, @CheckForNull String daysToKeepStr,
                                       @CheckForNull String numToKeepStr, boolean abortBuilds) {
        this.pruneDeadBranches = pruneDeadBranches;
        // TODO in lieu of DeadBranchCleanupThread, introduce a form warning if daysToKeep < PeriodicFolderTrigger.interval
        this.daysToKeep = pruneDeadBranches ? fromString(daysToKeepStr) : -1;
        this.numToKeep = pruneDeadBranches ? fromString(numToKeepStr) : -1;
        this.abortBuilds = abortBuilds;
    }

    /**
     * @deprecated Use {@link #DefaultOrphanedItemStrategy(boolean, int, int, boolean)} instead
     */
    @Deprecated
    public DefaultOrphanedItemStrategy(boolean pruneDeadBranches, int daysToKeep, int numToKeep) {
        this(pruneDeadBranches, daysToKeep, numToKeep, false);
    }

    /**
     * Programmatic constructor.
     *
     * @param pruneDeadBranches remove dead branches.
     * @param daysToKeep        how old a branch must be to remove.
     * @param numToKeep         how many branches to keep.
     * @param abortBuilds       abort pending or ongoing builds
     */
    public DefaultOrphanedItemStrategy(boolean pruneDeadBranches, int daysToKeep, int numToKeep, boolean abortBuilds) {
        this.pruneDeadBranches = pruneDeadBranches;
        this.daysToKeep = pruneDeadBranches && daysToKeep > 0? daysToKeep : -1;
        this.numToKeep = pruneDeadBranches && numToKeep > 0 ? numToKeep : -1;
        this.abortBuilds = abortBuilds;
    }

    /**
     * Migrate legacy configurations to correct configuration.
     *
     * @return the deserialized object.
     * @throws ObjectStreamException if something goes wrong.
     */
    private Object readResolve() throws ObjectStreamException {
        if (daysToKeep == 0 || numToKeep == 0) {
            return new DefaultOrphanedItemStrategy(pruneDeadBranches, daysToKeep, numToKeep, abortBuilds);
        }
        return this;
    }

    /**
     * Gets the number of days to keep dead branches.
     *
     * @return the number of days to keep dead branches.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public int getDaysToKeep() {
        return daysToKeep;
    }

    /**
     * Gets the number of dead branches to keep.
     *
     * @return the number of dead branches to keep.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public int getNumToKeep() {
        return numToKeep;
    }

    /**
     * Returns {@code true} if dead branches should be removed.
     *
     * @return {@code true} if dead branches should be removed.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public boolean isPruneDeadBranches() {
        return pruneDeadBranches;
    }

    /**
     * Returns {@code true} if pending or ongoing should be aborted.
     *
     * @return {@code true} if pending or ongoing should be aborted.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public boolean isAbortBuilds() {
        return abortBuilds;
    }

    /**
     * Returns the number of days to keep dead branches.
     *
     * @return the number of days to keep dead branches.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    @NonNull
    public String getDaysToKeepStr() {
        return toString(daysToKeep);
    }

    /**
     * Gets the number of dead branches to keep.
     *
     * @return the number of dead branches to keep.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    @NonNull
    public String getNumToKeepStr() {
        return toString(numToKeep);
    }

    /**
     * Helper to turn a int into a string where {@code -1} correspond to the empty string.
     *
     * @param i the possibly {@code null} {@link Integer}
     * @return the {@link String} representation of the int.
     */
    @NonNull
    private static String toString(int i) {
        if (i == -1) {
            return "";
        }
        return String.valueOf(i);
    }

    /**
     * Inverse of {@link #toString(int)}.
     *
     * @param s the string.
     * @return the int.
     */
    private static int fromString(@CheckForNull String s) {
        if (StringUtils.isBlank(s)) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long lastBuildTime(TopLevelItem item) {
        long t = 0;
        if (item instanceof ComputedFolder) {
            // pick up special case of computed folders
            t = ((ComputedFolder)item).getComputation().getTimestamp().getTimeInMillis();
        }
        for (Job<?,?> j : item.getAllJobs()) {
            Run<?,?> b = j.getLastBuild();
            if (b != null) {
                t = Math.max(t, b.getTimeInMillis());
            }
        }
        return t;
    }

    private static boolean disabled(TopLevelItem item) {
        // TODO revisit once on 2.61+ for https://github.com/jenkinsci/jenkins/pull/2866
        if (item instanceof AbstractFolder) {
            return ((AbstractFolder) item).isDisabled();
        } else if (item instanceof AbstractProject) {
            return ((AbstractProject) item).isDisabled();
        } else {
            try {
                Method isDisabled = item.getClass().getMethod("isDisabled");
                return boolean.class.equals(isDisabled.getReturnType()) && (Boolean)isDisabled.invoke(item);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // assume not disabled
                return false;
            }
        }
    }

    @Override
    public <I extends TopLevelItem> Collection<I> orphanedItems(ComputedFolder<I> owner, Collection<I> orphaned, TaskListener listener) throws IOException, InterruptedException {
        if (abortBuilds) {
            Queue<Run<?, ?>> abortedBuilds = new LinkedList<>();
            for (I item: orphaned) {
                for (Job<?, ?> job: item.getAllJobs()) {
                    for (Run<?, ?> build: job.getBuilds()) {
                        Executor executor = build.getExecutor();
                        if (executor == null) {
                            continue;
                        }
                        executor.interrupt(Result.ABORTED);
                        abortedBuilds.add(build);
                    }
                }
            }
            Run<?, ?> abortedBuild;
            while ((abortedBuild = abortedBuilds.poll()) != null) {
                while (abortedBuild.isLogUpdated()) {
                    Thread.sleep(100L);
                }
            }
        }
        List<I> toRemove = new ArrayList<I>();
        if (pruneDeadBranches) {
            listener.getLogger().printf("Evaluating orphaned items in %s%n", owner.getFullDisplayName());
            List<I> candidates = new ArrayList<I>(orphaned);
            candidates.sort(new Comparator<I>() {
                @Override
                public int compare(I i1, I i2) {
                    boolean disabled1 = disabled(i1);
                    boolean disabled2 = disabled(i2);
                    // prefer the not previously disabled ahead of the previously disabled
                    if (disabled1 ^ disabled2) {
                        return disabled2 ? -1 : +1;
                    }
                    // most recent build first
                    long ms1 = lastBuildTime(i1);
                    long ms2 = lastBuildTime(i2);
                    return Long.compare(ms2, ms1);
                }
            });
            CANDIDATES: for (Iterator<I> iterator = candidates.iterator(); iterator.hasNext();) {
                I item = iterator.next();
                for (Job<?,?> job : item.getAllJobs()) {
                    // Enumerating all builds is inefficient. But we will most likely delete this job anyway,
                    // which will have a cost proportional to the number of builds just to delete those files.
                    for (Run<?,?> build : job.getBuilds()) {
                        if (!abortBuilds && build.isBuilding()) {
                            listener.getLogger().printf("Will not remove %s as %s is still in progress%n", item.getDisplayName(), build.getFullDisplayName());
                            iterator.remove();
                            continue CANDIDATES;
                        }
                        String whyKeepLog = build.getWhyKeepLog();
                        if (whyKeepLog != null) {
                            listener.getLogger().printf("Will not remove %s as %s is marked to not be removed: %s%n", item.getDisplayName(), build.getFullDisplayName(), whyKeepLog);
                            iterator.remove();
                            continue CANDIDATES;
                        }
                    }
                }
            }
            int count = 0;
            if (numToKeep != -1) {
                for (Iterator<I> iterator = candidates.iterator(); iterator.hasNext(); ) {
                    I item = iterator.next();
                    count++;
                    if (count <= numToKeep) {
                        listener.getLogger().printf("Will not remove %s as it is only #%d in the list%n", item.getDisplayName(), count);
                        continue;
                    }
                    listener.getLogger().printf("Will remove %s as it is #%d in the list%n", item.getDisplayName(), count);
                    toRemove.add(item);
                    iterator.remove();
                }
            }
            if (daysToKeep != -1) {
                Calendar cal = new GregorianCalendar();
                cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
                for (I item : candidates) {
                    if (lastBuildTime(item) > cal.getTimeInMillis()) {
                        listener.getLogger().printf("Will not remove %s because it is new%n", item.getDisplayName());
                        continue;
                    }
                    listener.getLogger().printf("Will remove %s as it is too old%n", item.getDisplayName());
                    toRemove.add(item);
                }
            }
            if (daysToKeep == -1 && numToKeep == -1) {
                // special case, we have not said to keep for any count or duration, hence remove them all
                for (I item : candidates) {
                    listener.getLogger().printf("Will remove %s%n", item.getDisplayName());
                    toRemove.add(item);
                }
            }
        }
        return toRemove;
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends OrphanedItemStrategyDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }

}
