/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JobProperty;
import hudson.model.TopLevelItem;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.TransientActionFactory;

/**
 * Provides a way for a {@link ComputedFolder} to break the association between the directory names on disk
 * that are used to store its items and the {@link Item#getName()} which is used to create the URL of the item.
 * <p>
 * <strong>NOTE:</strong> if you need to implement this functionality, you need to ensure that users cannot rename
 * items within the {@link ComputedFolder} as renaming is not supported when using a {@link ChildNameGenerator}.
 * <p>
 * Challenges:
 * <ul>
 * <li>See the notes on {@link #itemNameFromItem(AbstractFolder, TopLevelItem)} and
 * {@link #dirNameFromItem(AbstractFolder, TopLevelItem)} regarding the constraints on how to name things</li>
 * <li>There are some items which need the {@link Item#getRootDir()} during construction (those are bold evil item types
 * that leak side-effects, you should fix them if you find them). While you wait for them to be fixed you will need
 * to work-arround the issue by ensuring that you call {@link #beforeCreateItem(AbstractFolder, String, String)}
 * passing the {@link Item#getName()} you want the item to have <strong>and</strong> the ideal unmangled name
 * <strong>before</strong> you call {@code new ChildItemType(parent,name)} and then call
 * {@link #afterItemCreated(Trace)} when the constructor has returned. Then insure that your
 * {@link #itemNameFromItem(AbstractFolder, TopLevelItem)} and {@link #dirNameFromItem(AbstractFolder, TopLevelItem)}
 * fall back to {@link #idealNameFromItem(AbstractFolder, TopLevelItem)} when the magic property they are looking
 * for is missing.</li>
 * </ul>
 *
 * For a valid implementation, the
 * {@link ComputedFolder} using this {@link ChildNameGenerator} will be attaching into the {@link Item} the
 * actual name, typically via a {@link JobProperty} or {@link Action} (beware {@link TransientActionFactory}
 * implementations may want to invoke {@link Item#getRootDir()} which will trigger a stack overflow though, so
 * safer to stick with the {@link JobProperty} or equivalent). The
 * {@link #itemNameFromItem(AbstractFolder, TopLevelItem)} method's task is to find the stored name
 * and return the name stored within or {@code null} if that information is missing (in which case
 * {@link #itemNameFromLegacy(AbstractFolder, String)} will be called to try and infer the name from the
 * disk name that the {@link Item} is being loaded from.
 * A similar relation exists for the {@link #dirNameFromItem(AbstractFolder, TopLevelItem)} and
 * {@link #dirNameFromLegacy(AbstractFolder, String)} methods.
 *
 * @param <P> the type of {@link AbstractFolder}.
 * @param <I> the type of {@link TopLevelItem} within the folder.
 * @since 5.17
 */
// TODO migrate this functionality (by changing the base class) into core once baseline Jenkins has JENKINS-41222 merged
public abstract class ChildNameGenerator<P extends AbstractFolder<I>, I extends TopLevelItem> {
    /**
     * The name of the file that contains the actual name of the child item. This file is to allow a Jenkins
     * Administrator to determine which child is which when dealing with a folder containing child names that have
     * been mangled.
     * <p>
     * If there is nothing else to go on, this file will be used in preference to the child directory name, but as it
     * is too easy for users to mistakenly think changing the contents of the file will rename the child (which could
     * cause data loss for the computed folder's child) it is better for implementations to store the definitive
     * ideal name in a {@link JobProperty}, {@link Action} or equivalent that is attached directly to the {@link Item}.
     */
    public static final String CHILD_NAME_FILE = "name-utf8.txt";

    private static final Map<Trace,String> idealNames = new WeakHashMap<Trace,String>();

    /**
     * Work-around helper method to "fix" {@link Item} constructors that have on-disk side-effects and therefore
     * need {@link Item#getRootDir()} to work during the constructor.
     * @param project the {@link AbstractFolder}.
     * @param itemName the name that will be returned by {@link Item#getName()} when the item is constructed. This is
     *                 the second parameter of {@link AbstractItem#AbstractItem(ItemGroup, String)}. This one would be
     *                 the one with URL path segment escaping.
     * @param idealName the original name before whatever URL path segment escaping you applied
     * @return the {@link Trace} to keep track of when we can remove the memory of the creation process. Please
     * {@link Trace#close()} the trace after the item is created.
     */
    @Nonnull
    public static final Trace beforeCreateItem(@Nonnull AbstractFolder<?> project,
                                               @Nonnull String itemName,
                                               @Nonnull String idealName) {
        final Trace trace = new Trace(project, itemName);
        synchronized (idealNames) {
            idealNames.put(trace, idealName);
        }
        return trace;
    }

    /**
     * Clean up for a creation {@link Trace}. Not strictly required, but nice implementations will do this via {@link Trace#close()}.
     * @param trace the trace.
     */
    private static final void afterItemCreated(@Nonnull Trace trace) {
        synchronized (idealNames) {
            idealNames.remove(trace);
        }
    }

    /**
     * Looks up the {@link Item} to see if we stored the ideal name before invoking the constructor that is having
     * on-disk side-effects before the object has escaped {@link #beforeCreateItem(AbstractFolder, String, String)}
     * @param parent the parent within which the item is being created.
     * @param item the partially created item.
     * @return the ideal name of the item.
     */
    @CheckForNull
    protected final String idealNameFromItem(@Nonnull P parent, @Nonnull I item) {
        String itemName = item.getName();
        if (itemName == null) {
            return null;
        }
        synchronized (idealNames) {
            return idealNames.get(new Trace(parent, itemName));
        }
    }

