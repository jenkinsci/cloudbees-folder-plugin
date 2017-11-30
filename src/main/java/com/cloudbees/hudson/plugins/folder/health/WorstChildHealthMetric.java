/*
 * The MIT License
 *
 * Copyright 2013 CloudBees.
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

package com.cloudbees.hudson.plugins.folder.health;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Extension;
import hudson.model.HealthReport;
import hudson.model.Item;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

public class WorstChildHealthMetric extends FolderHealthMetric {

    private boolean nonRecursive;

    @Deprecated
    public WorstChildHealthMetric() {
        nonRecursive = false;
    }

    @DataBoundConstructor
    public WorstChildHealthMetric(boolean recursive) {
        nonRecursive = !recursive;
    }

    public boolean isRecursive() {
        return !nonRecursive;
    }

    @Override
    public Type getType() {
        return nonRecursive ? Type.IMMEDIATE_TOP_LEVEL_ITEMS : Type.RECURSIVE_TOP_LEVEL_ITEMS;
    }

    @Override
    public Reporter reporter() {
        return new ReporterImpl();
    }

    @Extension(ordinal=400)
    public static class DescriptorImpl extends FolderHealthMetricDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.WorstChildHealthMetric_DisplayName();
        }

        @Override public FolderHealthMetric createDefault() {
            return new WorstChildHealthMetric(true);
        }

    }

    private static class ReporterImpl implements Reporter {
        HealthReport worst = null;

        public void observe(Item item) {
            if (item instanceof Folder) {
                // only interested in non-folders in order to prevent double counting
                return;
            }
            HealthReport report = getHealthReport(item);
            if (report != null && (worst == null || report.getScore() < worst.getScore())) {
                worst = new HealthReport(report.getScore(), report.getIconUrl(),
                        Messages._Folder_HealthWrap(item.getFullDisplayName(),
                                report.getLocalizableDescription()));
            }
        }

        public List<HealthReport> report() {
            return worst != null && worst.getScore() < 100
                    ? Collections.singletonList(worst)
                    : Collections.<HealthReport>emptyList();
        }
    }
}
