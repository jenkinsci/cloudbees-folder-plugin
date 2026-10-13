package com.cloudbees.hudson.plugins.folder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class FolderItemConfiguratorTest {

    @Test
    @ConfiguredWithCode("create-folder.yaml")
    @SuppressWarnings("unused")
    void shouldCreateNewFolder(JenkinsConfiguredWithCodeRule j) {
        Folder folder = (Folder) Jenkins.get().getItem("team-alpha");

        assertNotNull(folder, "Folder should have been created by JCasC");
        assertEquals("Team Alpha", folder.getDisplayName());
        assertEquals("Description for Team Alpha", folder.getDescription());
    }

    @Test
    void shouldUpdateExistingFolder(JenkinsConfiguredWithCodeRule j) throws Exception {
        Folder existingFolder = j.jenkins.createProject(Folder.class, "team-beta");
        existingFolder.setDescription("Old Description");
        existingFolder.setDisplayName("Old Display Name");

        ConfigurationAsCode.get()
                .configure(Objects.requireNonNull(getClass().getResource("update-folder.yaml"))
                        .toExternalForm());

        Folder updatedFolder = (Folder) Jenkins.get().getItem("team-beta");

        assertNotNull(updatedFolder);
        assertEquals("Team Beta Updated", updatedFolder.getDisplayName());
        assertEquals("New Description via JCasC", updatedFolder.getDescription());
    }

    @Test
    void shouldUpdateFolderPartially(JenkinsConfiguredWithCodeRule j) throws Exception {
        Folder existingFolder = j.jenkins.createProject(Folder.class, "team-gamma");
        existingFolder.setDescription("Original Description");
        existingFolder.setDisplayName("Original Display Name");

        ConfigurationAsCode.get()
                .configure(Objects.requireNonNull(getClass().getResource("update-folder-partial.yaml"))
                        .toExternalForm());

        Folder updatedFolder = (Folder) Jenkins.get().getItem("team-gamma");

        assertNotNull(updatedFolder);
        assertEquals("Partially Updated Description", updatedFolder.getDescription());
        assertEquals("Original Display Name", updatedFolder.getDisplayName());
    }

    @Test
    void shouldThrowExceptionWhenItemExistsButIsNotAFolder(JenkinsConfiguredWithCodeRule j) throws Exception {
        j.jenkins.createProject(FreeStyleProject.class, "team-alpha");

        ConfiguratorException exception = assertThrows(ConfiguratorException.class, () -> ConfigurationAsCode.get()
                .configure(Objects.requireNonNull(getClass().getResource("create-folder.yaml"))
                        .toExternalForm()));

        assertTrue(
                exception.getMessage().contains("already exists and is not a folder"),
                "Exception message should clearly explain the type mismatch");
    }

    @Test
    void shouldClearFolderFieldsWhenEmpty(JenkinsConfiguredWithCodeRule j) throws Exception {
        Folder existingFolder = j.jenkins.createProject(Folder.class, "team-cleared");
        existingFolder.setDescription("Original Description");
        existingFolder.setDisplayName("Original Display Name");

        ConfigurationAsCode.get()
                .configure(Objects.requireNonNull(getClass().getResource("clear-folder.yaml"))
                        .toExternalForm());

        Folder updatedFolder = (Folder) Jenkins.get().getItem("team-cleared");

        assertNotNull(updatedFolder);
        assertEquals("", updatedFolder.getDescription());
        assertEquals("team-cleared", updatedFolder.getDisplayName());
    }

    @Test
    @SuppressWarnings("unused")
    void shouldThrowExceptionOnUnsupportedAttribute(JenkinsConfiguredWithCodeRule j) {
        ConfiguratorException exception = assertThrows(ConfiguratorException.class, () -> ConfigurationAsCode.get()
                .configure(Objects.requireNonNull(getClass().getResource("invalid-folder-key.yaml"))
                        .toExternalForm()));

        assertTrue(
                exception.getMessage().contains("Unsupported attribute 'displayname' for folder 'team-typo'"),
                "Exception message should clearly identify the invalid key");
    }
}
