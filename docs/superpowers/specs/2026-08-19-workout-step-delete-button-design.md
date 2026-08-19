# Workout Step Delete Button Redesign

**Date:** 2026-08-19
**Status:** Draft

## Summary

In the workout editor (`CreateAdvancedWorkout`), each step row shows two buttons to the right of the step: a red button (`del_button`) and a blue button (`add_button`). Two problems:

1. The blue button is redundant — it inserts a new step after the current one (`addStepAfter`), which the FAB already covers.
2. The red delete button renders as a solid red square: its text glyph (the minus sign) does not paint on screen.

This change removes the blue add button and replaces the red delete button with a circular tonal button (soft red circle + red trash icon).

## What Changes

### `app/res/layout/advanced_workout_row.xml` and `app/res/layout/advanced_workout_repeat_row.xml`

- **Remove** the `add_button` `MaterialButton` block from both layouts.
- **Replace** the `del_button` `MaterialButton` block with a circular tonal icon button:
  - 40dp circle
  - background = `@drawable/bg_delete_tonal` (new drawable: soft red circle with `selectableItemBackgroundBorderless` ripple)
  - icon = `@drawable/ic_delete` with `app:tint="?attr/colorError"`
  - `contentDescription="@string/Delete"` (`org.runnerup.common.R.string.Delete`, in the common module) for accessibility

### New drawable `app/res/drawable/bg_delete_tonal.xml`

- `shape="oval"` with `solid` = `@color/deleteButtonContainer`
- wrapped in a `ripple` whose mask/container is the same oval so the ripple respects the circle bounds

### New colors in `app/res/values/colors.xml` and `app/res/values-night/colors.xml`

- `deleteButtonContainer`:
  - light: `#FCE8E9` (soft red, matches the approved mockup)
  - dark: `#3A1F21` (dark red-tinted, keeps the tonal red look on dark surfaces)

### `ic_delete.xml`

- Already exists with white fill; the white fill is fine because the button applies `app:tint="?attr/colorError"`.

### `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

- `StepRowViewHolder`: remove the `add` field and its `addStepAfter(stepEntry)` wiring; keep `del`.
- `RepeatRowViewHolder`: remove the `add` field and its `addStepAfter(entryFor(repeatStep))` wiring; keep `del`.
- `addStepAfter` method becomes unused — remove it.
- `confirmDeleteStep` / `deleteStep` logic unchanged.

## Out of Scope

- FAB add-step behavior, the add-step bottom sheet, move arrows, repeat-row footer "add step inside repeat" button — all unchanged.
- Delete confirmation dialog and "don't ask again" — unchanged.

## Verification

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug` (only the pre-existing okhttp `NewerVersionAvailable` may fail; no new issues)
3. `./gradlew spotlessApply && spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
6. Device: open a workout in edit mode; verify each step row shows only the move arrows and a soft-red circle with a red trash icon; tapping it opens the delete confirmation; no blue button remains; repeat rows look the same; both light and dark themes look correct.