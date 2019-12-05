# Folders Plugin

This plugin allows users to create "folders" to organize jobs. Users can
define custom taxonomies (e.g. by project type, organization type).
Folders are nestable and you can define views within folders.

## Documentation

[User guide](https://go.cloudbees.com/docs/plugins/folder/)

## Version History

New releases are logged in [GitHub Releases](https://github.com/jenkinsci/cloudbees-folder-plugin/releases).

### Version 6.9 (Jun 12, 2019)

-   [JENKINS-48452](https://issues.jenkins-ci.org/browse/JENKINS-48452) - Update the description of the
    orphaned item strategy adding a generic explanation that can be
    changed by implementations for a more context specific explanation
    to prevent from misunderstanding.

### Version 6.8 (Apr 5, 2019)

-   Add function to programmatically remove a trigger from a
    folder [PR\#126](https://github.com/jenkinsci/cloudbees-folder-plugin/pull/126)
-   Code cleanup [PR\#124](https://github.com/jenkinsci/cloudbees-folder-plugin/pull/124)
-   Allow implementations of `ComputedFolder` to run initialization
    tasks [PR\#121](https://github.com/jenkinsci/cloudbees-folder-plugin/pull/121)

### Version 6.7 (Nov 16, 2018)

-   [JENKINS-47077](https://issues.jenkins-ci.org/browse/JENKINS-47077) - Tidy up how the periodically if not
    otherwise run trigger for computed folders works to better align
    with its implied contract. 

### Version 6.6 (Jun 25, 2018)

-   [JENKINS-22936](https://issues.jenkins-ci.org/browse/JENKINS-22936) - Modify renaming logic to use the
    new flow introduced in Jenkins 2.110 once [JENKINS-52164](https://issues.jenkins-ci.org/browse/JENKINS-52164) is fixed.
-   Updated the baseline

### Version 6.5.1 (Jun 25, 2018)

-   [JENKINS-52164](https://issues.jenkins-ci.org/browse/JENKINS-52164) - Revert modifications to renaming
    logic until the associated bug is fixed.

### Version 6.5 (Jun 25, 2018)

-   [JENKINS-22936](https://issues.jenkins-ci.org/browse/JENKINS-22936) - Modify renaming logic to use the
    new flow introduced in Jenkins 2.110.

### Version 6.4 (Mar 16, 2018)

-   Show a warning when clicking apply while trying to rename a folder
    instead of a nested view.

### Version 6.3 (Jan 05, 2018)

-   [JENKINS-33622](https://issues.jenkins-ci.org/browse/JENKINS-33622) - Use Jenkins 2.x-style configuration page.
-   [JENKINS-47438](https://issues.jenkins-ci.org/browse/JENKINS-47438) - Race condition with throttling of
    computed folder indexing.

### Version 6.2.1 (Oct 16, 2017)

-   [JENKINS-47416](https://issues.jenkins-ci.org/browse/JENKINS-47416) Updating primary view from older
    plugin versions never worked correctly, but as of 6.2.0 began reporting errors in **Manage Old Data**.

### Version 6.2.0 (Oct 11, 2017)

-   **Now requires Jenkins 2.60.x or later.**
-   [JENKINS-40612](https://issues.jenkins-ci.org/browse/JENKINS-40612) - Performance improvement in rendering.
-   [JENKINS-46933](https://issues.jenkins-ci.org/browse/JENKINS-46933) - Metadata changes.

### Version 6.1.2 (Aug 4, 2017)

-   [JENKINS-45984](https://issues.jenkins-ci.org/browse/JENKINS-45984) - Fix default orphaned item strategy for new instances

### Version 6.1.1 (Aug 3, 2017)

-   [JENKINS-43518](https://issues.jenkins-ci.org/browse/JENKINS-43518) - Interrupt button on indexing log does not work
-   [JENKINS-45823](https://issues.jenkins-ci.org/browse/JENKINS-45823) - Fix default values for Orphaned Item Strategy

### Version 6.1.0 (Jul 17, 2017)

-   [JENKINS-45322](https://issues.jenkins-ci.org/browse/JENKINS-45322)
    Computed folders should disabled orphaned items prior to removal by
    the orphaned item strategy
-   [JENKINS-45501](https://issues.jenkins-ci.org/browse/JENKINS-45501)
    Fix PCT test failures

### Version 6.0.4 (May 2, 2017)

-   [JENKINS-43424](https://issues.jenkins-ci.org/browse/JENKINS-43424)
    PeriodicFolderTrigger demonstrates sympathetic harmonization for
    short intervals

### Version 6.0.3 (March 16, 2017)

-   [JENKINS-35112](https://issues.jenkins-ci.org/browse/JENKINS-35112)
    Interrupt in-progress builds and computations when deleting a Folder
-   [JENKINS-37369](https://issues.jenkins-ci.org/browse/JENKINS-37369)
    Expose the ComputedFolder's last computation results via Last
    Success / Last Failure and Last Duration columns
-   [JENKINS-42680](https://issues.jenkins-ci.org/browse/JENKINS-42680)
    Prevent infinite loop if a Folder is a child of a ComputedFolder

### Version 6.0.2 (March 8, 2017)

-   [JENKINS-42593](https://issues.jenkins-ci.org/browse/JENKINS-42593)
    Critical `NullPointerException` in 6.0.0/6.0.1 fixed.

### Version 6.0.1 (March 8, 2017)

-   [JENKINS-42511](https://issues.jenkins-ci.org/browse/JENKINS-42511) Turn
    on the fix (was accidentally turned off during testing for 6.0.0
    release)

Do not use!
[JENKINS-42593](https://issues.jenkins-ci.org/browse/JENKINS-42593)

### Version 6.0.0 (March 8, 2017)

-   Switch to a more [semver](http://semver.org/){.external-link}-like
    version scheme
-   [JENKINS-42146](https://issues.jenkins-ci.org/browse/JENKINS-42146)
    Added missing i18n bundle keys
-   [JENKINS-40921](https://issues.jenkins-ci.org/browse/JENKINS-40921)
    /
    [JENKINS-33020](https://issues.jenkins-ci.org/browse/JENKINS-33020)
    Only display relevant triggers in a ComputedFolder
-   [JENKINS-41416](https://issues.jenkins-ci.org/browse/JENKINS-41416)
    Fold the computation sub-menu into the main level for a
    ComputedFolder
-   [JENKINS-42511](https://issues.jenkins-ci.org/browse/JENKINS-42511)
    Change ComputedFolder API to define the behaviour for computations
    concurrent with events and fix PeriodicFolderTrigger to prevent a
    race condition that could cause concurrent computations

Do not use!
[JENKINS-42593](https://issues.jenkins-ci.org/browse/JENKINS-42593)

### Version 5.18 (February 22, 2017)

-   [JENKINS-41370](https://issues.jenkins-ci.org/browse/JENKINS-41370)
    Test harness upgrades to fix PCT
-   [JENKINS-41842](https://issues.jenkins-ci.org/browse/JENKINS-41842)
    Additional tests for Computed folder 
-   [JENKINS-42151](https://issues.jenkins-ci.org/browse/JENKINS-42151) Event
    support for Computed folders should be thread safe

### Version 5.17 (February 2, 2017)

-   [JENKINS-41004](https://issues.jenkins-ci.org/browse/JENKINS-41004)
    Credentials Binding does not guarantee folder credential with a
    given ID will be returned over global credential with same ID
-   [JENKINS-41124](https://issues.jenkins-ci.org/browse/JENKINS-41124)
    Add an API that allows the folder's child item URLs to follow a
    different structure from the on-disk directory name where the child
    is stored

### Version 5.16 (January 10, 2017)

-   [JENKINS-40922](https://issues.jenkins-ci.org/browse/JENKINS-40922) Events
    loading wheel needs explanation
-   Added some Japanese translations
-   [JENKINS-38606](https://issues.jenkins-ci.org/browse/JENKINS-38606) follow-up,
    when running on a version of Jenkins that has the fix for
    JENKINS-38606, trigger the migration of views

### Version 5.15 (December 14, 2016)

-   [JENKINS-40440](https://issues.jenkins-ci.org/browse/JENKINS-40440) Computed
    folder should not redirect to computation if not buildable

### Version 5.14 (December 5, 2016)

-   [JENKINS-39213](https://issues.jenkins-ci.org/browse/JENKINS-39213)
    Add ability for computed folders to control the views in the folder
-   [JENKINS-39355](https://issues.jenkins-ci.org/browse/JENKINS-39355)
    follow-up Adding API support to enable better handling of events
    that affect computed folders
-   [JENKINS-39404](https://issues.jenkins-ci.org/browse/JENKINS-39404)
    follow-up - synchronizing with the core changes
-   [JENKINS-38960](https://issues.jenkins-ci.org/browse/JENKINS-38960)
    Align with the IconSpec API
-   Better folder health caching

### Version 5.13 (September 23, 2016)

-   [JENKINS-36160](https://issues.jenkins-ci.org/browse/JENKINS-36160)
    - The credentials provider was not considering the folder as an
    itemgroup when accessed as an Item.
-   [JENKINS-37941](https://issues.jenkins-ci.org/browse/JENKINS-37941)
    - folder plugin missing norefresh=true in RelocationAction

**Version 5.12 (June 15, 2016)**

-   [JENKINS-32309](https://issues.jenkins-ci.org/browse/JENKINS-32309) -
    Feature: Allow Actions to contribute summary items for folders
-   [JENKINS-32359](https://issues.jenkins-ci.org/browse/JENKINS-32359) -
    BugFix: Fix persistence of properties, for folders, which have been
    previously loaded from the disk
-   [JENKINS-34939](https://issues.jenkins-ci.org/browse/JENKINS-34939) -
    Performance: Improve locking behavior for folder delete operations
    to avoid delays in plugins like [JobConfigHistory
    Plugin](https://wiki.jenkins.io/display/JENKINS/JobConfigHistory+Plugin)

### Version 5.11 (May 24, 2016)

-   Upgrade to [Credentials
    Plugin](https://wiki.jenkins.io/display/JENKINS/Credentials+Plugin)
    2.0 APIs for managing folder based credentials stores

### Version 5.10 (May 12, 2016)

-   [JENKINS-33819](https://issues.jenkins-ci.org/browse/JENKINS-33819)
    New API to set the `orphanedItemStrategy` field in `ComputedFolder`.
-   [JENKINS-34306](https://issues.jenkins-ci.org/browse/JENKINS-34306)
    Invalid destinations showed in the UI when you try to move an item.

### Version 5.9 (Apr 27, 2016)

-   [JENKINS-34200](https://issues.jenkins-ci.org/browse/JENKINS-34200){.external-link}
    After creating or reconfiguring a `ComputedFolder` (e.g. used for
    Pipeline Multibranch projects) user is redirected to indexing
    console output.
-   [JENKINS-34465](https://issues.jenkins-ci.org/browse/JENKINS-34465){.external-link}
    Indexing options in the left menu have hardcoded paths.

### Version 5.8 (Apr 12, 2016)

-   [JENKINS-31162](https://issues.jenkins-ci.org/browse/JENKINS-31162)
    Define *New Item* categorizations for use in Jenkins 2.0.

### Version 5.7 (Apr 02, 2016)

-   [JENKINS-25240](https://issues.jenkins-ci.org/browse/JENKINS-25240)
    Another fix to the fix from 5.5.
-   Cleanup from
    [JENKINS-33759](https://issues.jenkins-ci.org/browse/JENKINS-33759)
    fix in 5.6.
-   Matching change from
    [JENKINS-31162](https://issues.jenkins-ci.org/browse/JENKINS-31162)
    Jenkins 2.0 fix needed for *New Item* filtering, including
    [JENKINS-33972](https://issues.jenkins-ci.org/browse/JENKINS-33972)
    API change.

### Version 5.6 (Mar 29, 2016)

-   [JENKINS-25240](https://issues.jenkins-ci.org/browse/JENKINS-25240)
    Fix generalized to organization folders.
-   [JENKINS-33759](https://issues.jenkins-ci.org/browse/JENKINS-33759)
    Removed `new` link in favor of standard `newJob`, which will look
    different in Jenkins 2.0.
-   [JENKINS-33817](https://issues.jenkins-ci.org/browse/JENKINS-33817)
    Can now save backup copies of folder computation log:
    `-Dcom.cloudbees.hudson.plugins.folder.computed.FolderComputation.BACKUP_LOG_COUNT=5`
-   Handling of `AbortException` needed for
    [JENKINS-33815](https://issues.jenkins-ci.org/browse/JENKINS-33815).
-   Better thread names during folder computation to help diagnose
    issues.

### Version 5.5 (Mar 21, 2016)

-   [JENKINS-32179](https://issues.jenkins-ci.org/browse/JENKINS-32179)
    Improper handling of duplicated children in a computed folder, such
    as two branch sources with overlapping branch names.
-   [JENKINS-25240](https://issues.jenkins-ci.org/browse/JENKINS-25240)
    Do not delete branch projects with running builds.
-   Allowing *Delete Folder* to appear in the context menu.
-   [JENKINS-33479](https://issues.jenkins-ci.org/browse/JENKINS-33479)/[JENKINS-33480](https://issues.jenkins-ci.org/browse/JENKINS-33480)
    More general fix.
-   [JENKINS-33006](https://issues.jenkins-ci.org/browse/JENKINS-33006)
    Fixed form round-tripping for hour-unit values in *Periodically if
    not otherwise run* trigger.

### Version 5.4 (Mar 14, 2016)

-   [JENKINS-33479](https://issues.jenkins-ci.org/browse/JENKINS-33479)
    `NullPointerException` when using the [GitHub Organization Folder
    Plugin](https://wiki.jenkins.io/display/JENKINS/GitHub+Organization+Folder+Plugin).
-   Avoid a stack trace when *Credentials* plugin is disabled.

### Version 5.3 (Mar 4, 2016)

-   Correctly handle {{Action}}s contributed by other plugins
-   Allow display name of folder computation to be customized by other
    plugins

### Version 5.2.2 (Feb 25, 2016)

-   Deleted resource file left over from 5.2.

### Version 5.2.1 (Feb 22, 2016)

-   Restored Java 6 compatibility.

### Version 5.2 (Feb 22, 2016)

-   Removed dependency on the [Matrix Authorization Strategy
    Plugin](https://wiki.jenkins.io/display/JENKINS/Matrix+Authorization+Strategy+Plugin).
    **If you accept this update, you must also update that plugin (to
    1.3 or higher), and you must update the** **[Icon Shim
    Plugin](https://wiki.jenkins.io/display/JENKINS/Icon+Shim+Plugin)**
    **(to 2.0.3 or higher).**

### Version 5.1 (Nov 12, 2015)

-   Fixed a bug renaming a folder.
-   [JENKINS-31129](https://issues.jenkins-ci.org/browse/JENKINS-31129)
    Only rebuild a computed folder upon explicit configuration change.
-   API and UI adjustments to computed folder triggers.

### Version 5.0 (Oct 27,2015)

-   **Warning**: binary-incompatible change can result in
    `java.lang.NoSuchFieldError: owner` for `FolderProperty`
    implementations from other plugins. The following plugins, if
    installed, should be updated to match:
    -   CloudBees Folders Plus
    -   CloudBees Templates
    -   CloudBees Role-Based Access Control
    -   GitLab Auth (no fix yet)
-   Non-beta release, no changes since 4.11-beta-1 other than version
    number.

### Version 4.11-beta-1 (Oct 20, 2015)

-   Added new `ComputedFolder` API for folders whose contents are
    determined by an algorithm rather than manual customization.
-   [JENKINS-30951](https://issues.jenkins-ci.org/browse/JENKINS-30951)
    Implemented `ModifiableViewGroup` API.

### Version 4.10 (Sep 17, 2015)

-   Added support for the
    TopLevelItemDescriptor.isApplicableIn(ItemGroup) method introduced
    in Jenkins 1.607+ (plugin baseline Jenkins version remains unchanged
    as the support is via reflection)

### Version 4.9 (Jul 08, 2015)

-   Added an extension point to allow the UI for moving items to be
    extended/augmented by other plugins

### Version 4.8 (May 06, 2015)

-   [JENKINS-28243](https://issues.jenkins-ci.org/browse/JENKINS-28243)
    Allow all item-group-scoped permissions (such as View/\*) to be
    configured when using project-based matrix authorization strategy.
-   [JENKINS-25597](https://issues.jenkins-ci.org/browse/JENKINS-25597)
    Move link incorrectly shown--with a broken icon--for non-top-level
    items.

### Version 4.7 (Oct 30, 2014) (Jenkins 1.554.1+)

-   [JENKINS-25073](https://issues.jenkins-ci.org/browse/JENKINS-25073)
    Avoid double-counting health metrics from subfolders.
-   Better checking of Job/Discover permission.
-   Let user select configure folder’s viewTabBar.
-   Display an enhanced API summary page when running on 1.569+,
    including information on /createItem.

### Version 4.6.1 (Jun 4, 2014) \[Jenkins 1.548+\]

-   Missed one of the permission checks when fixing the permission
    checks in 4.6

### Version 4.6 (May 21, 2014) \[Jenkins 1.548+\]

-   Permission checks on the per-folder credentials store were checking
    against Jenkins and not the folder's permissions.
-   Use transparent PNGs instead of GIFs

### Version 4.5 (Feb 18, 2014) \[Jenkins 1.548+\]

-   Domain scoped credentials within folders were not discoverable via
    the credentials API
-   Configurable default view
    ([JENKINS-20235](https://issues.jenkins-ci.org/browse/JENKINS-20235),
    [JENKINS-20006](https://issues.jenkins-ci.org/browse/JENKINS-20006))
-   Provide views list in breadcrumb bar
    ([JENKINS-20523](https://issues.jenkins-ci.org/browse/JENKINS-20523))

### Version 4.4 (Feb 10, 2014) \[Jenkins 1.548+\]

-   *Move* link was broken in 4.3.
    ([JENKINS-21724](https://issues.jenkins-ci.org/browse/JENKINS-21724))

### Version 4.3 (Jan 21, 2014) \[Jenkins 1.548+\]

-   Taking advantage of APIs in Jenkins 1.548+ that allow items to be
    moved among folders and plugins to receive notifications of this.

### Version 4.2.3 (Jun 12, 2014) \[Jenkins 1.526+\]

-   Backported permission checks from 4.6 and 4.6.1.

### Version 4.2.2 (May 09, 2014) \[Jenkins 1.526+\]

-   Configurable default view
    ([JENKINS-20235](https://issues.jenkins-ci.org/browse/JENKINS-20235),
    [JENKINS-20006](https://issues.jenkins-ci.org/browse/JENKINS-20006))
-   Provide views list in breadcrumb bar
    ([JENKINS-20523](https://issues.jenkins-ci.org/browse/JENKINS-20523))

### Version 4.2.1 (Feb 18, 2014) \[Jenkins 1.480+\]

-   Domain scoped credentials within folders were not discoverable via
    the credentials API (Cherry-picked from 4.5 to provide fix for
    Jenkins instances from 1.480-1.547)

### Version 4.2 (Dec 23, 2013) \[Jenkins 1.480+\]

-   Try harder to enforce subitem restrictions.
    ([JENKINS-21113](https://issues.jenkins-ci.org/browse/JENKINS-21113))

### Version 4.1 (Dec 12, 2013) \[Jenkins 1.480+\]

-   Removed Job Config History plugin integration, with the intention of
    moving it into that plugin.
    ([JENKINS-20990](https://issues.jenkins-ci.org/browse/JENKINS-20990))

### Version 4.0.1 (Nov 28, 2013) \[Jenkins 1.480+\]

-   Added CodeMirror-based syntax highlighting and preview to the folder
    description.        

### Version 4.0 (Oct 10, 2013) \[Jenkins 1.480+\]

-   First open-source release. Formerly a CloudBees free plugin. [Blog post](http://blog.cloudbees.com/2013/10/cloudbees-folders-plugin-now-open-source.html)

### Versions 1.0 - 3.15

Previous versions were closed source, the version history can be found
on [the CloudBees release notes server](http://release-notes.cloudbees.com/product/Folders)
