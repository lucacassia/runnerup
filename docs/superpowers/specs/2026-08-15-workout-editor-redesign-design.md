# Workout Editor Redesign

Date: 2026-08-15

## Problem

The "Edit workouts" page (`CreateAdvancedWorkout`) has two problems:

1. **Dated action placement and style.** Actions live in a static `TableLayout` of filled `MaterialButton`s pinned to the bottom (`create_advanced_workout.xml:25-85`): "Add step"/"Add repeat" on one row, "Save"/"Discard"/"Rename" on another. The workout name is a read-only `MaterialTitleSpinner` at the top. This is the same dated pattern the Manage Workouts screen replaced with a `MaterialToolbar` + FAB (`6ccf929f`, `2b6491dd`).
2. **Repeats are confusing.** A `RepeatStep` renders as an ordinary-looking row. Tapping it opens a repeat-count dialog. Adding a sub-step *inside* a repeat happens implicitly: tapping the repeat row's `+` appends into the repeat's children (`CreateAdvancedWorkout.addStep`, `CreateAdvancedWorkout.java:187-206`) — indistinguishable from a row that inserts *after*. Users cannot discover how to add sub-activities inside a repeat.

## Goal

Redesign the editor screen (UI/interaction only) so that:

- Actions use Material 3 patterns consistent with the rest of the app (toolbar + FAB + overflow menu).
- Repeats render as explicit, visually distinct groups.
- Adding a step *inside* a repeat is an explicit, labeled action.
- Steps can be reordered (same-level up/down) — currently impossible.

## Design

Chosen approach: **Option A for all four layout decisions** (see decision log). All UI-only; the data model, `WorkoutSerializer`, and JSON file format are untouched.

### 1. Toolbar

Replace the top read-only `MaterialTitleSpinner` (`new_workout_spinner`) and the bottom `TableLayout` (`create_button_table`) with a `MaterialToolbar` (same pattern as `manage_workouts.xml:24-29` and other app activities):

