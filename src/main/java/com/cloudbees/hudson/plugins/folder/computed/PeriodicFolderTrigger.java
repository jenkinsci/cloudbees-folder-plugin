/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Items;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Trigger} for {@link ComputedFolder}s that will only trigger a recomputation if none has been
 * triggered within a specified timeframe.
 *
 * @author Stephen Connolly
 */
@SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE") // https://github.com/spotbugs/spotbugs/issues/1539
public class PeriodicFolderTrigger extends Trigger<ComputedFolder<?>> {

    private static final Logger LOGGER = Logger.getLogger(PeriodicFolderTrigger.class.getName());

    /**
     * The interval between successive indexings.
     */
    private final long interval;

    /**
     * Constructor.
     *
     * @param interval the interval.
     */
    @DataBoundConstructor
    public PeriodicFolderTrigger(String interval) {
        super(toCrontab(interval));
        long intervalMillis = toIntervalMillis(interval);
        this.interval = intervalMillis;
    }

    /**
     * Turns an interval into a suitable crontab.
     *
     * @param interval the interval.
     * @return the crontab.
     */
    private static String toCrontab(String interval) {
        long millis = toIntervalMillis(interval);
        // we want to ensure the crontab wakes us excessively
        if (millis <= TimeUnit.MINUTES.toMillis(5)) {
            return "* * * * *"; // 0-5min: check every minute
        }
        if (millis <= TimeUnit.MINUTES.toMillis(30)) {
            return "H/5 * * * *"; // 11-30min: check every 5 minutes
        }
        if (millis <= TimeUnit.HOURS.toMillis(1)) {
            return "H/15 * * * *"; // 30-60min: check every 15 minutes
        }
        if (millis <= TimeUnit.HOURS.toMillis(8)) {
            return "H/30 * * * *"; // 61min-8hr: check every 30 minutes
        }
        if (millis <= TimeUnit.DAYS.toMillis(1)) {
            return "H H/4 * * *"; // 8hr-24h: check every 4 hours
        }
        if (millis <= TimeUnit.DAYS.toMillis(2)) {
            return "H H/12 * * *"; // 24h-2d: check every 12 hours
        }
        return "H H * * *"; // check once per day
    }

    /**
     * Turns an interval into milliseconds./
     *
     * @param interval the interval.
     * @return the milliseconds.
     */
    private static long toIntervalMillis(String interval) {
        TimeUnit units = TimeUnit.MINUTES;
        interval = interval.toLowerCase();
        if (interval.endsWith("h")) {
            units = TimeUnit.HOURS;
            interval = StringUtils.removeEnd(interval, "h");
        }
        if (interval.endsWith("m")) {
            interval = StringUtils.removeEnd(interval, "m");
        } else if (interval.endsWith("d")) {
            units = TimeUnit.DAYS;
            interval = StringUtils.removeEnd(interval, "d");
        } else if (interval.endsWith("ms")) {
            units = TimeUnit.SECONDS;
            interval = StringUtils.removeEnd(interval, "ms");
        } else if (interval.endsWith("s")) {
            units = TimeUnit.SECONDS;
            interval = StringUtils.removeEnd(interval, "s");
        }
        long value = 0;
        try {
            value = Long.parseLong(interval);
        } catch (NumberFormatException e) {
            value = 1;
        }
        return Math.min(TimeUnit.DAYS.toMillis(30),
                Math.max(TimeUnit.MINUTES.toMillis(1), units.toMillis(value)));
    }

    /**
     * Returns the number of milliseconds between indexing.
     *
     * @return the number of milliseconds between indexing.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public long getIntervalMillis() {
        return interval;
    }

    /**
     * Returns the interval between indexing.
     *
     * @return the interval between indexing.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public String getInterval() {
        if (interval < TimeUnit.SECONDS.toMillis(1)) {
            return Long.toString(interval) + "ms";
        }
        if (interval < TimeUnit.MINUTES.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toSeconds(interval)) + "s";
        }
        if (interval < TimeUnit.HOURS.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toMinutes(interval)) + "m";
        }
        if (interval < TimeUnit.DAYS.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toHours(interval)) + "h";
        }
        return Long.toString(TimeUnit.MILLISECONDS.toDays(interval)) + "d";
    }

    /**
     * {@inheritDoc}
     */
    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "NP_NULL_ON_SOME_PATH"}, justification = "ComputedFolder.computation is set in init but that is overridable so let us just make sure; Trigger.job once set is never cleared")
    @Override
    public void run() {
        if (job == null) {
            return;
        }
        FolderComputation<?> computation = job.getComputation();
        long last = computation != null ? computation.getTimestamp().getTimeInMillis() : 0;
        if (last == 0) {
            LOGGER.fine(() -> job + " has not yet been computed");
        } else {
            // we will be run approximately every interval/2
            // we want to trigger such that the average time between triggers is `interval`
            // if we have the time since last trigger as almost `interval` then it will be 1.5*interval
            // so we trigger slightly early
            // Also take into account that we are delaying computation by 5s
            // and it may take ~3s for computation to go an executor and timestamp to be recorded,
            // so in the case of a 1m trigger we need some grace period or it will actually only be run every 2m typically.
            long almostInterval = interval - interval / 20 - TimeUnit.SECONDS.toMillis(15);
            long remaining = last + almostInterval - System.currentTimeMillis();
            if (remaining > 0) {
                LOGGER.fine(() -> job + " was last computed at " + new Date(last) + " which is within the adjusted interval of " + Util.getTimeSpanString(almostInterval) + " by " + Util.getTimeSpanString(remaining));
                return;
            }
            LOGGER.fine(() -> job + " was last computed at " + new Date(last) + " which exceeds the adjusted interval of " + Util.getTimeSpanString(almostInterval) + " by " + Util.getTimeSpanString(-remaining));
        }
        if (job.scheduleBuild(5, new TimerTrigger.TimerTriggerCause())) {
            LOGGER.fine(() -> "triggering " + job + " in 5s");
        } else {
            LOGGER.log(Level.WARNING, "Queue refused to schedule {0}", job);
        }
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends TriggerDescriptor {
        /**
         * {@inheritDoc}
         */
        public boolean isApplicable(Item item) {
            return item instanceof ComputedFolder;
        }

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return Messages.PeriodicFolderTrigger_DisplayName();
        }

        /**
         * Fills the interval list box.
         *
         * @return the interval list box.
         */
        @SuppressWarnings("unused") // used by Jelly
        public ListBoxModel doFillIntervalItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("1 minute", "1m");
            model.add("2 minutes", "2m");
            model.add("3 minutes", "3m");
            model.add("5 minutes", "5m");
            model.add("10 minutes", "10m");
            model.add("15 minutes", "15m");
            model.add("20 minutes", "20m");
            model.add("25 minutes", "25m");
            model.add("30 minutes", "30m");
            model.add("1 hour", "1h");
            model.add("2 hours", "2h");
            model.add("4 hours", "4h");
            model.add("8 hours", "8h");
            model.add("12 hours", "12h");
            model.add("1 day", "1d");
            model.add("2 days", "2d");
            model.add("1 week", "7d");
            model.add("2 weeks", "14d");
            model.add("4 weeks", "28d");
            return model;
        }

        static {
            Items.XSTREAM2.addCompatibilityAlias("jenkins.branch.IndexAtLeastTrigger", PeriodicFolderTrigger.class);
        }

    }


}
