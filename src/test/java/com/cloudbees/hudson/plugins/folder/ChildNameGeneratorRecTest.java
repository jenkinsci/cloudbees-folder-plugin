/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.StaplerRequest;

import static com.cloudbees.hudson.plugins.folder.ChildNameGeneratorAltTest.windowsFFS;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Tests {@link ChildNameGenerator} using a generator where the previous implementation of the computed folder used a
 * name encoding alogorithm and the new one has switched to {@link ChildNameGenerator}. We want to ensure that the
 * name gets decoded from the legacy name and fixed to the correct name while the directory name gets mangled.
 */
public class ChildNameGeneratorRecTest {

    @Rule
    public RestartableJenkinsRule r = new RestartableJenkinsRule();

    /**
     * Given: a computed folder
     * When: creating a new instance
     * Then: mangling gets applied
     */
    @Test
    public void createdFromScratch() throws Exception {
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ComputedFolderImpl instance = r.j.jenkins.createProject(ComputedFolderImpl.class, "instance");
                instance.assertItemNames(0);
                instance.recompute(Result.SUCCESS);
                instance.assertItemNames(1);
                instance.addKids(
                        "child-one",
                        "child_two",
                        "child three",
                        "leanbh c\u00FAig", // "leanbh cúig",
                        "\u0440\u0435\u0431\u0435\u043D\u043E\u043A \u043F\u044F\u0442\u044C", //"ребенок пять",
                        "\u513F\u7AE5\u516D", // "儿童六",
                        "\uC544\uC774 7", // "아이 7",
                        "ni\u00F1o ocho" // "niño ocho"
                );
                instance.recompute(Result.SUCCESS);
                checkComputedFolder(instance, 2);
            }
        });
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0);
                r.j.jenkins.reload();
                i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0);
                instance.doReload();
                checkComputedFolder(instance, 0);
            }
        });
    }

    /**
     * Given: a computed folder
     * When: upgrading from a version that does not have name mangling to a version that does
     * Then: mangling gets applied
     */
    @Test
    @LocalData // to enable running on e.g. windows, keep the resource path short, so the test name must be short too
    public void upgrade() throws Exception {
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0);
            }
        });
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0);
                r.j.jenkins.reload();
                i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0);
                instance.doReload();
                checkComputedFolder(instance, 0);
            }
        });
    }

    private void checkComputedFolder(ComputedFolderImpl instance, int round) throws IOException {
        // because these were previously encoded with rawEncode we can recover exactly
        instance.assertItemNames(round,
                "child-one",
                "child_two",
                "child three",
                "leanbh c\u00FAig", // "leanbh cúig",
                "\u0440\u0435\u0431\u0435\u043D\u043E\u043A \u043F\u044F\u0442\u044C", //"ребенок пять",
                "\u513F\u7AE5\u516D", // "儿童六",
                "\uC544\uC774 7", // "아이 7",
                "ni\u00F1o ocho" // "niño ocho"
        );
        instance.assertItemShortUrls(round,
                "job/child-one/",
                "job/child_two/",
                "job/child%20three/",
                "job/leanbh%20c%C3%BAig/",
                "job/%D1%80%D0%B5%D0%B1%D0%B5%D0%BD%D0%BE%D0%BA%20%D0%BF%D1%8F%D1%82%D1%8C/", // ребенок пять
                "job/%E5%84%BF%E7%AB%A5%E5%85%AD/", // 儿童六
                "job/%EC%95%84%EC%9D%B4%207/", // 아이 7
                "job/ni%C3%B1o%20ocho/"
        );
        instance.assertItemDirs(round,
                "child_on-1ec93354e47959489d1440d",
                "child_tw-bca7d461e11f4f3ed12fd0d",
                "child_th-b7a6e5662f26eb036090308",
                "leanbh_c-cde398abd1bc432e87c49ca",
                "________-97e4b38574769f9d9968fe9", // ребенок пять
                "___-d22e9fe51690274d8262bda", // 儿童六
                "_____7-d57fff123224bd679e4213b", // 아이 7
                "nin_o_oc-1a0c91070942136ba398919"
        );
        for (String name: Arrays.asList(
                "child-one",
                "child_two",
                "child three",
                "leanbh c\u00FAig", // "leanbh cúig",
                "\u0440\u0435\u0431\u0435\u043D\u043E\u043A \u043F\u044F\u0442\u044C", //"ребенок пять",
                "\u513F\u7AE5\u516D", // "儿童六",
                "\uC544\uC774 7", // "아이 7",
                "ni\u00F1o ocho" // "niño ocho"
        )) {
            checkChild(instance, name);
        }
    }

    private void checkChild(ComputedFolderImpl instance, String idealName) throws IOException {
        String encodedName = encode(idealName);
        FreeStyleProject item = instance.getItem(encodedName);
        assertThat("We have an item for name " + idealName, item, notNullValue());
        assertThat("The root directory of the item for name " + idealName + " is mangled",
                item.getRootDir().getName(), is(mangle(idealName)));
        File nameFile = new File(item.getRootDir(), ChildNameGenerator.CHILD_NAME_FILE);
        assertThat("We have the " + ChildNameGenerator.CHILD_NAME_FILE + " for the item for name " + idealName,
                nameFile.isFile(), is(true));
        String name = FileUtils.readFileToString(nameFile, "UTF-8");
        assertThat("The " + ChildNameGenerator.CHILD_NAME_FILE + " for the item for name " + idealName
                + " contains the encoded name", name, is(encodedName));
    }

    public static String encode(String s) {
        return s;
    }

    public static String mangle(String s) {
        String hash = Util.getDigestOf(s);
        String base = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase(Locale.ENGLISH);
        StringBuilder buf = new StringBuilder(32);
        for (char c : base.toCharArray()) {
            if (buf.length() >= 8) break;
            if (('A' <= c && c <= 'Z')
                    || ('a' <= c && c <= 'z')
                    || ('0' <= c && c <= '9')) {
                buf.append(Character.toLowerCase(c));
            } else {
                buf.append('_');
            }
        }
        buf.append('-');
        buf.append(hash.substring(0, 23));
        return buf.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class ComputedFolderImpl extends ComputedFolder<FreeStyleProject> {

        private Set<String> fatalKids = new TreeSet<String>();

        private List<String> kids = new ArrayList<String>();
        /**
         * The number of computations since either Jenkins was restarted or the folder was created.
         */
        private transient int round;
        /**
         * The items created in the last round.
         */
        private transient List<String> created;
        /**
         * The items removed in the last round.
         */
        private transient List<String> deleted;

        private ComputedFolderImpl(ItemGroup parent, String name) {
            super(parent, name);
        }

        public int getRound() {
            return round;
        }

        public List<String> getCreated() {
            return created == null ? new ArrayList<String>() : created;
        }

        public List<String> getDeleted() {
            return deleted == null ? new ArrayList<String>() : deleted;
        }

        public Set<String> getFatalKids() {
            return fatalKids;
        }

        public void setFatalKids(Set<String> fatalKids) {
            if (!this.fatalKids.equals(fatalKids)) {
                this.fatalKids = new TreeSet<String>(fatalKids);
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        public void setFatalKids(String... fatalKids) {
            setFatalKids(new TreeSet<String>(Arrays.asList(fatalKids)));
        }

        public List<String> getKids() {
            return kids;
        }

        public void setKids(List<String> kids) {
            if (!this.kids.equals(kids)) {
                this.kids = new ArrayList<String>(kids);
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        public void setKids(String... kids) {
            setKids(Arrays.asList(kids));
        }

        public void addKid(String kid) {
            if (!this.kids.contains(kid)) {
                this.kids.add(kid);
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        public void removeKid(String kid) {
            if (this.kids.remove(kid)) {
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        public void addKids(String... kids) {
            List<String> k = new ArrayList<String>(Arrays.asList(kids));
            k.removeAll(this.kids);
            if (this.kids.addAll(k)) {
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        public void removeKids(String... kid) {
            if (this.kids.removeAll(Arrays.asList(kid))) {
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        @Override
        protected void computeChildren(ChildObserver<FreeStyleProject> observer, TaskListener listener) throws
                IOException, InterruptedException {
            round++;
            created = new ArrayList<String>();
            deleted = new ArrayList<String>();
            listener.getLogger().println("=== Round #" + round + " ===");
            for (String kid : kids) {
                if (fatalKids.contains(kid)) {
                    throw new AbortException("not adding " + kid);
                }
                listener.getLogger().println("considering " + kid);
                String encodedKid = encode(kid);
                FreeStyleProject p = observer.shouldUpdate(encodedKid);
                try {
                    if (p == null) {
                        if (observer.mayCreate(encodedKid)) {
                            listener.getLogger().println("creating a child");
                            ChildNameGenerator.Trace trace = ChildNameGenerator.beforeCreateItem(this, encodedKid, kid);
                            try {
                                p = new FreeStyleProject(this, encodedKid);
                            } finally {
                                trace.close();
                            }
                            BulkChange bc = new BulkChange(p);
                            try {
                                p.addProperty(new NameProperty(kid));
                                p.setDescription("created in round #" + round);
                            } finally {
                                bc.commit();
                            }
                            observer.created(p);
                            created.add(kid);
                        } else {
                            listener.getLogger().println("not allowed to create a child");
                        }
                    } else {
                        listener.getLogger().println("updated existing child with description " + p.getDescription());
                        p.setDescription("updated in round #" + round);
                    }
                } finally {
                    observer.completed(encodedKid);
                }
            }
        }

        @Override
        protected Collection<FreeStyleProject> orphanedItems(Collection<FreeStyleProject> orphaned,
                                                             TaskListener listener)
                throws IOException, InterruptedException {
            Collection<FreeStyleProject> deleting = super.orphanedItems(orphaned, listener);
            for (FreeStyleProject p : deleting) {
                String kid = p.getName();
                listener.getLogger().println("deleting " + kid + " in round #" + round);
                deleted.add(kid);
            }
            return deleting;
        }

        public String recompute(Result result) throws Exception {
            scheduleBuild2(0).getFuture().get();
            FolderComputation<?> computation = getComputation();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            computation.writeWholeLogTo(baos);
            String log = baos.toString();
            assertEquals(log, result, computation.getResult());
            return log;
        }

        public void assertItemNames(int round, String... names) {
            assertEquals(round, this.round);
            TreeSet<String> actual = new TreeSet<String>();
            for (FreeStyleProject p : getItems()) {
                actual.add(p.getName());
            }
            assertThat(actual, anyOf(is(new TreeSet<String>(Arrays.asList(names))), is(windowsFFS(names))));
        }

        public void assertItemShortUrls(int round, String... names) {
            assertEquals(round, this.round);
            TreeSet<String> actual = new TreeSet<String>();
            for (FreeStyleProject p : getItems()) {
                actual.add(p.getShortUrl());
            }
            assertThat(actual, is(new TreeSet<String>(Arrays.asList(names))));
        }

        public void assertItemDirs(int round, String... names) {
            assertEquals(round, this.round);
            TreeSet<String> actual = new TreeSet<String>();
            for (FreeStyleProject p : getItems()) {
                actual.add(p.getRootDir().getName());
            }
            assertThat(actual, is(new TreeSet<String>(Arrays.asList(names))));
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractFolderDescriptor {

            private static final ChildNameGeneratorImpl GENERATOR = new ChildNameGeneratorImpl();

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new ComputedFolderImpl(parent, name);
            }

            @Override
            public <I extends TopLevelItem> ChildNameGenerator<AbstractFolder<I>, I> childNameGenerator() {
                return (ChildNameGenerator<AbstractFolder<I>, I>) GENERATOR;
            }

        }

    }

    public static class NameProperty extends JobProperty<FreeStyleProject> {
        private final String name;

        public NameProperty(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public boolean isApplicable(Class<? extends Job> jobType) {
                return FreeStyleProject.class.isAssignableFrom(jobType);
            }

            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }

    private static class ChildNameGeneratorImpl<F extends AbstractFolder<J>, J extends FreeStyleProject>
            extends ChildNameGenerator<F, J> {

        @Override
        public String itemNameFromItem(@Nonnull F parent,
                                       @Nonnull J item) {
            NameProperty property = item.getProperty(NameProperty.class);
            if (property != null) {
                return encode(property.getName());
            }
            String name = idealNameFromItem(parent, item);
            return name == null ? null : encode(name);
        }

        @Override
        public String dirNameFromItem(@Nonnull F parent,
                                      @Nonnull J item) {
            NameProperty property = item.getProperty(NameProperty.class);
            if (property != null) {
                return mangle(property.getName());
            }
            String name = idealNameFromItem(parent, item);
            return name == null ? null : mangle(name);
        }

        @Nonnull
        @Override
        public String itemNameFromLegacy(@Nonnull F parent,
                                         @Nonnull String legacyDirName) {
            return rawDecode(legacyDirName);
        }

        @Nonnull
        @Override
        public String dirNameFromLegacy(@Nonnull F parent,
                                        @Nonnull String legacyDirName) {
            return mangle(rawDecode(legacyDirName));
        }

        @Override
        public void recordLegacyName(F parent, J item, String legacyDirName) throws IOException {
            item.addProperty(new NameProperty(rawDecode(legacyDirName)));
        }
    }

    /**
     * Inverse function of {@link Util#rawEncode(String)}
     *
     * @param s the encoded string.
     * @return the decoded string.
     */
    @NonNull
    public static String rawDecode(@NonNull String s) {
        final byte[] bytes; // should be US-ASCII but we can be tolerant
        try {
            bytes = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JLS specification mandates UTF-8 as a supported encoding", e);
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            final int b = bytes[i];
            if (b == '%' && i + 2 < bytes.length) {
                final int u = Character.digit((char) bytes[++i], 16);
                final int l = Character.digit((char) bytes[++i], 16);
                if (u != -1 && l != -1) {
                    buffer.write((char) ((u << 4) + l));
                    continue;
                }
                // should be a valid encoding but we can be tolerant
                i -= 2;
            }
            buffer.write(b);
        }
        try {
            return new String(buffer.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JLS specification mandates UTF-8 as a supported encoding", e);
        }
    }


}
