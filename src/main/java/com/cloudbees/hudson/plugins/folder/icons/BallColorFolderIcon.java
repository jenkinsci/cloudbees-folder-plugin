/*
 * The MIT License
 *
 * Copyright 2015 Matthew DeTullio
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
package com.cloudbees.hudson.plugins.folder.icons;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import hudson.Extension;
import hudson.model.BallColor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link FolderIcon} that actually shows a {@link BallColor}, as determined by {@link AbstractFolder#getBallColor()}.
 *
 * @author Matthew DeTullio
 * @since TODO
 */
@SuppressWarnings("unused")
public class BallColorFolderIcon extends FolderIcon {
    private static final String UNUSED = "unused";

    private AbstractFolder<?> owner;

    /**
     * No-op constructor used only for data binding.
     */
    @SuppressWarnings(UNUSED)
    @DataBoundConstructor
    public BallColorFolderIcon() {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOwner(AbstractFolder<?> owner) {
        this.owner = owner;
    }

    /**
     * Delegates the image to the {@link #owner}'s {@link BallColor}.
     * <br>
     * {@inheritDoc}
     */
    @Override
    public String getImageOf(String size) {
        if (owner == null) {
            return BallColor.GREY.getImageOf(size);
        }

        return owner.getBallColor().getImageOf(size);
    }

    /**
     * Delegates the description to the {@link #owner}'s {@link BallColor}.
     * <br>
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        if (owner == null) {
            return BallColor.GREY.getDescription();
        }

        return owner.getBallColor().getDescription();
    }

    /**
     * Registers a descriptor to appear in the "Icon" dropdown on the configuration page.
     */
    @SuppressWarnings(UNUSED)
    @Extension
    public static class DescriptorImpl extends FolderIconDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Ball Color Icon";
        }
    }
}
