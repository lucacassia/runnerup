# Progress Tab Chart Redesign: Area Chart with Data Point Dots

**Date:** 2026-08-19
**Status:** Draft

## Summary

Replace the bar-chart rendering in the Progress tab's `DistanceChartView` with an area chart (line with gradient fill) and outline-style dots at each data point. The change is isolated to `DistanceChartView.java`; the public API and `HistoryFragment` remain unchanged.

## What Changes

### Chart rendering (in `app/src/main/org/runnerup/view/DistanceChartView.java`)

- **Line:** continuous path through all data points, 2.5dp stroke, theme primary color (`barColor`), rounded joints (`Paint.Join.ROUND`)
- **Area fill:** gradient beneath the line — primary color at ~25% opacity at the top fading to transparent at the bottom
- **Data point dots:** outline style — white-filled circles (~4dp radius) with a 2dp primary-color ring, drawn at each bucket value along the line
- **Grid / y-axis labels / x-axis labels / empty state / `niceMax` scaling:** unchanged

### What stays the same

- `setData(double[], String[])` and `setLabelFormatter(LabelFormatter)` API
- Theme-resolved colors (`colorPrimary`, `colorOnSurfaceVariant`, `colorOutlineVariant`) for light/dark mode
- Layout, title, metric/period toggles, cards, empty-state view

## Implementation Notes

- Use `android.graphics.Path` to build the line and area polygons
- Use `android.graphics.LinearGradient` + `Shader` for the area fill
- Draw order: grid → area fill → line → dots → x labels
- Zero-value buckets: the point sits at the baseline; dots still drawn (consistent with bar behavior where a zero bucket had no bar)
- The single data point case (count == 1): draw a single dot at the center baseline position with no line (or a degenerate point)

## Files to Modify

- `app/src/main/org/runnerup/view/DistanceChartView.java` — replace `onDraw` bar logic with line + gradient + dots

## Verification

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug`
3. `./gradlew spotlessApply && spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
6. Device: verify chart renders as smooth line with dots in light + dark modes for Distance, Time, and Elevation metrics