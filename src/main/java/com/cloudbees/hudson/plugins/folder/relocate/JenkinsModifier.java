/*
 * The MIT License
 *
 * Copyright 2013 CloudBees.
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

package com.cloudbees.hudson.plugins.folder.relocate;

import hudson.Extension;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles modifying Jenkins root object.
 */
@Extension
public class JenkinsModifier implements ItemGroupModifier<Jenkins, TopLevelItem> {

    /**
     * {@inheritDoc}
     */
    public Class<Jenkins> getTargetClass() {
        return Jenkins.class;
    }

    /**
     * {@inheritDoc}
     */
    public <II extends TopLevelItem> boolean canAdd(Jenkins target, II item) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public <I extends TopLevelItem> I add(final Jenkins target, I item) throws IOException {
        // TODO find some nice clean way to remove the screaming ugly hack
        Map<String, TopLevelItem> items =
                AccessController.doPrivileged(new PrivilegedAction<Map<String, TopLevelItem>>() {
                    /** {@inheritDoc} */
                    public Map<String, TopLevelItem> run() {
                        Class aClass = Jenkins.class;
                        while (aClass != Object.class) {
                            try {
                                final Field items = aClass.getDeclaredField("items");
                                boolean accessible = items.isAccessible();
                                items.setAccessible(true);
                                try {
                                    return (Map<String, TopLevelItem>) items.get(target);
                                } finally {
                                    items.setAccessible(accessible);
                                }
                            } catch (IllegalAccessException e) {
                                Logger.getLogger(getClass().getName()).log(Level.INFO, "Cannot get items", e);
                                return null;
                            } catch (NoSuchFieldException e) {
                                Logger.getLogger(getClass().getName()).log(Level.INFO, "Cannot get items", e);
                                aClass = aClass.getSuperclass();
                            }
                        }
                        return null;
                    }
                });
        if (items != null) {
            items.put(item.getName(), item);
        }
        item.onLoad(target, item.getName());
        return item;
    }

    /**
     * {@inheritDoc}
     */
    public void remove(Jenkins target, TopLevelItem item) throws IOException {
        target.onDeleted(item);
    }
}
