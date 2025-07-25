/*
 * The MIT License
 *
 * Copyright 2020 CloudBees.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.hudson.plugins.folder.Folder;

import hudson.model.HealthReport;

public class NamedChildHealthMetricTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void childExists() throws Exception {
        Folder folder = j.jenkins.createProject(Folder.class, "myFolder");
        folder.createProject(Folder.class, "mySubFolder");
        folder.getHealthMetrics().add(new NamedChildHealthMetric("mySubFolder"));

        List<HealthReport> reports = folder.getBuildHealthReports();
        assertThat("report should be available for existing child", reports, hasSize(1));
    }

    @Test
    public void childDoesNotExist() throws Exception {
        Folder folder = j.jenkins.createProject(Folder.class, "myFolder");
        folder.createProject(Folder.class, "mySubFolder");
        folder.getHealthMetrics().add(new NamedChildHealthMetric("doesnotexist"));

        List<HealthReport> reports = folder.getBuildHealthReports();
        assertThat("report should not contain report for non-existent child", reports, hasSize(0));
    }

    @Test
    public void nestedChild() throws Exception {
        Folder folder = j.jenkins.createProject(Folder.class, "myFolder");
        Folder subFolder = folder.createProject(Folder.class, "mySubFolder");
        subFolder.createProject(Folder.class, "nestedFolder");
        folder.getHealthMetrics().add(new NamedChildHealthMetric("mySubFolder/nestedFolder"));

        List<HealthReport> reports = folder.getBuildHealthReports();
        assertThat("report should not contain report for nested child", reports, hasSize(0));
    }

}
