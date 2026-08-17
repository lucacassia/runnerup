# Progress Tab Multi-Metric Selector — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Distance / Time / Elevation segmented button to the Progress tab that switches summary cards and bar chart to the selected metric.

**Architecture:** DB migration adds `elevation_gain` column. `TrackerElevation` accumulates gain during recording. `Statistics.java` gains a `Metric` enum and generalized `totals()`/`bucketize()` methods. UI adds a `MaterialButtonToggleGroup` above the summary cards. `HistoryFragment` re-renders from cached rows on metric toggle.

**Tech Stack:** Android SQLite, Material3 `MaterialButtonToggleGroup`, SharedPreferences, JUnit 4.

## Global Constraints

- Never stage `gradle.properties`, `gradle-daemon-jvm.properties`, `local.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- No code comments in edits unless asked.
- Conventional commits (`fix:`, `refactor:`, `feat:`, `style:`, `docs:`).
- `./gradlew test` (all tests), `./gradlew :app:lintLatestDebug` (do NOT fail on baseline 25), `./gradlew spotlessApply` then `spotlessCheck`, `./gradlew :app:assembleLatestDebug`, `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`.
- Use `:app:testLatestDebugUnitTest --tests "..."` (NOT `./gradlew test --tests`).
- `Formatter.java:823` `FormatterChanges` is pre-existing — do not fix.
- `NewerVersionAvailable` okhttp lint error is pre-existing.

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `common/.../util/Constants.java:39` | Modify | Add `ELEVATION_GAIN` to `DB.ACTIVITY` |
| `app/.../db/DBHelper.java:54` | Modify | Bump DBVERSION 31→32, add `ALTER TABLE` migration, add `elevation_gain` to `CREATE_TABLE_ACTIVITY` |
| `app/.../db/Statistics.java` | Modify | Add `Metric` enum, `Double elevationGain` on `ActivityRow`, generalize `totals()`/`bucketize()`, update `queryActivities()`, add lazy computation |
| `app/.../tracker/component/TrackerElevation.java` | Modify | Add elevation gain accumulator, write to DB on `onEnd()` |
| `app/.../view/HistoryFragment.java` | Modify | Add metric toggle listener, `SharedPreferences`, re-render on metric change, lazy computation in background |
| `app/res/layout/statistics.xml` | Modify | Add `MaterialButtonToggleGroup` for metric selection |
| `common/src/main/res/values/strings.xml` | Modify | Add 3 metric label strings |
| `app/test/.../StatisticsTest.java` | Modify | Add tests for multi-metric `totals()` and `bucketize()` |

---

### Task 1: Constants + DB Migration

**Files:**
- Modify: `common/src/main/java/org/runnerup/common/util/Constants.java:52` (after `DELETED`)
- Modify: `app/src/main/org/runnerup/db/DBHelper.java:54` (DBVERSION) and `:65` (CREATE_TABLE_ACTIVITY) and `:264` (onUpgrade)
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java` (no changes needed here — DB tests are integration)

**Interfaces:**
- Consumes: nothing
- Produces: `Constants.DB.ACTIVITY.ELEVATION_GAIN` string constant; DB schema with nullable `elevation_gain real` column

- [ ] **Step 1: Add constant to `Constants.java`**

In `Constants.java`, inside `interface ACTIVITY` (after line 52 `DELETED`), add:

```java
String ELEVATION_GAIN = "elevation_gain";
```

- [ ] **Step 2: Add column to CREATE_TABLE_ACTIVITY**

In `DBHelper.java`, in the `CREATE_TABLE_ACTIVITY` string (after line 80 `nullColumnHack text null`), add before the closing `);`:

Change:
```
+ ("deleted integer not null default 0, ")
+ "nullColumnHack text null"
+ ");";
```
To:
```
+ ("deleted integer not null default 0, ")
+ (DB.ACTIVITY.ELEVATION_GAIN + " real, ")
+ "nullColumnHack text null"
+ ");";
```

- [ ] **Step 3: Bump DBVERSION**

In `DBHelper.java:54`, change:
```java
private static final int DBVERSION = 31;
```
To:
```java
private static final int DBVERSION = 32;
```

- [ ] **Step 4: Add migration in onUpgrade**

