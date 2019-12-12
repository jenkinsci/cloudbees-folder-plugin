package com.cloudbees.hudson.plugins.folder.config;

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension @Symbol("defaultFolderConfiguration")
public class AbstractFolderConfiguration extends GlobalConfiguration {

    private List<FolderHealthMetric> healthMetrics;

    @Nonnull
    public static AbstractFolderConfiguration get() {
        AbstractFolderConfiguration instance = GlobalConfiguration.all().get(AbstractFolderConfiguration.class);
        if (instance == null) {
            throw new IllegalStateException();
        }
        return instance;
    }

    @DataBoundConstructor
    public AbstractFolderConfiguration() {
        this.load();
    }

    /**
     * Auto-configure the default metrics afetr all plugins have been loaded.
     */
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void autoConfigure() {
        AbstractFolderConfiguration abstractFolderConfiguration = AbstractFolderConfiguration.get();
        if (abstractFolderConfiguration.healthMetrics == null) {
            List<FolderHealthMetric> metrics = new ArrayList<>();
            for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
                FolderHealthMetric metric = d.createDefault();
                if (metric != null) {
                    metrics.add(metric);
                }
            }
            abstractFolderConfiguration.setHealthMetrics(new DescribableList<FolderHealthMetric,
                    FolderHealthMetricDescriptor>(abstractFolderConfiguration, metrics));
        }
    }

    @Nonnull
    public List<FolderHealthMetric> getHealthMetrics() {
        return healthMetrics == null ? Collections.emptyList() : healthMetrics;
    }

    @DataBoundSetter
    public void setHealthMetrics(List<FolderHealthMetric> healthMetrics) {
        this.healthMetrics = healthMetrics;
        save();
    }
}