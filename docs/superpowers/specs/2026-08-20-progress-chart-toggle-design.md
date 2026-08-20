# Progress Tab: 12-Year Period and Line/Bar Chart Toggle

**Date:** 2026-08-20
**Status:** Draft

## Summary

Two additions to the Progress tab (History → Progress):

1. **Year period:** a fourth option in the bottom Day/Week/Month selector that shows the last 12 years of the selected metric, bucketed by calendar year, rendered exactly like the existing periods.
2. **Chart view toggle:** a single icon button at the top-right of the chart title row that switches the chart between the current line-circle rendering and a bar rendering. The toggle applies to all periods and the choice is persisted across app restarts.

## What Changes

### Data layer — `app/src/main/org/runnerup/db/Statistics.java`

- Add `YEAR` to the `BucketPeriod` enum
- `bucketCount(YEAR)` returns `12`
- `key(date, YEAR)` returns `date.getYear()` (offsets: `todayYear - year`, same indexing as MONTH)
- `bucketize()`: YEAR handled with the same offset logic as MONTH (`offset = (int) dayDiff`, i.e. `todayYear - year`)
- `bucketStarts(YEAR)`: `LocalDate.of(today.getYear() - count + 1 + i, 1, 1)` — Jan 1 of each of the last 12 years, so the `from` used for the DB query starts at the beginning of the earliest year

### Fragment — `app/src/main/org/runnerup/view/HistoryFragment.java`

- `loadStatistics()`: change the query `from` to `Statistics.bucketStarts(YEAR, now, zone)[0]` (Jan 1 of the earliest year, 11 years back) so cached rows cover every period switch; remove the hard-coded MONTH start
- `renderChart()`: unchanged shape — `setData(buckets, labels)` then call `statisticsChart.setBarMode(chartBarMode)`
- New `boolean chartBarMode`, persisted with `putBoolean`/`getBoolean` under `pref_statistics_chart` (SharedPreferences, same pattern as the metric toggle)
- Toggle button handler: flip `chartBarMode`, persist, update button icon, re-render chart
- `chartTitleFor(YEAR)` → `Statistics_last_12_years`
- `buildXLabels(YEAR)` → `formatter.formatYear(date)`
- Wire new `statistics_toggle_year` button into the period listener (`checkedId == R.id.statistics_toggle_year ? BucketPeriod.YEAR : …`)

### Chart view — `app/src/main/org/runnerup/view/DistanceChartView.java`

- Add `boolean barMode` with `setBarMode(boolean)`
- `onDraw` branches on `barMode`:
  - **Line mode (default):** existing line + gradient fill + dots
  - **Bar mode:** rounded bars, width ≈ 60% of the bucket slot, same `barColor`, drawn baseline → value; no dots; grid, Y-axis labels, X labels unchanged
- `onDraw` calls `invalidate()`-safe: toggle simply flips the flag and re-renders

### Layout — `app/res/layout/statistics.xml`

- Add 4th `MaterialButton` `statistics_toggle_year` ("Year", `Statistics_year`) to the bottom `statistics_toggle` `MaterialButtonToggleGroup` (equal weight, existing outlined style)
- Wrap `statistics_chart_title` and the new toggle button in a horizontal `LinearLayout`:
  - title left (`layout_weight="1"`), toggle button right (`layout_gravity="center_vertical"`, wrap_content)
  - button is an `ImageButton`, 48dp, `background="?attr/selectableItemBackgroundBorderless"`, `src` set in code to the target-view icon, `app:tint="?attr/colorOnSurfaceVariant"`, content-description set per state (matches the `close_selection_button` pattern in `manage_workouts.xml`)

### Icons and strings

- New vector drawables in `app/res/drawable/`:
  - `ic_chart_line.xml` — Material `show_chart` path (24dp)
  - `ic_chart_bar.xml` — Material `bar_chart` path (24dp)
- New strings in `common/src/main/res/values/strings.xml`:
  - `Statistics_year` → "Year"
  - `Statistics_last_12_years` → "Last 12 years"
  - `pref_statistics_chart` → "pref_statistics_chart"
  - `Statistics_switch_to_bars` / `Statistics_switch_to_line` → content descriptions

### Formatter — `app/src/main/org/runnerup/util/Formatter.java`

- Add `formatYear(Date)` → `SimpleDateFormat("yyyy")`, matching the existing month/day-of-month format methods

## Behavior Details

- **Toggle button icon shows the target view:** in line mode it displays the bar icon (tap → bars); in bar mode it displays the line icon (tap → line). This is the approved Option A.
- **Toggle applies to all periods** (day/week/month/year), not just Year.
- **Persistence:** `chartBarMode` restored on fragment create via `getBoolean(pref, false)` (bar = true, default line).
- **Zero-value buckets:** line mode keeps the current dot-at-baseline behavior; bar mode draws no bar for zero buckets.

## Files to Modify

- `app/src/main/org/runnerup/db/Statistics.java`
- `app/src/main/org/runnerup/view/HistoryFragment.java`
- `app/src/main/org/runnerup/view/DistanceChartView.java`
- `app/src/main/org/runnerup/util/Formatter.java`
- `app/res/layout/statistics.xml`
- `app/res/drawable/ic_chart_line.xml` (new)
- `app/res/drawable/ic_chart_bar.xml` (new)
- `common/src/main/res/values/strings.xml`

## Verification

1. Unit tests: `Statistics` YEAR bucketing (`bucketize`, `bucketStarts`, `key`, `bucketCount`) and a bar-mode rendering check in `DistanceChartViewTest`
2. `./gradlew test`
3. `./gradlew :app:lintLatestDebug` (only pre-existing okhttp error at `app/build.gradle:174` allowed)
4. `./gradlew spotlessApply && spotlessCheck`
5. `./gradlew :app:assembleLatestDebug`
6. Device: toggle flips line ↔ bars for all periods; Year shows 12 yearly buckets; bar/line choice survives app restart; content descriptions announce correctly