    /**
     * Infers the {@link Item#getName()} from the {@link Item} instance itself.
     *
     * Challenges include:
     * <ul>
     * <li>There are some characters that it would be really bad to return in the item name, such as
     * {@code "/" / "?" / "#" / "[" / "]" / "\"} as these could end up modifying the effective URL</li>
     * <li>There are names that it would be bad to return as the item name, such as
     * {@code "" / "." / ".."} as these could end creating broken effective URLs</li>
     * </ul>
     * @param parent the parent within which the item is being loaded.
     * @param item the partially loaded item (take care what methods you call, the item will not have a reference to
     *             its parent).
     * @return the name of the item.
     */
    @CheckForNull
    public abstract String itemNameFromItem(@Nonnull P parent, @Nonnull I item);

    /**
     * Infers the directory name in which the {@link Item} instance itself should be stored.
     *
     * Challenges include:
     * <ul>
     * <li>The only really filesystem safe characters are {@code A-Za-z0-9_.-}</li>
     * <li>Because of Windows and allowing for users to migrate their Jenkins from Unix to Windows and vice-versa,
     * some names are reserved names under Windows:
     * {@code AUX, COM1, COM2, ..., COM9, CON, LPT1, LPT2, ..., LPT9, NUL, PRN} plus all case variations of these
     * names plus the variants where a single {@code .} is appended, you need to map those to something else</li>
     * <li>Don't make the filenames too long. Try to keep them under 32 characters. If you can go smaller, even
     * better.</li>
     * <li>Get it right the first time</li>
     * </ul>
     *
     * @param parent the parent within which the item is being loaded.
     * @param item   the partially loaded item (take care what methods you call, the item will not have a reference to
     *               its parent).
     * @return the filesystem safe mangled equivalent name of the item.
     */
    @CheckForNull
    public abstract String dirNameFromItem(@Nonnull P parent, @Nonnull I item);

    /**
     * {@link #itemNameFromItem(AbstractFolder, TopLevelItem)} could not help, we are loading the item for the first
     * time since the {@link ChildNameGenerator} was enabled for the parent folder type, this method's mission is
     * to pretend the {@code legacyDirName} is the "mostly correct" name and turn this into the actual name.
     *
     * Challenges include:
     * <ul>
     * <li>Previously the name may have been over-encoded with {@link Util#rawEncode(String)} so you may need to
     * decode it first</li>
     * <li>There are some characters that it would be really bad to return in the item name, such as
     * {@code "/" / "?" / "#" / "[" / "]" / "\"} as these could end up modifying the effective URL</li>
     * <li>There are names that it would be bad to return as the item name, such as
     * {@code "" / "." / ".."} as these could end creating broken effective URLs</li>
     * </ul>
     * @param parent the parent within which the item is being loaded.
     * @param legacyDirName the directory name that we are loading an item from.
     * @return the name of the item.
     */
    @Nonnull
    public abstract String itemNameFromLegacy(@Nonnull P parent, @Nonnull String legacyDirName);

    /**
     * {@link #dirNameFromItem(AbstractFolder, TopLevelItem)} could not help, we are loading the item for the first
     * time since the {@link ChildNameGenerator} was enabled for the parent folder type, this method's mission is
     * to pretend the {@code legacyDirName} is the "mostly correct" name and turn this into the filesystem safe
     * mangled equivalent name to use going forward.
     *
     * Challenges include:
     * <ul>
     * <li>The only really filesystem safe characters are {@code A-Za-z0-9_.-}</li>
     * <li>Because of Windows and allowing for users to migrate their Jenkins from Unix to Windows and vice-versa,
     * some names are reserved names under Windows:
     * {@code AUX, COM1, COM2, ..., COM9, CON, LPT1, LPT2, ..., LPT9, NUL, PRN} plus all case variations of these
     * names plus the variants where a single {@code .} is appended, you need to map those to something else</li>
     * <li>Don't make the filenames too long. Try to keep them under 32 characters. If you can go smaller, even
     * better.</li>
     * <li>Get it right the first time</li>
     * </ul>
     *
     * @param parent        the parent within which the item is being loaded.
     * @param legacyDirName the directory name that we are loading an item from.
     * @return the filesystem safe mangled equivalent name of the item.
     */
    @Nonnull
    public abstract String dirNameFromLegacy(@Nonnull P parent, @Nonnull String legacyDirName);

    /**
     * Record the ideal name inferred in the item when it was missing and has been inferred from the legacy directory
     * name.
     *
     * @param parent the parent.
     * @param item the item.
     * @param legacyDirName the name of the directory that the item was loaded from.
     * @throws IOException if the ideal name could not be attached to the item.
     */
    public abstract void recordLegacyName(P parent, I item, String legacyDirName) throws IOException;

    /**
     * Traces the creation of a new {@link Item} in a folder. Use
     * {@link ChildNameGenerator#beforeCreateItem(AbstractFolder, String, String)} to get the instance.
     */
    public static final class Trace implements Closeable {
        /**
         * The folder.
         */
        @Nonnull
        private final AbstractFolder<?> folder;
        /**
         * The {@link Item#getName()} that we expect to be created in the very near future.
         */
        @Nonnull
        private final String itemName;

        /**
         * Constructor.
         * @param folder the folder.
         * @param itemName the item name
         */
        private Trace(@Nonnull AbstractFolder<?> folder, @Nonnull String itemName) {
            this.folder = folder;
            this.itemName = itemName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Trace that = (Trace) o;

            return folder == that.folder && itemName.equals(that.itemName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = folder.hashCode();
            result = 31 * result + itemName.hashCode();
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            afterItemCreated(this);
        }
    }
}
