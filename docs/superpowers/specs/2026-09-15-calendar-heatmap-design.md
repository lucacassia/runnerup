# Design: Calendar heatmap in Progress tab

Status: approved 2026-09-15 (design presented and accepted; brainstorm menu #1).

## Goal

Show a month-grid heatmap of running activity inside the existing Progress tab so the user can see, at a glance, which days were active and how much distance each day had. Tapping a day opens that day's runs.

Placed in the Progress tab (`statistics.xml` ScrollView) — no new tab, zero new navigation.

## Placement & layout

- New section in `statistics.xml` between the summary cards (`statistics_7_value` etc.) and the metric toggle (`statistics_metric_toggle`).
- Section structure:
  - Header row: `◀ <Month Year> ▶` (label via `Formatter.formatMonth`). Prev/next arrows page one month; defaults to the current month. No month-picker; paging goes back as far as data exists (whole `activity` table query, no artificial limit).
  - Weekday header row (Mon-first, matching `Statistics.BucketPeriod.WEEK` Monday alignment): M T W T F S S.
  - 6-row, 7-column grid; leading/trailing days outside the shown month render as blank cells.
- Always visible when the Progress tab is shown. When the shown month has zero activities, cells render without fill (no intensity).

## Data & color model

- Query the `activity` table (deleted=0, `distance IS NOT NULL`, plus `type = ?` when a sport chip is selected) grouped by `LocalDate` (epoch `start_time` → `ZoneId.systemDefault()`).
- Reuse `Statistics.queryActivities(db, fromSeconds, sport)` shape (`Statistics.ActivityRow`: id, startTime, distance, time, elevationGain); the query is over the full table.
- Per-day cell content: day-of-month number + total distance in the user's preferred unit (e.g. `4.2`), truncated to one decimal.
- Color intensity = day total distance ÷ max day distance **within the shown month** (relative ramp, adapts to busy vs quiet months). Ramp is 4-5 linear alpha steps of the tertiary color; no-run days get no fill.
- Grid geometry and grouping are a pure function: `(List<ActivityRow>, LocalDate month, ZoneId) -> grid cells`, so they unit-test without Android.

## Interaction

- Tap a day cell:
  - Single run on that day → `openActivity(id)` directly (existing `reloadLauncher` → `DetailActivity` pattern).
  - 2+ runs → `MaterialBottomSheetDialog` listing that day's runs (distance, time, sport icon); tapping a row → `openActivity(id)`.
- Sport chip change (existing History chip row) re-triggers heatmap reload alongside the existing statistics/records reload.
- All computation on the existing `statisticsExecutor` → `mainHandler` chain (pattern: `loadStatistics()` in HistoryFragment).

## Edge cases

- GPS and non-GPS sports both qualify (distance column present for both); null/zero-distance activities excluded.
- `deleted = 1` always excluded.
- Distance for a day = sum of qualifying activities that day.
- Month with no data renders empty (blank cells, no intensity).

## Testing

- Pure helpers under `app/test/java/org/runnerup/...` (style: `StatisticsTest`):
  - grid geometry: leading/blank placement of day numbers for arbitrary month start weekday;
  - day grouping totals;
  - relative intensity bucket assignment;
  - weekday header alignment.
- View itself (`CalendarHeatmapView`) is rendering-only (like `DistanceChartView`): thin, no logic test.

## Out of scope

- In-run detail, tap-through to a day-filtered list.
- Color by pace/elevation (menu offered it; distance-only chosen).
- Year-at-a-glance heatmap, streaks, goals (separate menu items #4/#5/#6).

## New/changed files

- `app/src/main/org/runnerup/view/CalendarHeatmapView.java` — new custom View (DistanceChartView template + RunnerUpGraphView tap-listener pattern).
- `app/src/main/org/runnerup/db/Statistics.java` — add calendar-day grouping/color helpers (pure).
- `app/src/main/org/runnerup/view/HistoryFragment.java` — wire section, month paging, tap→bottom-sheet, sport-filter reload.
- `app/res/layout/statistics.xml` — calendar section (header + heatmap view).
- `app/res/values/strings.xml` — month-navigation and calendar strings if needed.
- `app/test/java/org/runnerup/db/StatisticsTest.java` (or a new test file) — pure-logic tests.