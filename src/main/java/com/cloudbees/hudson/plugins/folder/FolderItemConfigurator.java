package com.cloudbees.hudson.plugins.folder;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.ItemConfigurator;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import java.io.IOException;
import java.util.Set;
import jenkins.model.Jenkins;

@Extension
public class FolderItemConfigurator extends BaseConfigurator<Folder> implements ItemConfigurator<Folder> {

    @Override
    @NonNull
    public String getName() {
        return "folder";
    }

    @Override
    public Class<Folder> getTarget() {
        return Folder.class;
    }

    @Override
    protected Folder instance(Mapping mapping, ConfigurationContext context) throws ConfiguratorException {
        throw new UnsupportedOperationException("Folders must be created with an explicit name");
    }

    @Override
    public Folder configure(String name, CNode config, ConfigurationContext context) throws ConfiguratorException {
        try {
            Jenkins jenkins = Jenkins.get();
            Item existing = jenkins.getItem(name);
            Folder folder;

            if (existing == null) {
                folder = jenkins.createProject(Folder.class, name);
            } else if (existing instanceof Folder existingFolder) {
                folder = existingFolder;
            } else {
                throw new ConfiguratorException("Item '" + name + "' already exists and is not a folder");
            }

            Mapping mapping = config != null ? config.asMapping() : Mapping.EMPTY;

            Set<String> supportedAttributes = Set.of("description", "displayName");

            for (String key : mapping.keySet()) {
                if (!supportedAttributes.contains(key)) {
                    throw new ConfiguratorException("Unsupported attribute '" + key + "' for folder '" + name
                            + "'. Supported attributes are: description, displayName");
                }
            }

            if (mapping.containsKey("description")) {
                CNode descNode = mapping.get("description");
                folder.setDescription(descNode != null ? descNode.asScalar().getValue() : null);
            }

            if (mapping.containsKey("displayName")) {
                CNode nameNode = mapping.get("displayName");
                folder.setDisplayName(nameNode != null ? nameNode.asScalar().getValue() : null);
            }

            folder.save();
            return folder;

        } catch (ConfiguratorException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ConfiguratorException("Failed to configure folder: " + name, e);
        }
    }
}
