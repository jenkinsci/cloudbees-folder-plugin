package com.cloudbees.hudson.plugins.folder.relocate;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class MoveCommandTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    private CLICommand moveCommand;
    private CLICommandInvoker moveCommandInvoker;
    Jenkins jenkins;

    @Before
    public void setUp() throws Exception {
        jenkins = rule.jenkins;
        moveCommand = new MoveCommand();
        moveCommandInvoker = new CLICommandInvoker(rule, moveCommand);
    }

    @Test
    public void moveInsideNewFolder() throws Exception {
        Folder folder1 = jenkins.createProject(Folder.class, "folder1");
        Folder folder2 = jenkins.createProject(Folder.class, "folder2");

        FreeStyleProject project1 = folder1.createProject(FreeStyleProject.class, "project1");

        CLICommandInvoker.Result result = moveCommandInvoker.invokeWithArgs("folder1/project1", "folder2/project1");
        assertThat(result, succeededSilently());
        assertNotNull("folder2/project1 should have been created.", jenkins.getItem("folder2/project1"));
        assertNotNull("Folder1 should still exist.", jenkins.getItem("folder1"));
        assertNull("Project folder1/project1 should have been deleted.", jenkins.getItem("folder1/project1"));
    }
}