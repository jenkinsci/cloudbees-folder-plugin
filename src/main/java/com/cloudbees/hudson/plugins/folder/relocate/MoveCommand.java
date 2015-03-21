/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees., Steven Christou
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
package com.cloudbees.hudson.plugins.folder.relocate;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

/**
 * Add a cli operation to move a project from one folder to another.
 *
 * @author Steven Christou
 */
@Extension
public class MoveCommand extends CLICommand {
    @Argument(metaVar = "SRC", usage = "Name of the job to move.", required = true)
    public TopLevelItem src;

    @Argument(metaVar = "DST", usage = "Name of the new job to be created.", index = 1, required = true)
    public String dst;

    @Override
    public String getShortDescription() {
        return "Moves a job.";
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins.getItemByFullName(dst) != null) {
            stderr.println("Job " + dst + " already exists.");
            return -1;
        }

        jenkins.copy(src, dst).save();
        jenkins.remove(src);
        return 0;
    }
}
