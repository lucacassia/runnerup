# Workout Step Delete Button Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken filled-square delete button on workout editor step rows with a circular tonal delete button (soft red circle + trash icon) and remove the redundant blue add button.

**Architecture:** Two step-row layouts (`advanced_workout_row.xml`, `advanced_workout_repeat_row.xml`) each drop their `add_button` and swap `del_button` for an `ImageButton` using a new oval ripple drawable + the existing `ic_delete` icon. `CreateAdvancedWorkout.java` removes the now-unused `add` viewholder fields and `addStepAfter` method. Two new color resources (`deleteButtonContainer`) are added for light/dark. No testable logic changes — the delete/confirm flow is untouched, so this is resource + wiring only, verified by build and device smoke test.

**Tech Stack:** Java, Android XML resources (drawable/shape/ripple), Material Components.

## Global Constraints

- No comments added to code unless asked.
- `ic_delete.xml` already exists with white fill — do NOT modify it; the new button applies `app:tint="?attr/colorError"`.
- `Delete` string comes from the common module: `org.runnerup.common.R.string.Delete`.
- Keep the move-up/move-down arrow buttons and the repeat-row footer "add step inside repeat" button unchanged.
- Delete confirmation dialog / "don't ask again" logic unchanged.
- All changes gated by: `./gradlew test`, `./gradlew :app:lintLatestDebug` (only the pre-existing okhttp `NewerVersionAvailable` at `app/build.gradle:174` may fail — never fix it), `./gradlew spotlessApply && spotlessCheck`, `./gradlew :app:assembleLatestDebug`, `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`.

---

### Task 1: Add delete button color resources

**Files:**
- Modify: `app/res/values/colors.xml`
- Modify: `app/res/values-night/colors.xml`

**Interfaces:**
- Produces: color resource `@color/deleteButtonContainer` = `#FCE8E9` (light) / `#3A1F21` (dark), used by Task 2's drawable.

- [ ] **Step 1: Add the light color**

