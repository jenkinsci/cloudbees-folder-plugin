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
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

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
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (ItemGroup) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
        List<StandardUsernamePasswordCredentials> asItem =
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (Item) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
        assertThat(asGroup, is(asItem));
        CredentialsStore folderStore = getFolderStore(f);
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test-id", "description", "test-user",
                        "secret");
        folderStore.addCredentials(Domain.global(), credentials);
        asGroup = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (ItemGroup) f,
                ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
        asItem = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (Item) f,
                ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
        assertThat(asGroup, is(asItem));
        assertThat(asGroup, hasItem(credentials));
        assertThat(asItem, hasItem(credentials));
    }

    @Test
    public void credentialsListableAtFolderScope() throws Exception {
        Folder f = createFolder();
        ListBoxModel asGroup =
                CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, (ItemGroup) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always());
        ListBoxModel asItem =
                CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, (Item) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always());
        assertThat(asGroup, is(asItem));
        assertThat(asGroup.size(), is(0));
        assertThat(asItem.size(), is(0));
        CredentialsStore folderStore = getFolderStore(f);
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test-id", "description", "test-user",
                        "secret");
        folderStore.addCredentials(Domain.global(), credentials);
        asGroup = CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, (ItemGroup) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always());
        asItem = CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, (Item) f,
                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always());
        assertThat(asGroup.size(), is(1));
        assertThat(asGroup.get(0).value, is("test-id"));
        assertThat(asItem.size(), is(1));
        assertThat(asItem.get(0).value, is("test-id"));
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
