package com.cloudbees.hudson.plugins.folder;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FolderItemConfiguratorTest {

	@Rule
	public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

	@Test
	@ConfiguredWithCode("create-folder.yaml")
	public void shouldCreateNewFolder() {
		Folder folder = (Folder) Jenkins.get().getItem("team-alpha");

		assertNotNull("Folder should have been created by JCasC", folder);
		assertEquals("Team Alpha", folder.getDisplayName());
		assertEquals("Description for Team Alpha", folder.getDescription());
	}

	@Test
	public void shouldUpdateExistingFolder() throws Exception {
		Folder existingFolder = j.jenkins.createProject(Folder.class, "team-beta");
		existingFolder.setDescription("Old Description");
		existingFolder.setDisplayName("Old Display Name");

		ConfigurationAsCode.get().configure(
			Objects.requireNonNull(getClass().getResource("update-folder.yaml")).toExternalForm()
		);

		Folder updatedFolder = (Folder) Jenkins.get().getItem("team-beta");

		assertNotNull(updatedFolder);
		assertEquals("Team Beta Updated", updatedFolder.getDisplayName());
		assertEquals("New Description via JCasC", updatedFolder.getDescription());
	}
}
