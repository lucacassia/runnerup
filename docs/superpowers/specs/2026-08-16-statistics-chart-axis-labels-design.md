# Statistics Chart Axis and Label Fixes — Design

## Overview

Fix two readability issues in the Statistics chart (`DistanceChartView`) on the History tab's Progress page:

1. **Y-axis range:** the axis top rounds up through coarse 1/2/5/10 steps, so a weekly max bin in (20, 50] km renders a 0–50 km axis even when all bins are well below 50 km. The axis should be chosen dynamically, hugging the highest bin.
2. **X-axis date labels:** month labels ("Sep 2025") are nearly as wide as the two-slot spacing, so they overlap; and with every-other-label skipping, a label centered at slot N visually drifts toward its unlabeled neighbors. Labels should be shorter and visually anchored to their own bin.

## Scope

**In scope:**
- `DistanceChartView.niceMax()` — finer step ladder.
- `DistanceChartView.onDraw()` — tick anchors under labeled bars.
- New `Formatter.formatMonthShort(Date)` — short month label for the chart.
- `HistoryFragment.buildXLabels()` — use `formatMonthShort` for the month view.
- New unit tests for `niceMax` (`DistanceChartViewTest`).
- On-device verification of the chart in all three views.

**Out of scope:**
- The period toggle, card labels/totals, `Statistics.java` bucketing, `skipOdd` logic itself, the history-list header format (`formatMonth`, "LLL yyyy" stays), Wear/HR modules, other tabs.

## Requirements

### Y-axis range (`DistanceChartView.niceMax()`)

- Replace the step ladder {1, 2, 5, 10} with {1, 2, 3, 4, 5, 6, 8, 10} (× 10ⁿ). `fraction` selects the first step `>= fraction`.
- Examples: max 24 → axis 30; 18 → 20; 40 → 40; 47 → 50; 6 → 6; 85 → 100.
- `value <= 0` still returns 1.0 (empty/all-zero series unchanged).
- `niceMax` becomes package-private static so unit tests can call it. All other behavior unchanged.

### X-axis labels

- **Shorter month labels:** add `String formatMonthShort(Date)` to `Formatter` → `"LLL"` (e.g. "Sep"), used by `HistoryFragment.buildXLabels` for the `MONTH` period (line ~299). The 12-month window makes the year redundant. The history list header keeps `formatMonth` ("LLL yyyy").
- **Tick anchors:** in `DistanceChartView.onDraw`, for each drawn x-label, draw a short vertical tick (≈4dp, `gridColor`) at the label's center x from `chartBottom` downward, then draw the label text below the tick (within the existing `dp(20)` bottom padding). Every label is thus tied to its own bar's center. Existing `skipOdd` every-other behavior (count > 8) unchanged.
- Day and week labels keep `formatDayOfMonth` ("E d", e.g. "Wed 13").

### Strings

- No user-visible string resources change; `formatMonthShort` is a date format, not a resource.

## Architecture

- `DistanceChartView.java` — `niceMax` ladder + tick drawing (Canvas `drawLine`).
- `Formatter.java` — new `formatMonthShort` alongside `formatMonth`.
- `HistoryFragment.java` — month chart labels call `formatMonthShort`.
- `app/test/java/org/runnerup/view/DistanceChartViewTest.java` — new unit tests for `niceMax` (pure-Java, no device).

## Error Handling

- Empty/all-zero series: unchanged (axis 0–1, empty-state message already handled by `HistoryFragment`).
- Zero-width/zero-height chart: unchanged early return in `onDraw`.

## Testing

- Unit tests (`app/test/java/org/runnerup/view/DistanceChartViewTest.java`):
  - `niceMax`: 24→30, 18→20, 40→40, 47→50, 6→6, 85→100, 1→1, 0→1.0.
- On-device: open History → Progress; in Week view confirm axis top hugs the data (no wasted 0–50 band when bins < 50); in all three views confirm labels are readable, ticks anchor each label to its bin, and month labels show "Sep" style (no year).
- Gates per AGENTS.md: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond the 25 baseline; known pre-existing `AppBundleLocaleChanges` at `Formatter.java:817` is not introduced by this change), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` + nomap variant.

## Open Questions (decided)

- Finer step ladder {1,2,3,4,5,6,8,10} chosen over a denser {1,1.5,2,...} ladder (keeps gridline values as clean whole numbers).
- Ticks + shorter labels chosen over width-aware greedy placement and two-row staggering.