In `DBHelper.java`, after the `if (oldVersion < 31)` block (line 370) and before the commented-out `if (oldVersion < 32)` block (line 373), add:

```java
if (oldVersion < 32) {
  echoDo(
      arg0,
      "alter table " + DB.ACTIVITY.TABLE + " add column " + DB.ACTIVITY.ELEVATION_GAIN + " real");
}
```

- [ ] **Step 5: Run spotless and build check**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/org/runnerup/common/util/Constants.java \
        app/src/main/org/runnerup/db/DBHelper.java
git commit -m "feat: add elevation_gain column to activity table"
```

---

### Task 2: Statistics.java — Metric Enum, ActivityRow, Generalized Methods

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java`
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java`

**Interfaces:**
- Consumes: `Constants.DB.ACTIVITY.ELEVATION_GAIN` (from Task 1)
- Produces: `Statistics.Metric` enum, `ActivityRow` with `elevationGain`, `totals(rows, metric, now, zone)`, `bucketize(rows, metric, period, now, zone)`, `computeElevationGain(db, rows)`

- [ ] **Step 1: Write failing tests**

The existing test helper `rows(Object... pairs)` in `StatisticsTest.java` creates `ActivityRow` with 2 args (startTime, distance). Since `ActivityRow` now requires an `id` first arg, update the helper:

```java
private static long nextId = 1;

private static List<ActivityRow> rows(Object... pairs) {
  List<ActivityRow> rows = new ArrayList<>();
  for (int i = 0; i < pairs.length; i += 2) {
    rows.add(new ActivityRow(nextId++, (Long) pairs[i], (Double) pairs[i + 1]));
  }
  return rows;
}

```

Add to `StatisticsTest.java`:

```java
import org.runnerup.db.Statistics.Metric;

@Test
public void totalsTimeSumsActivityDuration() {
  long now = at("2026-08-14") + 12 * 3600;
  List<ActivityRow> rows = new ArrayList<>();
  rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 1800.0)); // 30 min
  rows.add(new ActivityRow(2, at("2026-08-10"), 2000.0, 3600.0)); // 60 min
  double[] totals = Statistics.totals(rows, Metric.TIME, now, UTC);
  assertEquals(1800.0, totals[0], 0.0);
  assertEquals(5400.0, totals[1], 0.0);
  assertEquals(5400.0, totals[2], 0.0);
}

@Test
public void totalsElevationSumsGain() {
  long now = at("2026-08-14") + 12 * 3600;
  List<ActivityRow> rows = new ArrayList<>();
  rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 50.0));
  rows.add(new ActivityRow(2, at("2026-08-10"), 2000.0, 120.0));
  double[] totals = Statistics.totals(rows, Metric.ELEVATION_GAIN, now, UTC);
  assertEquals(50.0, totals[0], 0.0);
  assertEquals(170.0, totals[1], 0.0);
  assertEquals(170.0, totals[2], 0.0);
}

@Test
public void totalsElevationSkipsNull() {
  long now = at("2026-08-14") + 12 * 3600;
  List<ActivityRow> rows = new ArrayList<>();
  rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, (Double) null));
  rows.add(new ActivityRow(2, at("2026-08-10"), 2000.0, 100.0));
  double[] totals = Statistics.totals(rows, Metric.ELEVATION_GAIN, now, UTC);
  assertEquals(0.0, totals[0], 0.0);
  assertEquals(100.0, totals[1], 0.0);
}

@Test
public void bucketizeTimeGroupsByMetricValue() {
  long now = at("2026-08-14") + 12 * 3600;
  List<ActivityRow> rows = new ArrayList<>();
  rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 1800.0));
  rows.add(new ActivityRow(2, at("2026-08-13"), 2000.0, 3600.0));
  double[] buckets = Statistics.bucketize(rows, Metric.TIME, BucketPeriod.DAY, now, UTC);
  assertEquals(12, buckets.length);
  assertEquals(1800.0, buckets[11], 0.0);
  assertEquals(3600.0, buckets[10], 0.0);
}

