# Workout Editor Visual Overhaul

**Date:** 2026-08-19
**Status:** Draft

## Summary

The workout editor (`CreateAdvancedWorkout`) screen currently has several visual pain points that make it feel outdated and cluttered:
1. **Move Controls**: Vertical up/down stacked arrow buttons on every row take up 40×80dp of space, adding clutter.
2. **Step Cards**: Custom `StepButton` uses an old dropdown spinner background (`@drawable/title_spinner`) which makes steps look like old HTML selectors rather than modern Material 3 cards.
3. **Repeat Group Container**: Repeats are styled with visual borders that split across top and bottom, but lack a unified containment background or clear guidance lines for nested steps.
4. **Empty State**: Flat, plain text "No steps yet. Tap + to add a step or a repeat."

This specification details a complete visual overhaul based on the approved **Option A: Material 3 Cards & Drag Reordering** approach.

## What Changes

### 1. Step Cards (`StepButton` & `advanced_workout_row.xml`)

- **Card Container**:
  - Replace `@drawable/title_spinner` with a new Material 3 container drawable `bg_step_card.xml`.
  - Shape: Rounded rectangle (`12dp` corner radius), background `?attr/colorSurfaceContainerLow`, stroke `1dp` `?attr/colorOutlineVariant`.
  - Include ripple behavior on tap (`?attr/colorControlHighlight`).
- **Internal Content Layout (`step_button.xml`)**:
  - Vertical/Horizontal structure:
    - **Header**: An intensity badge pill with matching tonal background and color (Warmup: green, Active: primary orange/peach, Rest: purple, Cooldown: orange, Recovery: teal).
    - **Body (Primary Text)**: Bold typography (`?attr/textAppearanceTitleMedium`), e.g., **"10:00"** or **"1.00 km"** or **"Until press"**.
    - **Footer (Secondary Text)**: Subtitle text (`?attr/textAppearanceBodySmall`) showing targets, e.g., *"Pace 4:15 - 4:30 /km"*. Omitted cleanly if no targets exist.
- **Row Actions**:
  - **Left**: Replace stacked move buttons with a single drag handle `ImageButton` (`40dp`, `@drawable/ic_drag_handle` or standard 6-dots reorder icon) tinted with `?attr/colorOnSurfaceVariant`.
  - **Right**: Keep the circular tonal delete button (`bg_delete_tonal`).

### 2. Repeat Group Container & Nesting (`advanced_workout_repeat_row.xml` + `advanced_workout_repeat_footer.xml`)

- **Group Container Card**:
  - Unify repeat groups under a single card border box (`14dp` corner radius, `?attr/colorSurfaceContainerLow`, stroke `1dp` `?attr/colorOutlineVariant`) spanning the repeat header, nested steps, and footer.
- **Repeat Header**:
  - **Left**: Single drag handle (`⋮⋮`) for reordering the entire repeat group block.
  - **Center**: A clean tonal chip **"Repeat 4×"** (tappable to edit repeat count).
  - **Right**: Circular tonal delete button.
- **Nesting Guide Line**:
  - Indent child steps by `16dp` inside the repeat box.
  - Add a subtle vertical guide line (`2dp` width, `?attr/colorOutlineVariant`) on the left side of the nested steps list to show hierarchy.
- **Group Footer**:
  - Styled outlined button **"+ Add step inside repeat"** (`?attr/colorPrimary` text, subtle rounded border) instead of raw dashed line.

### 3. Drag Reordering & Empty State (`CreateAdvancedWorkout.java` & `create_advanced_workout.xml`)

- **ItemTouchHelper**:
  - Wire up an AndroidX `ItemTouchHelper` to the `RecyclerView` in `CreateAdvancedWorkout.java`.
  - Attach drag-to-reorder support to the drag handles (`move_up_button` / `move_down_button` ids can be renamed or repurposed as `drag_handle`).
  - Dragging a card triggers adapter reordering and calls `notifyItemMoved` dynamically.
- **Modern Empty State**:
  - Replace plain centered text with a centered empty state block:
    - Large run/workout icon (e.g., standard vector icon)
    - Title: **"No steps yet"** (`?attr/textAppearanceTitleMedium`)
    - Subtitle: *"Tap + to add your first step or interval repeat"* (`?attr/textAppearanceBodyMedium`, centered, dimmed)

## Out of Scope

- Changes to the "Edit Step" dialog (`step_dialog.xml`), target range calculations, or save/delete logic.
- Adding a bottom sheet or other flow for adding steps.

## Verification

1. Run full gate suite:
   - `./gradlew test`
   - `./gradlew :app:lintLatestDebug` (confirm only okhttp issue fails)
   - `./gradlew spotlessApply && spotlessCheck`
   - Both builds (default and nomap) pass.
2. Device verification:
   - Create a new workout. Verify empty state renders beautifully with title, subtitle, and icon.
   - Add several steps and a repeat group.
   - Verify:
     - Drag handles exist on the left of every row; dragging reorders steps smoothly.
     - Steps are encapsulated in rounded cards with intensity badges (Warmup, Active, etc.).
     - Repeat groups have an outlined container card, tonal "Repeat X×" chips, indented guides, and clean "Add step inside repeat" footer buttons.
     - No legacy spinner/dropdown arrows remain on step rows.
     - Light and dark themes render correctly.
