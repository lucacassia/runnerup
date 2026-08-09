# Manage Workouts Screen Redesign

Date: 2026-08-09

## Problem

The "Manage workouts" screen has a bug and a dated layout:

1. **Bug:** With more than one workout listed, tapping several rows leaves several of them visually selected. Each row is a `RadioButton`, but the rows are not in a `RadioGroup`, so tapping a second row does not uncheck the first. The activity's `selectedWorkout` field is single-valued, but the list is not re-rendered on selection change, so stale rows keep their checked state (`ManageWorkoutsActivity.java:501-508`, `620-625`).
2. **Layout:** Actions live in a static 2x2 `TableLayout` (Share/Edit | Create/Delete) pinned to the bottom, which is not an idiomatic Material 3 pattern.

## Goal

Redesign the screen to use a contextual action bar + Create FAB (chosen approach, "Option 2"), and enforce true single selection both visually and logically.

## Design

### 1. Layout

`app/res/layout/manage_workouts.xml`:

- Keep the top `MaterialToolbar`.
- Replace the bottom `TableLayout` (id `manage_button_table`) with:
  - A `FloatingActionButton` (plus icon, contentDescription "Create workout") anchored bottom-end. This is the Create action.
  - A contextual action bar at the bottom, `GONE` by default, containing: a close (`X`) `IconButton`, the selected workout name (`TextView`), and Edit / Share / Delete buttons.
- Rule: the FAB and the contextual bar are never visible at the same time. When a workout is selected, the FAB hides and the bar shows; when the selection is cleared, the bar hides and the FAB returns.
- Root view can switch from `RelativeLayout` to a container that cleanly anchors the FAB (e.g. `CoordinatorLayout`, already available via Material Components).

`app/res/layout/manage_workouts_list_row.xml`:

- Single-select row: the whole row is a tap target (min 48dp), a radio indicator reflects selection.
- The row renders checked when it is the selected workout and unchecked otherwise.

### 2. Selection logic

Extract the selection state into a small pure class `WorkoutSelection` in `org.runnerup.view`:

- `onChecked(WorkoutRef workout, boolean isChecked)` — a checked row always becomes the sole selection; unchecking the selected row clears the selection.
- `getSelected()` / `clear()`.

`ManageWorkoutsActivity` delegates to it, keeping the existing `selectedWorkout` semantics.

**The bug fix:** every selection change calls `adapter.refresh()` (which calls `notifyDataSetChanged()`), so every row re-binds with `checked = (workout == selected)`. This guarantees only the selected row renders as checked.

### 3. UI wiring

Replace `handleButtons()` with `updateSelectionUI()` that:

- Shows/hides the contextual bar and the FAB.
- Enables Edit/Share/Delete only when a phone workout (`PHONE_STRING`) is selected, same rule as today.
- The close button and tap-to-deselect both call `selection.clear()`.

### 4. Unchanged behavior

- Delete keeps its confirmation dialog and clearing of the `pref_advanced_workout` preference (`deleteWorkout`).
- Share uses the `WorkoutFileProvider` content URI chooser.
- Edit launches `CreateAdvancedWorkout` with `WORKOUT_EDIT_MODE=true`.
- Create (now the FAB) keeps the name-input dialog then launches `CreateAdvancedWorkout`.
- Import from file is untouched.
- Collapsing a group clears a selection inside it (existing `collapseGroup` behavior).
- Deleting the selected workout clears the selection.

### 5. Testing

Unit tests (JUnit 4 + Mockito, matching `app/test` conventions, no Robolectric):

- New `WorkoutSelectionTest`:
  - Selecting B after A leaves only B selected.
  - Re-selecting the selected item deselects.
  - `clear()` empties the selection.

Verification gate (per AGENTS.md):

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug`
3. `./gradlew spotlessApply` then `spotlessCheck`

Device smoke test on `025b46e24edcbca6`:

- Seed several workouts, verify only one row stays selected at a time.
- Contextual bar shows Edit/Share/Delete; FAB hides during selection.
- `X` and tap-to-deselect both clear the selection.
- Delete removes the workout file and clears the selection.

## Out of scope

- Non-phone providers (only "My phone" is active; Garmin support is commented out).
- Any data-model or storage changes.
- Dark/light theme beyond what the existing Material 3 theme already provides.
