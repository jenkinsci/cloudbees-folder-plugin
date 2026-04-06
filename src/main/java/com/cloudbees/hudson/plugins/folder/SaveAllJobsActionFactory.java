package com.cloudbees.hudson.plugins.folder;

import hudson.Extension;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import java.util.Collection;
import java.util.Collections;

@Extension
public class SaveAllJobsActionFactory extends TransientActionFactory<Folder> {
    
    @Override
    public Class<Folder> type() {
        return Folder.class;
    }

    @Override
    public Collection<? extends Action> createFor(Folder target) {
        return Collections.singletonList(new SaveAllJobsAction(target));
    }
}
