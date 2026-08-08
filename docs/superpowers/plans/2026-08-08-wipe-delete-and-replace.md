# Wipe-Based Delete and Import Replace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the DB file-swap logic (delete-all and import-replace) with row-level clearing of the live database, so no restart is ever needed and importing right after a wipe never crashes.

**Architecture:** A single shared operation `clearAllActivities` deletes all rows from `activity`/`lap`/`location`/`report`/`feed` (keeping `account` + `audio_schemes` and the DB file itself). The "Permanently delete" Maintenance button runs it after a confirmation dialog. Import "Replace" runs it inside the merge transaction before inserting imported rows (a `clearFirst` flag on `DatabaseImporter.doMerge`), atomically. The old `replaceDatabase` file-copy + "Restart app" path and the soft-delete `purgeDeletedActivities` cleanup are deleted.

**Tech Stack:** Java, AndroidX appcompat `AlertDialog`/`MaterialAlertDialogBuilder`, `SQLiteDatabase`. Gradle multi-module; changes touch `app` module only.

## Global Constraints

- Gates (in order, from AGENTS.md): `./gradlew spotlessApply spotlessCheck test :app:lintLatestDebug :app:assembleLatestDebug`. Lint baseline `app/lint-baseline.xml` filters pre-existing issues; do not fail on them, and add no new issues.
- `:app:lintLatestDebug` promotes `InlinedApi`/`InconsistentArrays` to fatal via `app/lint.xml`.
- Do **not** add code comments (AGENTS.md: "No comments added to code unless asked"). googleJavaFormat via `spotlessApply`.
- Repo: `master`, push to `fork` only. Never stage `gradle.properties`, `AGENTS.md`, `gradle/gradle-daemon-jvm.properties`.
- Testing: no `androidTest` infra; JVM unit tests in `app/test/java` cover pure logic only (`ConflictClassifierTest`). DB-level behavior is verified by build + device smoke in Task 6.
- Strings: add to `app/res/values/strings.xml` only (pattern of existing `import_*` strings); reuse `org.runnerup.common.R.string.Cancel` / `.OK` for standard buttons.

---

### Task 1: Add `clearAllActivities` to DBHelper

**Files:**
- Modify: `app/src/main/org/runnerup/db/DBHelper.java`
- Modify: `app/res/values/strings.xml` (add `delete_all_confirm_title`, `delete_blocked_activity_in_progress`)

**Interfaces:**
- Produces: `public static boolean clearAllActivities(Context ctx)` — returns `false` and shows a blocked dialog when a run is in progress, otherwise wipes and returns `true`.
- Produces: `static void clearAllActivities(SQLiteDatabase db)` — package-private, deletes all activity rows (no transaction; caller owns transaction). Used by `DatabaseImporter` in Task 2.
- Produces: `private static void showBlockedDialog(Context ctx, String title, int messageRes)` — extracted helper reused by `importDatabase` and `clearAllActivities`.

- [ ] **Step 1: Add strings**

In `app/res/values/strings.xml`, before the closing `</resources>`:

```xml
    <string name="delete_all_confirm_title">Delete all activities</string>
    <string name="delete_blocked_activity_in_progress">
        Cannot delete while a run is in progress. Stop the run first.
    </string>
```

- [ ] **Step 2: Add the blocked-dialog helper and rewire `importDatabase`**

In `DBHelper.java`, replace the inline blocked dialog inside `importDatabase` (currently `DBHelper.java:718-726`):

```java
  public static void importDatabase(Context ctx, Uri from) {
    if (hasOngoingActivity(ctx)) {
      showBlockedDialog(ctx, "Import " + DBNAME, R.string.import_blocked_activity_in_progress);
      return;
    }
```

Add the helper next to `importDatabase`:

```java
  private static void showBlockedDialog(Context ctx, String title, int messageRes) {
    new MaterialAlertDialogBuilder(ctx)
        .setTitle(title)
        .setMessage(messageRes)
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
        .show();
  }
```

- [ ] **Step 3: Add the clear methods**

Add after `hasOngoingActivity` (i.e., after `DBHelper.java:715`):

```java
  public static boolean clearAllActivities(Context ctx) {
    if (hasOngoingActivity(ctx)) {
      showBlockedDialog(
          ctx, ctx.getString(R.string.delete_all_confirm_title), R.string.delete_blocked_activity_in_progress);
      return false;
    }

    SQLiteDatabase db = getWritableDatabase(ctx);
    db.beginTransaction();
    try {
      clearAllActivities(db);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return true;
  }

  static void clearAllActivities(SQLiteDatabase db) {
    db.delete(DB.EXPORT.TABLE, null, null);
    db.delete(DB.LOCATION.TABLE, null, null);
    db.delete(DB.LAP.TABLE, null, null);
    db.delete(DB.FEED.TABLE, null, null);
    db.delete(DB.ACTIVITY.TABLE, null, null);
  }
```