Append to `app/res/values/colors.xml` inside `<resources>` (after the `markerLap` line, keeping the file's existing 4-space indentation):

```xml
    <color name="deleteButtonContainer">#FCE8E9</color>
```

- [ ] **Step 2: Add the dark color**

Append to `app/res/values-night/colors.xml` inside `<resources>` (after the `markerLap` line):

```xml
    <color name="deleteButtonContainer">#3A1F21</color>
```

- [ ] **Step 3: Commit**

```bash
git add app/res/values/colors.xml app/res/values-night/colors.xml
git commit -m "feat: add tonal delete button container color"
```

---

### Task 2: Create the oval ripple drawable

**Files:**
- Create: `app/res/drawable/bg_delete_tonal.xml`

**Interfaces:**
- Consumes: `@color/deleteButtonContainer` from Task 1.
- Produces: `@drawable/bg_delete_tonal` — an oval ripple whose container is a soft-red circle; used as the delete button background in Task 3.

- [ ] **Step 1: Create the drawable**

Create `app/res/drawable/bg_delete_tonal.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">
    <item>
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
            android:shape="oval">
            <solid android:color="@color/deleteButtonContainer" />
        </shape>
    </item>
</ripple>
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/res/drawable/bg_delete_tonal.xml
git commit -m "feat: add oval tonal drawable for step delete button"
```

---

### Task 3: Update the step row and repeat row layouts

**Files:**
- Modify: `app/res/layout/advanced_workout_row.xml`
- Modify: `app/res/layout/advanced_workout_repeat_row.xml`

**Interfaces:**
- Consumes: `@drawable/bg_delete_tonal` (Task 2), `@drawable/ic_delete` (existing), `org.runnerup.common.R.string.Delete` (existing common string).
- Produces: layouts with `@+id/del_button` as an `ImageButton` (same id as before, so `CreateAdvancedWorkout.java` viewholder lookups keep working) and no `@+id/add_button`.

- [ ] **Step 1: Edit the step row**

In `app/res/layout/advanced_workout_row.xml`, replace the entire `del_button` MaterialButton block (lines 42-51) and the `add_button` MaterialButton block (lines 53-62) with a single ImageButton:

```xml
    <ImageButton
        android:id="@+id/del_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="4dp"
        android:layout_marginEnd="4dp"
        android:background="@drawable/bg_delete_tonal"
        android:contentDescription="@string/Delete"
        android:padding="9dp"
        android:src="@drawable/ic_delete"
        app:tint="?attr/colorError" />
```

- [ ] **Step 2: Edit the repeat row**

In `app/res/layout/advanced_workout_repeat_row.xml`, replace the `del_button` MaterialButton block (lines 54-63) and the `add_button` MaterialButton block (lines 65-74) with the same single ImageButton as Step 1 (same id `del_button`, same attributes, `@string/Delete`).

- [ ] **Step 3: Verify no add_button references remain in layouts**

Run: `grep -rn "add_button" app/res/layout/`
Expected: only `add_step_sheet_row` and `add_step_inside_repeat_button` in `workout_add_sheet.xml` / `advanced_workout_repeat_footer.xml` — no `add_button` in the two row layouts.

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/advanced_workout_row.xml app/res/layout/advanced_workout_repeat_row.xml
git commit -m "feat: swap step delete button to tonal circle, drop add button"
```

---

### Task 4: Remove unused add wiring from CreateAdvancedWorkout

**Files:**
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: layouts from Task 3 (no `add_button` id remains).
- Produces: `StepRowViewHolder` and `RepeatRowViewHolder` with only `del` wiring; the `addStepAfter(StepListEntry)` method deleted.

- [ ] **Step 1: Remove the add field and wiring from StepRowViewHolder**

In `CreateAdvancedWorkout.java`, in `class StepRowViewHolder`:
- Delete the `final Button add;` field declaration.
- Delete the two lines in the constructor:
  ```java
  add = itemView.findViewById(R.id.add_button);
  add.setOnClickListener(v -> addStepAfter(stepEntry));
  ```
- Keep the `del` field, `del` findViewById, and `del.setOnClickListener(v -> confirmDeleteStep(stepEntry.step()));`.

- [ ] **Step 2: Remove the add field and wiring from RepeatRowViewHolder**

In `class RepeatRowViewHolder`:
- Delete the `final Button add;` field declaration.
- Delete the two lines in the constructor:
  ```java
  add = itemView.findViewById(R.id.add_button);
  add.setOnClickListener(v -> addStepAfter(entryFor(repeatStep)));
  ```
- Keep `del`, `chip`, and `moveUp`/`moveDown` wiring.

- [ ] **Step 3: Delete the now-unused addStepAfter method**

Delete the whole `private void addStepAfter(Workout.StepListEntry entry) { ... }` method (currently lines 390-402).

- [ ] **Step 4: Remove the now-unused Button import if needed**

Check whether `android.widget.Button` is still used elsewhere in the file (e.g. `FooterRowViewHolder.addInside` is a `Button`). Run: `grep -n "Button" app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`. If `Button` still appears (the footer's `addInside`), leave the import; otherwise remove `import android.widget.Button;`.

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "refactor: drop unused step add wiring from workout editor"
```

---

### Task 5: Final verification

**Files:** none.

- [ ] **Step 1: Run full gate suite**

Run in order:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```
Expected: all pass; lint reports only the pre-existing okhttp `NewerVersionAvailable` error (do not fix it); spotless does not reformat files outside this plan (revert any RunActivity.java changes if spotlessApply touches it).

- [ ] **Step 2: Device smoke test**

Install the debug APK. Open Settings → Manage Workouts → edit a workout. Verify:
- Each step row shows move arrows, the step, and a single soft-red circle with a red trash icon.
- No blue "+" button remains on any row.
- Repeat-group rows show the same circular delete button.
- Tapping delete opens the confirmation dialog; "Yes" removes the step; "Don't ask again" still works.
- Light and dark themes both render the soft-red circle correctly.
- Push to fork: `git push fork master`