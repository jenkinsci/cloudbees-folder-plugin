package com.cloudbees.hudson.plugins.folder;

import hudson.Extension;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.ItemConfigurator;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import jenkins.model.Jenkins;
import java.io.IOException;

@Extension
public class FolderItemConfigurator implements ItemConfigurator<Folder> {

	@Override
	public String getName() {
		return "folder";
	}

	@Override
	public Class<Folder> getTarget() {
		return Folder.class;
	}

	@Override
	public Folder configure(String name, CNode config, ConfigurationContext context) throws ConfiguratorException {
		try {
			Jenkins jenkins = Jenkins.get();
			Folder folder = (Folder) jenkins.getItem(name);

			if (folder == null) {
				folder = jenkins.createProject(Folder.class, name);
			}

			Mapping mapping = config.asMapping();

			if (mapping.containsKey("description")) {
				folder.setDescription(mapping.getScalarValue("description"));
			}

			if (mapping.containsKey("displayName")) {
				folder.setDisplayName(mapping.getScalarValue("displayName"));
			}

			folder.save();
			return folder;

		} catch (IOException e) {
			throw new ConfiguratorException("Failed to configure folder: " + name, e);
		}
	}
}
