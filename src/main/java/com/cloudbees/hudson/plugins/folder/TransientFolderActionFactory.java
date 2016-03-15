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

package com.cloudbees.hudson.plugins.folder;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;

import java.util.Collection;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;

/**
 * Extension point for inserting transient {@link Action}s into {@link Folder}s.
 *
 * Actions contributed to {@link Folder}s by this method are transient &mdash; they will not be persisted,
 * and each time Nectar starts or the configuration of the job changes, they'll be recreated.
 * Therefore, to maintain persistent data per project, you'll need to do data serialization by yourself.
 * Do so by storing a file under {@link Folder#getRootDir()}.
 *
 * <p>
 * To register your implementation, put {@link Extension} on your subtype.
 *
 * @see Action
 * @deprecated Use {@link TransientActionFactory} on {@link Folder} instead.
 */
@Deprecated
public abstract class TransientFolderActionFactory implements ExtensionPoint {
    /**
     * Creates actions for the given project.
     *
     * @param target
     *      The project for which the action objects are requested. Never null.
     * @return
     *      Can be empty but must not be null.
     */
    public abstract Collection<? extends Action> createFor(Folder target);

    /**
     * Returns all the registered {@link TransientFolderActionFactory}s.
     */
    public static ExtensionList<TransientFolderActionFactory> all() {
        return Jenkins.getActiveInstance().getExtensionList(TransientFolderActionFactory.class);
    }
}

