/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;

public class FolderPropertyTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @LocalData
    @WithPlugin("pre-5.hpi")
    @Test public void pre5() throws Exception {
        validate();
    }

    @LocalData
    @WithPlugin("around-5.hpi")
    @Test public void around5() throws Exception {
        validate();
    }

    @LocalData
    @WithPlugin("post-5.hpi")
    @Test public void post5() throws Exception {
        validate();
    }

    private void validate() throws Exception {
        Folder d = r.jenkins.getItemByFullName("d", Folder.class);
        assertNotNull(d);
        assertEquals("[d-liteful]", d.getProperties().toString());
        Class<?> c = d.getProperties().get(0).getClass();
        assertEquals(r.jenkins.pluginManager.uberClassLoader.loadClass(c.getName()).getClassLoader(), c.getClassLoader());
    }

}
