package com.cloudbees.hudson.plugins.folder;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import hudson.XmlFile;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ChildLoader implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(ChildLoader.class.getName());

    /**
     * Loads all the child {@link Item}s.
     *
     * @param parent     the parent of the children.
     * @param modulesDir Directory that contains sub-directories for each child item.
     * @param key        the key generating function.
     * @param <K>        the key type
     * @param <V>        the child type.
     * @return a map of the children keyed by the generated keys.
     */
    protected abstract <K, V extends TopLevelItem> Map<K, V> loadChildren(
            AbstractFolder<V> parent, File modulesDir, Function<? super V, ? extends K> key);

    /**
     * Ensure that the specified directory exists. If the directory does not exist, attempt to create it.
     *
     * @param modulesDir the directory that should exist or needs to be created
     * @param parent parent folder associated with the directory
     * @return {@code true} if the directory exists or was successfully created; {@code false} otherwise
     */
    protected <V extends TopLevelItem> boolean ensureDirExists(File modulesDir, AbstractFolder<V> parent) {
        if (!modulesDir.isDirectory() && !modulesDir.mkdirs()) { // make sure it exists
            LOGGER.log(Level.SEVERE, "Could not create {0} for folder {1}",
                    new Object[]{modulesDir, parent.getFullName()});
            return false;
        }
        return true;
    }

    /**
     * Retrieve a map of items contained within a specified parent folder, keyed by their directory names.
     *
     * @param parent the parent folder containing the items
     * @return a map where the keys are the directory names of the items and the values are the items themselves
     */
    protected <V extends TopLevelItem> Map<String, V> getItemsByDirName(AbstractFolder<V> parent) {
        Map<String, V> byDirName = new HashMap<>();
        if (parent.items != null) {
            final ChildNameGenerator<AbstractFolder<V>, V> childNameGenerator = parent.childNameGenerator();
            for (V item : parent.items.values()) {
                byDirName.put(childNameGenerator.dirName(parent, item), item);
            }
        }
        return byDirName;
    }

    /**
     * Load a {@link TopLevelItem} from a given directory. The method attempts to read the item configuration, assign an
     * appropriate name to the item, and initialize it within the provided parent folder.
     *
     * @param parent the parent folder that will contain the loaded item
     * @param subdir the directory from which the item configuration can potentially be loaded
     * @param item an optional pre-existing item that can be reused; if null, the item will be loaded from the directory
     * @return the loaded item if successful, or {@code null} if an error occurs during loading
     */
    @CheckForNull
    public <V extends TopLevelItem> V loadItem(AbstractFolder<V> parent, File subdir, @CheckForNull V item) {
        try {
            if (item == null) {
                XmlFile xmlFile = Items.getConfigFile(subdir);
                if (xmlFile.exists()) {
                    item = (V) xmlFile.read();
                } else {
                    throw new FileNotFoundException("Could not find configuration file " + xmlFile.getFile());
                }
            }
            String name = parent.childNameGenerator().itemNameFromItem(parent, item);
            if (name == null) {
                name = subdir.getName();
            }
            item.onLoad(parent, name);
            return item;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "could not load " + subdir, e);
            return null;
        }
    }
}
