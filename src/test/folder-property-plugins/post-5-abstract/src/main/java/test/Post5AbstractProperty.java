package test;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

public class Post5AbstractProperty extends AbstractFolderProperty<AbstractFolder<?>> {
    
    private final String suffix;

    @DataBoundConstructor
    public Post5AbstractProperty(String suffix) {
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
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Post5AbstractProperty";
        }

    }

}