- [ ] **Step 4: Verify build + lint**

Run: `./gradlew :app:assembleLatestDebug :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, lint no new issues.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/db/DBHelper.java app/res/values/strings.xml
git commit -m "feat: add database clear-all helper with in-progress guard"
```

---

### Task 2: Support `clearFirst` in the merge import

**Files:**
- Modify: `app/src/main/org/runnerup/db/DatabaseImporter.java`

**Interfaces:**
- Consumes: `DBHelper.clearAllActivities(SQLiteDatabase db)` from Task 1.
- Produces: `static void merge(Context ctx, File tempDbFile)` — unchanged behavior (delegates with `clearFirst = false`).
- Produces: `static void merge(Context ctx, File tempDbFile, boolean clearFirst)` — when true, wipes the live DB inside the merge transaction before inserting.

- [ ] **Step 1: Add the `clearFirst` overload to `merge`**

Replace the existing `merge` header + guard (currently `DatabaseImporter.java:60-65`):

```java
  static void merge(Context ctx, File tempDbFile) {
    merge(ctx, tempDbFile, false);
  }

  static void merge(Context ctx, File tempDbFile, boolean clearFirst) {
    if (DBHelper.hasOngoingActivity(ctx)) {
      showBlockedDialog(ctx);
      tempDbFile.delete();
      return;
    }
```

- [ ] **Step 2: Thread `clearFirst` into `doMerge`**

Change the call inside `merge`'s executor (currently `DatabaseImporter.java:85`):

```java
            MergeResult result = doMerge(ctx, tempDbFile, handler, clearFirst);
```

Change the signature (currently `DatabaseImporter.java:128`):

```java
  private static MergeResult doMerge(
      Context ctx, File tempDbFile, Handler handler, boolean clearFirst) throws Exception {
```

- [ ] **Step 3: Restructure conflict detection and add the in-transaction wipe**

Replace the body of `doMerge` from the `try {` after `MergeResult result = new MergeResult();` through the `live.endTransaction();` block (currently `DatabaseImporter.java:136-210`) with:

```java
    try {
      List<ImportedActivity> importedActivities =
          readActivities(imported, tableColumns(live, DB.ACTIVITY.TABLE));

      List<ImportedActivity> toImport = new ArrayList<>();
      for (ImportedActivity activity : importedActivities) {
        if (activity.deleted) {
          result.skippedDeleted++;
          continue;
        }
        toImport.add(activity);
      }

      List<Conflict> conflicts = new ArrayList<>();
      List<Decision> decisions = Collections.emptyList();
      if (!clearFirst) {
        Map<String, Long> localIndex = readLocalIndex(live);
        List<ImportedActivity> distinct = new ArrayList<>();
        for (ImportedActivity activity : toImport) {
          if (isDuplicate(localIndex, activity.startTime, activity.type)) {
            conflicts.add(
                new Conflict(activity, localIndex.get(key(activity.startTime, activity.type))));
          } else {
            distinct.add(activity);
          }
        }
        toImport = distinct;

        if (!conflicts.isEmpty()) {
          decisions = resolveConflicts(ctx, conflicts, handler);
          if (containsAbort(decisions)) {
            result.cancelled = true;
            return result;
          }
        }
      }

      Set<String> lapCols = tableColumns(live, DB.LAP.TABLE);
      Set<String> locationCols = tableColumns(live, DB.LOCATION.TABLE);
      Set<String> reportCols = tableColumns(live, DB.EXPORT.TABLE);
      Set<String> audioCols = tableColumns(live, DB.AUDIO_SCHEMES.TABLE);

      Map<Long, Long> idMap = new HashMap<>();

      live.beginTransaction();
      try {
        if (clearFirst) {
          DBHelper.clearAllActivities(live);
        }

        for (ImportedActivity activity : toImport) {
          long newId = live.insert(DB.ACTIVITY.TABLE, null, activity.values);
          idMap.put(activity.oldId, newId);
          result.mergedActivityIds.add(newId);
          result.imported++;
        }

        for (int i = 0; i < conflicts.size(); i++) {
          Decision decision = decisions.get(i);
          Conflict conflict = conflicts.get(i);
          switch (decision.resolution) {
            case KEEP:
              result.kept++;
              break;
            case OVERWRITE:
              overwriteActivity(live, conflict, idMap);
              result.mergedActivityIds.add(conflict.localId);
              result.overwritten++;
              break;
            case DUPLICATE:
              long newId = live.insert(DB.ACTIVITY.TABLE, null, conflict.imported.values);
              idMap.put(conflict.imported.oldId, newId);
              result.mergedActivityIds.add(newId);
              result.duplicated++;
              break;
            case ABORT:
              break;
          }
        }

        importChildren(live, imported, idMap, lapCols, locationCols);
        importReport(live, imported, idMap, reportCols);
        importAudioSchemes(live, imported, audioCols);

        live.setTransactionSuccessful();
      } finally {
        live.endTransaction();
      }

      return result;
    } finally {
      imported.close();
      tempDbFile.delete();
    }
  }
```

