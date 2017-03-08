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
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
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

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Tests {@link ChildNameGenerator} using a generator that modifies both the {@link Item#getName()} and the directory.
 * <p>
 * The test data set was generated using NFC encoded names. There are 8 children in the computed folder, each name
 * is the name of that child with different "fun" translations or other characters.
 * <nl>
 *     <li><code>child-one</code> demonstrates a name that should have no issues</li>
 *     <li><code>child_two</code> demonstrates a name that should have no issues</li>
 *     <li><code>child three</code> demonstrates a name that has a space in it</li>
 *     <li><code>leanbh cúig</code> (Irish for "child five") demonstrates a name that has a NFC chacacter with a
 *     different NFD encoding (also I messed up with the Russian translation and <code>leanbh ceithre</code> doesn't
 *     have a fada)</li>
 *     <li><code>ребенок пять</code> (Russian, should probably be "ребенок номер четыре" but I'm not re-generating the
 *     test data now - should be "child four") demonstrates an indo-european character set where the NFC and NFD forms
 *     are identical but we are using unicode characters</li>
 *     <li><code>儿童六</code> (Chinese, reversing through google translate gives "children six") demonstrates an asian
 *     character set where the NFC and NFD forms are identical</li>
 *     <li><code>아이 7</code> (Korean, google translate gave this for "child seven" but converts back as "child 7")
 *     demonstrates an asian character set where the NFC and NFD forms are different.</li>
 *     <li><code>niño ocho</code> (Spanish, supposed to be "child eight" round-tripped through Google translate gives
 *     "eight boy") demonstrates a name that has a NFC chacacter with a different NFD encoding</li>
 * </nl>
 *
 * Aside: <a href="https://www.youtube.com/watch?v=LMkJuDVJdTw">Here's what happens when you round-trip through Google
 * Translate</a> "Cold, apricot, relaxing satisfaction!"
 */
public class ChildNameGeneratorTest {

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
                instance.addKids( // these are all NFC names
                        "child-one",
                        "child_two",
                        "child three",
                        "leanbh c\u00faig", // leanbh cúig
                        "ребенок пять",
                        "儿童六",
                        "\uc544\uc774 7", // 아이 7
                        "ni\u00f1o ocho" // niño ocho
                );
                instance.recompute(Result.SUCCESS);
                checkComputedFolder(instance, 2, Normalizer.Form.NFC);
            }
        });
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, Normalizer.Form.NFC);
                r.j.jenkins.reload();
                i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, Normalizer.Form.NFC);
                instance.doReload();
                checkComputedFolder(instance, 0, Normalizer.Form.NFC);
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
        // The test data was generated using NFC filename encodings... but when unzipping the name can be changed
        // to NFD by the filesystem, so we need to check the expected outcome based on the inferred canonical form
        // used by the filesystem.
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
            }
        });
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
                r.j.jenkins.reload();
                i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
                instance.doReload();
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
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
    public void upgradeNFD() throws Exception {
        // The test data was generated using NFD filename encodings... but when unzipping the name can be changed
        // to NFC by the filesystem, so we need to check the expected outcome based on the inferred canonical form
        // used by the filesystem.
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
            }
        });
        r.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TopLevelItem i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                ComputedFolderImpl instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
                r.j.jenkins.reload();
                i = r.j.jenkins.getItem("instance");
                assertThat("Item loaded from disk", i, instanceOf(ComputedFolderImpl.class));
                instance = (ComputedFolderImpl) i;
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
                instance.doReload();
                checkComputedFolder(instance, 0, ChildNameGeneratorTest.this.inferNormalizerForm());
            }
        });
    }

    private void checkComputedFolder(ComputedFolderImpl instance, int round, Normalizer.Form form) throws IOException {
        assertThat("We detected the filesystem normalization form", form, notNullValue());
        instance.assertItemNames(round,
                "$$child-one",
                "$$child_two",
                "$$child three",
                "$$leanbh cu\u0301ig",
                "$$ребенок пять",
                "$$儿童六",
                "$$\u110b\u1161\u110b\u1175 7",
                "$$nin\u0303o ocho"
        );
        instance.assertItemShortUrls(round,
                "job/$$child-one/",
                "job/$$child_two/",
                "job/$$child%20three/",
                "job/$$leanbh%20cu%CC%81ig/",
                "job/$$%D1%80%D0%B5%D0%B1%D0%B5%D0%BD%D0%BE%D0%BA%20%D0%BF%D1%8F%D1%82%D1%8C/", // ребенок пять
                "job/$$%E5%84%BF%E7%AB%A5%E5%85%AD/", // 儿童六
                "job/$$%E1%84%8B%E1%85%A1%E1%84%8B%E1%85%B5%207/", // 아이 7
                "job/$$nin%CC%83o%20ocho/"
        );
        switch (form) {
            case NFC:
            case NFKC:
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
                break;
            case NFD:
            case NFKD:
                instance.assertItemDirs(round,
                        "child_on-1ec93354e47959489d1440d",
                        "child_tw-bca7d461e11f4f3ed12fd0d",
                        "child_th-b7a6e5662f26eb036090308",
                        "leanbh_c-66fe5ac0be4a896280ef09f",
                        "________-97e4b38574769f9d9968fe9", // ребенок пять
                        "___-d22e9fe51690274d8262bda", // 儿童六
                        "_____7-6d2219439eec0df19863ab8", // 아이 7
                        "nin_o_oc-782e3bad2d233732a03f9dd"
                );
                break;
        }
        for (String name: Arrays.asList(
                "child-one",
                "child_two",
                "child three",
                "leanbh cúig",
                "ребенок пять",
                "儿童六",
                "아이 7",
                "niño ocho"
        )) {
            checkChild(instance, Normalizer.normalize(name, form));
        }
    }

    private Normalizer.Form inferNormalizerForm() {
        Normalizer.Form form = null;
        File[] contents = r.j.jenkins.getRootDir().listFiles();
        if (contents != null) {
            for (File f: contents) {
                if ("leanbh-c\u00faig.probe".equals(f.getName())) {
                    form = Normalizer.Form.NFC;
                    System.out.println("\n\nUsing NFC normalization dataset as underlying filesystem is NFC\n\n");
                    break;
                }
                if ("leanbh-cu\u0301ig.probe".equals(f.getName())) {
                    form = Normalizer.Form.NFD;
                    System.out.println("\n\nUsing NFD normalization dataset as underlying filesystem is NFD\n\n");
                    break;
                }
            }
        }
        return form;
    }

    private void checkChild(ComputedFolderImpl instance, String idealName) throws IOException {
        String encodedName = encode(idealName);
        FreeStyleProject item = instance.getItem(encodedName);
        assertThat("We have an item for name " + idealName, item, notNullValue());
        assertThat("The root directory of the item for name " + idealName + " is mangled",
                item.getRootDir().getName(), is(mangle(idealName)));
        String altEncoding = Normalizer.normalize(idealName, Normalizer.Form.NFD);
        if (idealName.equals(altEncoding)) {
            altEncoding = Normalizer.normalize(idealName, Normalizer.Form.NFC);
        }
        if (!idealName.equals(altEncoding)) {
            File altRootDir = instance.getRootDirFor(altEncoding);
            assertThat("Alternative normalized form: " + altRootDir + " does not exist",
                    altRootDir.isDirectory(), is(false));
        }
        File nameFile = new File(item.getRootDir(), ChildNameGenerator.CHILD_NAME_FILE);
        assertThat("We have the " + ChildNameGenerator.CHILD_NAME_FILE + " for the item for name " + idealName,
                nameFile.isFile(), is(true));
        String name = FileUtils.readFileToString(nameFile);
        assertThat("The " + ChildNameGenerator.CHILD_NAME_FILE + " for the item for name " + idealName
                + " contains the encoded name", name, is(encodedName));
    }

    public static String encode(String s) {
        // we want to test that the name can be different from the on-disk name
        return "$$"+Normalizer.normalize(s, Normalizer.Form.NFD);
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
        // TODO refactor the ChildNameGeneratorTests to remove duplication of most of this class

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
            assertThat(actual, is(new TreeSet<String>(Arrays.asList(names))));
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
            return encode(legacyDirName);
        }

        @Nonnull
        @Override
        public String dirNameFromLegacy(@Nonnull F parent,
                                        @Nonnull String legacyDirName) {
            return mangle(legacyDirName);
        }

        @Override
        public void recordLegacyName(F parent, J item, String legacyDirName) throws IOException {
            item.addProperty(new NameProperty(legacyDirName));
        }
    }


}
