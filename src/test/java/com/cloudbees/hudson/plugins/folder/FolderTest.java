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

package com.cloudbees.hudson.plugins.folder;

import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import hudson.model.Actionable;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.User;
import hudson.model.listeners.ItemListener;
import hudson.search.SearchItem;
import hudson.security.ACL;
import hudson.security.WhoAmI;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.tasks.BuildTrigger;
import hudson.views.BuildButtonColumn;
import hudson.views.JobColumn;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.acegisecurity.AccessDeniedException;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

public class FolderTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * Tests rename operation.
     */
    @Test public void rename() throws Exception {
        Folder f = createFolder();
        f.setDescription("Some view");

        String oldName = f.getName();

        HtmlForm cfg = r.createWebClient().getPage(f, "configure").getFormByName("config");
        cfg.getInputByName("_.name").setValueAttribute("newName");
        for (HtmlForm form : r.submit(cfg).getForms()) {
            if (form.getActionAttribute().equals("doRename")) {
                r.submit(form);
                break;
            }
        }

        assertEquals("newName",f.getName());
        assertEquals("Some view",f.getDescription());
        assertNull(r.jenkins.getItem(oldName));
        assertSame(r.jenkins.getItem("newName"),f);
    }

    @Test public void configRoundtrip() throws Exception {
        Folder f = createFolder();
        r.configRoundtrip(f);
    }

    /**
     * Makes sure the child can be deleted.
     */
    @Test public void deleteChild() throws Exception {
        Folder f = createFolder();
        FreeStyleProject child = f.createProject(FreeStyleProject.class, "foo");
        assertEquals(1,f.getItems().size());

        child.delete();
        assertEquals(0,f.getItems().size());
    }

    /**
     * Tests the path resolution of "foo" (relative) vs "/foo" (absolute)
     */
    @Test public void copyJob() throws Exception {
        /*
            - foo
            - folder
              - foo
         */
        FreeStyleProject top = r.createFreeStyleProject("foo");
        top.setDescription("top");

        Folder f = createFolder();
        FreeStyleProject child = f.createProject(FreeStyleProject.class, "foo");
        child.setDescription("child");

        JenkinsRule.WebClient wc = r.createWebClient();

        // "foo" should copy "child"
        copyFromGUI(f, wc, "foo", "xyz");
        assertEquals("child",((Job)f.getItem("xyz")).getDescription());
        
        // "/foo" should copy "top"
        copyFromGUI(f, wc, "/foo", "uvw");
        assertEquals("top",((Job)f.getItem("uvw")).getDescription());

    }

    private void copyFromGUI(Folder f, JenkinsRule.WebClient wc, String fromName, String toName) throws Exception {
        HtmlPage page = wc.getPage(f, "newJob");
        ((HtmlInput)page.getElementById("name")).type(toName);
        HtmlInput fe = (HtmlInput) page.getElementById("from");
        fe.focus();
        fe.type(fromName);
        r.submit(page.getFormByName("createItem"));
    }

    /**
     * When copying a folder, its contents need to be recursively copied.
     */
    @Test public void copy() throws Exception {
        Folder f = createFolder();
        FreeStyleProject c1 = f.createProject(FreeStyleProject.class, "child1");
        Folder c2 = f.createProject(Folder.class, "nested");
        FreeStyleProject c21 = c2.createProject(FreeStyleProject.class,"child2");

        Folder f2 = r.jenkins.copy(f, "fcopy");
        assertTrue(f2.getItem("child1") instanceof FreeStyleProject);
        Folder n = (Folder)f2.getItem("nested");
        assertTrue(n.getItem("child2") instanceof FreeStyleProject);
    }

    @Issue("JENKINS-34939")
    @Test public void delete() throws Exception {
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        d1.createProject(FreeStyleProject.class, "p1");
        d1.createProject(FreeStyleProject.class, "p2");
        d1.createProject(Folder.class, "d2").createProject(FreeStyleProject.class, "p4");
        d1.delete();
        assertEquals("AbstractFolder.items is sorted by name so we can predict deletion order",
            "{d1=[d1], d1/d2=[d1, d1/d2, d1/p1, d1/p2], d1/d2/p4=[d1, d1/d2, d1/d2/p4, d1/p1, d1/p2], d1/p1=[d1, d1/p1, d1/p2], d1/p2=[d1, d1/p2]}",
            DeleteListener.whatRemainedWhenDeleted.toString());
    }
    @TestExtension("delete") public static class DeleteListener extends ItemListener {
        static Map<String,Set<String>> whatRemainedWhenDeleted = new TreeMap<String,Set<String>>();
        @Override public void onDeleted(Item item) {
            try {
                // Access metadata from another thread.
                whatRemainedWhenDeleted.put(item.getFullName(), Timer.get().submit(new Callable<Set<String>>() {
                    @Override public Set<String> call() throws Exception {
                        Set<String> remaining = new TreeSet<String>();
                        for (Item i : Jenkins.getActiveInstance().getAllItems()) {
                            remaining.add(i.getFullName());
                            if (i instanceof Actionable) {
                                ((Actionable) i).getAllActions();
                            }
                        }
                        return remaining;
                    }
                }).get());
            } catch (Exception x) {
                assert false : x;
            }
        }
    }

    /**
     * This is more of a test of the core, but make sure the triggers resolve between ourselves.
     */
    @Test public void trigger() throws Exception {
        Folder f = createFolder();
        FreeStyleProject a = f.createProject(FreeStyleProject.class, "a");
        FreeStyleProject b = f.createProject(FreeStyleProject.class, "b");
        a.getPublishersList().add(new BuildTrigger("b",false));

        FreeStyleBuild a1 = r.assertBuildStatusSuccess(a.scheduleBuild2(0));
        for (int i=0; i<10 && b.getLastBuild()==null; i++) {
            Thread.sleep(100);
        }
        // make sue that a build of B happens
    }

    /**
     * Makes sure that there's no JavaScript error in the new view page.
     */
    @Test public void newViewPage() throws Exception {
        Folder f = createFolder();
        HtmlPage p = r.createWebClient().getPage(f, "newView");
        HtmlForm fm = p.getFormByName("createItem");
        fm.getInputByName("name").setValueAttribute("abcView");
        for (HtmlRadioButtonInput r : fm.getRadioButtonsByName("mode")) {
            if (r.getValueAttribute().equals(ListView.class.getName()))
                r.click();
        }
        r.submit(fm);
        assertSame(ListView.class, f.getView("abcView").getClass());
    }

    /**
     * Make sure we can load the data before we supported views and the configuration of the view
     * correctly comes back.
     */
    @LocalData
    @Test public void dataCompatibility() throws Exception {
        Folder f = (Folder) r.jenkins.getItem("foo");
        ListView pv = (ListView)f.getPrimaryView();
        assertEquals(2,pv.getColumns().size());
        assertEquals(JobColumn.class, pv.getColumns().get(0).getClass());
        assertEquals(BuildButtonColumn.class, pv.getColumns().get(1).getClass());

        // we only have 2 columns in the zip but we expect a lot more in the out-of-the-box ListView.
        assertTrue(2<new ListView("test").getColumns().size());
    }

    @Test public void search() throws Exception {
        FreeStyleProject topJob = r.jenkins.createProject(FreeStyleProject.class, "top job");
        Folder f1 = r.jenkins.createProject(Folder.class, "f1");
        FreeStyleProject middleJob = f1.createProject(FreeStyleProject.class, "middle job");
        Folder f2 = f1.createProject(Folder.class, "f2");
        FreeStyleProject bottomJob = f2.createProject(FreeStyleProject.class, "bottom job");
        List<SearchItem> items = new ArrayList<SearchItem>();
        f1.getSearchIndex().suggest("job", items);
        assertEquals(new HashSet<SearchItem>(Arrays.asList(middleJob, bottomJob)), new HashSet<SearchItem>(items));
    }

    @Test public void reloadJenkinsAndFindBuildInProgress() throws Exception {
        Folder f1 = r.jenkins.createProject(Folder.class, "f");
        FreeStyleProject p1 = f1.createProject(FreeStyleProject.class, "test1");

        FreeStyleBuild p1b1 = p1.scheduleBuild2(0).get();  // one completed build

        p1.getBuildersList().add(new SleepBuilder(99999999));
        p1.save();
        FreeStyleBuild p1b2 = p1.scheduleBuild2(0).waitForStart(); // another build in progress

        // trigger the full Jenkins reload
        r.jenkins.reload();

        Folder f2 = (Folder) r.jenkins.getItem("f");
        assertNotSame(f1,f2);

        FreeStyleProject p2 = (FreeStyleProject) f2.getItem("test1");
        /* Fails now. Why was this here?
        assertNotSame(p1,p2);
        */

        FreeStyleBuild p2b1 = p2.getBuildByNumber(1);
        FreeStyleBuild p2b2 = p2.getBuildByNumber(2);

        assertTrue(p2b2.isBuilding());
        assertSame(p2b2,p1b2);

        assertNotSame(p1b1,p2b1);

        p1b2.getExecutor().interrupt(); // kill the executor
    }

    @Test public void discoverPermission() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        final Folder d = r.jenkins.createProject(Folder.class, "d");
        final FreeStyleProject p1 = d.createProject(FreeStyleProject.class, "p1");
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().toEveryone().
                grant(Item.DISCOVER).everywhere().toAuthenticated().
                grant(Item.READ).onItems(d).toEveryone().
                grant(Item.READ).onItems(p1).to("alice"));
        FreeStyleProject p2 = d.createProject(FreeStyleProject.class, "p2");
        ACL.impersonate(Jenkins.ANONYMOUS, new Runnable() {
            @Override public void run() {
                assertEquals(Collections.emptyList(), d.getItems());
                assertNull(d.getItem("p1"));
                assertNull(d.getItem("p2"));
            }
        });
        ACL.impersonate(User.get("alice").impersonate(), new Runnable() {
            @Override public void run() {
                assertEquals(Collections.singletonList(p1), d.getItems());
                assertEquals(p1, d.getItem("p1"));
                try {
                    d.getItem("p2");
                    fail("should have been told p2 exists");
                } catch (AccessDeniedException x) {
                    // correct
                }
            }
        });
    }

    @Test public void addAction() throws Exception {
        Folder f = createFolder();
        WhoAmI a = new WhoAmI();
        f.addAction(a);
        assertNotNull(f.getAction(WhoAmI.class));
    }
    
    @Issue("JENKINS-32487")
    @Test public void shouldAssignPropertyOwnerOnCreationAndReload() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "myFolder");
        r.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        
        // We add a stub property to generate the persisted list
        // Then we ensure owner is being assigned properly.
        folder.addProperty(new FolderCredentialsProvider.FolderCredentialsProperty(new DomainCredentials[0]));
        assertPropertyOwner("After property add", folder, FolderCredentialsProvider.FolderCredentialsProperty.class);
    
        // Reload and ensure that the property owner is set
        r.jenkins.reload();
        Folder reloadedFolder = r.jenkins.getItemByFullName("myFolder", Folder.class);
        assertPropertyOwner("After reload", reloadedFolder, FolderCredentialsProvider.FolderCredentialsProperty.class);
    }
    
    @Issue("JENKINS-32359")
    @Test public void shouldProperlyPersistFolderPropertiesOnMultipleReloads() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "myFolder");

        // We add a stub property to generate the persisted list
        // After that we save and reload the config in order to drop PersistedListOwner according to the JENKINS-32359 scenario
        folder.addProperty(new FolderCredentialsProvider.FolderCredentialsProperty(new DomainCredentials[0]));
        r.jenkins.reload();
        
        // Add another property
        Map<Permission,Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();
        Set<String> sids = new HashSet<String>();
        sids.add("admin");
        grantedPermissions.put(Jenkins.ADMINISTER, sids);
        folder = r.jenkins.getItemByFullName("myFolder", Folder.class); 
        r.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        folder.addProperty(new com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty(grantedPermissions));
        
        // Reload folder from disk and check the state
        r.jenkins.reload();
        Folder reloadedFolder = r.jenkins.getItemByFullName("myFolder", Folder.class);
        assertThat("Folder has not been found after the reloading", reloadedFolder, notNullValue());
        assertThat("Property has not been reloaded, hence it has not been saved properly",
            reloadedFolder.getProperties().get(com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty.class),
            notNullValue());
        
        // Also ensure that both property owners are configured correctly
        assertPropertyOwner("After reload", reloadedFolder, FolderCredentialsProvider.FolderCredentialsProperty.class);
        assertPropertyOwner("After reload", reloadedFolder, com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty.class);
    }

    /**
     * Ensures that the specified property points to the folder.
     * @param <T> Property type
     * @param folder Folder
     * @param propertyClass Property class
     * @param step Failure message prefix
     */
    private <T extends AbstractFolderProperty<AbstractFolder<?>>> void assertPropertyOwner
            (String step, Folder folder, Class<T> propertyClass) {
        AbstractFolder<?> propertyOwner = folder.getProperties().get(propertyClass).getOwner();
        assertThat(step + ": The property owner should be instance of Folder", 
                propertyOwner, instanceOf(Folder.class));
        assertThat(step + ": The owner field of the " + propertyClass + 
                " property should point to the owner folder " + folder, 
                (Folder)propertyOwner, equalTo(folder));
    }
    
    private Folder createFolder() throws IOException {
        return r.jenkins.createProject(Folder.class, "folder" + r.jenkins.getItems().size());
    }

}