- [ ] **Step 4: Verify tests + build + lint**

Run: `./gradlew test :app:assembleLatestDebug :app:lintLatestDebug`
Expected: `ConflictClassifierTest` (8 tests) still passes, BUILD SUCCESSFUL, lint no new issues.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/db/DatabaseImporter.java
git commit -m "refactor: support clearing the database before a merge import"
```

---

### Task 3: Wire import "Replace" to the wipe-based merge and delete `replaceDatabase`

**Files:**
- Modify: `app/src/main/org/runnerup/db/DBHelper.java`

**Interfaces:**
- Consumes: `DatabaseImporter.merge(Context, File, boolean)` from Task 2.
- Removes: `private static void replaceDatabase(Context ctx, Uri from, File tempDbFile)` and the "Copied N bytes … Restart app to use the database" dialog.

- [ ] **Step 1: Point the Replace button at the wipe-based merge**

In `importDatabase` (currently `DBHelper.java:746-749`), replace the Replace callback:

```java
        .setNegativeButton(
            R.string.import_choice_replace,
            (dialog, which) -> DatabaseImporter.merge(ctx, tempDbFile, true))
```

- [ ] **Step 2: Delete `replaceDatabase`**

Delete the entire `replaceDatabase` method (currently `DBHelper.java:753-783`).

- [ ] **Step 3: Verify build + lint**

Run: `./gradlew :app:assembleLatestDebug :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, lint no new issues. If unused-import warnings appear, remove them.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/org/runnerup/db/DBHelper.java
git commit -m "fix: replace import clears then re-imports instead of swapping files"
```

---

### Task 4: Confirmation + full-wipe behavior for the "Delete all activities" button

**Files:**
- Modify: `app/src/main/org/runnerup/view/SettingsMaintenanceFragment.java`
- Modify: `app/res/xml/settings_maintenance.xml`
- Modify: `app/res/values/strings.xml`

**Interfaces:**
- Consumes: `DBHelper.clearAllActivities(Context)` from Task 1.
- Produces: `onPruneClick` now shows a confirmation dialog, wipes on confirm, and shows a result dialog.

- [ ] **Step 1: Add strings**

In `app/res/values/strings.xml`, before the closing `</resources>`:

```xml
    <string name="delete_all_confirm_message">
        This will permanently delete all activities and their laps, locations and upload history. This cannot be undone.
    </string>
    <string name="delete_all_confirm_ok">Delete</string>
    <string name="delete_all_done">All activities deleted</string>
    <string name="delete_all_summary">Delete all activities and their laps, locations and upload history from the database</string>
```

- [ ] **Step 2: Update the preference**

In `app/res/xml/settings_maintenance.xml`, replace the `pref_prunedb` Preference block (currently lines 45-49):

```xml
        <Preference
            android:key="@string/pref_prunedb"
            android:summary="@string/delete_all_summary"
            android:title="@string/delete_all_confirm_title"
            app:iconSpaceReserved="false" />
```

- [ ] **Step 3: Replace `onPruneClick` in the fragment**

In `SettingsMaintenanceFragment.java`, replace the whole `onPruneClick` field (currently lines 99-106):

```java
  private final Preference.OnPreferenceClickListener onPruneClick =
      preference -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all_confirm_title)
            .setMessage(R.string.delete_all_confirm_message)
            .setPositiveButton(
                R.string.delete_all_confirm_ok,
                (dialog, which) -> {
                  if (DBHelper.clearAllActivities(requireContext())) {
                    new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.delete_all_confirm_title)
                        .setMessage(R.string.delete_all_done)
                        .setPositiveButton(
                            org.runnerup.common.R.string.OK, (d, w) -> d.dismiss())
                        .show();
                  }
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
            .show();
        return true;
      };
