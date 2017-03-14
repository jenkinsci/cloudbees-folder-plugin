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
import hudson.AbortException;
import hudson.model.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class ComputedFolderWithFolderChildrenTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void basicTestWithFolders() throws Exception {
        final SampleComputedFolderWithFolderChildren d = r.jenkins.createProject(SampleComputedFolderWithFolderChildren.class, "d");
        d.recompute(Result.SUCCESS);
        d.assertItemNames(1);
        d.kids.addAll(Arrays.asList("A"));
        d.recompute(Result.SUCCESS);
        d.assertItemNames(2, "A");
        assertEquals("[A]", d.created.toString());

        // Folder page opens correctly
        Folder a = d.getItems().iterator().next();
        r.createWebClient().getPage(a);

        // ComputerFolder page does no open
        r.createWebClient().getPage(d);
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
    public static class SampleComputedFolderWithFolderChildren extends ComputedFolder<Folder> {

        List<String> kids = new ArrayList<String>();
        int round;
        List<String> created = new ArrayList<String>();
        List<String> deleted = new ArrayList<String>();

        private SampleComputedFolderWithFolderChildren(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<Folder> observer, TaskListener listener) throws IOException, InterruptedException {
            round++;
            listener.getLogger().println("=== Round #" + round + " ===");
            for (String kid : kids) {
                if (kid.equals("Z")) {
                    throw new AbortException("not adding Z");
                }
                listener.getLogger().println("considering " + kid);
                Folder p = observer.shouldUpdate(kid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(kid)) {
                            listener.getLogger().println("creating a child");
                            p = new Folder(this, kid);
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
        protected Collection<Folder> orphanedItems(Collection<Folder> orphaned, TaskListener listener) throws IOException, InterruptedException {
            Collection<Folder> deleting = super.orphanedItems(orphaned, listener);
            for (Folder p : deleting) {
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
            for (Folder p : getItems()) {
                actual.add(p.getName());
            }
            assertEquals(new TreeSet<String>(Arrays.asList(names)).toString(), actual.toString());
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new SampleComputedFolderWithFolderChildren(parent, name);
            }

        }

    }

}
