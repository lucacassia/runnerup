# Statistics Periods to Calendar-Aligned — Design

## Overview

Modify the Statistics sub-page of the History tab so the three stat cards show calendar-aligned totals instead of rolling windows, and the chart shows different bucket counts.

**Before:**
- Cards: rolling "Last 7 days" / "Last 30 days" / "Last 365 days".
- Chart: 14 daily bars, 8 weekly bars, 12 monthly bars.

**After:**
- Cards: "This week" (Monday–Sunday) / "This month" / "This year".
- Chart: 12 daily bars, 12 weekly bars, 12 monthly bars (monthly unchanged).

## Scope

**In scope:**
- `Statistics.java` — `totals()` becomes calendar-aligned; `bucketCount()` DAY=12, WEEK=12.
- `HistoryFragment.java` — pass `ZoneId` to `totals()`, updated card labels and chart titles.
- `statistics.xml` — card label and chart-title string references.
- `strings.xml` (common module) — replace old labels with new.
- `StatisticsTest.java` — update totals and bucket-count tests.

**Out of scope:**
- Monthly chart view (stays at 12 months), toggle behavior, stat-card styling, any other tab or screen.

## Requirements

### Data layer (`org.runnerup.db.Statistics`)

- `double[] totals(List<ActivityRow> rows, long nowSeconds, ZoneId zone)` → three sums, each a calendar-aligned period in the zone:
  - index 0: current week, Monday through Sunday (`startOfWeek(today)`);
  - index 1: current calendar month;
  - index 2: current calendar year.
  - A row counts toward a period when its local-time period key equals today's key. Reuse the existing `key()` helper (`Statistics.java:134`); add a `YEAR` key (`date.getYear()`). The `date` used is the local date of the row's `startTime`.
  - Remove `TOTALS_DAYS` and `DAY_SECONDS` (unused after this change).
- `bucketCount(BucketPeriod)`: DAY → 12, WEEK → 12, MONTH → 12 (unchanged at 12).
- `bucketize()` and `bucketStarts()` logic is unchanged; they already align weeks to Monday.

### UI (`HistoryFragment.java`, `statistics.xml`)

- `loadStatistics()` calls `Statistics.totals(rows, now, ZoneId.systemDefault())`.
- Card labels: `Statistics_this_week` ("This week") / `Statistics_this_month` ("This month") / `Statistics_this_year` ("This year").
- Chart titles: `Statistics_last_12_days` ("Last 12 days") / `Statistics_last_12_weeks` ("Last 12 weeks") / `Statistics_last_12_months` (unchanged value "Last 12 months"). Update `chartTitleFor()` and the default title text in `statistics.xml`.
- The load query already covers the needed range: it fetches from `bucketStarts(MONTH)[0]` (12 months back), which always includes Jan 1 of the current year.

### Strings (common module)

Replace in `common/src/main/res/values/strings.xml`:
- `Statistics_7_days` → `Statistics_this_week`
- `Statistics_30_days` → `Statistics_this_month`
- `Statistics_365_days` → `Statistics_this_year`
- `Statistics_last_14_days` → `Statistics_last_12_days`
- `Statistics_last_8_weeks` → `Statistics_last_12_weeks`

`Statistics_last_12_months`, `Statistics_day`/`week`/`month`, `Statistics_no_activities` unchanged. English values only; no translations exist for these strings yet.

## Architecture

- No new files, no new dependencies.
- `Statistics.totals()` gains a `ZoneId` parameter; callers updated in `HistoryFragment`.
- The YEAR key is added to the `key()` switch; `BucketPeriod` enum is not extended (year totals are computed directly against today's key, not through `bucketize`).

## Error Handling

- Empty DB / empty window → cards render "0 " + unit (existing behavior).
- Rows outside the fetched window (e.g. startTime before the query boundary) are simply not included, same as today.

## Testing

- Unit tests (`app/test/java/org/runnerup/db/StatisticsTest.java`):
  - `totalsCalendarPeriods`: rows just inside/outside the current week (Monday boundary), month (1st boundary), and year (Jan 1 boundary) verify inclusion/exclusion; future rows excluded.
  - Update `bucketizeDayGroupsByCalendarDay` (12 buckets), `bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary` (12 buckets), `bucketStartsAlignToDayWeekMonthStarts` (12/12/12), `bucketCountMatchesPeriods` (12/12/12).
- On-device: open History → Statistics, confirm card labels ("This week/This month/This year"), chart titles ("Last 12 days/Last 12 weeks/Last 12 months"), and bar counts (12/12/12).
- Gates per AGENTS.md: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond the 25 baseline), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` + nomap variant.

## Open Questions (decided)

- Labels change to "This week / This month / This year" (user-confirmed).
- Week is Monday–Sunday (already the bucketize convention).
- Monthly chart view stays at 12 months.