```

- [ ] **Step 4: Fix imports**

In `SettingsMaintenanceFragment.java`, remove `import org.runnerup.util.M3ProgressDialog;` and add `import com.google.android.material.dialog.MaterialAlertDialogBuilder;`. Verify `org.runnerup.R` is imported (already is) and used (`R.string.delete_all_*`).

- [ ] **Step 5: Verify build + lint**

Run: `./gradlew :app:assembleLatestDebug :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, lint no new issues.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/SettingsMaintenanceFragment.java app/res/xml/settings_maintenance.xml app/res/values/strings.xml
git commit -m "feat: confirm before permanently deleting all activities"
```

---

### Task 5: Delete the soft-delete cleanup

**Files:**
- Modify: `app/src/main/org/runnerup/db/DBHelper.java`

**Interfaces:**
- Removes: `public static void purgeDeletedActivities(Context, M3ProgressDialog, Runnable)` and its now-unused imports.

- [ ] **Step 1: Delete `purgeDeletedActivities`**

Delete the entire method (currently `DBHelper.java:618-668`).

- [ ] **Step 2: Remove now-unused imports**

Remove from `DBHelper.java`: `import android.os.Handler;`, `import android.os.Looper;`, `import java.util.concurrent.ExecutorService;`, `import java.util.concurrent.Executors;`, `import org.runnerup.util.M3ProgressDialog;`.

- [ ] **Step 3: Verify tests + build + lint**

Run: `./gradlew test :app:assembleLatestDebug :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, lint no new issues.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/org/runnerup/db/DBHelper.java
git commit -m "refactor: drop soft-delete cleanup purge"
```

---

### Task 6: Full gates + device smoke

**Files:**
- (no source changes expected; docs only if needed)

- [ ] **Step 1: Run all gates**

Run: `./gradlew spotlessApply spotlessCheck test :app:lintLatestDebug :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL; lint reports only baseline issues ("N errors filtered by baseline").

- [ ] **Step 2: Install and seed the test device**

Device: Nexus 5X `025b46e24edcbca6`, package `org.runnerup.debug`. Install the fresh APK, grant location (3rd party) + physical activity permissions, enable stay-awake, then seed two live activities + one soft-deleted (`deleted=1`) row using the host-sqlite pull/push technique from AGENTS.md (`adb exec-out run-as org.runnerup.debug cat databases/runnerup.db` / push via `/data/local/tmp` + `run-as ... cp`), and confirm with `SELECT _id, name, deleted FROM activity`.

- [ ] **Step 3: Smoke W1 (cancel keeps DB)**

Settings → Maintenance ("Delete all activities" row) → tap → confirmation dialog appears → tap Cancel. Pull DB and confirm `SELECT count(*) FROM activity` is unchanged.

- [ ] **Step 4: Smoke W2 (confirm wipes immediately, no restart)**

Settings → Maintenance → "Delete all activities" → confirm → "All activities deleted" dialog. Without restarting the app, switch to History → list is empty. Pull DB and confirm `activity`, `lap`, `location`, `report`, `feed` all have 0 rows while `account` and `audio_schemes` are non-empty (seed data intact).

- [ ] **Step 5: Smoke W3 (import right after wipe works, no crash)**

Push a small valid RunnerUp DB to the app cache and launch via VIEW intent (`adb shell am start -a android.intent.action.VIEW -d file:///data/data/org.runnerup.debug/cache/import.db -t application/octet-stream -n org.runnerup.debug/org.runnerup.view.MainLayout`). Merge → result dialog with imported count; app does not crash.

- [ ] **Step 6: Smoke R1 (Replace wipes then imports, no restart)**

With activities present, VIEW-import again and choose **Replace**. Result dialog shows the imported count. Without restart, pull DB and confirm old activities are gone and imported activities/laps/locations/reports are present.

- [ ] **Step 7: Smoke G1 (guarded while a run is in progress)**

Inject a live in-progress row (`time IS NULL AND distance IS NULL AND deleted=0`). Trigger "Delete all activities" → blocked dialog (message "Cannot delete while a run is in progress…"). VIEW-import → Replace → blocked dialog ("Cannot import while a run is in progress…"). Confirm DB untouched, then remove the injected row.

- [ ] **Step 8: Re-run gates and update docs**

Run all gates once more; expected BUILD SUCCESSFUL. Update `docs/superpowers/specs/2026-08-08-wipe-delete-and-replace-design.md` or the progress ledger with the smoke results if any deviation is noted.
