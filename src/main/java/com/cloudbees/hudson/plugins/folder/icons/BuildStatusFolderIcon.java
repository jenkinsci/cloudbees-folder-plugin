package com.cloudbees.hudson.plugins.folder.icons;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import hudson.Extension;
import hudson.model.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

/**
 * Created by dune on 09/09/15.
 */

    public class BuildStatusFolderIcon extends FolderIcon {

        Folder folder;

        @DataBoundConstructor
        public BuildStatusFolderIcon() {
        }

        public Integer ordinal() {
          return this.getCombinedBallColor().ordinal();
        }

        private BallColor getCombinedBallColor() {
            Boolean taskIsBuilding = false;
            Boolean oneTaskIsIncomplete = false;
            Result combinedResult = null;
            for(Job job : this.folder.getAllJobs()) {
                Run build = job.getLastBuild();
                if(build == null) {
                    oneTaskIsIncomplete = true;
                    continue;
                }

                if(build.isBuilding()) {
                    taskIsBuilding = true;
                    build = job.getLastCompletedBuild();
                    if(build == null) continue;
                }
                Result result = build.getResult();
                if(combinedResult == null) {
                    combinedResult = result;
                } else {
                    combinedResult = combinedResult.combine(result);
                }
            }

            BallColor cumulativeIcon = null;
            if(oneTaskIsIncomplete) {
                cumulativeIcon = BallColor.NOTBUILT;
            } else if(combinedResult != null) {
                cumulativeIcon = combinedResult.color;
            }
            if(taskIsBuilding) {
                cumulativeIcon = cumulativeIcon.anime();
            }
            return cumulativeIcon;
        }

        public String getImageOf(String size) {
            BallColor cumulativeIcon = this.getCombinedBallColor();
            return Stapler.getCurrentRequest().getContextPath()+ Hudson.RESOURCE_PATH+"/images/"+size+ "/" + cumulativeIcon.getImage();
        }

        @Override
        protected void setFolder(Folder folder) {
            this.folder = folder;
        }

        public String getDescription() {
            return "Folder";
        }

        @Extension(ordinal=100)
        public static class DescriptorImpl extends FolderIconDescriptor {
            @Override
            public String getDisplayName() {
                return "Build Status Icon";
            }
        }
    }
