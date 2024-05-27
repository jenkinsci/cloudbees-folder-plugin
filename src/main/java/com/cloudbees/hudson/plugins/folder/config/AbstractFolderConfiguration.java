package com.cloudbees.hudson.plugins.folder.config;

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.security.Permission;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension @Symbol("defaultFolderConfiguration")
public class AbstractFolderConfiguration extends GlobalConfiguration {

    private List<FolderHealthMetric> healthMetrics;


    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.MANAGE;
    }

    @NonNull
    public static AbstractFolderConfiguration get() {
        AbstractFolderConfiguration instance = GlobalConfiguration.all().get(AbstractFolderConfiguration.class);
        if (instance == null) {
            throw new IllegalStateException();
        }
        return instance;
    }

    private static boolean getHealthMetricsProperty() {
        return Boolean.getBoolean(AbstractFolderConfiguration.class.getName() + ".ADD_HEALTH_METRICS");
    }

    @DataBoundConstructor
    public AbstractFolderConfiguration() {
        this.load();
    }

    /**
     * Auto-configure the default metrics after all plugins have been loaded.
     */
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void autoConfigure() {
        // Don't add health metrics by default in autoConfigure as per JENKINS-58282 & JENKINS-63836
        if (getHealthMetricsProperty()) {
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
    }

    @NonNull
    public List<FolderHealthMetric> getHealthMetrics() {
        return healthMetrics == null ? Collections.emptyList() : healthMetrics;
    }

    @DataBoundSetter
    public void setHealthMetrics(List<FolderHealthMetric> healthMetrics) {
        this.healthMetrics = healthMetrics;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        if(json.containsKey("healthMetrics")) {
            req.bindJSON(this, json);
            this.save();
        } else {
            this.setHealthMetrics(Collections.emptyList());
        }
        return true;
    }
}
