# Workout Editor Visual Redesign — Design

**Date:** 2026-09-09
**Status:** Approved (direction selected via visual companion: "Refined outline")

## Goal

Redesign the visual style of the step list in the advanced workout editor
(`CreateAdvancedWorkout`), opened by tapping a workout in Manage Workouts.
Structure stays the same; readability and polish improve.

## Non-goals

- Toolbar, FAB / add-sheet, step-edit dialog, empty state.
- Manage Workouts, Start/History screens.
- Workout data model, serialization format, or interval behavior.
- New accent colors or dark-mode work (`values-night` variants already exist
  and keep working).

## Selected direction: "Refined outline"

Evolve the current bordered-card look. Keep the Material outline card language
and the four intensity colors; reduce chrome elsewhere.

### 1. Step card — badge moves onto the main-text line

`step_button.xml` restructure inside `StepButton`:

- One horizontal line: **duration text on the left, intensity badge aligned
  right at the end of the same line.** The badge's full-width top row is gone.
- Goal text stays on its own line below (BodySmall, `?colorOnSurfaceVariant`).
- Card surface unchanged: `bg_step_card` (`?colorSurfaceContainerLow`, 1dp
  `?colorOutlineVariant` stroke, radius **12dp**).
- Badge visuals unchanged (11sp bold caps, per-intensity text color, 6dp
  rounded tinted background, existing light/night values). The existing
  programmatic badge styling in `StepButton.java` (text + GradientDrawable
  background + colors) stays; only its position in the layout moves.
- For `REPEAT` intensity the duration text stays hidden as today (groups do
  not use this row layout anymore, see section 2).

### 2. Repeat block wraps its sub-steps

- A repeat group renders as **one item** whose container visually encloses
  header + sub-steps + footer, instead of today's separate header box and
  footer box with flat sub-step rows between them.
- Container: `bg_repeat_group` surface style (`?colorSurfaceContainerLowest`,
  1dp `?colorOutlineVariant` stroke) with radius **unified to 12dp**
  (currently 14dp).
- Header row inside the container: drag handle, **plain bold blue title**
  `Repeat N times` (no tonal chip, no "Inside repeat" label), delete button.
  Remove `bg_repeat_chip` usage; drop the static "Inside repeat" label.
- Sub-steps inside the container render **borderless** (no card stroke/solid):
  each shows the 2dp left `nested_guide` line (existing color
  `?colorOutlineVariant`) plus the row content, consistent with the chosen
  "plain title + borderless sub-steps" mockup. Sub-steps keep a tappable
  ripple (clicking still opens the step dialog).
- Footer inside the container: dashed primary **"Add step inside repeat"**
  button (existing style), at the bottom of the wrapper.
- Nested repeats (a repeat inside a repeat) render the same wrapper style,
  recursively, inside their parent container.

### 3. Row actions — minimal ghosts

- Drag handle and delete button render at **low resting alpha (~35% of their
  normal tint)** via state-list tint drawables, strengthening while the
  control is pressed or the row is drag-activated.
  - Handle: resting = `?colorOnSurfaceVariant` @ ~35%; pressed/dragged = full.
  - Delete: resting = grey ghost @ ~35%; pressed = `?colorError` full.
  - No tonal delete disc (`bg_delete_tonal` no longer used in the editor rows).
- Deleting a step/repeat and dragging keep their current gestures and
  confirmations.

### 4. Reorder behavior

- Preserve today's semantics: swap is only allowed within the same parent
  group (top-level with top-level; sub-steps only within their own repeat).
- Top-level rows (including whole repeat groups) keep the outer
  `ItemTouchHelper` drag.
- Sub-steps inside a wrapped group keep their drag handle; implement the
  group's child list as a nested, non-scrolling `RecyclerView` (nested
  scrolling disabled, `wrap_content` height) with its own small adapter and an
  inner `ItemTouchHelper` restricted to that group. This is the mechanism that
  renders borderless sub-steps inside the wrapper while keeping in-group drag
  reorder. Nesting depth equals workout nesting (typically 1, rarely 2), and
  only the inner-recycler adpater recurses, so scroll/stack safety is not a
  concern.
- All swaps mutate the same `Workout` step lists and mark
  `reorderDirty`/`onWorkoutChanged` exactly as today; cross-parent moves stay
  rejected (`fromEntry.parent() == toEntry.parent()`, `StepReorder` unchanged).

### 5. Consistency

- Corner radii unified to 12dp for step card and repeat container.
- Row vertical rhythm (margins/paddings) otherwise preserved.
- Existing `values-night` color variants reused; no new dark-mode resources.

## Files touched (expected)

- `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java` — adapter
  restructure: `VIEW_TYPE_STEP` (top-level only), group items for repeats,
  nested child adapter + inner drag wiring; ghost handler changes.
- `app/src/main/org/runnerup/view/StepButton.java` — badge position handling
  if any layout binding changes; nested (borderless) background switching.
- `app/res/layout/step_button.xml` — badge moved onto the main-text line.
- `app/res/layout/advanced_workout_row.xml` — sub-step row (borderless variant
  + guide), ghost state wiring.
- `app/res/layout/advanced_workout_repeat_row.xml` — becomes the group
  wrapper: header + nested child host + footer button. Remove
  `advanced_workout_repeat_footer.xml` usage (footer merges into the wrapper).
- `app/res/drawable/bg_repeat_group.xml` — radius 14dp → 12dp.
- `app/res/drawable/` — ghost state-list tint drawables for handle and
  delete; drop use of `bg_delete_tonal` in editor rows; `bg_repeat_chip`
  removed.
- `app/res/values/colors.xml` (and `values-night` if any alpha variants need
  dark adjustments — expected: no).

## Verification

- `./gradlew test` — existing unit tests stay green (no behavior change).
- `./gradlew :app:lintLatestDebug` — no new issues beyond the 29-issue
  baseline.
- `./gradlew spotlessCheck` after `spotlessApply`.
- `./gradlew :app:assembleLatestDebug`.
- Device smoke (`org.runnerup.debug`):
  - badges sit right-aligned on the duration line for warmup/active/
    recovery/cooldown/rest;
  - a repeat group encloses header, borderless sub-steps, and the add button;
  - nested-repeat rendering;
  - sub-step drag within a group reorders and persists after save/back;
  - ghost controls strengthen on press; delete still works (with its
    confirmation and `pref_advanced_workout` clearing);
  - dark mode renders acceptably (existing night colors).