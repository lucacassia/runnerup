# Sport Filter + Period Label Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a sport filter (with all-time activity count) to the History screen that filters both the Activities and Progress tabs, and fix the truncated Day/Week/Month/Year labels on the Progress chart period selector.

**Architecture:** A dropdown row (`TextInputLayout` + `MaterialAutoCompleteTextView`) is inserted into `history.xml` below the Activities/Progress tabs; selecting a sport updates a fragment-level `Integer currentSport` (null = all). The Activities list loader query and `Statistics.queryActivities` gain an optional sport clause; a new `Statistics.sportCounts` grouped query feeds the count TextView. Persistence mirrors the existing metric-toggle pref.

**Tech Stack:** Java, AndroidX, Material 3 (`TextInputLayout`/`MaterialAutoCompleteTextView`, `MaterialButton`), SQLite (`SQLiteDatabase`), LoaderManager, JUnit 4 + Mockito 5.

## Global Constraints

- Do NOT add code comments unless a plan snippet explicitly includes them.
- Strings live in the `common` module → `org.runnerup.common.R.string` / `org.runnerup.common.R.plurals`. Drawables/layouts in `app/res/` (NOT `app/src/main/res/`). App uses non-transitive `org.runnerup.R`.
- Tests are JUnit 4 in `app/test/java` (non-standard; `sourceSets` root is `test`). Correct unit-test task: `./gradlew :app:testLatestDebugUnitTest --tests "<TestClass>"` (plain `./gradlew test` does NOT accept `--tests`).
- Gate order: `./gradlew test` → `:app:lintLatestDebug` (only pre-existing okhttp error at `app/build.gradle:174` allowed; never fix it) → `spotlessApply`/`spotlessCheck` → `:app:assembleLatestDebug`.
- `DB.ACTIVITY.SPORT` column is named `"type"` (`Constants.java:43`); `DB.ACTIVITY.SPORT_MAX = 7`; sport dbValues are 0..7.
- Conventional commits. Do NOT stage user-local files: `gradle.properties`, `gradle-daemon-jvm.properties`, `local.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- Device for smoke tests: Redmi `6a6743fd`, app `org.runnerup.debug`, APK `app/build/outputs/apk/latest/debug/app-latest-debug.apk`.

---

### Task 1: Sport-aware queries in Statistics

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java` (`queryActivities` at line 171, add `sportCounts`)
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java`

**Interfaces:**
- Consumes: `Constants.DB.ACTIVITY` (`TABLE`, `SPORT`=`"type"`, `DELETED`, `DISTANCE`, `START_TIME`, `TIME`, `ELEVATION_GAIN`, `SPORT_MAX`), `Constants.DB.PRIMARY_KEY`. `ActivityRow` as currently defined.
- Produces:
  - `queryActivities(SQLiteDatabase, long fromSeconds, Integer sport)` — new 3-arg overload; `sport == null` = no filter.
  - `queryActivities(SQLiteDatabase, long fromSeconds)` — kept 2-arg, delegates to 3-arg with `null`. (Task 4 switches the caller to 3-arg.)
  - `sportCounts(SQLiteDatabase) -> int[]` — length `DB.ACTIVITY.SPORT_MAX + 1`, index = sport dbValue, value = all-time non-deleted count; 0 where absent. Later used by Task 4.

- [ ] **Step 1: Write the failing tests**

Append to `StatisticsTest.java`. Add imports:

```java
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.mockito.ArgumentCaptor;
import org.runnerup.common.util.Constants.DB;
```

Append the tests:

```java
@Test
public void queryActivitiesAppliesSportFilter() {
  SQLiteDatabase db = mock(SQLiteDatabase.class);
  Cursor cursor = mock(Cursor.class);
  when(cursor.moveToNext()).thenReturn(true, false);
  when(cursor.getLong(0)).thenReturn(1L);
  when(cursor.getLong(1)).thenReturn(at("2026-08-14"));
  when(cursor.getDouble(2)).thenReturn(1000.0);
  when(cursor.isNull(3)).thenReturn(true);
  when(cursor.isNull(4)).thenReturn(true);
  when(db.query(eq(DB.ACTIVITY.TABLE), any(String[].class), anyString(), any(String[].class),
          isNull(), isNull(), anyString()))
      .thenReturn(cursor);
  List<Statistics.ActivityRow> rows = Statistics.queryActivities(db, at("2026-01-01"), 0);
  assertEquals(1, rows.size());
  assertEquals(1000.0, rows.get(0).distance, 0.0);
  ArgumentCaptor<String> selection = ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
  verify(db)
      .query(eq(DB.ACTIVITY.TABLE), any(String[].class), selection.capture(), args.capture(),
          isNull(), isNull(), anyString());
  assertTrue(selection.getValue().contains("type = ?"));
  assertEquals(2, args.getValue().length);
  assertEquals("0", args.getValue()[1]);
}

