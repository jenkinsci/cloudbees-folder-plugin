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

import hudson.AbortException;
import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
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
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.SaveableListener;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.io.Charsets;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * A particular “run” of {@link ComputedFolder}.
 * @since 4.11-beta-1
 */
public class FolderComputation<I extends TopLevelItem> extends Actionable implements Queue.Executable, Saveable {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FolderComputation.class.getName());

    /** If defined, a number of backup log files to keep. */
    @SuppressWarnings("FieldMayBeFinal") // let this be set dynamically by system Groovy script
    private static @CheckForNull Integer BACKUP_LOG_COUNT = Integer.getInteger(FolderComputation.class.getName() + ".BACKUP_LOG_COUNT");

    /** If defined, a number of kB that the event log can grow to before rotation. */
    @SuppressWarnings("FieldMayBeFinal") // let this be set dynamically by system Groovy script
    @Nonnull
    private static int EVENT_LOG_MAX_SIZE = Math.max(1,Integer.getInteger(FolderComputation.class.getName() + ".EVENT_LOG_MAX_SIZE", 150));

    /** The associated folder. */
    @Nonnull
    private transient final ComputedFolder<I> folder;

    /** The previous run, if any. */
    @CheckForNull
    private transient final FolderComputation<I> previous;

    /** The result of the build, if finished. */
    @CheckForNull
    private volatile Result result;

    /** The past few durations for purposes of estimation. */
    @CheckForNull
    private List<Long> durations;

    /** Start time. */
    private long timestamp;

    /** Run time. */
    private long duration;

    protected FolderComputation(@Nonnull ComputedFolder<I> folder, @CheckForNull FolderComputation<I> previous) {
        this.folder = folder;
        this.previous = previous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        StreamBuildListener listener;
        try {
            File logFile = getLogFile();
            OutputStream os;
            if (BACKUP_LOG_COUNT != null) {
                os = new ReopenableRotatingFileOutputStream(logFile, BACKUP_LOG_COUNT);
                ((ReopenableRotatingFileOutputStream) os).rewind();
            } else {
                os = new FileOutputStream(logFile);
            }
            listener = new StreamBuildListener(os, Charsets.UTF_8);
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
            LOGGER.log(Level.FINE, "recomputation of " + folder.getFullName() + " was aborted", x);
            listener.getLogger().println("Aborted");
            _result = Result.ABORTED;
        } catch (Exception x) {
            LOGGER.log(Level.FINE, "recomputation of " + folder.getFullName() + " failed", x);
            if (x instanceof AbortException) {
                listener.fatalError(x.getMessage());
            } else {
                x.printStackTrace(listener.fatalError("Failed to recompute children of " + folder.getFullDisplayName()));
            }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile dataFile = getDataFile();
        dataFile.write(this);
        SaveableListener.fireOnChange(this, dataFile);
    }

    @Nonnull
    public File getLogFile() {
        return new File(folder.getComputationDir(), "computation.log");
    }

    @Nonnull
    public File getEventsFile() {
        return new File(folder.getComputationDir(), "events.log");
    }

    public TaskListener createEventsListener() throws IOException {
        File eventsFile = getEventsFile();
        boolean rotate = eventsFile.length() > EVENT_LOG_MAX_SIZE * 1024;
        OutputStream os;
        if (BACKUP_LOG_COUNT != null) {
            os = new ReopenableRotatingFileOutputStream(eventsFile, BACKUP_LOG_COUNT);
            if (rotate) {
                ((ReopenableRotatingFileOutputStream) os).rewind();
            }
        } else {
            os = new FileOutputStream(eventsFile, !rotate);
        }
        return new StreamBuildListener(os, Charsets.UTF_8);
    }

    @Nonnull
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return AlternativeUiTextProvider.get(DISPLAY_NAME, this, Messages.FolderComputation_DisplayName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSearchUrl() {
        return "computation/";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public ComputedFolder<I> getParent() {
        return folder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

    @Nonnull
    public AnnotatedLargeText<FolderComputation<I>> getLogText() {
        return new AnnotatedLargeText<FolderComputation<I>>(getLogFile(), Charsets.UTF_8, !isLogUpdated(), this);
    }

    @Nonnull
    public AnnotatedLargeText<FolderComputation<I>> getEventsText() {
        File eventsFile = getEventsFile();
        if (eventsFile.length() <= 0) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write(
                        String.format("No events as of %tc, waiting for events...%n", new Date())
                                .getBytes(Charsets.UTF_8)
                );
                return new AnnotatedLargeText<FolderComputation<I>>(buffer, Charsets.UTF_8, false, this);
            } catch (IOException e) {
                // ignore and fall through
            }
        }
        return new AnnotatedLargeText<FolderComputation<I>>(eventsFile, Charsets.UTF_8, false, this);
    }

    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        getLogText().writeHtmlTo(offset, out.asWriter());
    }

    public void writeWholeLogTo(@Nonnull OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        AnnotatedLargeText<?> logText;
        do {
            logText = getLogText();
            pos = logText.writeLogTo(pos, out);
        } while (!logText.isComplete());
    }

    @CheckForNull
    public Result getResult() {
        return result;
    }

    @Nonnull
    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    @Nonnull
    public String getDurationString() {
        if (isBuilding()) {
            return hudson.model.Messages.Run_InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - timestamp));
        } else {
            return Util.getTimeSpanString(duration);
        }
    }

    @Nonnull
    public String getUrl() {
        return folder.getUrl() + "computation/";
    }

    @CheckForNull
    public Result getPreviousResult() {
        return previous == null ? null : previous.result;
    }

    @Nonnull
    public BallColor getIconColor() {
        Result _result = result;
        if (_result != null) {
            return _result.color;
        }
        Result previousResult = getPreviousResult();
        if (previousResult == null) {
            return isBuilding() ? BallColor.GREY_ANIME : BallColor.GREY;
        }
        return isBuilding() ? previousResult.color.anime() : previousResult.color;
    }

    public String getBuildStatusIconClassName() {
        return getIconColor().getIconClassName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + folder.getFullName() + "]";
    }

    static {
        Items.XSTREAM.alias("folder-computation", FolderComputation.class);
    }

    /**
     * Allow other code to override the display name for {@link FolderComputation}.
     */
    public static final Message<FolderComputation> DISPLAY_NAME = new Message<FolderComputation>();
}
