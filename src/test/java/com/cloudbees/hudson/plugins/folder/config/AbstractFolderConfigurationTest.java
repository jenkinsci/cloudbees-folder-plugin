package com.cloudbees.hudson.plugins.folder.config;

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import hudson.ExtensionFinder;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;

public class AbstractFolderConfigurationTest {

    private static final String GUICE_INITIALIZATION_MESSAGE =
            "Failed to instantiate Key[type=" + AbstractFolderConfiguration.class.getName() + ", annotation=[none]];";
    
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ExtensionFinder.GuiceFinder.class, Level.INFO).capture(100);

    @Test
    @Issue("JENKINS-60393")
    public void testInitialization() {
        assertThat("AbstractFolderConfiguration should not cause circular dependency on startup",
                logging.getRecords().stream()
                        .filter(lr -> lr.getLevel().intValue() == Level.WARNING.intValue())
                        .filter(lr -> lr.getMessage().contains(GUICE_INITIALIZATION_MESSAGE))
                        .map(lr -> lr.getSourceClassName() + "." + lr.getSourceMethodName() + ": " + lr.getMessage())
                        .collect(Collectors.toList()),
                emptyIterable());
    }

    @Test
    public void healthMetricsAppearsInConfiguredGlobally() throws Exception {
        HtmlForm cfg = r.createWebClient().goTo("configure").getFormByName("config");
        assertThat("adding metrics from Global Configuration",
                AbstractFolderConfiguration.get().getHealthMetrics(), hasSize(cfg.getElementsByAttribute("div", "suffix", "healthMetrics").size()));
    }

    @Issue("JENKINS-60393")
    @Test
    public void shouldBeAbleToRemoveHealthMetricConfiguredGlobally() throws Exception {
        List<FolderHealthMetric> healthMetrics = new ArrayList<>(1);
        healthMetrics.add(new WorstChildHealthMetric(true));
        AbstractFolderConfiguration.get().setHealthMetrics(healthMetrics);
        HtmlForm cfg = r.createWebClient().goTo("configure").getFormByName("config");
        for (HtmlElement element : cfg.getElementsByAttribute("div", "name", "healthMetrics")) {
            element.remove();
        }
        r.submit(cfg);

        assertThat("deleting all global metrics should result in an empty list",
                AbstractFolderConfiguration.get().getHealthMetrics(), hasSize(0));
    }

    /**
     * A initializer that can produce circular dependency if AbstractFolderConfiguration is not properly initialized 
     * on startup.
     */
    public static class TestInitialization {

        @Initializer(before = InitMilestone.JOB_LOADED, after = InitMilestone.PLUGINS_STARTED)
        @SuppressWarnings("unused")
        public static void init(Jenkins jenkins) {
            AbstractFolderConfiguration.get();
        }

        @Initializer(after = InitMilestone.PLUGINS_STARTED)
        @SuppressWarnings("unused")
        public static void init2(Jenkins jenkins) {
            AbstractFolderConfiguration.get();
        }
    }
}