- **Title** = workout name.
- **Action: Save** (`ic_check`) — always present. Writes the file and finishes (same behavior as today's `workout_save_button`, `CreateAdvancedWorkout.java:279-288`).
- **Overflow menu** (`⋮`) with:
  - **Rename** — only in edit mode (`WORKOUT_EDIT_MODE=true`). Opens the existing rename dialog (`renameWorkoutButtonClick`, `CreateAdvancedWorkout.java:317-385`).
  - **Discard** — only in create mode. Deletes the workout file with the existing confirmation dialog (`discardWorkoutButtonClick`, `CreateAdvancedWorkout.java:298-315`).
- **Up navigation** (back arrow) enabled via `setDisplayHomeAsUpEnabled(true)`, matching every other sub-activity. Back behavior stays the same (`persistCurrentWorkoutName`, `CreateAdvancedWorkout.java:99-111`).

Menu XML: one `menu/workout_editor_menu.xml` containing `rename` and `discard` items; visibility toggled in `onCreateOptionsMenu` based on `workoutEditMode`.

### 2. FAB

A `FloatingActionButton` anchored bottom-end (same style as `create_workout_button` in `manage_workouts.xml:113-123`):

- **`+` icon** (`ic_add_white_24dp`).
- **Tap** → `ModalBottomSheet` (Material 3) chooser with two rows:
  - **Step** → `advancedWorkout.addStep(new Step())` (appends at end of top-level list)
  - **Repeat** → `advancedWorkout.addStep(new RepeatStep())`
- Both then `advancedWorkoutStepsAdapter.refreshSteps()`, replacing `addStepButtonClick`/`addRepeatStepButtonClick` (`CreateAdvancedWorkout.java:267-277`).

A `LinearLayout` in `create_advanced_workout.xml` hosts the RecyclerView + FAB (root switches from `RelativeLayout` to `LinearLayout`, matching the rest of the app's screens).

### 3. Repeat groups

A `RepeatStep` renders as a group, not a flat row:

- **Header chip**: `Repeat` label with the repeat count, using the existing `repeat_times` string ("Repeat %1$d times", e.g. "Repeat 4 times"). Tapping the chip opens the existing repeat-count `NumberPicker` dialog (`StepButton.onRepeatClickListener`, `StepButton.java:164-199`).
- **Inset container** directly below the header, visually distinct (tinted background + border, using the app's `colorSurfaceContainer`-style tokens):
  - **Group label**: e.g. "Inside repeat".
  - **Sub-steps**: rendered as child rows, indented.
  - **"+ Add step inside repeat"**: a dashed-border button at the bottom of the container → appends `new Step()` to the repeat's `getSteps()` (`CreateAdvancedWorkout.java:189-191`).
- The container is always expanded (no collapse chevron) — sub-steps stay visible.

### 4. Row layout

Each step row becomes (left to right):

- **Up arrow** and **down arrow**, stacked in a narrow vertical column (disabled at the first/last position of the row's level).
- **Step card** — unchanged `StepButton` (`step_button.xml`): duration, intensity icon, goal. Tapping opens the existing step-edit dialog (unchanged).
- **Delete** `−` button (keeps existing confirm dialog).
- **Add** `+` button — inserts a new `Step` *after* this row at the same level.

Semantics of `+` (fixes the repeat confusion):

| Context | `+` behavior |
|---|---|
| Top-level step row | Insert new Step after it (existing path, `CreateAdvancedWorkout.java:201-203`) |
| Repeat header row | Insert new Step after the whole repeat (top-level) |
| Sub-step row inside a group | Insert new Step after it within the group (`CreateAdvancedWorkout.java:195-200`) |
| "+ Add step inside repeat" (group footer) | Append new Step to the group |

The repeat header row's `+` adds a *sibling after the repeat*, never inside — that's the group footer's job. This makes "inside" vs "after" explicit and removes the hidden-append behavior.

The adapter (`WorkoutStepsAdapter`, `CreateAdvancedWorkout.java:138-185`) gains two view types via `getItemViewType`: **step row** (arrows + `StepButton` card + del + add) and **repeat header row** (arrows + chip + del + add). The repeat header no longer inflates a `StepButton`, so the `Intensity.REPEAT` branch of `StepButton.setStep` (`StepButton.java:105-115`) becomes unused by this screen — leave it in place (other callers unaffected).

### 5. Reordering

New up/down buttons, constrained to same-level moves:

- **Same level** = within the same parent (top-level steps, or children of the same `RepeatStep`).
- Moving a repeat header reorders top-level steps; moving a sub-step reorders only within its group.
- First/last item in a level disables the corresponding arrow.
- Reorder mutates the relevant `ArrayList<Step>` (`Workout.getSteps()` or `RepeatStep.getSteps()`), then `refreshSteps()`.

Helper: a small `StepReorder` static utility in `org.runnerup.view` (pure function over a `List<Step>`: `swapIndex(List<Step> list, int i, int j)`) — unit-testable without Android.

### 6. Empty state

When a new workout has no steps, show a centered hint: "No steps yet. Tap + to add a step or a repeat." Replaces the blank list on a freshly created workout.

### 7. Unchanged behavior

- Autosave on every change (`onWorkoutChanged`, `CreateAdvancedWorkout.java:249-265`).
- Save writes the file and finishes.
- Discard confirmation ("Don't ask again" checkbox stays in the dialog as-is — existing behavior).
- Rename validation (empty / `/` / `\\` / `..` / duplicate names), and the `pref_advanced_workout` preference update.
- Step-edit dialog (`step_dialog.xml`) untouched.
- Repeat-count dialog (`NumberPicker`) untouched.
- Back button persists the workout name.
- Data model, serialization, and run engine untouched.

## Layout/asset changes

- `app/res/layout/create_advanced_workout.xml` — rewrite (LinearLayout root, MaterialToolbar, RecyclerView, FAB, empty-state view).
- `app/res/layout/advanced_workout_row.xml` — add up/down arrow buttons; add repeat-header variant (row layout used for repeat header vs. step).
- `app/res/menu/workout_editor_menu.xml` — new (rename + discard).
- New strings in `common/src/main/res/values/strings.xml` (English values only, per convention): "Inside repeat" (`Inside_repeat`), "Add step inside repeat" (`Add_step_inside_repeat`), empty-state hint (`No_steps_yet`, "No steps yet. Tap + to add a step or a repeat."). Reuse existing `Add_step`, `Add_repeat`, `Save`, `Discard`, `Rename`.
- Drawables: reuse existing `ic_check` (Save), `ic_add_white_24dp` (FAB), `ic_expand_up_white_24dp` / `ic_expand_down_white_24dp` (reorder arrows), `ic_close` (if needed). Verify tints; the arrow drawables are white-filled and will need `app:tint`/`android:tint` for visibility on light rows — handle in layout.

## Testing

Unit tests (JUnit 4 + Mockito, matching `app/test` conventions, no Robolectric):

- `StepReorderTest`:
  - Swap within a top-level list moves the step; indices swap correctly.
  - Swap is a no-op for out-of-range indices.
- Adapter/UI logic stays thin; covered by device verification below.

Verification gate (per AGENTS.md):

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug` (no NEW issues; 25 baseline remain)
3. `./gradlew spotlessApply` then `spotlessCheck`

Device smoke test on `025b46e24edcbca6`:

- Create a workout: Warm-up → Repeat ×4 {Fast 400m, Easy 200m} → Cool-down.
- Verify "+ Add step inside repeat" appends into the group (visible inside container).
- Verify repeat row `+` appends a top-level sibling after the repeat.
- Verify up/down reorder stays within level (top-level vs. group).
- Verify Save writes file, back persists name, Rename works in edit mode, Discard works in create mode.

## Out of scope

- Any data-model / storage / format changes.
- Step-edit dialog and repeat-count dialog restyling.
- Drag-and-drop reordering (decided: up/down buttons).
- Cross-level reordering (moving a step into or out of a repeat).
- Nesting repeats inside repeats.
- Restyling the Manage Workouts list screen (already done).

## Decision log

- Repeat rendering: **A — expanded group with inset container** (vs. collapsible group, flat list).
- FAB behavior: **A — FAB opens a bottom-sheet chooser** (vs. mini-FAB morph, persistent buttons).
- Row controls: **A — stacked up/down on left, add/del on right** (vs. side-by-side arrows, drag-grip).
- Action placement: **FAB + toolbar** (chosen in initial scoping).
- Reordering: **in scope, via up/down buttons**.
- Model: **UI-only, no model changes**.
- Step dialog: **unchanged**.
