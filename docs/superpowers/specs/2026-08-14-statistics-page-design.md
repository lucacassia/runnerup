# Statistics Page in History Tab — Design

## Overview

Add a "Statistics" sub-page to the History tab. It shows total distance over the last 7 / 30 / 365 days (three stat cards) and a bar chart of distance per period with three selectable views: daily for the last 14 days, weekly for the last 8 weeks, or monthly for the last 12 months. Individual (non-deleted) activities are the data source. Follows the app's existing UI patterns: `TabLayout` sub-tabs with manual `VISIBLE/GONE` switching, hand-rolled Canvas chart view, Material 3 theme, common-module strings, and the `ExecutorService + Handler` async pattern.

## Scope

**In scope:**
- History sub-tabs ("History" / "Statistics") in `HistoryFragment` + `history.xml`.
- `Statistics` data helper (`app/src/main/org/runnerup/db/Statistics.java`) with 7/30/365-day totals and day/week/month bucketization.
- Statistics UI: three distance cards, a Day/Week/Month toggle, and a new `DistanceChartView`.
- Unit tests for the pure aggregation/bucketization logic.
- On-device verification of the chart in day and night themes.

**Out of scope:**
- Any other tab or screen; per-sport breakdowns; pace/HR/elevation statistics; calendar selection; export; Wear/HR modules.
- Changing the history list behavior beyond the sub-tab switch and FAB visibility.

## Requirements

### Navigation

- `history.xml` gains a `TabLayout` (`@id/history_tabs`, `tabMode="fixed"`) directly below the toolbar, with two tabs: "History" and "Statistics".
- Existing content (RecyclerView `history_list`, empty-state `history_empty`, FAB `history_add`) is wrapped in a container; a new `ScrollView` (`@id/statistics_content`, `GONE` by default) holds the statistics UI.
- `HistoryFragment` toggles the two containers with `VISIBLE/GONE` on tab select (StartFragment pattern). Default selected tab: History.
- The `history_add` FAB is `GONE` while the Statistics tab is active.

### Data layer (`org.runnerup.db.Statistics`)

- All queries filter `deleted = 0 AND distance IS NOT NULL AND start_time >= ?` (in-progress activities have NULL distance and are excluded automatically).
- **Totals:** `double[] totals(SQLiteDatabase db, long nowSeconds)` → sums of `distance` over rolling windows of the last 7, 30, and 365 days. Implemented as one range query (365 days back) + Java summation with `Calendar`.
- **Series:** `double[] bucketize(List<ActivityRow>, BucketPeriod)` → pure function, no DB, unit-testable. Periods:
  - `DAY` — 14 bars, calendar days in local time (today is the last bucket; bucket N = day `now - (13 - N)`), oldest first.
  - `WEEK` — 8 bars, calendar weeks (Monday-start in local time), oldest first.
  - `MONTH` — 12 bars, calendar months, oldest first.
  - `ActivityRow` = minimal (startTime seconds, distance meters) holder returned by the query.
- Queries and bucketing run on a background thread via `ExecutorService + Handler` (GraphWrapper pattern); results posted to the main thread. Loading triggered on tab select and on `onResume` while the Statistics tab is active.

### Statistics UI (`statistics_content`)

- **Period toggle:** `MaterialButtonToggleGroup` (single-select, `app:selectionRequired="true"`, `app:singleSelection="true"`) with buttons `Day` / `Week` / `Month`. Default: `Day`.
- **Stat cards:** three equal-width `MaterialCardView`s in a horizontal row. Each has a label (`RunStatLabel` style: `textAppearanceLabelMedium`, `colorOnSurfaceVariant`, ALL CAPS) and a value (`textAppearanceTitleMedium`, `colorOnSurface`) via `Formatter.formatDistance`. Values never blank ("0 " + unit when empty). Totals are independent of the period toggle and are loaded once per data load.
- **Chart:** `DistanceChartView extends View` (`app/src/main/org/runnerup/view/DistanceChartView.java`), Canvas-drawn:
  - Vertical bars (equal width, `colorPrimary`), rounded tops, inset within padding.
  - Faint horizontal grid (`colorOutlineVariant`) with up to 4 y-axis labels (`colorOnSurfaceVariant`), values via `Formatter.formatDistance` (short form).
  - X-axis bucket labels below the bars (`colorOnSurfaceVariant`): DAY → day-of-month (`Formatter.formatDayOfMonth`), WEEK → week-start date (`Formatter.formatDayOfMonth`), MONTH → short month name (`Formatter.formatMonth`). If labels would overlap, render every other label.
  - Y-axis scales to the max bar value (with headroom); all-zero series renders an empty state, not bars.
  - No pan/zoom. Colors resolved from Material 3 attrs with hardcoded fallbacks (RunnerUpGraphView precedent).
- **Title:** a `TextView` above the chart (`textAppearanceTitleMedium`): "Last 14 days" / "Last 8 weeks" / "Last 12 months", updated by the toggle.
- **Empty state:** if the whole window has no activities, the chart area shows a muted "No activities yet" message instead of bars.

### Strings (common module, multilingual per convention)

New strings in `common/src/main/res/values/strings.xml`: `Statistics`, `History` (already exists as `History`), `Statistics_7_days` ("Last 7 days"), `Statistics_30_days` ("Last 30 days"), `Statistics_365_days` ("Last 365 days"), `Statistics_day` / `Statistics_week` / `Statistics_month` (toggle), `Statistics_last_14_days` / `Statistics_last_8_weeks` / `Statistics_last_12_months` (chart titles), `Statistics_no_activities` ("No activities yet"). (English values only; existing translations follow later.)

## Architecture

- `HistoryFragment` — gains tab handling and statistics loading orchestration.
- `history.xml` — tab bar + container + `statistics_content` ScrollView.
- `statistics_content` lives in its own layout `app/res/layout/statistics.xml`, `<include>`d into `history.xml` (mirrors StartFragment's `<include>`d sub-layouts).
- `Statistics.java` — data helper (queries + pure aggregation).
- `DistanceChartView.java` — bar-chart View.
- No new dependencies, no ViewModel/coroutines, no DAO layer (follows the codebase's inline-query convention).

## Error Handling

- Empty DB / empty window → "No activities yet" in the chart area; cards still render "0 " + unit.
- Query failure → same empty state (no crash); no user-facing error dialog needed (personal app, read-only query).
- Activities added while Statistics tab is open → refreshed on `onResume`.

## Testing

- Unit tests (`app/test/java`): `bucketize` for DAY/WEEK/MONTH with fixed timestamps (boundary cases: week-start Monday, month rollover, today-only data); `totals` 7/30/365 window boundaries (exactly 7 days ago counts, 8 days ago does not). Pure-Java, no device DB required.
- On-device: switch to Statistics, toggle Day/Week/Month, confirm bars/cards/labels update; verify in day and night themes; check History↔Statistics switching and FAB visibility.
- Gates per AGENTS.md: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond the 25 baseline; `SmallSp`-style findings are known to fail on `warningsAsErrors` — keep label text ≥ 11sp or apply view-scoped `tools:ignore` with justification), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` + nomap variant.

## Open Questions (decided)

- Rolling (not calendar-to-date) windows for the three totals.
- All sports count (not just running).
- Custom Canvas bar chart, no third-party chart library.
- Sub-tabs, not a toolbar action or a fourth bottom tab.