@Test
public void bucketizeElevationGroupsByMetricValue() {
  long now = at("2026-08-14") + 12 * 3600;
  List<ActivityRow> rows = new ArrayList<>();
  rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 50.0));
  rows.add(new ActivityRow(2, at("2026-08-13"), 2000.0, 120.0));
  double[] buckets = Statistics.bucketize(rows, Metric.ELEVATION_GAIN, BucketPeriod.DAY, now, UTC);
  assertEquals(12, buckets.length);
  assertEquals(50.0, buckets[11], 0.0);
  assertEquals(120.0, buckets[10], 0.0);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: FAIL (compile error — no `Metric` enum, no 3-arg constructor, wrong `totals` signature)

- [ ] **Step 3: Add Metric enum and update ActivityRow**

In `Statistics.java`, add the `Metric` enum inside the class (after the `BucketPeriod` enum):

```java
public enum Metric {
  DISTANCE,
  TIME,
  ELEVATION_GAIN
}
```

Replace the `ActivityRow` class:

```java
public static final class ActivityRow {
  public final long id;
  public final long startTime;
  public final double distance;
  public final Double time;
  public final Double elevationGain;

  public ActivityRow(long id, long startTime, double distance) {
    this(id, startTime, distance, null, null);
  }

  public ActivityRow(long id, long startTime, double distance, Double time) {
    this(id, startTime, distance, time, null);
  }

  public ActivityRow(long id, long startTime, double distance, Double time, Double elevationGain) {
    this.id = id;
    this.startTime = startTime;
    this.distance = distance;
    this.time = time;
    this.elevationGain = elevationGain;
  }
}
```

- [ ] **Step 4: Generalize totals()**

Replace the existing `totals()` method with:

```java
public static double[] totals(List<ActivityRow> rows, Metric metric, long nowSeconds, ZoneId zone) {
  double[] totals = new double[3];
  LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
  long todayWeekKey = key(today, BucketPeriod.WEEK);
  long todayMonthKey = key(today, BucketPeriod.MONTH);
  int todayYear = today.getYear();
  for (ActivityRow row : rows) {
    if (row.startTime > nowSeconds) {
      continue;
    }
    double value = metricValue(row, metric);
    if (Double.isNaN(value)) {
      continue;
    }
    LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
    if (key(date, BucketPeriod.WEEK) == todayWeekKey) {
      totals[0] += value;
    }
    if (key(date, BucketPeriod.MONTH) == todayMonthKey) {
      totals[1] += value;
    }
    if (date.getYear() == todayYear) {
      totals[2] += value;
    }
  }
  return totals;
}

private static double metricValue(ActivityRow row, Metric metric) {
  switch (metric) {
    case DISTANCE:
      return row.distance;
    case TIME:
      return row.time != null ? row.time : Double.NaN;
    case ELEVATION_GAIN:
      return row.elevationGain != null ? row.elevationGain : Double.NaN;
    default:
      throw new IllegalArgumentException("unknown metric " + metric);
  }
}
```

- [ ] **Step 5: Generalize bucketize()**

Replace the existing `bucketize()` method with:

```java
public static double[] bucketize(
    List<ActivityRow> rows, Metric metric, BucketPeriod period, long nowSeconds, ZoneId zone) {
  double[] buckets = new double[bucketCount(period)];
  LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
  long todayKey = key(today, period);
  for (ActivityRow row : rows) {
    if (row.startTime > nowSeconds) {
      continue;
    }
    double value = metricValue(row, metric);
    if (Double.isNaN(value)) {
      continue;
    }
    LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
    long dayDiff = todayKey - key(date, period);
    int offset;
    switch (period) {
      case DAY:
      case MONTH:
        offset = (int) dayDiff;
        break;
      case WEEK:
        offset = (int) (dayDiff / 7);
        break;
      default:
        throw new IllegalArgumentException("unknown period " + period);
    }
    if (offset >= 0 && offset < buckets.length) {
      buckets[buckets.length - 1 - offset] += value;
    }
  }
  return buckets;
}
```

- [ ] **Step 6: Update queryActivities() to fetch time and elevation_gain**

Replace the existing `queryActivities()` method:

```java
public static List<ActivityRow> queryActivities(SQLiteDatabase db, long fromSeconds) {
  List<ActivityRow> rows = new ArrayList<>();
  try (Cursor cursor =
      db.query(
          ACTIVITY.TABLE,
          new String[] {
            DB.PRIMARY_KEY, ACTIVITY.START_TIME, ACTIVITY.DISTANCE,
            ACTIVITY.TIME, ACTIVITY.ELEVATION_GAIN
          },
          ACTIVITY.DELETED
              + " = 0 AND "
              + ACTIVITY.DISTANCE
              + " IS NOT NULL AND "
              + ACTIVITY.START_TIME
              + " >= ?",
          new String[] {Long.toString(fromSeconds)},
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
```

