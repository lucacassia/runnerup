# Progress Tab — Multi-Metric Selector

## Summary

Add a segmented button to the Progress tab that lets users switch between viewing Distance, Time, or Elevation Gain stats. The summary cards and bar chart update to reflect the selected metric. Elevation gain is pre-computed during recording and stored in the activity table.

## Current State

The Progress tab shows only distance data:
- Summary cards: week/month/year distance totals
- Bar chart: distance per day/week/month (12 buckets)
- Period toggle: Day/Week/Month controls bucket granularity

Data flow: `Statistics.queryActivities()` → `List<ActivityRow>` (cached) → `totals()` + `bucketize()` → cards + chart.

The activity table has `distance` (real) and `time` (integer) columns. Altitude exists only per-GPS-point in the `location` table — no elevation gain column.

## Design

### 1. Data Model

**New column:** `elevation_gain` (REAL, nullable) on the `activity` table.

- NULL means not yet computed (legacy activities)
- Populated during recording by the tracker
- Legacy activities: lazily computed on first Statistics tab access, value written back to column

**DB migration:** `ALTER TABLE activity ADD COLUMN elevation_gain real` with version bump.

**Constants:** Add `ELEVATION_GAIN = "elevation_gain"` to `Constants.DB.ACTIVITY`.

**ActivityRow:** Add `double elevationGain` field.

**SQL query:** Change from `SELECT start_time, distance` to `SELECT start_time, distance, time, elevation_gain`.

### 2. Tracker — Recording Elevation Gain

In `TrackerElevation.java`, maintain a running sum of positive altitude deltas between consecutive GPS fixes.

- On each new GPS fix with valid altitude: compute `delta = currentAltitude - previousAltitude`
- If `delta > 0`, add to cumulative `elevationGain` accumulator
- At activity end (`onEnd()`): write `elevationGain` to the activity's `elevation_gain` column
- The tracker already processes altitude per-fix — this adds one accumulator variable

### 3. UI — Segmented Button

In `statistics.xml`, add a `MaterialButtonToggleGroup` with `app:singleSelection="true"` and `app:selectionRequired="true"`:

```
Position: right after the statistics_content include boundary,
          before the summary cards LinearLayout
Buttons: Distance | Time | Elevation
Style:    Material3 segmented button (same visual as Day/Week/Month toggle)
Default:  Distance (preserves current behavior)
```

**SharedPreferences:** Store selected metric under key `R.string.pref_statistics_metric` (int: 0=Distance, 1=Time, 2=Elevation). Default 0 (Distance). Restored on fragment creation via `PreferenceManager`.

### 4. Statistics.java — Multi-Metric Abstraction

**Metric enum:**
```java
public enum Metric {
    DISTANCE,
    TIME,
    ELEVATION_GAIN
}
```

**`totals(List<ActivityRow> rows, Metric metric, long now, ZoneId zone)`:**
- Iterates rows, sums the appropriate field per calendar period
- `DISTANCE` → `row.distance` (meters)
- `TIME` → `row.time` (seconds)
- `ELEVATION_GAIN` → `row.elevationGain` (meters)

**`bucketize(List<ActivityRow> rows, Metric metric, BucketPeriod period, long now, ZoneId zone)`:**
- Same bucketing logic, but extracts the metric's value from each row

Both methods keep the same signatures otherwise — the metric parameter is the only addition.

### 5. HistoryFragment — Wiring

`loadStatistics()` caches `statisticsRows` and remains unchanged.

**Metric toggle listener:** When metric changes:
1. Save selection to SharedPreferences
2. Re-call `Statistics.totals(rows, newMetric, now, zone)` → update 3 card TextViews
3. Re-call `Statistics.bucketize(rows, newMetric, period, now, zone)` → update chart
4. Update chart title and Y-axis label formatter

No background thread needed — data is already cached. The toggle just re-derives from cached rows.

**Card labels:** Stay the same (Week / Month / Year). Values and units change based on metric.

**Chart title:** Stays the same ("Last 12 days/weeks/months"). Only the Y-axis unit changes.

### 6. Formatter

The `Formatter` class already has the needed methods:
- `formatDistance(TXT_SHORT, value)` → "5.23 km" or "3.25 mi"
- `formatTime(value)` → "1:32" (h:mm)
- `formatElevation(TXT_SHORT, value)` → "320 m" or "1,050 ft"

The chart's `LabelFormatter` callback switches based on current metric.

### 7. Lazy Elevation Gain Computation

For activities where `elevation_gain IS NULL`:

1. On Statistics tab load, after fetching `statisticsRows`, identify rows needing computation: query `SELECT _id, elevation_gain FROM activity WHERE _id IN (...) AND elevation_gain IS NULL` to find which activities lack data
2. For each such activity, query `location` table: `SELECT altitude FROM location WHERE activity_id = ? AND altitude IS NOT NULL ORDER BY time`
3. Sum positive deltas between consecutive altitude readings
4. Write result back to `activity.elevation_gain`
5. Update the in-memory `ActivityRow.elevationGain`

Note: `ActivityRow.elevationGain` uses `Double` (boxed) to distinguish NULL (not computed) from 0.0 (flat activity). The SQL query reads the column as nullable; `Cursor.getDouble()` returns 0.0 for NULL, so we use `Cursor.isNull()` to detect unset values.

This runs on the background executor. Activities with elevation_gain already set (newly recorded) skip this entirely.

### 8. Testing

**Unit tests (StatisticsTest.java):**
- `totals()` with each metric type
- `bucketize()` with each metric type
- Edge cases: activities with NULL elevation_gain excluded from elevation totals

**No UI tests** — the segmented button is a standard Material3 component; existing manual device testing covers it.

## Files Modified

| File | Change |
|------|--------|
| `common/.../Constants.java` | Add `ELEVATION_GAIN` to `DB.ACTIVITY` |
| `app/.../db/DBHelper.java` | DB version bump + ALTER TABLE migration |
| `app/.../db/Statistics.java` | `ActivityRow.elevationGain`, `Metric` enum, generalized `totals()` + `bucketize()`, lazy computation |
| `app/.../view/HistoryFragment.java` | Metric toggle listener, SharedPreferences, re-render on metric change |
| `app/.../tracker/component/TrackerElevation.java` | Elevation gain accumulator, write to DB on activity end |
| `app/res/layout/statistics.xml` | Add segmented button group |
| `app/test/.../StatisticsTest.java` | Tests for multi-metric totals/bucketize |

## Out of Scope

- Sport-specific filtering (showing only running stats vs all sports)
- Custom date ranges (beyond the fixed 12-bucket window)
- Altitude graph in the Progress tab (exists in DetailActivity already)
- Backfill migration for all existing activities (lazy computation handles this)
