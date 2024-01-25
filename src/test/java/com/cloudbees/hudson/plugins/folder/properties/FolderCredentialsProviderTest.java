/*
 * The MIT License
 *
 * Copyright 2016 CloudBees.
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
package com.cloudbees.hudson.plugins.folder.properties;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.StringDescription;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

public class FolderCredentialsProviderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void foldersHaveTheirOwnStore() throws Exception {
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        assertThat(folderStore, notNullValue());
    }

    @Test
    public void credentialsAvailableAtFolderScope() throws Exception {
        Folder f = createFolder();
        List<StandardUsernamePasswordCredentials> asGroup =
                CredentialsProvider.lookupCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList());
        List<StandardUsernamePasswordCredentials> asItem =
                CredentialsProvider.lookupCredentialsInItem(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList());
        assertThat(asGroup, is(asItem));
        CredentialsStore folderStore = getFolderStore(f);
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test-id", "description", "test-user",
                        "secret");
        folderStore.addCredentials(Domain.global(), credentials);
        asGroup = CredentialsProvider.lookupCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, f,
                ACL.SYSTEM2, Collections.emptyList());
        asItem = CredentialsProvider.lookupCredentialsInItem(StandardUsernamePasswordCredentials.class, f,
                ACL.SYSTEM2, Collections.emptyList());
        assertThat(asGroup, is(asItem));
        assertThat(asGroup, hasItem(credentials));
        assertThat(asItem, hasItem(credentials));
    }

    @Test
    public void credentialsListableAtFolderScope() throws Exception {
        Folder f = createFolder();
        ListBoxModel asGroup =
                CredentialsProvider.listCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        ListBoxModel asItem =
                CredentialsProvider.listCredentialsInItem(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        assertThat(asGroup, is(asItem));
        assertThat(asGroup.size(), is(0));
        assertThat(asItem.size(), is(0));
        CredentialsStore folderStore = getFolderStore(f);
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test-id", "description", "test-user",
                        "secret");
        folderStore.addCredentials(Domain.global(), credentials);
        asGroup = CredentialsProvider.listCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        asItem = CredentialsProvider.listCredentialsInItem(StandardUsernamePasswordCredentials.class, f,
                        ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        assertThat(asGroup.size(), is(1));
        assertThat(asGroup.get(0).value, is("test-id"));
        assertThat(asItem.size(), is(1));
        assertThat(asItem.get(0).value, is("test-id"));
    }

    @Test
    public void given_folderCredential_when_builtAsSystem_then_credentialFound() throws Exception {
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Dr. Fu Manchu", "foo",
                        "manchu"));
        FreeStyleProject prj = f.createProject(FreeStyleProject.class, "job");
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));
        r.buildAndAssertSuccess(prj);
    }

    @Test
    public void given_folderCredential_when_builtAsUserWithUseItem_then_credentialFound() throws Exception {
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Dr. Fu Manchu", "foo",
                        "manchu"));
        FreeStyleProject prj = f.createProject(FreeStyleProject.class, "job");
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(CredentialsProvider.USE_ITEM).everywhere().to("bob");
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getOrCreateByIdOrFullName("bob").impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        r.buildAndAssertSuccess(prj);
    }

    @Test
    public void given_folderCredential_when_builtAsUserWithoutUseItem_then_credentialNotFound() throws Exception {
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Dr. Fu Manchu", "foo",
                        "manchu"));
        FreeStyleProject prj = f.createProject(FreeStyleProject.class, "job");
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getOrCreateByIdOrFullName("bob").impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        r.assertBuildStatus(Result.FAILURE, prj.scheduleBuild2(0).get());
    }

    @Test
    public void given_folderAndSystemCredentials_when_builtAsUserWithUseItem_then_folderCredentialFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "You don't want me", "bar", "fly")
        );
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Dr. Fu Manchu", "foo",
                        "manchu"));
        FreeStyleProject prj = f.createProject(FreeStyleProject.class, "job");
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu", Matchers.hasProperty("username", is("foo"))));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(CredentialsProvider.USE_ITEM).everywhere().to("bob");
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getOrCreateByIdOrFullName("bob").impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        try {
            r.buildAndAssertSuccess(prj);
        } catch (Exception e) {
            FreeStyleBuild build = prj.getLastBuild();
            if (build != null) {
                System.out.println(JenkinsRule.getLog(build));
            }
            throw e;
        }
    }

    @Test
    public void given_nestedFolderAndSystemCredentials_when_builtAsUserWithUseItem_then_folderCredentialFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "You don't want me", "bar", "fly")
        );
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Prof. Xavier", "prof",
                        "xavier"));
        Folder child = f.createProject(Folder.class, "child");
        getFolderStore(child).addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo-manchu", "Dr. Fu Manchu", "foo",
                        "manchu"));
        FreeStyleProject prj = child.createProject(FreeStyleProject.class, "job");
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu", Matchers.hasProperty("username", is("foo"))));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(CredentialsProvider.USE_ITEM).everywhere().to("bob");
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getOrCreateByIdOrFullName("bob").impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        try {
            r.buildAndAssertSuccess(prj);
        } catch (Exception e) {
            FreeStyleBuild build = prj.getLastBuild();
            if (build != null) {
                System.out.println(JenkinsRule.getLog(build));
            }
            throw e;
        }
    }

    @Test
    @Issue("SECURITY-3252")
    public void cannotUpdateCredentialsId() throws Exception {
        UsernamePasswordCredentialsImpl cred1 = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "cred1", "Cred 1", "foo", "bar");
        UsernamePasswordCredentialsImpl cred2 = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "cred2", "Cred 2", "fee", "baz");
        Folder f = createFolder();
        CredentialsStore folderStore = getFolderStore(f);
        folderStore.addCredentials(Domain.global(), cred1);

        assertThrows(IllegalArgumentException.class, () -> folderStore.updateCredentials(Domain.global(), cred1, cred2));
    }

    private static class HasCredentialBuilder extends TestBuilder {

        private final String id;
        private final Matcher<?> matcher;

        HasCredentialBuilder(String id) {
            this(id, null);
        }

        HasCredentialBuilder(String id, Matcher<?> matcher) {
            this.id = id;
            this.matcher = matcher;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            IdCredentials credentials = CredentialsProvider.findCredentialById(id, IdCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find any credentials with id %s%n", id);
                build.setResult(Result.FAILURE);
                return false;
            } else {
                listener.getLogger()
                        .printf("Found %s credentials with id %s%n", CredentialsNameProvider.name(credentials), id);
                if (matcher != null) {
                    if (matcher.matches(credentials)) {
                        listener.getLogger().println("Credentials match criteria");
                    } else {
                        StringDescription description = new StringDescription();
                        matcher.describeMismatch(credentials, description);
                        listener.getLogger().println(description.toString());
                        return false;
                    }
                }
                return true;
            }
        }

    }

    private CredentialsStore getFolderStore(Folder f) {
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        return folderStore;
    }

    private Folder createFolder() throws IOException {
        return r.jenkins.createProject(Folder.class, "folder" + r.jenkins.getItems().size());
    }

}
