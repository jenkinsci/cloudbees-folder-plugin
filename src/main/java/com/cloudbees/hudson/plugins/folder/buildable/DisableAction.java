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

package com.cloudbees.hudson.plugins.folder.buildable;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.Messages;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.util.HttpResponses;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

public class DisableAction implements Action {
    private final Folder item;

    public DisableAction(Folder folder) {
        this.item = folder;
    }

    public String getIconFileName() {
        if (!item.hasPermission(Job.CONFIGURE) || item.isDisabled()) {
            return null;
        }
        return "disabled.png";
    }

    public String getDisplayName() {
        return Messages.DisableAction_displayName();
    }

    public String getUrlName() {
        return "disable";
    }

    public Folder getItem() {
        return item;
    }

    public Collection<? extends Job> getJobs() {
        return new Ordering<Job>() {
            @Override
            public int compare(Job job, Job t1) {
                if (job.getParent().equals(t1.getParent())) {
                    return job.getName().compareTo(t1.getName());
                }
                return job.getParent().getFullDisplayName().compareTo(t1.getParent().getFullDisplayName());
            }
        }.immutableSortedCopy(getPossibleJobs());
    }

    private Collection<? extends Job> getPossibleJobs() {
        return Collections2.filter(item.getAllJobs(), new Predicate<Job>() {
            public boolean apply(Job job) {
                return (job.isBuildable() && job.hasPermission(Job.CONFIGURE));
            }
        });
    }

    /**
     * Disable jobs contained in this folder.
     */
    @RequirePOST
    public HttpResponse doDisable(StaplerRequest req) throws IOException {
        LOGGER.finest("Trying to disable all jobs in " + item.getName());
        Collection<Job> missed = Lists.newArrayList();
        for (Job job : getPossibleJobs()) {
            try {
                ((AbstractProject) job).disable();
                LOGGER.finest("Set " + job.getName() + " as not buildable");
                missed.add(job);
            } catch (ClassCastException e) {
                LOGGER.info("Cannot disable " + job.getName());
                LOGGER.info(e.getMessage());
            }
        }
        if (!missed.isEmpty()) {
            return HttpResponses.forwardToView(this, "error").with("missed", missed);
        }
        return HttpResponses.redirectViaContextPath(item.getParent().getUrl() + item.getShortUrl());
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<Folder> {
        @Override
        public Class<Folder> type() {
            return Folder.class;
        }

        @Override
        public Collection<? extends Action> createFor(Folder target) {
            return Collections.singleton(new DisableAction(target));
        }
    }

    private static Logger LOGGER = Logger.getLogger(DisableAction.class.getName());
}
