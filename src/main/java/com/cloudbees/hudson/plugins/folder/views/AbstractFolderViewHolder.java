/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package com.cloudbees.hudson.plugins.folder.views;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.View;
import hudson.views.ViewsTabBar;
import java.util.List;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Responsible for holding the view configuration of an {@link AbstractFolder}. Each {@link AbstractFolder} concrete
 * type should define its view configuration holder by returning the implementaion from
 * {@link AbstractFolder#newFolderViewHolder()}
 *
 * Use-cases:
 * <ul>
 * <li>
 * Where the {@link AbstractFolder} permits the views to be configured by the user, use a
 * {@link DefaultFolderViewHolder}
 * </li>
 * <li>
 * Where the {@link AbstractFolder} has a fixed set of pre-configured views, the plugin can provide
 * a custom implementation that returns the fixed set of views.
 * </li>
 * </ul>
 *
 * @since FIXME
 */
public abstract class AbstractFolderViewHolder {
    /**
     * Returns the list of views. If {@link #isViewsModifiable()} then this list is modifiable.
     *
     * @return the list of views.
     */
    @NonNull
    public abstract List<View> getViews();

    /**
     * Changes the list of {@link View}s. May be a no-op if {@link #isViewsModifiable()} returns {@code false}.
     *
     * @param views the new list of {@link View}s.
     * @see #isViewsModifiable()
     */
    public abstract void setViews(@NonNull List<? extends View> views);

    /**
     * Returns {@code true} if the list of views is modifiable.
     *
     * @return {@code true} if the list of views is modifiable.
     */
    public boolean isViewsModifiable() {
        return true;
    }

    /**
     * Returns the {@link View#getViewName()} of the primary view or {@code null} if the first view should be primary.
     *
     * @return the {@link View#getViewName()} of the primary view or {@code null} if the first view should be primary.
     */
    @CheckForNull
    public abstract String getPrimaryView();

    /**
     * Changes the primary {@link View}. May be a no-op if {@link #isPrimaryModifiable()} returns {@code false}.
     *
     * @param name the {@link View#getViewName()} of the primary {@link View} of {@code null} to use the first view.
     * @see #isPrimaryModifiable()
     */
    public abstract void setPrimaryView(@CheckForNull String name);

    /**
     * Returns {@code true} if the primary {@link View} is modifiable.
     *
     * @return {@code true} if the primary {@link View} is modifiable.
     */
    public boolean isPrimaryModifiable() {
        return true;
    }

    /**
     * Returns the {@link ViewsTabBar}.
     *
     * @return the {@link ViewsTabBar}.
     */
    @NonNull
    public abstract ViewsTabBar getTabBar();

    /**
     * Changes the {@link ViewsTabBar}. May be a no-op if {@link #isTabBarModifiable()} returns {@code false}.
     *
     * @param tabBar the new {@link ViewsTabBar}.
     * @see #isTabBarModifiable()
     */
    public abstract void setTabBar(@NonNull ViewsTabBar tabBar);

    /**
     * Returns {@code true} if the {@link ViewsTabBar} is modifiable.
     *
     * @return {@code true} if the {@link ViewsTabBar} is modifiable.
     */
    public boolean isTabBarModifiable() {
        return true;
    }

    /**
     * Called by {@link AbstractFolder#save()} to signal that the view holder should clear any internal state caches.
     */
    public void invalidateCaches() {}
}