- [ ] **Step 7: Add lazy elevation computation method**

Add this new static method to `Statistics.java`:

```java
public static void computeMissingElevation(SQLiteDatabase db, List<ActivityRow> rows) {
  for (int i = 0; i < rows.size(); i++) {
    ActivityRow row = rows.get(i);
    if (row.elevationGain != null) {
      continue;
    }
    double gain = computeElevationGainForActivity(db, row.id);
    rows.set(i, new ActivityRow(row.id, row.startTime, row.distance, row.time, gain));
    ContentValues cv = new ContentValues();
    cv.put(ACTIVITY.ELEVATION_GAIN, gain);
    db.update(ACTIVITY.TABLE, cv, DB.PRIMARY_KEY + " = ?",
        new String[] {Long.toString(row.id)});
  }
}

private static double computeElevationGainForActivity(SQLiteDatabase db, long activityId) {
  double gain = 0;
  Double prevAlt = null;
  try (Cursor cursor =
      db.query(
          "location",
          new String[] {DB.LOCATION.ALTITUDE},
          DB.LOCATION.ACTIVITY + " = ? AND " + DB.LOCATION.ALTITUDE + " IS NOT NULL ORDER BY "
              + DB.LOCATION.TIME + " ASC",
          new String[] {Long.toString(activityId)},
          null, null, null)) {
    while (cursor.moveToNext()) {
      double alt = cursor.getDouble(0);
      if (prevAlt != null) {
        double delta = alt - prevAlt;
        if (delta > 0) {
          gain += delta;
        }
      }
      prevAlt = alt;
    }
  }
  return gain;
}
```

Add the needed imports to `Statistics.java`:

```java
import android.content.ContentValues;
import org.runnerup.common.util.Constants.DB;
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: PASS

- [ ] **Step 9: Run spotless and build check**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: generalize Statistics totals/bucketize for multi-metric support"
```

---

### Task 3: TrackerElevation — Accumulate Elevation Gain During Recording

**Files:**
- Modify: `app/src/main/org/runnerup/tracker/component/TrackerElevation.java`
- Modify: `app/src/main/org/runnerup/tracker/Tracker.java:571` (saveActivity)

**Interfaces:**
- Consumes: `Constants.DB.ACTIVITY.ELEVATION_GAIN` (from Task 1)
- Produces: `TrackerElevation.getElevationGain()`, writes to DB in `onEnd()`

- [ ] **Step 1: Add elevation gain accumulator to TrackerElevation**

Add fields to `TrackerElevation` (after line 48 `private boolean isStarted;`):

```java
private double mElevationGain = 0;
private Double mLastRawAltitude = null;
```

- [ ] **Step 2: Track elevation gain in onLocationChanged**

In `TrackerElevation.onLocationChanged()` (the method at line 105), at the very beginning (before the existing altitude averaging logic), add:

```java
if (arg0.hasAltitude()) {
  double alt = arg0.getAltitude();
  if (mLastRawAltitude != null) {
    double delta = alt - mLastRawAltitude;
    if (delta > 0) {
      mElevationGain += delta;
    }
  }
  mLastRawAltitude = alt;
}
```

Uses raw GPS altitude for per-fix delta tracking. This avoids calling `getValue()` which accesses `tracker.getLastKnownLocation()` — not yet updated when `onLocationChanged` fires.

- [ ] **Step 3: Add getter for elevation gain**

Add a new public method to `TrackerElevation` (after `getValue()`):

```java
public double getElevationGain() {
  return mElevationGain;
}
```

- [ ] **Step 4: Reset accumulator on start/pause/resume**

In `onStart()` (line 165), after `isStarted = true;`, add:
```java
mElevationGain = 0;
mLastRawAltitude = null;
```

In `onPause()` (line 171), after existing state reset, add:
```java
mLastRawAltitude = null;
```

In `onResume()` (line 179), after `isStarted = true;`, no changes needed — altitude continuity is fine.