@Test
public void queryActivitiesWithoutSportOmitsFilter() {
  SQLiteDatabase db = mock(SQLiteDatabase.class);
  Cursor cursor = mock(Cursor.class);
  when(cursor.moveToNext()).thenReturn(false);
  when(db.query(eq(DB.ACTIVITY.TABLE), any(String[].class), anyString(), any(String[].class),
          isNull(), isNull(), anyString()))
      .thenReturn(cursor);
  List<Statistics.ActivityRow> rows = Statistics.queryActivities(db, at("2026-01-01"), null);
  assertEquals(0, rows.size());
  ArgumentCaptor<String> selection = ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
  verify(db)
      .query(eq(DB.ACTIVITY.TABLE), any(String[].class), selection.capture(), args.capture(),
          isNull(), isNull(), anyString());
  assertFalse(selection.getValue().contains("type = ?"));
  assertEquals(1, args.getValue().length);
}

@Test
public void queryActivitiesTwoArgDelegatesToThreeArg() {
  SQLiteDatabase db = mock(SQLiteDatabase.class);
  Cursor cursor = mock(Cursor.class);
  when(cursor.moveToNext()).thenReturn(true, true, false);
  when(cursor.getLong(0)).thenReturn(1L, 2L);
  when(cursor.getLong(1)).thenReturn(at("2026-08-14"), at("2026-08-13"));
  when(cursor.getDouble(2)).thenReturn(1000.0, 2000.0);
  when(cursor.isNull(3)).thenReturn(true, true);
  when(cursor.isNull(4)).thenReturn(true, true);
  when(db.query(eq(DB.ACTIVITY.TABLE), any(String[].class), anyString(), any(String[].class),
          isNull(), isNull(), anyString()))
      .thenReturn(cursor);
  List<Statistics.ActivityRow> rows = Statistics.queryActivities(db, at("2026-01-01"));
  assertEquals(2, rows.size());
  ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
  verify(db)
      .query(eq(DB.ACTIVITY.TABLE), any(String[].class), anyString(), args.capture(),
          isNull(), isNull(), anyString());
  assertEquals(1, args.getValue().length);
}

