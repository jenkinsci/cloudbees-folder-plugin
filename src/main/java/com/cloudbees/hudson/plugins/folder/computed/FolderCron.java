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

import hudson.Extension;
import hudson.model.Item;
import hudson.model.PeriodicWork;
import hudson.scheduler.CronTabList;
import hudson.triggers.Trigger;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link hudson.triggers.Trigger.Cron} analogue for {@link ComputedFolder}.
 * TODO should introduce a core API defining an {@link Item} with a {@code public Map<TriggerDescriptor,Trigger<?>> getTriggers()} interface
 * (retrofit {@link jenkins.model.ParameterizedJobMixIn.ParameterizedJob} and {@link ComputedFolder} to extend it, and use it from {@link hudson.triggers.Trigger.Cron}).
 */
@SuppressWarnings("unused") // instantiated by Jenkins
@Restricted(NoExternalUse.class)
@Extension
public class FolderCron extends PeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(FolderCron.class.getName());

    /**
     * A calendar to use.
     */
    private final Calendar cal = new GregorianCalendar();

    /**
     * The hack field we want to access.
     */
    private final Field tabsField;

    /**
     * Constructor.
     *
     * @throws NoSuchFieldException
     */
    // instantiated by Jenkins
    @SuppressWarnings("unused")
    public FolderCron() throws NoSuchFieldException {
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        tabsField = Trigger.class.getDeclaredField("tabs");
        tabsField.setAccessible(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRecurrencePeriod() {
        return MIN;
    }

    @Override
    public long getInitialDelay() {
        return MIN - (Calendar.getInstance().get(Calendar.SECOND) * 1000);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doRun() {
        while (new Date().getTime() >= cal.getTimeInMillis()) {
            LOGGER.log(Level.FINE, "cron checking {0}", cal.getTime());
            try {
                checkTriggers(cal);
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Cron thread throw an exception", e);
                // bug in the code. Don't let the thread die.
                e.printStackTrace();
            }
            cal.add(Calendar.MINUTE, 1);
        }
    }

    /**
     * Checks the triggers.
     */
    public void checkTriggers(final Calendar cal) {
        Jenkins inst = Jenkins.getInstance();
        if (inst == null) {
            return;
        }
        for (ComputedFolder<?> p : inst.getAllItems(ComputedFolder.class)) {
            for (Trigger<?> t : p.getTriggers().values()) {
                LOGGER.log(Level.FINE, "cron checking {0}", p.getName());
                CronTabList tabs;
                try {
                    tabs = (CronTabList) this.tabsField.get(t);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (tabs.check(cal)) {
                    LOGGER.log(Level.CONFIG, "cron triggered {0}", p.getName());
                    try {
                        t.run();
                    } catch (Throwable e) {
                        // t.run() is a plugin, and some of them throw RuntimeException and other things.
                        // don't let that cancel the polling activity. report and move on.
                        LOGGER.log(Level.WARNING, t.getClass().getName() + ".run() failed for " + p.getName(), e);
                    }
                }
            }
        }
    }

}
