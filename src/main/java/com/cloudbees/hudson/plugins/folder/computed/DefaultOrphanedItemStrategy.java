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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.tasks.LogRotator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
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
     * Stapler's constructor.
     *
     * @param pruneDeadBranches remove dead branches.
     * @param daysToKeepStr     how old a branch must be to remove.
     * @param numToKeepStr      how many branches to keep.
     */
    @DataBoundConstructor
    public DefaultOrphanedItemStrategy(boolean pruneDeadBranches, @CheckForNull String daysToKeepStr,
                                     @CheckForNull String numToKeepStr) {
        this.pruneDeadBranches = pruneDeadBranches;
        // TODO in lieu of DeadBranchCleanupThread, introduce a form warning if daysToKeep < PeriodicFolderTrigger.interval
        this.daysToKeep = pruneDeadBranches ? fromString(daysToKeepStr) : -1;
        this.numToKeep = pruneDeadBranches ? fromString(numToKeepStr) : -1;
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
        for (Job<?,?> j : item.getAllJobs()) {
            Run<?,?> b = j.getLastBuild();
            if (b != null) {
                t = Math.max(t, b.getTimeInMillis());
            }
        }
        return t;
    }

    @Override
    public <I extends TopLevelItem> Collection<I> orphanedItems(ComputedFolder<I> owner, Collection<I> orphaned, TaskListener listener) throws IOException, InterruptedException {
        List<I> toRemove = new ArrayList<I>();
        if (pruneDeadBranches && (numToKeep != -1 || daysToKeep != -1)) {
            listener.getLogger().printf("Evaluating orphaned items in %s%n", owner.getFullDisplayName());
            List<I> candidates = new ArrayList<I>(orphaned);
            Collections.sort(candidates, new Comparator<I>() {
                @Override
                public int compare(I i1, I i2) {
                    // most recent build first
                    long ms1 = lastBuildTime(i1);
                    long ms2 = lastBuildTime(i2);
                    return (ms2 < ms1) ? -1 : ((ms2 == ms1) ? 0 : 1); // TODO Java 7+: Long.compare(ms2, ms1);
                }
            });
            for (Iterator<I> iterator = candidates.iterator(); iterator.hasNext();) {
                I item = iterator.next();
                if (item instanceof Job) {
                    // Enumerating all builds is inefficient. But we will most likely delete this job anyway,
                    // which will have a cost proportional to the number of builds just to delete those files.
                    for (Run<?,?> build : ((Job<?,?>) item).getBuilds()) {
                        if (build.isBuilding()) {
                            listener.getLogger().printf("Will not remove %s as build #%d is still in progress%n", item.getDisplayName(), build.getNumber());
                            iterator.remove();
                        }
                        String whyKeepLog = build.getWhyKeepLog();
                        if (whyKeepLog != null) {
                            listener.getLogger().printf("Will not remove %s as build #%d is marked to not be removed: %s%n", item.getDisplayName(), build.getNumber(), whyKeepLog);
                            iterator.remove();
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
