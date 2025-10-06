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

package com.cloudbees.hudson.plugins.folder;

import com.cloudbees.hudson.plugins.folder.config.AbstractFolderConfiguration;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import hudson.util.DescribableList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FolderSystemPropertyTest {

    private static final String HEALTH_METRIC_PROPERTY = System.getProperty(AbstractFolderConfiguration.class.getName() + ".ADD_HEALTH_METRICS");

    private JenkinsRule r;

    @BeforeAll
    static void beforeAll() {
        System.setProperty(AbstractFolderConfiguration.class.getName() + ".ADD_HEALTH_METRICS", "true");
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @AfterAll
    static void afterAll() {
        // Put back the previous value before the test was executed
        if (HEALTH_METRIC_PROPERTY != null) {
            System.setProperty(AbstractFolderConfiguration.class.getName() + ".ADD_HEALTH_METRICS", HEALTH_METRIC_PROPERTY);
        } else {
            System.clearProperty(AbstractFolderConfiguration.class.getName() + ".ADD_HEALTH_METRICS");
        }
    }

    @Issue("JENKINS-63836")
    @Test
    void shouldHaveHealthMetricConfiguredGloballyOnSystemProperty() throws Exception {
        assertThat("if used .ADD_HEALTH_METRICS system property, global configuration should have all folder health metrics",
                AbstractFolderConfiguration.get().getHealthMetrics(), hasSize((int) FolderHealthMetricDescriptor.all().stream().filter(d -> d.createDefault() != null).count()));

        Folder folder = r.jenkins.createProject(Folder.class, "myFolder");
        DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor> healthMetrics = folder.getHealthMetrics();
        assertThat("a new created folder should have all the folder health metrics configured globally",
                healthMetrics.toList(), containsInAnyOrder(AbstractFolderConfiguration.get().getHealthMetrics().toArray()));

        AbstractFolderConfiguration.get().setHealthMetrics(null);
        folder = r.jenkins.createProject(Folder.class, "myFolder2");
        healthMetrics = folder.getHealthMetrics();
        assertThat("a new created folder should have all the folder health metrics configured globally",
                healthMetrics, iterableWithSize(0));
    }
}
