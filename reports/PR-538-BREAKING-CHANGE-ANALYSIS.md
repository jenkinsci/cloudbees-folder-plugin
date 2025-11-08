# PR #538 Breaking Change Analysis

**PR**: https://github.com/jenkinsci/cloudbees-folder-plugin/pull/538
**Date**: 2025-11-08
**Status**: ⚠️ BREAKING CHANGE IDENTIFIED

---

## Executive Summary

The icon migration PR (#538) **will break** all branch source plugins (GitHub Branch Source, Bitbucket Branch Source, etc.) because they hardcode references to `"icon-folder"` and `"icon-folder-disabled"` CSS classes that this PR removes.

**Impact**: GitHub Organizations, Bitbucket projects, and multibranch pipelines will show **black ×** instead of folder icons.

---

## Root Cause

### What the PR Changes

**File**: `Folder.java`
**Changes**: IconSet registration migration

```java
// BEFORE (current master)
IconSet.icons.addIcon(new Icon("icon-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", ...));
IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", ...));

// AFTER (PR #538 migrate-icons-to-symbols branch)
IconSet.icons.addIcon(new Icon("symbol-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", ...));
IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", ...));
```

### What Branch-API Plugin Depends On

**Plugin**: branch-api-plugin (dependency of all branch source plugins)
**File**: `jenkins/branch/MetadataActionFolderIcon.java`
**Lines**: 74, 111

```java
@Override
public String getIconClassName() {
    if (owner != null) {
        if (owner.isDisabled()) {
            return "icon-folder-disabled";  // ❌ HARDCODED STRING
        }
        // ... metadata action logic ...
    }
    return "icon-folder";  // ❌ HARDCODED STRING (fallback)
}
```

**Used by**:
- `OrganizationFolder` (GitHub Organizations, Bitbucket projects)
- `MultiBranchProject` (all multibranch pipelines)

---

## Impact Assessment

### Affected Plugins

1. **branch-api-plugin** ⚠️
   - Returns hardcoded `"icon-folder"` and `"icon-folder-disabled"`
   - Used by ALL branch source implementations

2. **github-branch-source-plugin** ⚠️
   - GitHub Organizations use `MetadataActionFolderIcon`
   - Will show black × for org folders

3. **bitbucket-branch-source-plugin** ⚠️
   - Bitbucket projects use `MetadataActionFolderIcon`
   - Will show black × for project folders

4. **gitlab-branch-source-plugin** ⚠️
   - GitLab organizations use `MetadataActionFolderIcon`
   - Will show black × for org folders

5. **All multibranch pipelines** ⚠️
   - Use `MultiBranchProject` which extends branch-api
   - Folder icons will break

### User-Visible Impact

**Before PR** (working):
```
📁 My GitHub Organization
  └─ 📁 repository-1
  └─ 📁 repository-2
```

**After PR** (broken):
```
❌ My GitHub Organization
  └─ ❌ repository-1
  └─ ❌ repository-2
```

**Severity**: HIGH - Very visible to users, affects core Jenkins workflow (GitHub orgs, multibranch)

---

## Proposed Solutions

### Option 1: Backward-Compatible Transition (RECOMMENDED)

Register **BOTH** icon sets during transition period.

**Change in `Folder.java`**:
```java
static {
    // Modern symbol- icons (preferred)
    IconSet.icons.addIcon(new Icon("symbol-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_SMALL_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder icon-md", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_MEDIUM_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder icon-lg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_LARGE_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder icon-xlg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_XLARGE_STYLE));

    IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_SMALL_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-md", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_MEDIUM_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-lg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_LARGE_STYLE));
    IconSet.icons.addIcon(new Icon("symbol-folder-disabled icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_XLARGE_STYLE));

    // Deprecated icon- classes (backward compatibility for branch-api-plugin)
    // TODO: Remove after branch-api-plugin migrates to symbol- format
    IconSet.icons.addIcon(new Icon("icon-folder icon-sm", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_SMALL_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder icon-md", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_MEDIUM_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder icon-lg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_LARGE_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder icon-xlg", "plugin/cloudbees-folder/images/svgs/folder.svg", Icon.ICON_XLARGE_STYLE));

    IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-sm", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_SMALL_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-md", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_MEDIUM_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-lg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_LARGE_STYLE));
    IconSet.icons.addIcon(new Icon("icon-folder-disabled icon-xlg", "plugin/cloudbees-folder/images/svgs/folder-disabled.svg", Icon.ICON_XLARGE_STYLE));
}
```

**Pros**:
- ✅ No breaking changes - both icon sets work
- ✅ Can merge folder-plugin PR immediately
- ✅ Gives time for branch-api-plugin to migrate
- ✅ Safe rollout strategy

**Cons**:
- ⚠️ Temporary code duplication (12 extra lines)
- ⚠️ Requires follow-up PR to clean up deprecated icons

**Timeline**:
1. **Week 1**: Merge folder-plugin with both icon sets
2. **Week 2-4**: Update branch-api-plugin to use `symbol-folder`
3. **Week 5**: Remove deprecated `icon-*` registrations from folder-plugin

---

### Option 2: Coordinated PRs (Complex)

Update branch-api-plugin first, then folder-plugin.

**PR 1 (branch-api-plugin)**:
```java
// File: jenkins/branch/MetadataActionFolderIcon.java
@Override
public String getIconClassName() {
    if (owner != null) {
        if (owner.isDisabled()) {
            return "symbol-folder-disabled";  // Changed
        }
        // ... metadata action logic ...
    }
    return "symbol-folder";  // Changed
}
```

**PR 2 (folder-plugin)**: Current PR #538 (no changes needed)

**Pros**:
- ✅ Clean migration, no temporary duplication
- ✅ Modern approach

**Cons**:
- ❌ Requires branch-api-plugin PR approval first
- ❌ Delays folder-plugin merge
- ❌ Coordination overhead across 2 repos
- ❌ Risk: What if branch-api PR stalls?

---

### Option 3: Convert to Non-Breaking Change (Safe Alternative)

Keep ONLY Jelly migrations, don't change IconSet registrations.

**Keep `icon-*` registrations in Folder.java**, only update Jelly files to use modern syntax:
```xml
<!-- Jelly files -->
<l:icon src="symbol-folder"/>  <!-- Modern syntax -->
```

But keep Java IconSet registrations as `icon-folder` for backward compatibility.

**Pros**:
- ✅ Jelly files use modern patterns
- ✅ No breaking changes to dependent plugins
- ✅ Can merge immediately

**Cons**:
- ⚠️ Incomplete migration (Java still uses deprecated names)
- ⚠️ Doesn't fully solve the icon deprecation issue

---

## Recommendation

**Use Option 1: Backward-Compatible Transition**

### Migration Plan

#### Phase 1: Folder Plugin (This PR)
1. **Update PR #538** to register BOTH icon sets
2. **Add deprecation comment** in code
3. **Update PR description** with migration timeline
4. **Get approval** from maintainers (@alecharp, @rsandell, @jglick)
5. **Merge** folder-plugin with both icon sets

#### Phase 2: Branch-API Plugin (New PR)
1. **Create PR** in branch-api-plugin
2. **Update** `MetadataActionFolderIcon.java` to return `symbol-folder*`
3. **Test** with GitHub Branch Source, Bitbucket Branch Source
4. **Merge** branch-api-plugin

#### Phase 3: Cleanup (Follow-up PR)
1. **Wait** for branch-api-plugin adoption (1-2 months)
2. **Create PR** in folder-plugin to remove deprecated `icon-*` registrations
3. **Update release notes** about icon deprecation removal

---

## Testing Requirements

### Before Merging PR #538

1. **Build folder-plugin** with backward-compatible fix
2. **Start test Jenkins** with:
   - GitHub Branch Source Plugin
   - Bitbucket Branch Source Plugin
3. **Create**:
   - GitHub Organization folder
   - Bitbucket project folder
   - Regular folder (baseline)
4. **Verify icons display correctly** (no black ×)
5. **Screenshot evidence** for PR

### After Branch-API Update

1. **Build branch-api-plugin** with symbol- migration
2. **Test with folder-plugin** (both old and new)
3. **Verify no regressions**

---

## Files to Update

### Immediate (PR #538)

- `src/main/java/com/cloudbees/hudson/plugins/folder/Folder.java`
  - Add backward-compatible icon registrations
  - Add deprecation comment

### Future (Branch-API PR)

- `branch-api-plugin/src/main/java/jenkins/branch/MetadataActionFolderIcon.java`
  - Lines 74, 111: Change to `symbol-folder*`

### Cleanup (Future PR)

- `src/main/java/com/cloudbees/hudson/plugins/folder/Folder.java`
  - Remove deprecated `icon-*` registrations

---

## Communication Plan

### Update PR #538 Comment

Post findings and proposed solution to PR:

```markdown
## 🚨 Breaking Change Identified

After investigation, I've confirmed that merging this PR **as-is would break**:
- GitHub Organizations
- Bitbucket projects
- All multibranch pipelines

### Root Cause

The branch-api-plugin hardcodes `"icon-folder"` and `"icon-folder-disabled"` in `MetadataActionFolderIcon.java` (lines 74, 111). These CSS classes would no longer exist after this PR.

### Proposed Solution

I'll update this PR to register **BOTH** icon sets during the transition:
- New: `symbol-folder*` (preferred)
- Deprecated: `icon-folder*` (backward compatibility)

This allows:
1. ✅ Merge this PR safely (no breaking changes)
2. ✅ Time for branch-api-plugin to migrate
3. ✅ Clean up deprecated icons in future PR

### Testing Plan

- Setting up test Jenkins with GitHub Branch Source
- Will verify icons display correctly
- Screenshots coming soon

Detailed analysis: [link to report]
```

### Contact Branch-API Maintainers

- Open issue in branch-api-plugin repository
- Link to this analysis
- Propose PR timeline
- Coordinate release schedule

---

## Risk Assessment

### High Risk (Current PR without fix)

- ⚠️ Breaks GitHub orgs, Bitbucket projects, multibranch pipelines
- ⚠️ Very visible to users
- ⚠️ Could block Jenkins upgrades
- ⚠️ Reputation risk for thoroughness

### Low Risk (With backward-compatible fix)

- ✅ No breaking changes
- ✅ Smooth migration path
- ✅ Time to coordinate with branch-api
- ✅ Safe rollout

---

## Timeline Estimate

| Phase | Duration | Status |
|-------|----------|--------|
| Document findings | 2 hours | ✅ Complete |
| Update PR #538 with backward-compat fix | 1 hour | ⏳ Next |
| Test with branch source plugins | 2 hours | ⏳ Pending |
| Get PR approval | 1-3 days | ⏳ Pending |
| Merge folder-plugin | 1 day | ⏳ Pending |
| Create branch-api PR | 2 hours | 📅 Week 2 |
| Branch-api review & merge | 1-2 weeks | 📅 Week 3-4 |
| Cleanup deprecated icons | 1 hour | 📅 Week 5+ |

---

## References

### PRs
- **Folder Plugin**: https://github.com/jenkinsci/cloudbees-folder-plugin/pull/538

### Source Files
- **Folder.java**: `/src/jenkins/plugins/cloudbees-folder-plugin/src/main/java/com/cloudbees/hudson/plugins/folder/Folder.java`
- **MetadataActionFolderIcon.java**: `/src/jenkins/plugins/branch-api-plugin/src/main/java/jenkins/branch/MetadataActionFolderIcon.java`
- **OrganizationFolder.java**: `/src/jenkins/plugins/branch-api-plugin/src/main/java/jenkins/branch/OrganizationFolder.java`

### Related Work
- Icon migration patterns: `/src/jenkins/jenkins-core-monitor/docs/migrations/ICON-PATTERNS.md`
- Jenkins core PR: https://github.com/jenkinsci/jenkins/pull/10245

---

**Analysis by**: Thorsten Scherler (@scherler)
**Date**: 2025-11-08
**Severity**: HIGH (breaking change)
**Recommendation**: Implement backward-compatible transition (Option 1)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
