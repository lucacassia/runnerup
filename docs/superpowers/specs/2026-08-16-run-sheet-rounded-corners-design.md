# Rounded Top Corners for the Recording Bottom Sheet

## Overview

The recording activity (`RunActivity`, layout `run.xml`) shows a persistent bottom sheet (`run_bottom_sheet`) above the map. Its top corners are currently square; the stats card directly above it already uses 12dp corners. This change rounds the sheet's two top corners to match the existing 12dp convention.

## Scope

- **In scope:** new drawable `app/res/drawable/bg_run_bottom_sheet.xml`; one background attribute change in `app/res/layout/run.xml` (`run_bottom_sheet`).
- **Out of scope:** the workout-editor `BottomSheetDialog` in `CreateAdvancedWorkout`, sheet behavior/peek/expansion logic, `RunActivity.java`, colors, any other layout.
- Only the **top** corners round. The bottom edge stays square because the sheet extends to the screen bottom when expanded.

## Requirements

- Top-left and top-right corners of `run_bottom_sheet` render with 12dp radius.
- Fill color stays theme-aware (`?attr/colorSurface`) so light/dark themes keep working.
- Sheet content (handle bar, "Workout" title, workout list) unchanged; no padding/content changes.
- No Java changes.

## Architecture

### New drawable — `bg_run_bottom_sheet.xml`

`<shape>` with `topLeftRadius` and `topRightRadius` = `@dimen/history_row_corner` (12dp) and `<solid android:color="?attr/colorSurface" />`. Bottom corners left at 0.

### Layout change — `run.xml`

`run_bottom_sheet`'s `android:background` changes from `?attr/colorSurface` to `@drawable/bg_run_bottom_sheet`. Everything else in the layout stays as-is.

## Testing

- `./gradlew :app:assembleLatestDebug` — builds (drawable references resolve).
- Visual check on device (`025b46e24edcbca6`, `org.runnerup.debug`): start a workout, confirm the sheet's top corners are rounded at peek and expanded states, in both light and dark theme.
