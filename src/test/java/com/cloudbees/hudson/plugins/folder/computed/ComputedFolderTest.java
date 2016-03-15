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

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class ComputedFolderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-32179")
    @Test
    public void duplicateEntries() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.recompute();
        d.assertItemNames(1);
        d.kids.addAll(Arrays.asList("A", "B", "A", "C"));
        d.recompute();
        d.assertItemNames(2, "A", "B", "C");
        assertEquals("[A, B, C]", d.created.toString());
        d.recompute();
        d.assertItemNames(3, "A", "B", "C");
        assertEquals("[A, B, C]", d.created.toString());
        d.kids.remove("B");
        d.recompute();
        d.assertItemNames(4, "A", "C");
        assertEquals("[A, B, C]", d.created.toString());
        assertEquals("[B]", d.deleted.toString());
        d.kids.addAll(Arrays.asList("D", "B"));
        d.recompute();
        d.assertItemNames(5, "A", "B", "C", "D");
        assertEquals("[A, B, C, D, B]", d.created.toString());
        assertEquals("[B]", d.deleted.toString());
        Map<String,String> descriptions = new TreeMap<String,String>();
        for (FreeStyleProject p : d.getItems()) {
            descriptions.put(p.getName(), p.getDescription());
        }
        assertEquals("{A=updated in round #5, B=created in round #5, C=updated in round #5, D=created in round #5}", descriptions.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class SampleComputedFolder extends ComputedFolder<FreeStyleProject> {

        List<String> kids = new ArrayList<String>();
        int round;
        List<String> created = new ArrayList<String>();
        List<String> deleted = new ArrayList<String>();

        private SampleComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener) throws IOException, InterruptedException {
            round++;
            listener.getLogger().println("=== Round #" + round + " ===");
            for (String kid : kids) {
                listener.getLogger().println("considering " + kid);
                FreeStyleProject p = observer.shouldUpdate(kid);
                if (p == null) {
                    if (observer.mayCreate(kid)) {
                        listener.getLogger().println("creating a child");
                        p = new FreeStyleProject(this, kid);
                        p.setDescription("created in round #" + round);
                        observer.created(p);
                        created.add(kid);
                    } else {
                        listener.getLogger().println("not allowed to create a child");
                    }
                } else {
                    listener.getLogger().println("updated existing child with description " + p.getDescription());
                    p.setDescription("updated in round #" + round);
                }
            }

        }

        @Override
        protected Collection<FreeStyleProject> orphanedItems(Collection<FreeStyleProject> orphaned, TaskListener listener) throws IOException, InterruptedException {
            Collection<FreeStyleProject> deleting = super.orphanedItems(orphaned, listener);
            for (FreeStyleProject p : deleting) {
                String kid = p.getName();
                listener.getLogger().println("deleting " + kid + " in round #" + round);
                deleted.add(kid);
            }
            return deleting;
        }

        void recompute() throws Exception {
            scheduleBuild2(0).getFuture().get();
            getComputation().writeWholeLogTo(System.out);
        }

        void assertItemNames(int round, String... names) {
            assertEquals(round, this.round);
            Set<String> actual = new TreeSet<String>();
            for (FreeStyleProject p : getItems()) {
                actual.add(p.getName());
            }
            assertEquals(new TreeSet<String>(Arrays.asList(names)).toString(), actual.toString());
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SampleComputedFolder(parent, name);
            }

        }

    }

}
