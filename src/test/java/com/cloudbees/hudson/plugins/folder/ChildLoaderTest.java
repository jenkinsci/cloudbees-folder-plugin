/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.hudson.plugins.folder;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.nio.file.Files;
import java.time.Duration;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.FlagExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

final class ChildLoaderTest {

    @RegisterExtension private final JenkinsSessionExtension js = new JenkinsSessionExtension();

    @RegisterExtension private static final FlagExtension<String> gracePeriodFlag = FlagExtension.systemProperty(ChildLoader.GRACE_PERIOD_PROP);

    @Test void brokenChildren() throws Throwable {
        js.then(r -> {
            var bot = r.createProject(Folder.class, "top").createProject(Folder.class, "mid").createProject(FreeStyleProject.class, "bot");
            r.buildAndAssertStatus(Result.SUCCESS, bot);
            Files.delete(bot.getConfigFile().getFile().toPath());
        });
        js.then(r -> {
            assertThat(r.jenkins.allItems(FreeStyleProject.class), emptyIterable());
            assertThat(Files.exists(r.jenkins.getItemByFullName("top/mid").getRootDir().toPath().resolve("broken-children")), is(false));
        });
        System.setProperty(ChildLoader.GRACE_PERIOD_PROP, "1s");
        Thread.sleep(Duration.ofSeconds(5).toMillis());
        js.then(r -> {
            assertThat(r.jenkins.allItems(FreeStyleProject.class), emptyIterable());
            assertThat(Files.exists(r.jenkins.getItemByFullName("top/mid").getRootDir().toPath().resolve("broken-children/bot")), is(true));
        });
    }

}