@Test
public void sportCountsGroupsBySport() {
  SQLiteDatabase db = mock(SQLiteDatabase.class);
  Cursor cursor = mock(Cursor.class);
  when(cursor.moveToNext()).thenReturn(true, true, false);
  when(cursor.getInt(0)).thenReturn(0, 4);
  when(cursor.getInt(1)).thenReturn(3, 5);
  when(db.query(eq(DB.ACTIVITY.TABLE), any(String[].class), anyString(), any(String[].class),
          eq(DB.ACTIVITY.SPORT), isNull(), isNull()))
      .thenReturn(cursor);
  int[] counts = Statistics.sportCounts(db);
  assertEquals(DB.ACTIVITY.SPORT_MAX + 1, counts.length);
  assertEquals(3, counts[0]);
  assertEquals(5, counts[4]);
  assertEquals(0, counts[1]);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: FAIL — `queryActivities(SQLiteDatabase, long, Integer)` and `sportCounts(SQLiteDatabase)` not found (compile error). Existing tests still pass.

- [ ] **Step 3: Implement in Statistics.java**

Replace `queryActivities` (lines 171-201) with a 2-arg overload + a 3-arg overload; add `sportCounts` after it:

```java
  public static List<ActivityRow> queryActivities(SQLiteDatabase db, long fromSeconds) {
    return queryActivities(db, fromSeconds, null);
  }

  public static List<ActivityRow> queryActivities(
      SQLiteDatabase db, long fromSeconds, Integer sport) {
    String selection =
        ACTIVITY.DELETED
            + " = 0 AND "
            + ACTIVITY.DISTANCE
            + " IS NOT NULL AND "
            + ACTIVITY.START_TIME
            + " >= ?";
    String[] args;
    if (sport != null) {
      selection += " AND " + ACTIVITY.SPORT + " = ?";
      args = new String[] {Long.toString(fromSeconds), Integer.toString(sport)};
    } else {
      args = new String[] {Long.toString(fromSeconds)};
    }
    List<ActivityRow> rows = new ArrayList<>();
    try (Cursor cursor =
        db.query(
            ACTIVITY.TABLE,
            new String[] {
              DB.PRIMARY_KEY,
              ACTIVITY.START_TIME,
              ACTIVITY.DISTANCE,
              ACTIVITY.TIME,
              ACTIVITY.ELEVATION_GAIN
            },
            selection,
            args,
            null,
            null,
            ACTIVITY.START_TIME + " ASC")) {
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        Double time = cursor.isNull(3) ? null : cursor.getDouble(3);
        Double elevationGain = cursor.isNull(4) ? null : cursor.getDouble(4);
        rows.add(new ActivityRow(id, cursor.getLong(1), cursor.getDouble(2), time, elevationGain));
      }
    }
    return rows;
  }

  public static int[] sportCounts(SQLiteDatabase db) {
    int[] counts = new int[ACTIVITY.SPORT_MAX + 1];
    try (Cursor cursor =
        db.query(
            ACTIVITY.TABLE,
            new String[] {ACTIVITY.SPORT, "count(*)"},
            ACTIVITY.DELETED + " = 0",
            null,
            ACTIVITY.SPORT,
            null,
            null)) {
      while (cursor.moveToNext()) {
        int sport = cursor.getInt(0);
        if (sport >= 0 && sport < counts.length) {
          counts[sport] = cursor.getInt(1);
        }
      }
    }
    return counts;
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: PASS — all tests green, including the 4 new ones.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: add sport filter and counts to statistics queries"
```

---

### Task 2: Strings and period-label fix

**Files:**
- Modify: `common/src/main/res/values/strings.xml` (after line 280)
- Modify: `app/res/layout/statistics.xml` (period buttons, lines 200-230)

**Interfaces:**
- Consumes: existing `Statistics_*` string cluster; existing `statistics_toggle_day/week/month/year` button ids.
- Produces:
  - `org.runnerup.common.R.string.Statistics_all_sports` = "All sports"
  - `org.runnerup.common.R.string.pref_statistics_sport` = "pref_statistics_sport"
  - `org.runnerup.common.R.plurals.Statistics_activities_count` — "%d activity" (one) / "%d activities" (other)
  - Four period buttons no longer truncate their labels.

- [ ] **Step 1: Add strings**

In `common/src/main/res/values/strings.xml`, after line 280 (`pref_statistics_metric`), add:

```xml
    <string name="Statistics_all_sports">All sports</string>
    <string name="pref_statistics_sport">pref_statistics_sport</string>
```

After the closing `</string>` of `Statistics_switch_to_line` (line 278), add the plural (place it right before `pref_statistics_chart` at line 279):

```xml
    <plurals name="Statistics_activities_count">
        <item quantity="one">%d activity</item>
        <item quantity="other">%d activities</item>
    </plurals>
```

- [ ] **Step 2: Fix the four period buttons**

In `app/res/layout/statistics.xml`, add these four attributes to `statistics_toggle_day` (lines 200-206), `statistics_toggle_week` (208-214), `statistics_toggle_month` (216-222), and `statistics_toggle_year` (224-230):

```xml
                android:minWidth="0dp"
                android:textAllCaps="false"
                app:insetLeft="0dp"
                app:insetRight="0dp"
```

The day button becomes:

```xml
            <com.google.android.material.button.MaterialButton
                android:id="@+id/statistics_toggle_day"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:minWidth="0dp"
                android:textAllCaps="false"
                app:insetLeft="0dp"
                app:insetRight="0dp"
                android:text="@string/Statistics_day" />
```

Repeat the same four attributes on the week, month, and year buttons, preserving each button's id and text.

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add common/src/main/res/values/strings.xml app/res/layout/statistics.xml
git commit -m "feat: add sport filter strings and fix period button labels"
```

---

### Task 3: Sport selector row in history layout

**Files:**
- Modify: `app/res/layout/history.xml` (after line 32, and constraint edits at lines 41, 86)

**Interfaces:**
- Consumes: `@style/Widget.Material3.TextInputLayout.OutlinedBox.Dense.ExposedDropdownMenu` (already used by `material_title_spinner.xml`); strings from Task 2.
- Produces:
  - `@id/history_sport_filter` — the row LinearLayout
  - `@id/history_sport_selector` — `MaterialAutoCompleteTextView` (Task 4 sets its adapter)
  - `@id/history_sport_count` — `TextView` (Task 4 sets its text)
  - Both `history_list_content` and `statistics_content` re-constrained below the new row.

- [ ] **Step 1: Insert the selector row**

In `app/res/layout/history.xml`, immediately after the closing `</com.google.android.material.tabs.TabLayout>` (line 32), insert:

```xml
    <LinearLayout
        android:id="@+id/history_sport_filter"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/history_tabs">

        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/history_sport_field"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox.Dense.ExposedDropdownMenu"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1">

            <com.google.android.material.textfield.MaterialAutoCompleteTextView
                android:id="@+id/history_sport_selector"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:cursorVisible="false"
                android:focusable="false"
                android:inputType="none" />
        </com.google.android.material.textfield.TextInputLayout>

        <TextView
            android:id="@+id/history_sport_count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant" />
    </LinearLayout>
```

- [ ] **Step 2: Re-constrain the content containers**

Change `history_list_content` (line 41) and `statistics_content` (line 86) so each uses `app:layout_constraintTop_toBottomOf="@id/history_sport_filter"` instead of `"@id/history_tabs"`.

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/history.xml
git commit -m "feat: add sport filter row to history layout"
```

---

### Task 4: Wire the sport filter in HistoryFragment

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java`

**Interfaces:**
- Consumes: `Statistics.queryActivities(db, from, Integer)`, `Statistics.sportCounts(db)` (Task 1); `@id/history_sport_selector`, `@id/history_sport_count` (Task 3); `Statistics_all_sports`, `pref_statistics_sport`, plural `Statistics_activities_count` (Task 2); `Sport.getStringArray(Resources)`; `Constants.DB.ACTIVITY.SPORT`.
- Produces: fragment-level `Integer currentSport` (null = all) that filters the Activities loader, the Progress `loadStatistics()` window, and drives the count TextView; persistence under `pref_statistics_sport`.

- [ ] **Step 1: Add imports**

Add to `HistoryFragment.java` imports:

```java
import android.widget.ArrayAdapter;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
```

- [ ] **Step 2: Add fields**

After `statistics365Value` (line 100), add:

```java
  private Integer currentSport = null; // null = all sports
  private MaterialAutoCompleteTextView sportSelector;
  private TextView sportCountText;
```

- [ ] **Step 3: Wire the selector in onViewCreated**

After the `statisticsChart.setLabelFormatter(...)` line (144), add:

```java
    sportSelector = view.findViewById(R.id.history_sport_selector);
    sportCountText = view.findViewById(R.id.history_sport_count);
    SharedPreferences sportPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    int savedSport = sportPrefs.getInt(getString(org.runnerup.common.R.string.pref_statistics_sport), -1);
    currentSport = savedSport >= 0 ? savedSport : null;
    String[] sportNames = Sport.getStringArray(getResources());
    String[] entries = new String[sportNames.length + 1];
    entries[0] = getString(org.runnerup.common.R.string.Statistics_all_sports);
    System.arraycopy(sportNames, 0, entries, 1, sportNames.length);
    ArrayAdapter<String> sportAdapter =
        new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, entries);
    sportSelector.setAdapter(sportAdapter);
    sportSelector.setText(entries[currentSport == null ? 0 : currentSport + 1], false);
    sportSelector.setOnItemClickListener(
        (parent, view, position, id) -> {
          currentSport = position == 0 ? null : position - 1;
          sportPrefs
              .edit()
              .putInt(
                  getString(org.runnerup.common.R.string.pref_statistics_sport),
                  currentSport == null ? -1 : currentSport)
              .apply();
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          if (currentTab == TAB_STATISTICS_INDEX) {
            loadStatistics();
          }
          updateSportCount();
        });
    updateSportCount();
    if (currentSport != null) {
      LoaderManager.getInstance(this).restartLoader(0, null, this);
    }
