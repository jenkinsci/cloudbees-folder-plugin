package com.cloudbees.hudson.plugins.folder;

import hudson.model.Action;
import hudson.model.Job;
import java.io.IOException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class SaveAllJobsAction implements Action {

    private final Folder folder;

    public SaveAllJobsAction(Folder folder) {
        this.folder = folder;
    }

    @Override
    public String getIconFileName() {
        return "save.png"; // small save icon
    }

    @Override
    public String getDisplayName() {
        return "Save All Jobs";
    }

    @Override
    public String getUrlName() {
        return "save-all-jobs";
    }

    // Called when the form is submitted
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doSaveAll();
        rsp.sendRedirect2("../"); // redirect back to folder page after saving
    }

    // Actual saving logic
    public void doSaveAll() throws IOException {
        for (Job<?, ?> job : folder.getAllJobs()) {
            job.save();
        }
    }
}
