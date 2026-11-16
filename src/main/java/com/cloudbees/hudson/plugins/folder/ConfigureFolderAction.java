/*
 * The MIT License
 *
 * Copyright (c) 2026, Jan Faracik
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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlag;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * App bar action that links to the configuration page for an {@link AbstractFolder}.
 *
 * <p>The display name is adjusted depending on whether the user has {@link Item#CONFIGURE}
 * or only {@link Item#EXTENDED_READ}.
 */
@Restricted(NoExternalUse.class)
public final class ConfigureFolderAction implements Action {

    private final AbstractFolder<?> target;

    ConfigureFolderAction(AbstractFolder<?> target) {
        this.target = target;
    }

    @Override
    public String getDisplayName() {
        return target.hasPermission(Item.CONFIGURE)
                ? Messages.ConfigureFolderAction_Title()
                : Messages.ConfigureFolderAction_ViewConfiguration();
    }

    @Override
    public String getIconFileName() {
        return "symbol-settings";
    }

    @Override
    public Group getGroup() {
        return Group.IN_APP_BAR;
    }

    @Override
    public String getUrlName() {
        return "configure";
    }

    @Extension
    @Restricted(NoExternalUse.class)
    public static final class Factory extends TransientActionFactory<AbstractFolder> {

        @Override
        public Class<AbstractFolder> type() {
            return AbstractFolder.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractFolder target) {
            // This condition can be removed when the flag has been removed
            if (!Boolean.TRUE.equals(UserExperimentalFlag.getFlagValueForCurrentUser(
                    "jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag"))) {
                return Set.of();
            }

            if (!target.hasPermission(Item.CONFIGURE) && !target.hasPermission(Item.EXTENDED_READ)) {
                return Set.of();
            }

            return Set.of(new ConfigureFolderAction(target));
        }
    }
}