```

The final `if` re-applies a restored sport filter on fragment recreation: `initLoader` (line 132) runs with `currentSport` still null, so without this the loader would show all sports until the user re-picks one.

- [ ] **Step 4: Filter the Activities loader**

Replace the body of `onCreateLoader` (lines 256-275) so selection is built from `currentSport`:

```java
  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    String[] from =
        new String[] {
          "_id",
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.SPORT,
          DB.ACTIVITY.AVG_HR
        };
    String selection = "deleted == 0";
    String[] args = null;
    if (currentSport != null) {
      selection += " AND " + DB.ACTIVITY.SPORT + " = ?";
      args = new String[] {Integer.toString(currentSport)};
    }
    return new SimpleCursorLoader(
        requireContext(), mDB, DB.ACTIVITY.TABLE, from, selection, args,
        DB.ACTIVITY.START_TIME + " desc");
  }
```

- [ ] **Step 5: Pass sport into loadStatistics**

Change line 314 `Statistics.queryActivities(mDB, from)` to `Statistics.queryActivities(mDB, from, currentSport)`.

- [ ] **Step 6: Add updateSportCount**

Add this method after `loadStatistics()` (after line 329):

```java
  private void updateSportCount() {
    if (mDB == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          int[] counts = Statistics.sportCounts(mDB);
          mainHandler.post(
              () -> {
                int count = 0;
                if (currentSport == null) {
                  for (int c : counts) {
                    count += c;
                  }
                } else if (currentSport >= 0 && currentSport < counts.length) {
                  count = counts[currentSport];
                }
                sportCountText.setText(
                    getResources()
                        .getQuantityString(
                            org.runnerup.common.R.plurals.Statistics_activities_count,
                            count,
                            count));
              });
        });
  }
