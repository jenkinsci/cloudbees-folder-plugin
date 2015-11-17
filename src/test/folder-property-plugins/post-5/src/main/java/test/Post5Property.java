package test;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.FolderProperty;
import com.cloudbees.hudson.plugins.folder.FolderPropertyDescriptor;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

public class Post5Property extends FolderProperty<Folder> {
    
    private final String suffix;

    @DataBoundConstructor
    public Post5Property(String suffix) {
        this.suffix = suffix;
    }
    
    public String getSuffix() {
        return suffix;
    }

    @Override
    public String toString() {
        return getOwner().getName() + suffix;
    }

    @Extension
    public static class DescriptorImpl extends FolderPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Post5Property";
        }

    }

}
