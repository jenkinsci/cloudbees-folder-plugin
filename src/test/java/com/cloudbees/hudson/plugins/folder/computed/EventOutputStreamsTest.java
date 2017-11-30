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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class EventOutputStreamsTest {

    @Rule
    public TemporaryFolder work = new TemporaryFolder();

    @Test
    public void given_everyoneFlushing_when_twoThreads_then_outputCorrect() throws Exception {
        test(true, true);
    }

    @Test
    public void given_nobodyFlushing_when_twoThreads_then_outputCorrect() throws Exception {
        test(false, false);
    }

    @Test
    public void given_oneFlushing_when_twoThreads_then_outputCorrect() throws Exception {
        test(true, false);
    }

    public void test(final boolean aFlush, final boolean bFlush) throws Exception {
        final File file = work.newFile();
        final EventOutputStreams instance = new EventOutputStreams(new EventOutputStreams.OutputFile() {
            @NonNull
            @Override
            public File get() {
                return file;
            }
        }, 250, TimeUnit.MILLISECONDS, 8192, false, Long.MAX_VALUE, 0);
        Thread t1 = new Thread() {
            public void run() {
                OutputStream os = instance.get();
                try {
                    PrintWriter pw = new PrintWriter(os, aFlush);
                    for (int i = 0; i < 10000; i += 1) {
                        pw.println(String.format("%1$05dA", i));
                    }
                    pw.flush();
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                } finally {
                    IOUtils.closeQuietly(os);
                }
            }
        };
        Thread t2 = new Thread() {
            public void run() {
                OutputStream os = instance.get();
                try {
                    PrintWriter pw = new PrintWriter(os, bFlush);
                    for (int i = 0; i < 10000; i+=1) {
                        pw.println(String.format("%1$05dB", i));
                    }
                    pw.flush();
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                } finally {
                    IOUtils.closeQuietly(os);
                }
            }
        };
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        List<String> as = new ArrayList<String>();
        List<String> bs = new ArrayList<String>();
        for (String line : FileUtils.readLines(file)) {
            assertThat("Line does not have both thread output: '" + StringEscapeUtils.escapeJava(line)+"'",
                    line.matches("^\\d+[AB](\\d+[AB])+$"), is(false));
            assertThat("Line does not contain a null character: '" + StringEscapeUtils.escapeJava(line) + "'",
                    line.indexOf(0), is(-1));
            if (line.endsWith("A")) {
                as.add(line);
            } else if (line.endsWith("B")) {
                bs.add(line);
            } else {
                fail("unexpected line: '" + StringEscapeUtils.escapeJava(line) +"'");
            }
        }
        List<String> sorted = new ArrayList<String>(as);
        Collections.sort(sorted);
        assertThat(as, is(sorted));
        sorted = new ArrayList<String>(bs);
        Collections.sort(sorted);
        assertThat(bs, is(sorted));
    }
}
