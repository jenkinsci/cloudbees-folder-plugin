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
import hudson.model.AbstractDescribableImpl;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public abstract class FolderHealthMetric extends AbstractDescribableImpl<FolderHealthMetric> {

    public Type getType() {
        return Type.RECURSIVE_ALL_ITEMS; // TODO should be Type.RECURSIVE_TOP_LEVEL_ITEMS but backwards compatibilty
    }

    public abstract Reporter reporter();

    public static HealthReport getHealthReport(Item item) {
        if (item instanceof Job) {
            return ((Job) item).getBuildHealth();
        }
        if (item instanceof Folder) {
            return ((Folder) item).getBuildHealth();
        }
        try {
            Method getBuildHealth = item.getClass().getMethod("getBuildHealth");
            return  (HealthReport) getBuildHealth.invoke(item);
        } catch (NoSuchMethodException e) {
            // ignore best effort only
        } catch (InvocationTargetException e) {
            // ignore best effort only
        } catch (IllegalAccessException e) {
            // ignore best effort only
        }
        return null;
    }

    public static interface Reporter {
        /**
         * Called during recursive traversal of the tree from the folder on which this metric is specified.
         * May be called on intermediate {@code Folder}s, so implementations should not call {@link #getHealthReport} in this case.
         * @param item a {@link Folder} or any other {@link TopLevelItem}
         */
        void observe(Item item);
        List<HealthReport> report();
    }

    public enum Type {
        IMMEDIATE_TOP_LEVEL_ITEMS(false, true),
        RECURSIVE_TOP_LEVEL_ITEMS(true, true),
        IMMEDIATE_ALL_ITEMS(false, false),
        RECURSIVE_ALL_ITEMS(true, false);

        private final boolean recursive;

        private final boolean topLevelItems;

        Type(boolean recursive, boolean topLevelItems) {
            this.recursive = recursive;
            this.topLevelItems = topLevelItems;
        }

        public boolean isRecursive() {
            return recursive;
        }

        public boolean isTopLevelItems() {
            return topLevelItems;
        }
    }

}
