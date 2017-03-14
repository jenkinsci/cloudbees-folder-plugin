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
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Functions;
import hudson.model.AllView;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.ListView;
import hudson.model.MyView;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.queue.QueueTaskFuture;
import hudson.triggers.TimerTrigger;
import hudson.util.StreamTaskListener;
import hudson.triggers.Trigger;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import javax.servlet.ServletException;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ComputedFolderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-32179")
    @Test
    public void duplicateEntries() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.recompute(Result.SUCCESS);
        d.assertItemNames(1);
        d.kids.addAll(Arrays.asList("A", "B", "A", "C"));
        d.recompute(Result.SUCCESS);
        d.assertItemNames(2, "A", "B", "C");
        assertEquals("[A, B, C]", d.created.toString());

        // ComputedFolder page opens correctly
        try {
            r.createWebClient().getPage(d);
        } catch (Exception ex) {
            Assert.fail("ComputedFolder<FreeStyleProject> cannot be opened: " + ex.getMessage());
        }

        d.recompute(Result.SUCCESS);
        d.assertItemNames(3, "A", "B", "C");
        assertEquals("[A, B, C]", d.created.toString());
        d.kids.remove("B");
        d.recompute(Result.SUCCESS);
        d.assertItemNames(4, "A", "C");
        assertEquals("[A, B, C]", d.created.toString());
        assertEquals("[B]", d.deleted.toString());
        d.kids.addAll(Arrays.asList("D", "B"));
        d.recompute(Result.SUCCESS);
        d.assertItemNames(5, "A", "B", "C", "D");
        assertEquals("[A, B, C, D, B]", d.created.toString());
        assertEquals("[B]", d.deleted.toString());
        Map<String,String> descriptions = new TreeMap<String,String>();
        for (FreeStyleProject p : d.getItems()) {
            descriptions.put(p.getName(), p.getDescription());
        }
        assertEquals("{A=updated in round #5, B=created in round #5, C=updated in round #5, D=created in round #5}", descriptions.toString());
    }

    @Issue("JENKINS-42680")
    @Test
    public void foldersAsChildren() throws Exception {
        final SampleComputedFolderWithFoldersAsChildren d = r.jenkins.createProject(SampleComputedFolderWithFoldersAsChildren.class, "d");
        d.recompute(Result.SUCCESS);
        d.kids.addAll(Arrays.asList("A"));
        d.recompute(Result.SUCCESS);

        // Folder page opens correctly
        try {
            Folder a = d.getItems().iterator().next();
            r.createWebClient().getPage(a);
        } catch (Exception ex) {
            Assert.fail("Folder inside ComputedFolder cannot be opened: " + ex.getMessage());
        }

        // ComputerFolder page does no open
        try {
            r.createWebClient().getPage(d);
        } catch (Exception ex) {
            Assert.fail("ComputedFolder<Folder> cannot be opened: " + ex.getMessage());
        }
    }

    @Test
    public void abortException() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.setDisplayName("My Folder");
        d.kids.addAll(Arrays.asList("A", "B"));
        d.recompute(Result.SUCCESS);
        d.assertItemNames(1, "A", "B");
        d.kids.add("Z");
        d.kids.remove("A");
        // Despite its name, AbortException is intended to be FAILURE, not ABORTED which would be InterruptedException.
        String log = d.recompute(Result.FAILURE);
        d.assertItemNames(2, "A", "B");
        assertTrue(log, log.contains("not adding Z"));
        assertFalse(log, log.contains(SampleComputedFolder.class.getName()));
    }

    @Issue("JENKINS-25240")
    @Test
    public void runningBuild() throws Exception {
        SampleComputedFolder d = r.jenkins.createProject(SampleComputedFolder.class, "d");
        d.kids.addAll(Arrays.asList("A", "B"));
        d.recompute(Result.SUCCESS);
        d.assertItemNames(1, "A", "B");
        d.getItem("B").getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        d.getItem("B").setConcurrentBuild(true);
        FreeStyleBuild b1 = d.getItem("B").scheduleBuild2(0).waitForStart();
        FreeStyleBuild b2 = d.getItem("B").scheduleBuild2(0).waitForStart();
        d.kids.remove("B");
        d.recompute(Result.SUCCESS);
        d.assertItemNames(2, "A", "B");
        for (FreeStyleBuild b : new FreeStyleBuild[] {b1, b2}) {
            assertTrue(b.isBuilding());
            b.doStop();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        }
        d.recompute(Result.SUCCESS);
        d.assertItemNames(3, "A");
        FreeStyleBuild a1 = d.getItem("A").scheduleBuild2(0).get();
        FreeStyleBuild a2 = d.getItem("A").scheduleBuild2(0).get();
        a1.keepLog(true);
        d.kids.remove("A");
        d.recompute(Result.SUCCESS);
        d.assertItemNames(4, "A");
        a1.keepLog(false);
        d.recompute(Result.SUCCESS);
        d.assertItemNames(5);
    }

    @Test
    public void notAddChildren() throws Exception {
        JenkinsRule.WebClient client = r.createWebClient();
        SampleComputedFolder s = r.jenkins.createProject(SampleComputedFolder.class, "s");

        assertEquals(client.getPage(s).getByXPath("//a[contains(text(), \"New Item\")]").size(), 0);

        s.kids.add("A");
        s.recompute(Result.SUCCESS);

        assertEquals(client.getPage(s).getByXPath("//a[contains(text(), \"New Item\")]").size(), 0);
    }

    @Test
    public void runByTrigger() throws Exception {
        SampleComputedFolder s = r.jenkins.createProject(SampleComputedFolder.class, "s");
        s.assertItemNames(0);

        PeriodicFolderTrigger t = new PeriodicFolderTrigger("1m");
        s.addTrigger(t);
        t.run();

        r.waitUntilNoActivity();

        s.assertItemNames(1);
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

    @Test
    public void viewHolderRestrictions() throws Exception {
        LockedDownSampleComputedFolder org = r.jenkins.createProject(LockedDownSampleComputedFolder.class, "org");
        // initial setup is correct
        assertThat(org.getViews().size(), is(2));
        assertThat(org.getPrimaryView().getViewName(), is("Empty"));
        assertThat(org.getView("All"), notNullValue());

        // permissions are as expected
        assertThat(org.getFolderViews().isPrimaryModifiable(), is(false));
        assertThat(org.getFolderViews().isViewsModifiable(), is(false));
        assertThat(org.getFolderViews().isTabBarModifiable(), is(false));

        // trying to set the primary view is a no-op
        org.setPrimaryView(org.getView("All"));
        assertThat(org.getPrimaryView().getViewName(), is("Empty"));

        // adding a view is a no-op
        org.addView(new MyView("mine", org));
        assertThat(org.getViews().size(), is(2));

        // primary not modifiable, no ability to configure primary view
        JenkinsRule.WebClient client = r.createWebClient();
        HtmlPage configure = client.getPage(org, "configure");
        assertThat(configure.getElementsByName("primaryView"), is(Collections.<DomElement>emptyList()));

        SampleComputedFolder org2 = r.jenkins.createProject(SampleComputedFolder.class, "org2");

        // only one view, no ability to configure primary
        configure = client.getPage(org, "configure");
        assertThat(configure.getElementsByName("primaryView"), is(Collections.<DomElement>emptyList()));

        // more than one view and primary is modifiable, so we can configure it
        org2.addView(new MyView("mine", org2));
        configure = client.getPage(org2, "configure");
        assertThat(configure.getElementsByName("primaryView"), not(is(Collections.<DomElement>emptyList())));
    }

    @Test
    public void recomputationSuppression() throws Exception {
        final VariableRecomputationComputedFolder org = r.jenkins.createProject(VariableRecomputationComputedFolder.class, "org");

        // no recalculateAfterSubmitted calls means we recalculate
        r.waitUntilNoActivity();
        int round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round + 1));

        // recalculateAfterSubmitted(true) calls means we recalculate
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(true);
            }
        };

        round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round + 1));

        // recalculateAfterSubmitted(false) calls means we suppress
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(false);
            }
        };

        round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round));

    }

    @Test
    public void recomputationSuppressionMulti() throws Exception {
        final VariableRecomputationComputedFolder org =
                r.jenkins.createProject(VariableRecomputationComputedFolder.class, "org");

        // at least one recalculateAfterSubmitted(true) calls means we recalculate
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(true);
                org.recalculateAfterSubmitted(true);
            }
        };

        int round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round + 1));

        // at least one recalculateAfterSubmitted(true) calls means we recalculate
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(true);
                org.recalculateAfterSubmitted(false);
            }
        };

        round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round + 1));

        // at least one recalculateAfterSubmitted(true) calls means we recalculate
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(false);
                org.recalculateAfterSubmitted(true);
            }
        };

        round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round + 1));

        // all recalculateAfterSubmitted(false) calls means we suppress
        org.submit = new Runnable() {
            @Override
            public void run() {
                org.recalculateAfterSubmitted(false);
                org.recalculateAfterSubmitted(false);
            }
        };

        round = org.round;
        r.configRoundtrip(org);
        r.waitUntilNoActivity();
        assertThat(org.round, is(round));
    }

    @Test
    public void triggersRoundtrip() throws Exception {
        SampleComputedFolder s = r.jenkins.createProject(SampleComputedFolder.class, "s");
        s.addTrigger(new PeriodicFolderTrigger("30m"));
        SampleComputedFolder s2 = r.configRoundtrip(s);
        Trigger<?> trigger = s2.getTriggers().get(r.jenkins.getDescriptorByType(PeriodicFolderTrigger.DescriptorImpl.class));
        assertThat(trigger, notNullValue());
        assertThat(trigger, instanceOf(PeriodicFolderTrigger.class));
        assertThat(((PeriodicFolderTrigger)trigger).getInterval(), is("30m"));

    }

    @Test
    @Issue("JENKINS-42511")
    public void concurrentEvents() throws Exception {
        CoordinatedComputedFolder d = r.jenkins.createProject(CoordinatedComputedFolder.class, "d");
        d.kids.addAll(Arrays.asList("A", "B"));
        QueueTaskFuture<Queue.Executable> future = d.scheduleBuild2(0).getFuture();
        d.onKid("B");
        future.get();
        waitUntilNoActivityIgnoringThreadDeathUpTo(10000);
        List<Throwable> deaths = new ArrayList<Throwable>();
        for (Computer comp : r.jenkins.getComputers()) {
            for (Executor e : comp.getExecutors()) {
                if (e.getCauseOfDeath() != null) {
                    deaths.add(e.getCauseOfDeath());
                }
            }
            for (Executor e : comp.getOneOffExecutors()) {
                if (e.getCauseOfDeath() != null) {
                    deaths.add(e.getCauseOfDeath());
                }
            }
        }
        assertThat("None of the executors have died abnormally", deaths, containsInAnyOrder());
    }

    @Test
    @Issue("JENKINS-35112")
    public void deleteWhileComputing() throws Exception {
        CoordinatedComputedFolder d = r.jenkins.createProject(CoordinatedComputedFolder.class, "d");
        d.kids.addAll(Arrays.asList("A", "B"));
        QueueTaskFuture<Queue.Executable> future = d.scheduleBuild2(0).getFuture();
        FolderComputation<FreeStyleProject> computation;
        while (Executor.of((computation = d.getComputation())) == null) {
            Thread.sleep(50);
        }
        d.delete();
        assertThat(computation.getResult(), is(Result.ABORTED));
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue, or fail the test
     * if the specified timeout milliseconds is
     */
    public void waitUntilNoActivityIgnoringThreadDeathUpTo(int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int streak = 0;

        while (true) {
            Thread.sleep(10);
            if (isSomethingHappeningIgnoringThreadDeath())
                streak = 0;
            else
                streak++;

            if (streak > 5)   // the system is quiet for a while
                return;

            if (System.currentTimeMillis() - startTime > timeout) {
                List<Queue.Executable> building = new ArrayList<Queue.Executable>();
                List<Throwable> deaths = new ArrayList<Throwable>();
                for (Computer c : r.jenkins.getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        if (e.isBusy()) {
                            if (e.getCauseOfDeath() == null) {
                                building.add(e.getCurrentExecutable());
                            } else {
                                deaths.add(e.getCauseOfDeath());
                            }
                        }
                    }
                    for (Executor e : c.getOneOffExecutors()) {
                        if (e.isBusy()) {
                            if (e.getCauseOfDeath() == null) {
                                building.add(e.getCurrentExecutable());
                            } else {
                                deaths.add(e.getCauseOfDeath());
                            }
                        }
                    }
                }
                ThreadInfo[] threadInfos = Functions.getThreadInfos();
                Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
                for (ThreadInfo ti : threadInfos) {
                    System.err.println(Functions.dumpThreadInfo(ti, m));
                }
                throw new AssertionError(
                        String.format("Jenkins is still doing something after %dms: queue=%s building=%s deaths=%s",
                                timeout, Arrays.asList(r.jenkins.getQueue().getItems()), building, deaths));
            }
        }
    }

    /**
     * Returns true if Hudson is building something or going to build something.
     */
    public boolean isSomethingHappeningIgnoringThreadDeath() {
        if (!r.jenkins.getQueue().isEmpty()) {
            return true;
        }
        for (Computer n : r.jenkins.getComputers()) {
            for (OneOffExecutor e : n.getOneOffExecutors()) {
                if (e.getCauseOfDeath() == null && e.isBusy()) {
                    return true;
                }
            }
            for (Executor e : n.getExecutors()) {
                if (e.getCauseOfDeath() == null && e.isBusy()) {
                    return true;
                }
            }
        }
        return false;
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
                try {
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
                } finally {
                    observer.completed(kid);
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

        String recompute(Result result) throws Exception {
            return doRecompute(this, result);
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
    public static class SampleComputedFolderWithFoldersAsChildren extends ComputedFolder<Folder> {

        List<String> kids = new ArrayList<String>();
        int round;

        private SampleComputedFolderWithFoldersAsChildren(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<Folder> observer, TaskListener listener) throws IOException, InterruptedException {
            round++;
            listener.getLogger().println("=== Round #" + round + " ===");
            for (String kid : kids) {
                listener.getLogger().println("considering " + kid);
                Folder p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("creating a child");
                            p = new Folder(this, kid);
                            p.setDescription("created in round #" + round);
                            observer.created(p);
                        } else {
                            listener.getLogger().println("not allowed to create a child");
                        }
                    } else {
                        listener.getLogger().println("updated existing child with description " + p.getDescription());
                        p.setDescription("updated in round #" + round);
                    }
                } finally {
                    observer.completed(kid);
                }
            }

        }

        String recompute(Result result) throws Exception {
            return doRecompute(this, result);
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SampleComputedFolderWithFoldersAsChildren(parent, name);
            }

        }

    }

    public static class LockedDownSampleComputedFolder extends SampleComputedFolder {

        private LockedDownSampleComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected AbstractFolderViewHolder newFolderViewHolder() {
            return new FixedViewHolder(this);
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new LockedDownSampleComputedFolder(parent, name);
            }

        }

        private static class FixedViewHolder extends AbstractFolderViewHolder {
            final List<View> views;
            final ViewsTabBar tabBar = new DefaultViewsTabBar();

            public FixedViewHolder(ViewGroup owner) {
                views = new ArrayList<View>(Arrays.asList(new AllView("All", owner), new ListView("Empty", owner)));
            }

            @NonNull
            @Override
            public List<View> getViews() {
                return Collections.unmodifiableList(views);
            }

            @Override
            public void setViews(@NonNull List<? extends View> views) {
                throw new UnsupportedOperationException("Blow up if called");
            }

            @Override
            public boolean isViewsModifiable() {
                return false;
            }

            @Override
            public boolean isPrimaryModifiable() {
                return false;
            }

            @Override
            public boolean isTabBarModifiable() {
                return false;
            }

            @Override
            public String getPrimaryView() {
                return "Empty";
            }

            @Override
            public void setPrimaryView(@CheckForNull String name) {
                throw new UnsupportedOperationException("Blow up if called");
            }

            @NonNull
            @Override
            public ViewsTabBar getTabBar() {
                return tabBar;
            }

            @Override
            public void setTabBar(@NonNull ViewsTabBar tabBar) {
                throw new UnsupportedOperationException("Blow up if called");
            }
        }
    }

    public static class VariableRecomputationComputedFolder extends SampleComputedFolder {

        private transient Runnable submit;

        private VariableRecomputationComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void submit(StaplerRequest req, StaplerResponse rsp)
                throws IOException, ServletException, Descriptor.FormException {
            super.submit(req, rsp);
            if (submit != null) {
                submit.run();
            }
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new VariableRecomputationComputedFolder(parent, name);
            }

        }
    }

    static String doRecompute(ComputedFolder<?> d, Result result) throws Exception {
        d.scheduleBuild2(0).getFuture().get();
        FolderComputation<?> computation = d.getComputation();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        computation.writeWholeLogTo(baos);
        String log = baos.toString();
        assertEquals(log, result, computation.getResult());
        return log;
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
                try {
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
                } finally {
                    observer.completed(childName);
                }
            }

        }

        String assertItemNames(String... names) throws Exception {
            String log = doRecompute(this, Result.SUCCESS);
            Set<String> actual = new TreeSet<String>();
            for (SampleComputedFolder d : getItems()) {
                d.recompute(Result.SUCCESS);
                d.assertItemNames(d.round, d.kids.toArray(new String[0]));
                actual.add(d.getName());
            }
            assertEquals(new TreeSet<String>(Arrays.asList(names)).toString(), actual.toString());
            return log;
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SecondOrderComputedFolder(parent, name);
            }

        }

    }

    public static class CoordinatedComputedFolder extends ComputedFolder<FreeStyleProject> {

        List<String> kids = new ArrayList<String>();
        int round;
        List<String> created = new ArrayList<String>();
        List<String> deleted = new ArrayList<String>();
        CountDownLatch compute = new CountDownLatch(2);

        private CoordinatedComputedFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener)
                throws IOException, InterruptedException {
            round++;
            listener.getLogger().println("=== Round #" + round + " ===");
            List<String> kids = new ArrayList<>(this.kids);
            compute.countDown();
            compute.await();
            for (String kid : kids) {
                Thread.sleep(25);
                if (kid.equals("Z")) {
                    throw new AbortException("not adding Z");
                }
                listener.getLogger().println("considering " + kid);
                FreeStyleProject p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("creating a child");
                            p = new FreeStyleProject(this, kid);
                            p.setDescription("created in round #" + round);
                            p.getBuildersList().add(new SleepBuilder(500));
                            observer.created(p);
                            created.add(kid);
                            p.scheduleBuild(0, new TimerTrigger.TimerTriggerCause());
                        } else {
                            listener.getLogger().println("not allowed to create a child");
                        }
                    } else {
                        listener.getLogger().println("updated existing child with description " + p.getDescription());
                        p.setDescription("updated in round #" + round);
                    }
                } finally {
                    observer.completed(kid);
                }
            }
        }

        public void onKid(String kid) throws InterruptedException {
            compute.countDown();
            compute.await();
            Thread.sleep(25);
            try (StreamTaskListener listener = getComputation().createEventsListener();
                 ChildObserver<FreeStyleProject> observer = openEventsChildObserver()) {
                listener.getLogger().println("considering " + kid);
                FreeStyleProject p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("creating a child");
                            p = new FreeStyleProject(this, kid);
                            p.setDescription("created in event #" + round);
                            p.getBuildersList().add(new SleepBuilder(500));
                            observer.created(p);
                            created.add(kid);
                            p.scheduleBuild(0, new TimerTrigger.TimerTriggerCause());
                        } else {
                            listener.getLogger().println("not allowed to create a child");
                        }
                    } else {
                        listener.getLogger().println("updated existing child with description " + p.getDescription());
                        p.setDescription("updated in event #" + round);
                    }
                } finally {
                    observer.completed(kid);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Collection<FreeStyleProject> orphanedItems(Collection<FreeStyleProject> orphaned,
                                                             TaskListener listener)
                throws IOException, InterruptedException {
            Collection<FreeStyleProject> deleting = super.orphanedItems(orphaned, listener);
            for (FreeStyleProject p : deleting) {
                String kid = p.getName();
                listener.getLogger().println("deleting " + kid + " in round #" + round);
                deleted.add(kid);
            }
            return deleting;
        }

        String recompute(Result result) throws Exception {
            return doRecompute(this, result);
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
                return new CoordinatedComputedFolder(parent, name);
            }

        }

    }

}