In `onComplete()` (line 185), after existing state reset, add:
```java
mElevationGain = 0;
mLastRawAltitude = null;
```

In `onEnd()` (line 194), after existing state reset, add:
```java
mElevationGain = 0;
mLastRawAltitude = null;
```

- [ ] **Step 5: Write elevation gain to DB in Tracker.saveActivity()**

In `Tracker.java`, in the `saveActivity()` method (line 571), after the block that writes `mElapsedDistance` (line 597) and before the TIME write (line 598), add:

```java
tmp.put(Constants.DB.ACTIVITY.ELEVATION_GAIN, trackerElevation.getElevationGain());
```

The `trackerElevation` field is declared at line 97 in Tracker.java. It is accessible from `saveActivity()` since both are in the same class.

- [ ] **Step 6: Run spotless and build check**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/org/runnerup/tracker/component/TrackerElevation.java \
        app/src/main/org/runnerup/tracker/Tracker.java
git commit -m "feat: accumulate and persist elevation gain during recording"
```

---

### Task 4: UI Layout — Segmented Metric Button

**Files:**
- Modify: `app/res/layout/statistics.xml`
- Modify: `common/src/main/res/values/strings.xml:271` (after Statistics_no_activities)

**Interfaces:**
- Consumes: nothing
- Produces: `R.id.statistics_metric_toggle`, `R.id.statistics_metric_distance`, `R.id.statistics_metric_time`, `R.id.statistics_metric_elevation` view IDs

- [ ] **Step 1: Add string resources**

In `common/src/main/res/values/strings.xml`, after line 271 (`Statistics_no_activities`), add:

```xml
<string name="Statistics_distance">Distance</string>
<string name="Statistics_time">Time</string>
<string name="Statistics_elevation">Elevation</string>
<string name="pref_statistics_metric">pref_statistics_metric</string>
```

- [ ] **Step 2: Add segmented button group to statistics.xml**

In `statistics.xml`, between the closing `</LinearLayout>` of `statistics_cards` (line 113) and the opening `<com.google.android.material.button.MaterialButtonToggleGroup` of `statistics_toggle` (line 115), insert:

```xml
<com.google.android.material.button.MaterialButtonToggleGroup
    android:id="@+id/statistics_metric_toggle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    app:checkedButton="@id/statistics_metric_distance"
    app:selectionRequired="true"
    app:singleSelection="true">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/statistics_metric_distance"
        style="?attr/materialButtonOutlinedStyle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/Statistics_distance" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/statistics_metric_time"
        style="?attr/materialButtonOutlinedStyle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/Statistics_time" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/statistics_metric_elevation"
        style="?attr/materialButtonOutlinedStyle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/Statistics_elevation" />
</com.google.android.material.button.MaterialButtonToggleGroup>
```

- [ ] **Step 3: Run spotless and build check**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/statistics.xml common/src/main/res/values/strings.xml
git commit -m "feat: add metric segmented button to statistics layout"
```

---

### Task 5: HistoryFragment — Wire Metric Toggle + Lazy Computation

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java`

**Interfaces:**
- Consumes: `Statistics.Metric`, `Statistics.totals(rows, metric, ...)`, `Statistics.bucketize(rows, metric, ...)`, `Statistics.computeMissingElevation(db, rows)`, `R.id.statistics_metric_*`, `R.string.pref_statistics_metric`
- Produces: Metric-reactive card rendering + chart rendering

- [ ] **Step 1: Add imports and fields**

Add to imports in `HistoryFragment.java`:

```java
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import org.runnerup.db.Statistics.Metric;
```

Add fields to the class (after line 86 `private BucketPeriod currentPeriod = BucketPeriod.DAY;`):

```java
private Metric currentMetric = Metric.DISTANCE;
```

- [ ] **Step 2: Initialize metric toggle in onViewCreated**

In `onViewCreated()`, after the existing `statisticsToggle` listener block (line 162-176) and before the closing `}` of `onViewCreated()`, add:

```java
MaterialButtonToggleGroup metricToggle = view.findViewById(R.id.statistics_metric_toggle);
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
int savedMetric = prefs.getInt(getString(R.string.pref_statistics_metric), 0);
currentMetric = Metric.values()[savedMetric];
if (savedMetric != 0) {
  int checkedId =
      savedMetric == 1 ? R.id.statistics_metric_time : R.id.statistics_metric_elevation;
  metricToggle.check(checkedId);
}
metricToggle.addOnButtonCheckedListener(
    (group, checkedId, isChecked) -> {
      if (!isChecked) {
        return;
      }
      Metric metric;
      if (checkedId == R.id.statistics_metric_time) {
        metric = Metric.TIME;
      } else if (checkedId == R.id.statistics_metric_elevation) {
        metric = Metric.ELEVATION_GAIN;
      } else {
        metric = Metric.DISTANCE;
      }
      currentMetric = metric;
      prefs.edit().putInt(getString(R.string.pref_statistics_metric), metric.ordinal()).apply();
      if (statisticsRows != null) {
        updateStatisticsCards();
        renderChart();
      }
    });
