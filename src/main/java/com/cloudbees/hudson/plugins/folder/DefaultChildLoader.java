package com.cloudbees.hudson.plugins.folder;

import hudson.Extension;
import hudson.model.TopLevelItem;
import hudson.util.CopyOnWriteMap;
import java.io.File;
import java.util.Map;
import java.util.function.Function;

@Extension
public final class DefaultChildLoader extends ChildLoader {

    @Override
    public <K, V extends TopLevelItem> Map<K, V> loadChildren(
            AbstractFolder<V> parent, File modulesDir, Function<? super V, ? extends K> key) {
        CopyOnWriteMap.Tree<K, V> configurations = new CopyOnWriteMap.Tree<>();
        if (!ensureDirExists(modulesDir, parent)) {
            return configurations;
        }

        File[] subdirs = modulesDir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return configurations;
        }

        Map<String, V> byDirName = getItemsByDirName(parent);
        for (File subdir : subdirs) {
            // Try to retain the identity of an existing child object if we can.
            V existingItem = byDirName.get(subdir.getName());
            V item = loadItem(parent, subdir, existingItem);
            if (item != null) {
                configurations.put(key.apply(item), item);
            }
        }

        return configurations;
    }
}
