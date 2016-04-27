/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import java.util.Arrays;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class StandardHandlerTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void getDestinations() throws Exception {
        Folder d1 = r.jenkins.createProject(Folder.class, "d1"); // where we start
        FreeStyleProject j = d1.createProject(FreeStyleProject.class, "j");
        final Folder d2 = r.jenkins.createProject(Folder.class, "d2"); // where we could go
        Folder d3 = r.jenkins.createProject(Folder.class, "d3"); // where we cannot
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ, Item.READ).everywhere().to("joe").
            grant(Item.CREATE).onItems(d2).to("joe"));
        SecurityContext sc = ACL.impersonate(User.get("joe").impersonate());
        try {
            assertEquals(Arrays.asList(d2), new StandardHandler().validDestinations(j));
            assertEquals(Arrays.asList(d2), new StandardHandler().validDestinations(d1));

            assertNotEquals(Arrays.asList(r.jenkins, j, d1, d3), new StandardHandler().validDestinations(j));
            assertNotEquals(Arrays.asList(r.jenkins, j, d1, d3), new StandardHandler().validDestinations(d1));
        } finally {
            SecurityContextHolder.setContext(sc);
        }
    }

    @Test public void getDestinationsUsingSubfolders() throws Exception {
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        Folder d11 = d1.createProject(Folder.class, "d11");
        FreeStyleProject j = d1.createProject(FreeStyleProject.class, "j");
        Folder d2 = r.jenkins.createProject(Folder.class, "d2");
        Folder d3 = r.jenkins.createProject(Folder.class, "d3");
        Folder d31 = d3.createProject(Folder.class, "d31");

        assertEquals(Arrays.asList(r.jenkins, d11, d2, d3, d31), new StandardHandler().validDestinations(j));
        assertEquals(Arrays.asList(r.jenkins, d2, d3, d31), new StandardHandler().validDestinations(d11));

        assertNotEquals(d1, new StandardHandler().validDestinations(j));
        assertNotEquals(Arrays.asList(d1, d11), new StandardHandler().validDestinations(d11));
    }

    @Test public void getDestinationsUsingItemsWithSameName() throws Exception {
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        Folder d11 = d1.createProject(Folder.class, "d11");
        FreeStyleProject j = d1.createProject(FreeStyleProject.class, "j");
        Folder d2 = r.jenkins.createProject(Folder.class, "d2");
        FreeStyleProject g = d2.createProject(FreeStyleProject.class, "j");
        Folder d3 = r.jenkins.createProject(Folder.class, "d3");
        Folder d31 = d3.createProject(Folder.class, "d11");

        assertEquals(Arrays.asList(r.jenkins, d11, d3, d31), new StandardHandler().validDestinations(j));
        assertEquals(Arrays.asList(r.jenkins, d2, d31), new StandardHandler().validDestinations(d11));

        assertNotEquals(Arrays.asList(d1, d2), new StandardHandler().validDestinations(j));
        assertNotEquals(Arrays.asList(d1, d11, j, g, d3), new StandardHandler().validDestinations(d11));
    }

    @Test public void getDestinationsUsingItemsWithSameNameOnRootContext() throws Exception {
        FreeStyleProject j = r.jenkins.createProject(FreeStyleProject.class, "j");
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        Folder d11 = d1.createProject(Folder.class, "d11");
        Folder d2 = r.jenkins.createProject(Folder.class, "d2");
        FreeStyleProject g = d2.createProject(FreeStyleProject.class, "j");
        Folder d3 = r.jenkins.createProject(Folder.class, "d3");
        Folder d31 = d3.createProject(Folder.class, "d11");

        assertEquals(Arrays.asList(d1, d11, d3, d31), new StandardHandler().validDestinations(j));
        assertEquals(Arrays.asList(r.jenkins, d2, d31), new StandardHandler().validDestinations(d11));

        assertNotEquals(Arrays.asList(g, j, r.jenkins, d2), new StandardHandler().validDestinations(j));
        assertNotEquals(Arrays.asList(g, j, d1, d3), new StandardHandler().validDestinations(d11));
    }

    @Test public void getDestinationsMovingAParentFolderInToTheTree() throws Exception {
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        Folder d11 = d1.createProject(Folder.class, "d2");
        Folder d12 = d11.createProject(Folder.class, "d3");
        Folder d4 = r.jenkins.createProject(Folder.class, "d4");

        assertEquals(Arrays.asList(d4), new StandardHandler().validDestinations(d1));
        assertNotEquals(Arrays.asList(r.jenkins, d1, d11, d12), new StandardHandler().validDestinations(d1));
    }

}
