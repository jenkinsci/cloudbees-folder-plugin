/*
 * The MIT License
 *
 * Copyright 2026 Tim Jacomb
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ListView;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Folders show the experimental dashboard of core to users who enable it.
 */
@WithJenkins
class FolderDashboardTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to("admin")
                .grant(Jenkins.READ, Item.READ, Item.BUILD)
                .everywhere()
                .to("alice", "bob"));
    }

    @Test
    void folderShowsItsViewWithoutTheFlag() throws Exception {
        Folder team = r.jenkins.createProject(Folder.class, "team");
        team.createProject(FreeStyleProject.class, "p");
        try (JenkinsRule.WebClient wc = r.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("job/team/");
            assertThat(page.querySelector("[data-testid=dashboard-tabs]"), nullValue());
            assertThat(page.querySelector("a[href$='job/p/']"), notNullValue());

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/team/all/").getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void nestedFoldersShowTheirOwnDashboard() throws Exception {
        enableFlag("alice");
        Folder team = r.jenkins.createProject(Folder.class, "team");
        team.setDescription("Team builds");
        FreeStyleProject web = team.createProject(FreeStyleProject.class, "web");
        Folder backend = team.createProject(Folder.class, "backend");
        FreeStyleProject api = backend.createProject(FreeStyleProject.class, "api");
        Folder other = r.jenkins.createProject(Folder.class, "team-b");
        FreeStyleProject mobile = other.createProject(FreeStyleProject.class, "mobile");
        for (FreeStyleProject p : List.of(web, api, mobile)) {
            r.buildAndAssertSuccess(p);
        }

        FreeStyleProject soak = backend.createProject(FreeStyleProject.class, "soak");
        Blocker blocker = new Blocker();
        soak.getBuildersList().add(blocker);
        FreeStyleBuild running = soak.scheduleBuild2(0).waitForStart();
        blocker.started.block(TimeUnit.SECONDS.toMillis(30));

        try (JenkinsRule.WebClient wc = r.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("job/team/");
            assertThat(tabNames(page), contains("Recent", "All"));
            assertThat(page.querySelector("h1").getTextContent().trim(), is("team"));
            assertThat(page.querySelector("#description").getTextContent().trim(), is("Team builds"));
            assertThat(sectionLabels(page, "running"), contains(startsWith("backend » soak")));
            assertThat(sectionLabels(page, "finished"), contains(startsWith("backend » api"), startsWith("web")));

            HtmlPage nested = wc.goTo("job/team/job/backend/");
            assertThat(nested.querySelector("h1").getTextContent().trim(), is("backend"));
            assertThat(sectionLabels(nested, "running"), contains(startsWith("soak")));
            assertThat(sectionLabels(nested, "finished"), contains(startsWith("api")));
            assertThat(rowLabels(wc.goTo("job/team/job/backend/all/")), contains("api", "soak"));

            HtmlPage sibling = wc.goTo("job/team-b/");
            assertThat(sectionLabels(sibling, "running"), empty());
            assertThat(sectionLabels(sibling, "finished"), contains(startsWith("mobile")));

            HtmlPage all = wc.goTo("job/team/all/");
            assertThat(rowLabels(all), contains("backend", "web"));
            HtmlPage search = (HtmlPage) wc.goTo("job/team/all/items?start=0&search=api", null);
            assertThat(rowLabels(search), contains("backend » api"));
        } finally {
            blocker.release.signal();
            r.waitForCompletion(running);
        }
    }

    @Test
    void folderViewsAndActionsKeepWorking() throws Exception {
        enableFlag("admin");
        Folder team = r.jenkins.createProject(Folder.class, "team");
        team.createProject(FreeStyleProject.class, "p");
        team.addView(new ListView("mine", team));
        try (JenkinsRule.WebClient wc = r.createWebClient().login("admin")) {
            for (String view : List.of("job/team/view/All/", "job/team/view/mine/")) {
                assertThat(view, wc.goTo(view).querySelector("[data-testid=dashboard-tabs]"), nullValue());
            }
            wc.goTo("job/team/configure");
            wc.goTo("job/team/newJob");
            wc.goTo("job/team/builds");
            wc.goTo("job/team/api/json", "application/json");

            // The app bar has the folder's actions, and its menu the folder's views
            HtmlPage page = wc.goTo("job/team/");
            assertThat(page.getWebResponse().getContentAsString(), containsString("New Item"));
            String menu = wc.goTo("job/team/contextMenu", "application/json")
                    .getWebResponse()
                    .getContentAsString();
            assertThat(menu, containsString("job/team/view/mine/"));
        }
    }

    @Test
    void emptyFolderOffersToCreateAJobInIt() throws Exception {
        enableFlag("admin");
        r.jenkins.createProject(Folder.class, "empty");
        try (JenkinsRule.WebClient wc = r.createWebClient().login("admin")) {
            HtmlPage all = wc.goTo("job/empty/all/");
            assertThat(all.getWebResponse().getContentAsString(), containsString("This folder is empty"));
            assertThat(all.getAnchorByHref(r.contextPath + "/job/empty/newJob"), notNullValue());
        }
    }

    @Test
    void dashboardRespectsPermissions() throws Exception {
        enableFlag("bob");
        Folder team = r.jenkins.createProject(Folder.class, "team");
        FreeStyleProject visible = team.createProject(FreeStyleProject.class, "visible");
        FreeStyleProject hidden = team.createProject(FreeStyleProject.class, "hidden");
        Folder closed = r.jenkins.createProject(Folder.class, "closed");
        r.buildAndAssertSuccess(visible);
        r.buildAndAssertSuccess(hidden);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to("bob")
                .grant(Item.READ)
                .onItems(team, visible)
                .to("bob"));

        try (JenkinsRule.WebClient wc = r.createWebClient().login("bob")) {
            assertThat(sectionLabels(wc.goTo("job/team/"), "finished"), contains(startsWith("visible")));
            assertThat(rowLabels(wc.goTo("job/team/all/")), contains("visible"));

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/closed/all/").getWebResponse().getStatusCode(), is(404));
        }
        assertThat(closed.hasPermission2(User.getById("bob", true).impersonate2(), Item.READ), is(false));
    }

    @Test
    void computedFoldersShowTheDashboard() throws Exception {
        enableFlag("alice");
        Branches branches = r.jenkins.createProject(Branches.class, "repo");
        branches.scheduleBuild();
        r.waitUntilNoActivity();
        FreeStyleProject main = (FreeStyleProject) branches.getItem("main");
        r.buildAndAssertSuccess(main);
        FreeStyleProject outside = r.jenkins.createProject(FreeStyleProject.class, "outside");
        r.buildAndAssertSuccess(outside);

        try (JenkinsRule.WebClient wc = r.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("job/repo/");
            assertThat(tabNames(page), contains("Recent", "All"));
            assertThat(sectionLabels(page, "finished"), contains(startsWith("main")));
            assertThat(rowLabels(wc.goTo("job/repo/all/")), contains("feature", "main"));
        }
    }

    private static void enableFlag(String user) throws IOException {
        User.getById(user, true)
                .addProperty(new UserExperimentalFlagsProperty(Map.of("new-dashboard-page.flag", "true")));
    }

    private static List<String> tabNames(HtmlPage page) {
        return page.querySelectorAll("[data-testid=dashboard-tabs] a").stream()
                .map(n -> {
                    DomElement tab = (DomElement) n;
                    String text = tab.getTextContent();
                    DomElement badge = tab.querySelector(".jenkins-badge");
                    return (badge == null ? text : text.replace(badge.getTextContent(), "")).trim();
                })
                .toList();
    }

    private static List<String> rowLabels(HtmlPage page) {
        return page.querySelectorAll(".app-temporary-list__item__label").stream()
                .map(n -> n.getTextContent().trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static List<String> sectionLabels(HtmlPage page, String section) {
        return page.querySelectorAll("[data-section=" + section + "] .app-temporary-list__item__label").stream()
                .map(n -> n.getTextContent().trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static final class Blocker extends TestBuilder {
        final OneShotEvent started = new OneShotEvent();
        final OneShotEvent release = new OneShotEvent();

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException {
            started.signal();
            release.block();
            return true;
        }
    }

    /**
     * A computed folder with a fixed set of children, like a multibranch project's branches.
     */
    public static final class Branches extends ComputedFolder<FreeStyleProject> {

        private Branches(ItemGroup<?> parent, String name) {
            super(parent, name);
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener)
                throws IOException, InterruptedException {
            for (String branch : List.of("main", "feature")) {
                FreeStyleProject p = observer.shouldUpdate(branch);
                try {
                    if (p == null && observer.mayCreate(branch)) {
                        observer.created(new FreeStyleProject(this, branch));
                    }
                } finally {
                    observer.completed(branch);
                }
            }
        }

        @TestExtension("computedFoldersShowTheDashboard")
        public static final class DescriptorImpl extends AbstractFolderDescriptor {
            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new Branches(parent, name);
            }
        }
    }
}
