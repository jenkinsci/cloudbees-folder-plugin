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
import hudson.AbortException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
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

    @Test
    public void abortException() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.setDisplayName("My Folder");
        d.kids.addAll(Arrays.asList("A", "B"));
        d.recompute();
        d.assertItemNames(1, "A", "B");
        d.kids.add("Z");
        d.kids.remove("A");
        d.recompute();
        d.assertItemNames(2, "A", "B");
        FolderComputation<FreeStyleProject> computation = d.getComputation();
        assertEquals(Result.FAILURE, computation.getResult());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        computation.writeWholeLogTo(baos);
        String log = baos.toString();
        assertTrue(log, log.contains("not adding Z"));
        assertFalse(log, log.contains(SampleComputedFolder.class.getName()));
    }

    @Issue("JENKINS-25240")
    @Test
    public void runningBuild() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.kids.addAll(Arrays.asList("A", "B"));
        d.recompute();
        d.assertItemNames(1, "A", "B");
        d.getItem("B").getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        FreeStyleBuild b1 = d.getItem("B").scheduleBuild2(0).waitForStart();
        d.kids.remove("B");
        d.recompute();
        d.assertItemNames(2, "A", "B");
        assertTrue(b1.isBuilding());
        b1.doStop();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b1));
        d.recompute();
        d.assertItemNames(3, "A");
        FreeStyleBuild a1 = d.getItem("A").scheduleBuild2(0).get();
        FreeStyleBuild a2 = d.getItem("A").scheduleBuild2(0).get();
        a1.keepLog(true);
        d.kids.remove("A");
        d.recompute();
        d.assertItemNames(4, "A");
        a1.keepLog(false);
        d.recompute();
        d.assertItemNames(5);
    }

    /** Verify that running branch projects are not deleted even after an organization folder reindex. */
    @Issue("JENKINS-25240")
    @Test
    public void runningBuildMeta() throws Exception {
        SecondOrderComputedFolder org = r.jenkins.createProject(SecondOrderComputedFolder.class, "org");
        org.metakids.add(Arrays.asList("A", "B"));
        org.metakids.add(Arrays.asList("C", "D"));
        org.assertItemNames("A+B", "C+D");
        FreeStyleProject b = r.jenkins.getItemByFullName("org/A+B/B", FreeStyleProject.class);
        b.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        FreeStyleBuild b1 = b.scheduleBuild2(0).waitForStart();
        org.metakids.remove(0);
        org.assertItemNames("A+B", "C+D");
        assertTrue(b1.isBuilding());
        b1.doStop();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b1));
        org.assertItemNames("C+D");
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
                if (kid.equals("Z")) {
                    throw new AbortException("not adding Z");
                }
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class SecondOrderComputedFolder extends ComputedFolder<SampleComputedFolder> {

        List<List<String>> metakids = new ArrayList<List<String>>();

        private SecondOrderComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<SampleComputedFolder> observer, TaskListener listener) throws IOException, InterruptedException {
            for (List<String> kids : metakids) {
                String childName = StringUtils.join(kids, '+');
                listener.getLogger().println("considering " + childName);
                SampleComputedFolder d = observer.shouldUpdate(childName);
                if (d == null) {
                    if (observer.mayCreate(childName)) {
                        listener.getLogger().println("creating a child");
                        d = new SampleComputedFolder(this, childName);
                        d.kids = kids;
                        observer.created(d);
                    } else {
                        listener.getLogger().println("not allowed to create a child");
                    }
                } else {
                    listener.getLogger().println("left existing child");
                }
            }

        }

        void assertItemNames(String... names) throws Exception {
            scheduleBuild2(0).getFuture().get();
            getComputation().writeWholeLogTo(System.out);
            Set<String> actual = new TreeSet<String>();
            for (SampleComputedFolder d : getItems()) {
                d.recompute();
                d.assertItemNames(d.round, d.kids.toArray(new String[0]));
                actual.add(d.getName());
            }
            assertEquals(new TreeSet<String>(Arrays.asList(names)).toString(), actual.toString());
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SecondOrderComputedFolder(parent, name);
            }

        }

    }

}
