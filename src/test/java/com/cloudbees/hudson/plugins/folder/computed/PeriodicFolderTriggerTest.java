/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.util.ListBoxModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.Issue;

public class PeriodicFolderTriggerTest {

    @Test
    public void getIntervalMillis() throws Exception {
        for (int i = 1; i < 60; i++) {
            assertEquals(
                "correctly converts " + i + "m to milliseconds",
                TimeUnit.MINUTES.toMillis(i),
                new PeriodicFolderTrigger(i + "m").getIntervalMillis()
            );
        }
        
        for (int i = 1; i < 24; i++) {
            assertEquals(
                "correctly converts " + i + "h to milliseconds",
                TimeUnit.HOURS.toMillis(i),
                new PeriodicFolderTrigger(i + "h").getIntervalMillis()
            );
        }

        for (int i = 1; i < 31; i++) {
            assertEquals(
                "correctly converts " + i + "d to milliseconds",
                TimeUnit.DAYS.toMillis(i),
                new PeriodicFolderTrigger(i + "d").getIntervalMillis()
            );
        }
    }

    @Issue("JENKINS-33006")
    @Test
    public void interval() throws Exception {
        for (ListBoxModel.Option option : new PeriodicFolderTrigger.DescriptorImpl().doFillIntervalItems()) {
            assertEquals("correctly round-trip " + option.name, option.value, new PeriodicFolderTrigger(option.value).getInterval());
        }
    }

    @Test
    public void toCrontab() throws Exception {
        // Note, Jenkins replaces the "H" with the current job's hash (a fixed value).
        // This allows cron jobs to be more evenly distributed, thus spreading out the load. 
        Map<String, String> cronSpecs = new HashMap<>();
        cronSpecs.put("1m", "* * * * *");
        cronSpecs.put("2m", "H/2 * * * *");
        cronSpecs.put("5m", "H/5 * * * *");
        cronSpecs.put("10m", "H/10 * * * *");
        cronSpecs.put("15m", "H/15 * * * *");
        cronSpecs.put("20m", "H/20 * * * *");
        cronSpecs.put("25m", "H/25 * * * *");
        cronSpecs.put("30m", "H/30 * * * *");
        cronSpecs.put("1h", "H * * * *");
        cronSpecs.put("2h", "H H/2 * * *");
        cronSpecs.put("4h", "H H/4 * * *");
        cronSpecs.put("8h", "H H/8 * * *");
        cronSpecs.put("1d", "H H * * *");
        cronSpecs.put("2d", "H H H/2 * *");
        cronSpecs.put("7d", "H H H/7 * *");
        cronSpecs.put("14d", "H H H/14 * *");
        cronSpecs.put("28d", "H H H/28 * *");

        for (Entry<String, String> cronSpec : cronSpecs.entrySet()) {
            assertEquals(
                "correctly converts " + cronSpec.getKey() + " to cron spec",
                cronSpec.getValue(),
                new PeriodicFolderTrigger(cronSpec.getKey()).getSpec()
            );
        }
    }

}