```

- [ ] **Step 3: Extract updateStatisticsCards() from loadStatistics()**

Refactor `loadStatistics()` — extract the card-updating logic into a new method. Replace lines 260-267 of `loadStatistics()` (the `mainHandler.post` body) with:

```java
mainHandler.post(
    () -> {
      statisticsRows = rows;
      statisticsExecutor.execute(
          () -> {
            Statistics.computeMissingElevation(mDB, rows);
            mainHandler.post(() -> {
              updateStatisticsCards();
              renderChart();
            });
          });
    });
```

Add the new method:

```java
private void updateStatisticsCards() {
  if (statisticsRows == null) {
    return;
  }
  long now = System.currentTimeMillis() / 1000;
  double[] totals = Statistics.totals(statisticsRows, currentMetric, now, ZoneId.systemDefault());
  switch (currentMetric) {
    case TIME:
      statistics7Value.setText(
          formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
      statistics30Value.setText(
          formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
      statistics365Value.setText(
          formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
      break;
    case ELEVATION_GAIN:
      statistics7Value.setText(
          formatter.formatElevation(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
      statistics30Value.setText(
          formatter.formatElevation(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
      statistics365Value.setText(
          formatter.formatElevation(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
      break;
    case DISTANCE:
    default:
      statistics7Value.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
      statistics30Value.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
      statistics365Value.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
      break;
  }
}
```

- [ ] **Step 4: Update renderChart() to use currentMetric**

In `renderChart()`, change the `bucketize` call (line 278) from:

```java
double[] buckets =
    Statistics.bucketize(statisticsRows, currentPeriod, now, ZoneId.systemDefault());
```

To:

```java
double[] buckets =
    Statistics.bucketize(statisticsRows, currentMetric, currentPeriod, now, ZoneId.systemDefault());
```

Also update the label formatter (line 138-139) to be metric-aware. In `onViewCreated`, replace:

```java
statisticsChart.setLabelFormatter(
    value -> formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value)));
```

With:

```java
statisticsChart.setLabelFormatter(this::formatChartValue);
```

Add the helper method:

```java
private String formatChartValue(double value) {
  switch (currentMetric) {
    case TIME:
      return formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(value));
    case ELEVATION_GAIN:
      return formatter.formatElevation(Formatter.Format.TXT_SHORT, Math.round(value));
    case DISTANCE:
    default:
      return formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value));
  }
}
```

- [ ] **Step 5: Run spotless and build check**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: wire metric toggle to statistics cards and chart"
```

---

### Task 6: Final Verification

**Files:** None — verification only.

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testLatestDebugUnitTest`
Expected: PASS

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: PASS (no new issues beyond baseline 25)

- [ ] **Step 3: Run spotless**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: PASS

- [ ] **Step 4: Build debug APK**

Run: `./gradlew :app:assembleLatestDebug`
Expected: PASS

- [ ] **Step 5: Build nomap variant**

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: PASS

- [ ] **Step 6: Device smoke test (if device available)**

Install APK, open app → History → Progress tab:
- Default: Distance selected, same values as before
- Tap Time: cards show "1:32" format, chart bars reflect duration
- Tap Elevation: cards show "320 m" format, chart bars reflect gain
- Switch Day/Week/Month: chart updates correctly for all metrics
- Kill app, reopen: metric selection persisted

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

- [ ] **Step 7: Final commit if spotless made changes**

Check `git status`. If spotless modified files:
```bash
git add -A && git commit -m "style: apply spotless formatting"
```
