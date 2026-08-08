# Wipe-based "Permanently delete" and import "Replace"

Date: 2026-08-08
Status: Approved

## Problem

Two related bugs come from the app swapping out its database *file* underneath live
`SQLiteOpenHelper` connections:

- The **"Permanently delete"** preference in Settings → Maintenance (`pref_prunedb`) runs a
  soft-delete cleanup with no confirmation, and users read it as a full wipe. It never touches
  live activities, so entries "come back" on reload — broken expectations, no confirmation.
- Import **Replace** copies the imported file over `databases/runnerup.db` and requires a
  restart ("Copied N bytes … Restart app to use the database"). Because `DBHelper.close()` is a
  no-op (see `DBHelper.java:220-227`), the cached connections stay open across the file swap;
  subsequent DB access reads stale/malformed state → wrong data shown, and importing again
  before a restart crashes.

## Goal

Drop the file-swap logic entirely. "Emptying" the database = deleting rows through the app's
normal open connection inside a transaction. Takes effect immediately; no restart for delete or
import, ever.

## Decisions (confirmed with user)

1. **"Permanently delete" becomes a full wipe.** It replaces the current soft-delete cleanup
   (`purgeDeletedActivities` and the "Clean deleted activities" summary are dropped). Deleting
   requires a confirmation dialog.
2. **Wipe scope:** clear all rows from `activity`, `lap`, `location`, `report`, `feed`.
   **Keep** `account` (login credentials) and `audio_schemes` (voice prompt config).
3. **No extra confirmation after choosing "Replace"** in the Merge/Replace/Cancel dialog — the
   choice itself is the confirmation.
4. **"Replace" = wipe + fresh import**, not a byte-for-byte file restore. Consequences the user
   accepted: local accounts/audio schemes survive; imported `report` rows are remapped to local
   accounts by name and silently skipped when no name matches; imported soft-deleted activities
   are skipped (they are not restored). This semantic change is inherent to dropping the file
   swap.

## Design

### 1. Shared wipe — `DBHelper.clearAllActivities(Context ctx)`

- Runs on the main thread (fast: a handful of `DELETE`s), inside one transaction.
- Deletes children first, then parents: `report`, `location`, `lap`, `feed`, then `activity`.
  The schema has no FK `ON DELETE CASCADE`, so order is required for correctness if FKs are ever
  added (`DBHelper.deleteActivity` already deletes in this order).
- Guarded by the existing `hasOngoingActivity(ctx)` — same blocked dialog as import. Deleting
  the activity being recorded would corrupt tracker state.
- Does not run `VACUUM`; the file keeps allocated pages. Harmless, not worth the cost.

### 2. "Permanently delete" preference (Settings → Maintenance)

- Click → confirmation dialog ("This will permanently delete all activities and their laps,
  locations and upload history. This cannot be undone." / Delete / Cancel).
- On confirm → `clearAllActivities`. History reloads on next tab visit (`HistoryFragment`
  restarts its loader in `onResume`, `HistoryFragment.java:107-110`) — no restart needed.
- On success → small result dialog ("All activities deleted").
- Preference title/summary strings updated to reflect a full wipe; `pref_prunedb` key reused.

### 3. Import "Replace"

- `DBHelper.replaceDatabase` (file copy + restart dialog) is **deleted**.
- The choice dialog's Replace button calls the merge path with a `clearFirst` flag:
  `DatabaseImporter.doMerge(ctx, tempDbFile, handler, clearFirst)`.
- When `clearFirst`, the wipe runs **inside the same transaction** as the inserts, before any
  activity rows are added. Replace is atomic: a mid-import failure leaves the old DB intact,
  not half-wiped.
- After the wipe the local duplicate index is empty → conflict dialogs are skipped; every
  non-deleted imported activity inserts fresh; reports remap by local account name; audio
  schemes `CONFLICT_IGNORE`. Result dialog ("Merged: N imported, …") confirms.
- `copyToTempDb`, `hasOngoingActivity`, and the Merge path are unchanged.

### 4. Deleted code

- `DBHelper.replaceDatabase` and its `FileUtil.copyFile` swap.
- `DBHelper.purgeDeletedActivities` and the `pref_prunedb` progress dialog in
  `SettingsMaintenanceFragment` (`onPruneClick`).
- The "Copied N bytes … Restart app to use the database" dialog.

## Strings

New app-local strings (app `res/values/strings.xml`):
- `delete_all_confirm_title` / `delete_all_confirm_message` / `delete_all_confirm_ok`
- `delete_all_done` ("All activities deleted")
- Updated preference title/summary for the wipe meaning (or reuse with new wording).

## Verification

- Gates: `./gradlew spotlessApply spotlessCheck test :app:lintLatestDebug :app:assembleLatestDebug`.
- JVM tests: classification logic unchanged; no new unit tests expected (wipe is row deletes).
  Add one only if a helper worth testing emerges.
- Device smoke (Nexus 5X `025b46e24edcbca6`, `org.runnerup.debug`, VIEW-intent entry path from
  the merge feature):
  - W1. Wipe shows confirmation; Cancel leaves DB untouched.
  - W2. Confirm wipes → History empty immediately (no restart); `activity`/`lap`/`location`/
    `report`/`feed` empty; `account`/`audio_schemes` intact.
  - W3. Import immediately after wipe works without crash.
  - R1. Replace import → old activities gone, imported present, no restart, no crash, result
    dialog shows imported count.
  - G1. Wipe and Replace are blocked while an in-progress run exists.

## Known limitation (deferred, 2026-08-08)

The in-progress guard (`hasOngoingActivity` → `time IS NULL AND distance IS NULL`) can be
defeated if the user opens the History tab while a run is recording: the pre-existing
`HistoryFragment.onViewCreated` → `ActivityCleaner.conditionalRecompute` rewrites the last
activity's NULL time/distance to `0`/`0.0` (its purpose is repairing crash-leftover
activities, which are indistinguishable in the DB from an in-progress run). After that the
guard no longer matches and a subsequent wipe/import is not blocked during the run. All other
scenarios verified PASS. Deferred hardening (chosen 2026-08-08): introduce a global recording
flag set by `Tracker.start()`/save/stop and have the guard (or `conditionalRecompute`) consult
it, so "blocked while running" is reliable regardless of navigation.
