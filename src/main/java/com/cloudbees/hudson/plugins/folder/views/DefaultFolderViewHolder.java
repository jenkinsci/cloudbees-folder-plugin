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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AllView;
import hudson.model.View;
import hudson.views.ViewsTabBar;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The default implementation of {@link AbstractFolderViewHolder} which allows all the elements to be modified.
 */
public class DefaultFolderViewHolder extends AbstractFolderViewHolder {

    /**
     * The views.
     */
    @NonNull
    private /*almost final*/ CopyOnWriteArrayList<View> views;
    /**
     * The primary view's {@link View#getViewName()}.
     */
    @CheckForNull
    private String primaryView;
    /**
     * The {@link ViewsTabBar}.
     */
    @NonNull
    private ViewsTabBar tabBar;

    /**
     * Our constructor.
     * @param views the initial views.
     * @param primaryView the initial primary view.
     * @param tabBar the initial {@link ViewsTabBar}.
     */
    @DataBoundConstructor
    public DefaultFolderViewHolder(@NonNull Collection<? extends View> views,
                                   @CheckForNull String primaryView, @NonNull ViewsTabBar tabBar) {
        if (views.isEmpty()) {
            throw new IllegalArgumentException("Initial views cannot be empty");
        }
        this.views = new CopyOnWriteArrayList<>(views);
        this.primaryView = primaryView;
        this.tabBar = tabBar;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<View> getViews() {
        return views;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setViews(List<? extends View> views) {
        if (views.isEmpty()) {
            throw new IllegalArgumentException("Views cannot be empty");
        }
        this.views = new CopyOnWriteArrayList<>(views);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPrimaryView() {
        return primaryView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPrimaryView(String primaryView) {
        this.primaryView = primaryView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ViewsTabBar getTabBar() {
        return tabBar;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTabBar(ViewsTabBar tabBar) {
        this.tabBar = tabBar;
    }

    private Object readResolve() throws ObjectStreamException {
        if (primaryView != null) {
            primaryView = AllView.migrateLegacyPrimaryAllViewLocalizedName(views, primaryView);
        }
        return this;
    }

}
