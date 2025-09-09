package com.cloudbees.hudson.plugins.folder;

import com.cloudbees.hudson.plugins.folder.config.AbstractFolderConfiguration;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

@WithJenkins
class ConfigurationAsCodeTest extends AbstractRoundTripTest {

    @Override
    protected String stringInLogExpected() {
        return "Setting class com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric.recursive = false";
    }

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule rule, String s) {
        List<FolderHealthMetric> healthMetrics = AbstractFolderConfiguration.get().getHealthMetrics();
        assertThat(healthMetrics, hasSize(1));
        assertThat(healthMetrics.get(0), instanceOf(WorstChildHealthMetric.class));
        WorstChildHealthMetric worstChildHealthMetric = (WorstChildHealthMetric) healthMetrics.get(0);
        assertFalse(worstChildHealthMetric.isRecursive());
    }
}
