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

package com.cloudbees.hudson.plugins.folder.computed;

import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractItem;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Executor;
import hudson.model.Items;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.StreamBuildListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.SubTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import org.apache.commons.io.Charsets;
import org.apache.commons.jelly.XMLOutput;

/**
 * A particular “run” of .
 
 */
public class FolderComputation<I extends TopLevelItem> extends Actionable implements Queue.Executable, Saveable {

    private static final Logger LOGGER = Logger.getLogger(FolderComputation.class.getName());

    /** The associated folder. */
    private transient final ComputedFolder<I> folder;

    /** The previous run, if any. */
    private transient final FolderComputation<I> previous;

    /** The result of the build, if finished. */
    private volatile Result result;

    /** The past few durations for purposes of estimation. */
    private List<Long> durations;

    /** Start time. */
    private long timestamp;

    /** Run time. */
    private long duration;

    protected FolderComputation(ComputedFolder<I> folder, FolderComputation<I> previous) {
        this.folder = folder;
        this.previous = previous;
    }

    
    public void run() {
        StreamBuildListener listener;
        try {
            listener = new StreamBuildListener(new FileOutputStream(getLogFile()), Charsets.UTF_8);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            result = Result.FAILURE;
            return;
        }
        timestamp = System.currentTimeMillis();
        // TODO print start time
        listener.started(getCauses());
        Result _result = Result.NOT_BUILT; // cf. isLogUpdated, do not set this.result until listener closed
        try {
            folder.updateChildren(listener);
            _result = Result.SUCCESS;
        } catch (InterruptedException x) {
            listener.getLogger().println("Aborted");
            _result = Result.ABORTED;
        } catch (Exception x) {
            // TODO skip stack trace for AbortException
            x.printStackTrace(listener.fatalError("Failed to recompute children of " + getDisplayName()));
            _result = Result.FAILURE;
        } finally {
            duration = System.currentTimeMillis() - timestamp;
            if (durations == null) {
                durations = new ArrayList<Long>();
            }
            while (durations.size() > 32) {
                durations.remove(0);
            }
            durations.add(duration);
            listener.finished(_result);
            listener.closeQuietly();
            result = _result;
            try {
                save();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile dataFile = getDataFile();
        dataFile.write(this);
        SaveableListener.fireOnChange(this, dataFile);
    }

    public File getLogFile() {
        return new File(folder.getComputationDir(), "computation.log");
    }

    protected XmlFile getDataFile() {
        return new XmlFile(Items.XSTREAM, new File(folder.getComputationDir(), "computation.xml"));
    }

    public List<Cause> getCauses() {
        CauseAction a = getAction(CauseAction.class);
        if (a == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(a.getCauses());
    }

    
    public String getDisplayName() {
        return AlternativeUiTextProvider.get(DISPLAY_NAME, this, Messages.FolderComputation_DisplayName());
    }

    
    public String getSearchUrl() {
        return "computation/";
    }

    
    public ComputedFolder<I> getParent() {
        return folder;
    }

    
    public long getEstimatedDuration() {
        if (durations == null || durations.isEmpty()) {
            return -1;
        }
        long total = 0;
        for (Long d : durations) {
            total += d;
        }
        return total / durations.size();
    }

    public boolean isBuilding() {
        return Executor.of(this) != null;
    }

    public boolean isLogUpdated() {
        return result == null;
    }

    public AnnotatedLargeText<FolderComputation<I>> getLogText() {
        return new AnnotatedLargeText<FolderComputation<I>>(getLogFile(), Charsets.UTF_8, !isLogUpdated(), this);
    }

    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        getLogText().writeHtmlTo(offset, out.asWriter());
    }

    public void writeWholeLogTo(OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        AnnotatedLargeText<?> logText;
        do {
            logText = getLogText();
            pos = logText.writeLogTo(pos, out);
        } while (!logText.isComplete());
    }

    public Result getResult() {
        return result;
    }

    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    public String getDurationString() {
        if (isBuilding()) {
            return hudson.model.Messages.Run_InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - timestamp));
        } else {
            return Util.getTimeSpanString(duration);
        }
    }

    public String getUrl() {
        return folder.getUrl() + "computation/";
    }

    public Result getPreviousResult() {
        return previous == null ? null : previous.result;
    }

    public BallColor getIconColor() {
        Result _result = result;
        if (_result != null) {
            return _result.color;
        }
        Result previousResult = getPreviousResult();
        if (previousResult == null) {
            return BallColor.GREY_ANIME;
        }
        return previousResult.color.anime();
    }

    public String getBuildStatusIconClassName() {
        return getIconColor().getIconClassName();
    }

    static {
        Items.XSTREAM.alias("folder-computation", FolderComputation.class);
    }

    /**
     * Allow other code to override the display name for .
     */
    public static final Message<FolderComputation> DISPLAY_NAME = new Message<FolderComputation>();
}
