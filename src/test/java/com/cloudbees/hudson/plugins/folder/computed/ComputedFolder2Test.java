/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class ComputedFolder2Test {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue("JENKINS-42593")
    @Test
    public void eventAfterRestart() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                EventableFolder d = rr.j.jenkins.createProject(EventableFolder.class, "d");
                d.add("one");
                String log = ComputedFolderTest.doRecompute(d, Result.SUCCESS);
                assertThat(log, d.getItems(), hasSize(equalTo(1)));
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                EventableFolder d = rr.j.jenkins.getItemByFullName("d", EventableFolder.class);
                assertNotNull(d);
                assertThat(d.getItems(), hasSize(equalTo(1)));
                d.add("two");
                String log = ComputedFolderTest.doRecompute(d, Result.SUCCESS);
                assertThat(log, d.getItems(), hasSize(equalTo(1)));
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final class EventableFolder extends ComputedFolder<FreeStyleProject> {

        private final List<String> kids = new ArrayList<>();

        public EventableFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener) throws IOException, InterruptedException {
            for (String kid : kids) {
                FreeStyleProject p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("loading " + kid);
                            p = new FreeStyleProject(this, kid);
                            observer.created(p);
                        }
                    }
                } finally {
                    observer.completed(kid);
                }
            }
        }

        public void add(String kid) throws IOException, InterruptedException {
            try (StreamTaskListener listener = getComputation().createEventsListener();
                    ChildObserver<FreeStyleProject> observer = openEventsChildObserver()) {
                FreeStyleProject p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("adding " + kid);
                            kids.add(kid);
                            p = new FreeStyleProject(this, kid);
                            observer.created(p);
                        }
                    }
                } finally {
                    observer.completed(kid);
                }
            }
        }

        @TestExtension("eventAfterRestart")
        public static final class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new EventableFolder(parent, name);
            }

        }

    }

}