```

- [ ] **Step 7: Build to verify**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: wire sport filter into history and progress"
```

---

### Task 5: Final verification and device smoke test

**Files:** none (verification only)

**Interfaces:**
- Consumes: the full feature from Tasks 1-4.

- [ ] **Step 1: Full gate run**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug && ./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: all pass; lint shows only the pre-existing okhttp error at `app/build.gradle:174`; spotless must be clean on the committed tree.

- [ ] **Step 2: Install on device**

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
```

- [ ] **Step 3: Manual smoke test**

Device `6a6743fd`, app `org.runnerup.debug`:
- Open History → Progress: the four period buttons show full "Day"/"Week"/"Month"/"Year" (no "…").
- The selector row below the tabs shows "All sports" with the total activity count on the same line.
- Select a sport (e.g. Running): the Activities list shows only Running rows, the Progress cards/chart show only Running totals, and the count updates to the Running all-time count. Confirm at least one other sport exists so filtering is visibly different (use `adb shell dumpsys activity top` / `uiautomator dump` for assertions).
- Select "All sports": everything returns to unfiltered.
- Force-stop and relaunch: the selected sport and its count are restored.

- [ ] **Step 4: Push final commit (if any fixes were needed)**

If the smoke test revealed issues, fix them, rerun gates, and commit. Otherwise no new commit.

---

## Self-Review Notes

- **Spec coverage:** Part 1 (period label fix) → Task 2. Part 2 (selector placement, row contents, options, behavior, persistence) → Tasks 2-4. Part 3 (data layer: loader filter, `queryActivities` sport param, count query, persistence) → Tasks 1, 4. Part 4 (tests + verification) → Tasks 1, 5. Edge cases (0-count sport, 12-year window unchanged) → covered by `updateSportCount` guard and unchanged `loadStatistics` window.
- **Placeholder scan:** no TBD/TODO; every step has concrete code or commands.
- **Type consistency:** `queryActivities(SQLiteDatabase, long, Integer)` identical between Task 1 and Task 4 call site; `sportCounts(SQLiteDatabase) -> int[]` consistent; `currentSport` (Integer, null = all) consistent across Tasks 1, 3, 4; pref key `pref_statistics_sport` consistent between Task 2 string and Task 4 read/write; plurals `Statistics_activities_count` consistent.
- **Cross-task ordering:** Task 1 keeps the 2-arg `queryActivities` delegating so `HistoryFragment.java:314` compiles until Task 4 switches it. Task 2's strings are consumed by Tasks 3 (layout references none at compile time, but count TextView is plain) and 4. Task 3's row ids are consumed by Task 4.