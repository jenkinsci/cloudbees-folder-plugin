package com.cloudbees.hudson.plugins.folder.computed;

import hudson.Functions;
import hudson.Util;
import hudson.model.Actionable;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A fake {@link Run} used to render last build information via Stapler and Jelly
 */
@Restricted(NoExternalUse.class) // used by stapler / jelly only
public class PseudoRun<I extends TopLevelItem> extends Actionable implements StaplerFallback {
    private final FolderComputation<I> computation;

    PseudoRun(FolderComputation<I> computation) {
        this.computation = computation;
    }

    public String getDisplayName() {
        return "log";
    }

    public RunUrl decompose(StaplerRequest req) {
        List<Ancestor> ancestors = req.getAncestors();

        // find the first and last Run instances
        Ancestor f = null, l = null;
        for (Ancestor anc : ancestors) {
            if (anc.getObject() instanceof PseudoRun) {
                if (f == null) f = anc;
                l = anc;
            }
        }
        if (l == null) return null;    // there was no Run object

        String base = l.getUrl();

        return new RunUrl(base);

    }

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return string like "3 minutes" "1 day" etc.
     */
    @Nonnull
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis() - computation.getTimestamp().getTimeInMillis();
        return Util.getPastTimeString(duration);
    }

    /**
     * Returns the timestamp formatted in xs:dateTime.
     *
     * @return the timestamp formatted in xs:dateTime.
     */
    @Nonnull
    public String getTimestampString2() {
        return Util.XS_DATETIME_FORMATTER.format(computation.getTimestamp());
    }

    @CheckForNull
    public Result getResult() {
        return computation.getResult();
    }

    @Nonnull
    public Calendar getTimestamp() {
        return computation.getTimestamp();
    }

    @Nonnull
    public String getDurationString() {
        return computation.getDurationString();
    }

    @Override
    public String getSearchUrl() {
        return computation.getSearchUrl();
    }

    @Nonnull
    public File getLogFile() {
        return computation.getLogFile();
    }

    @Override
    public Object getStaplerFallback() {
        return computation;
    }

    public HttpResponse doIndex(StaplerRequest request) {
        return HttpResponses.redirectViaContextPath(computation.getUrl());
    }

    public HttpResponse doConsole(StaplerRequest request) {
        return HttpResponses.redirectViaContextPath(computation.getUrl() + "console");
    }

    public HttpResponse doConsoleText(StaplerRequest request) {
        return HttpResponses.redirectViaContextPath(computation.getUrl() + "consoleText");
    }

    @Restricted(NoExternalUse.class)
    public static final class RunUrl {
        private final String base;


        public RunUrl(String base) {
            this.base = base;
        }

        public String getBaseUrl() {
            return base;
        }

        /**
         * Returns the same page in the next build.
         */
        public String getNextBuildUrl() {
            return null;
        }

        /**
         * Returns the same page in the previous build.
         */
        public String getPreviousBuildUrl() {
            return null;
        }

    }

}
