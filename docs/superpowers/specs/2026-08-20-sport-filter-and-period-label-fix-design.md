# Sport Filter + Period Label Fix — Design

Date: 2026-08-20

Two changes to the History screen:

1. **Period label fix:** the 4-way Day/Week/Month/Year selector truncates "Week"/"Month" with "…" on narrow screens. Make the labels render in full.
2. **Sport filter:** a selector below the Activities/Progress tabs lets the user filter both tabs by a single sport (or all sports together, the current behavior), with an all-time activity count shown on the same line.

## 1. Period label fix

The `statistics_toggle` `MaterialButtonToggleGroup` (statistics.xml:191-231) holds four equal-weight `MaterialButton`s, each ~82dp on a 360dp screen. MaterialButton defaults — `android:minWidth` (~76dp), horizontal `inset` (~6dp/side), internal padding, and all-caps text — leave too little room, so "Week"/"Month" ellipsize.

**Fix:** on all four period buttons add:

- `android:minWidth="0dp"`
- `app:insetLeft="0dp"`
- `app:insetRight="0dp"`
- `android:textAllCaps="false"`

Single-row layout and current label strings are unchanged. This reclaimed text space makes "Day", "Week", "Month", "Year" render fully.

## 2. Sport filter

### 2.1 Placement

A horizontal row in `history.xml` directly below `history_tabs` (lines 26-32) and above both `history_list_content` (line 34) and `statistics_content` (line 78), so it is shared and visible on both tabs. Both content containers are re-constrained below the new row.

### 2.2 Row contents

Left to right:

1. `TextInputLayout` (`style="?attr/textInputStyle"`) wrapping a `MaterialAutoCompleteTextView` (`app:exposedDropdownMenuStyle`), `layout_weight="1"`. Shows the selected entry, e.g. "All sports" or "Running", with a dropdown arrow.
2. `TextView` with the all-time activity count for the selection, e.g. "42 activities" / "1 activity", right-aligned and vertically centered with the field.

### 2.3 Options (9 entries)

- Entry 0: **"All sports"** — internal marker `null` sport filter. Default, matching current behavior.
- Entries 1-8: the 8 sports in `Sport` enum order, labeled via `Sport.getStringArray(res)`: Running, Biking, Other, Orienteering, Walking, Treadmill, Gym, Stationary bike.

Always show all 8 sports (a sport with no activities shows count 0).

### 2.4 Behavior

- Selecting a sport filters **both** the Activities list and the Progress stats by `DB.ACTIVITY.SPORT = <dbValue>`.
- "All sports" removes the filter.
- The same-line count always reflects the current selection (all-time, non-deleted rows).
- Selection persists under new pref key `pref_statistics_sport`, restored on open (same pattern as `pref_statistics_metric`).

### 2.5 Data layer

**Activities list:** `HistoryFragment.onCreateLoader` (HistoryFragment.java:256-275) selection is currently `"deleted == 0"`. When a sport is selected, append `AND SPORT = ?` and pass the sport value as a bind arg. On sport change, `LoaderManager.restartLoader(0, null, this)` re-queries. The sport column is already in the `from` projection.

**Progress stats:**
- `Statistics.queryActivities(db, fromSeconds)` (Statistics.java:171) gains an `Integer sport` parameter (nullable = no filter). Selection becomes `deleted = 0 AND distance IS NOT NULL AND start_time >= ? [AND sport = ?]`.
- `HistoryFragment.loadStatistics()` passes the current sport. Sport change re-runs `loadStatistics()` (re-query + recompute missing elevation + re-render cards/chart). The `from` window stays at 12 years back regardless of sport.
- Summary cards and chart then only include the selected sport's rows.

**Count query:** a grouped query `SELECT sport, COUNT(*) FROM activity WHERE deleted = 0 GROUP BY sport`, plus a total count for "All sports". Only the same-line count is displayed; the dropdown items are plain labels without per-item counts (Option A). Runs on `statisticsExecutor`; result posted to the main thread.

### 2.6 Persistence

Pref key `pref_statistics_sport` stores the sport `dbValue`, or `-1` for "All sports". Read on view creation; written on selection change. Same `PreferenceManager` pattern as `pref_statistics_metric` (HistoryFragment.java:186-194).

### 2.7 Edge cases

- Sport with 0 activities: Activities list shows the empty-state view; cards/chart show zeros. Same as today's empty behavior.
- Sport filter applies to the all-time count (unrestricted window); the stats window remains 12 years.

## Files modified

- `app/res/layout/statistics.xml` — period button attributes (Part 1)
- `app/res/layout/history.xml` — sport selector row + constraint changes
- `app/src/main/org/runnerup/view/HistoryFragment.java` — selector wiring, sport state + persistence, loader selection, count query, pass sport into `loadStatistics`
- `app/src/main/org/runnerup/db/Statistics.java` — `queryActivities` sport overload; count helper
- `common/src/main/res/values/strings.xml` — `Statistics_all_sports`, `Statistics_activities_count`

## Tests

JUnit 4 in `app/test/java`:

- `StatisticsTest.java`: `queryActivities` with sport filter (matching rows only; `null` = all); grouped count query (correct per-sport counts and total).
- No fragment/UI unit tests (repo pattern); covered by build + device smoke.

## Verification

- Gates: `./gradlew test`, `:app:lintLatestDebug` (only pre-existing okhttp error at app/build.gradle:174), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug`.
- Device smoke (`6a6743fd`): Day/Week/Month/Year labels render fully; selector shows "All sports" + total by default; selecting a sport filters both tabs and shows that sport's count; count and selection survive force-stop + relaunch.