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

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.ItemGroup;

/**
 * A health metric for a named child item.
 * 
 * @author strangelookingnerd
 *
 */
public class NamedChildHealthMetric extends FolderHealthMetric {

    private String childName;

    /**
     * Ctor.
     * 
     * @param childName
     *            name of the child
     */
    @DataBoundConstructor
    public NamedChildHealthMetric(String childName) {
        this.childName = childName;
    }

    /**
     * @return the name of the child.
     */
    public String getChildName() {
        return childName;
    }

    @Override
    public Type getType() {
        return Type.IMMEDIATE_ALL_ITEMS;
    }

    @Override
    public Reporter reporter() {
        return new ReporterImpl(childName);
    }

    /**
     * Descriptor Implementation.
     * 
     * @author strangelookingnerd
     *
     */
    @Extension(ordinal = 400)
    public static class DescriptorImpl extends FolderHealthMetricDescriptor {

        private static final String DEFAULT = "";

        @Override
        public String getDisplayName() {
            return Messages.NamedChildHealthMetric_DisplayName();
        }

        @Override
        public FolderHealthMetric createDefault() {
            return new NamedChildHealthMetric(DEFAULT);
        }

        /**
         * Auto-completion for the "child names" field in the configuration.
         * 
         * @param value
         *            the input
         * @param container
         *            the context
         * @return the candidates
         */
        @Restricted(DoNotUse.class)
        public AutoCompletionCandidates doAutoCompleteChildName(@QueryParameter String value,
                @AncestorInPath ItemGroup<Item> container) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (Item item : container.getItems()) {
                if (StringUtils.startsWith(item.getName(), value)) {
                    candidates.add(item.getName());
                }
            }
            return candidates;
        }
    }

    private static class ReporterImpl implements Reporter {
        private HealthReport report = null;
        private String childName;

        /**
         * Ctor.
         * 
         * @param childName
         *            name of the child
         */
        public ReporterImpl(String childName) {
            this.childName = childName;
        }

        @Override
        public void observe(Item item) {
            if (StringUtils.equals(childName, item.getName())) {
                report = getHealthReport(item);
            }
        }

        @Override
        public List<HealthReport> report() {
            return Collections.singletonList(report);
        }
    }
}
