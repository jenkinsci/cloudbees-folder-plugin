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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.TopLevelItem;
import java.util.logging.Logger;
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
public abstract class ChildNameGenerator<P extends AbstractFolder<I>, I extends TopLevelItem> {
    private static final Logger LOGGER = Logger.getLogger(ChildNameGenerator.class.getName());

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
    public abstract String itemNameFromItem(@NonNull P parent, @NonNull I item);

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
    public abstract String dirNameFromItem(@NonNull P parent, @NonNull I item);

    @NonNull
    final String dirName(@NonNull P parent, @NonNull I item) {
        var name = dirNameFromItem(parent, item);
        if (name == null) {
            name = dirNameFromLegacy(parent, item.getName());
        }
        return name;
    }

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
    @NonNull
    public abstract String itemNameFromLegacy(@NonNull P parent, @NonNull String legacyDirName);

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
    @NonNull
    public abstract String dirNameFromLegacy(@NonNull P parent, @NonNull String legacyDirName);
}
