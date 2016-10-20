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

package com.cloudbees.hudson.plugins.folder.views;

import hudson.Extension;
import hudson.views.JobColumn;
import hudson.views.ListViewColumnDescriptor;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.NonLocalizable;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link JobColumn} with different caption
 *
 * @author Kohsuke Kawaguchi
 */
public class CustomNameJobColumn extends JobColumn {
    /**
     * Resource bundle name.
     */
    private final String bundle;
    private final String key;

    private transient Localizable loc;

    @DataBoundConstructor
    public CustomNameJobColumn(String bundle, String key) {
        this.bundle = bundle;
        this.key = key;
        readResolve();
    }

    public CustomNameJobColumn(Class bundle, Localizable loc) {
        this.bundle = bundle.getName();
        this.key = loc.getKey();
        this.loc = loc;
    }

    private Object readResolve() {
        try {
            loc = new Localizable(
                ResourceBundleHolder.get(Jenkins.getActiveInstance().pluginManager.uberClassLoader.loadClass(bundle)),
                key);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "No such bundle: "+bundle);
            loc = new NonLocalizable(bundle+':'+key);
        }
        return this;
    }


    public String getBundle() {
        return bundle;
    }

    public String getKey() {
        return key;
    }

    public String getMessage() {
        return loc.toString();
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.CustomNameJobColumn_DisplayName();
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CustomNameJobColumn.class.getName());
}
