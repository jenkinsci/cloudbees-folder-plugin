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

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import java.util.zip.GZIPInputStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.console.PlainTextConsoleOutputStream;
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
import hudson.model.queue.QueueTaskDispatcher;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.HttpResponses;
import hudson.util.StreamTaskListener;
import hudson.util.io.RewindableRotatingFileOutputStream;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.diagnosis.OldDataMonitor;
import net.jcip.annotations.GuardedBy;
import java.nio.charset.StandardCharsets;
import jenkins.model.Loadable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A particular “run” of {@link ComputedFolder}.
 * @since 4.11-beta-1
 */
public class FolderComputation<I extends TopLevelItem> extends Actionable implements Queue.Executable, Saveable, Loadable {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FolderComputation.class.getName());

    /** If defined, a number of backup log files to keep. */
    @SuppressWarnings("FieldMayBeFinal") // let this be set dynamically by system Groovy script
    private static @CheckForNull Integer BACKUP_LOG_COUNT = Integer.getInteger(FolderComputation.class.getName() + ".BACKUP_LOG_COUNT");

    /** If defined, a number of kB that the event log can grow to before rotation. */
    @SuppressWarnings("FieldMayBeFinal") // let this be set dynamically by system Groovy script
    @NonNull
    private static int EVENT_LOG_MAX_SIZE = Math.max(1,Integer.getInteger(FolderComputation.class.getName() + ".EVENT_LOG_MAX_SIZE", 150));

    /** The associated folder. */
    @NonNull
    private transient final ComputedFolder<I> folder;

    /** The previous run result, if any. */
    @CheckForNull
    private transient final Result previousResult;

    /** The events output stream handler */
    @CheckForNull
    @GuardedBy("this")
    private transient EventOutputStreams eventStreams;

    /** The result of the build, if finished. */
    @CheckForNull
    private volatile Result result;

    /** The past few durations for purposes of estimation. */
    @Nullable // until set
    private List<Long> durations;

    /** Start time. */
    private long timestamp;

    /** Run time. */
    private long duration;

    protected FolderComputation(@NonNull ComputedFolder<I> folder, @CheckForNull FolderComputation<I> previous) {
        this.folder = folder;
        this.previousResult = previous == null ? null : previous.getResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        StreamBuildListener listener;
        try {
            File logFile = getLogFile();
            FileUtils.forceMkdir(logFile.getParentFile());
            OutputStream os;
            if (BACKUP_LOG_COUNT != null) {
                os = new RewindableRotatingFileOutputStream(logFile, BACKUP_LOG_COUNT);
                ((RewindableRotatingFileOutputStream) os).rewind();
            } else {
                os = new FileOutputStream(logFile);
            }
            listener = new StreamBuildListener(os, StandardCharsets.UTF_8);
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
                Functions.printStackTrace(x, listener.fatalError("Failed to recompute children of " + folder.getFullDisplayName()));
            }
            _result = Result.FAILURE;
        } finally {
            duration = System.currentTimeMillis() - timestamp;
            if (durations == null) {
                durations = new ArrayList<>();
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
        LOGGER.fine(() -> "saving " + this);
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile dataFile = getDataFile();
        dataFile.write(this);
        SaveableListener.fireOnChange(this, dataFile);
    }

    @Override
    public void load() throws IOException {
        LOGGER.fine(() -> "loading " + this);
        XmlFile dataFile = getDataFile();
        if (dataFile.exists()) {
            dataFile.unmarshal(this);
        }
    }

    @NonNull
    public File getLogFile() {
        return new File(folder.getComputationDir(), "computation.log");
    }

    @NonNull
    public File getEventsFile() {
        return new File(folder.getComputationDir(), "events.log");
    }

    @WithBridgeMethods(TaskListener.class)
    @NonNull
    public synchronized StreamTaskListener createEventsListener() {
        File eventsFile = getEventsFile();
        if (!eventsFile.getParentFile().isDirectory() && !eventsFile.getParentFile().mkdirs()) {
            LOGGER.log(Level.WARNING, "Could not create directory {0} for {1}",
                    new Object[]{eventsFile.getParentFile(), folder.getFullName()});
            // TODO return a StreamTaskListener sending output to a log, for now this will just try and fail to write
        }
        if (eventStreams == null) {
            eventStreams = new EventOutputStreams(new EventOutputStreams.OutputFile() {
                @NonNull
                @Override
                public File get() {
                    return getEventsFile();
                }

                @Override
                public boolean canWriteNow() {
                    // TODO rework once JENKINS-42248 is solved
                    GregorianCalendar timestamp = new GregorianCalendar();
                    timestamp.setTimeInMillis(System.currentTimeMillis() - 10000L);
                    Queue.Item probe = new Queue.WaitingItem(timestamp, folder, Collections.emptyList());
                    for (QueueTaskDispatcher d: QueueTaskDispatcher.all()) {
                        if (d.canRun(probe) != null) {
                            return false;
                        }
                    }
                    return true;
                }
            },
                    250, TimeUnit.MILLISECONDS,
                    1024,
                    true,
                    EVENT_LOG_MAX_SIZE * 1024L,
                    BACKUP_LOG_COUNT == null ? 0 : Math.max(0, BACKUP_LOG_COUNT)
            );
        }
        return new StreamTaskListener(eventStreams.get(), StandardCharsets.UTF_8);
    }

    @NonNull
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
     * May be used by {@link OldDataMonitor} in its {@code manage} view.
     * @return {@link ComputedFolder#getFullName}
     */
    public String getFullName() {
        return folder.getFullName();
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
    @NonNull
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

    @NonNull
    public AnnotatedLargeText<FolderComputation<I>> getLogText() {
        return new AnnotatedLargeText<>(getLogFile(), StandardCharsets.UTF_8, !isLogUpdated(), this);
    }

    @NonNull
    public AnnotatedLargeText<FolderComputation<I>> getEventsText() {
        File eventsFile = getEventsFile();
        if (eventsFile.length() <= 0) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write(
                        String.format("No events as of %tc, waiting for events...%n", new Date())
                                .getBytes(StandardCharsets.UTF_8)
                );
                return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, false, this);
            } catch (IOException e) {
                // ignore and fall through
            }
        }
        return new AnnotatedLargeText<>(eventsFile, StandardCharsets.UTF_8, false, this);
    }

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "Only one page is ever written here")
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        getLogText().writeHtmlTo(offset, out.asWriter());
    }

    public void writeWholeLogTo(@NonNull OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        AnnotatedLargeText<?> logText;
        do {
            logText = getLogText();
            pos = logText.writeLogTo(pos, out);
        } while (!logText.isComplete());
    }

    /**
     * Sends out the raw console output.
     *
     * @param req the request
     * @param rsp the response.
     * @throws IOException if things go wrong.
     */
    public void doConsoleText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        try (PlainTextConsoleOutputStream out = new PlainTextConsoleOutputStream(rsp.getOutputStream());
             InputStream input = getLogInputStream()) {
                    IOUtils.copy(input, out);
                    out.flush();
        }
    }

    /**
     * Stops this build if it's still going.
     *
     * @return the Http response.
     */
    @RequirePOST
    public synchronized HttpResponse doStop() throws IOException, ServletException {
        Executor e = Executor.of(this);
        if (e != null) {
            return e.doStop();
        } else {
            // nothing is building
            return HttpResponses.forwardToPreviousPage();
        }
    }

    /**
     * Returns an input stream that reads from the log file.
     * It will use a gzip-compressed log file (log.gz) if that exists.
     *
     * @return An input stream from the log file.
     * If the log file does not exist, the error message will be returned to the output.
     * @throws IOException if things go wrong
     */
    @NonNull
    public InputStream getLogInputStream() throws IOException {
        File logFile = getLogFile();

        if (logFile.exists()) {
            // Checking if a ".gz" file was return
            FileInputStream fis = new FileInputStream(logFile);
            if (logFile.getName().endsWith(".gz")) {
                return new GZIPInputStream(fis);
            } else {
                return fis;
            }
        }

        String message = "No such file: " + logFile.getName();
        return new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
    }

    @CheckForNull
    public Result getResult() {
        return result;
    }

    @NonNull
    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    @NonNull
    public String getDurationString() {
        if (isBuilding()) {
            return Messages.Run_InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - timestamp));
        } else {
            return Util.getTimeSpanString(duration);
        }
    }

    @NonNull
    public String getUrl() {
        return folder.getUrl() + "computation/";
    }

    @CheckForNull
    public Result getPreviousResult() {
        return previousResult;
    }

    @NonNull
    public BallColor getIconColor() {
        Result _result = result;
        if (_result != null) {
            return _result.color;
        }
        Result previousResult = getPreviousResult();
        if (previousResult == null) {
            return isBuilding() ? BallColor.NOTBUILT_ANIME : BallColor.NOTBUILT;
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
    public static final Message<FolderComputation> DISPLAY_NAME = new Message<>();
}
