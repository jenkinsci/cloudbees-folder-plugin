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
import jenkins.model.menu.Semantic;
import jenkins.model.menu.action.DeleteAction;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * App bar action that deletes an {@link AbstractFolder} after user confirmation.
 *
 * <p>Wraps {@link DeleteAction}, only reading the folder's pronoun and display name when rendered,
 * as some folders, e.g. multibranch projects, look up their actions to compute their display name.
 */
@Restricted(NoExternalUse.class)
public final class DeleteFolderAction implements Action {

    private final AbstractFolder<?> target;

    DeleteFolderAction(AbstractFolder<?> target) {
        this.target = target;
    }

    private DeleteAction delegate() {
        return new DeleteAction(target.getPronoun(), target.getDisplayName());
    }

    @Override
    public String getDisplayName() {
        return delegate().getDisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-trash";
    }

    @Override
    public Group getGroup() {
        return Group.LAST_IN_MENU;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Event getEvent() {
        return delegate().getEvent();
    }

    @Override
    public Semantic getSemantic() {
        return Semantic.DESTRUCTIVE;
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

            if (!target.hasPermission(Item.DELETE)) {
                return Set.of();
            }

            return Set.of(new DeleteFolderAction(target));
        }
    }
}
