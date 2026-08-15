# Workout Editor Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the `CreateAdvancedWorkout` editor screen — Material 3 toolbar + FAB, explicit repeat groups with an "Add step inside repeat" button, and same-level up/down reordering — without touching the workout data model.

**Architecture:** UI-only redesign of `CreateAdvancedWorkout.java` and its layouts. A `RepeatStep` renders as a header row + an always-expanded inset container (children rows + a footer button). A flat RecyclerView item list mixes step entries, repeat headers, and repeat footers; a new pure `StepReorder` helper does same-level swaps. The `Step`/`RepeatStep`/`Workout`/`WorkoutSerializer` model is unchanged.

**Tech Stack:** AndroidX AppCompat + RecyclerView, Material 3 (com.google.android.material:1.14.0), JUnit 4 + Mockito (app/test), Gradle 9.6.1 / AGP 9.3.1, googleJavaFormat via spotless.

## Global Constraints

- No data-model, storage, or JSON-format changes. Only `CreateAdvancedWorkout.java`, its layouts, new strings, a new menu, and a new `StepReorder` helper change.
- New strings go in `common/src/main/res/values/strings.xml` only (English values only; existing translations follow later).
- No comments in code unless asked.
- Do not add new Android API calls that use `onActivityResult`/`startActivityForResult`.
- Lint gate: `./gradlew :app:lintLatestDebug` must not introduce NEW issues beyond the 25-item `app/lint-baseline.xml`.
- Formatting gate: `./gradlew spotlessApply` then `spotlessCheck` (googleJavaFormat).
- Every behavior change must preserve autosave on every edit (`onWorkoutChanged`).

---

### Task 1: StepReorder helper + unit tests

**Files:**
- Create: `app/src/main/org/runnerup/view/StepReorder.java`
- Test: `app/test/java/org/runnerup/view/StepReorderTest.java`

**Interfaces:**
- Produces: `public final class StepReorder` with `public static boolean swapIndex(List<Step> list, int i, int j)` — swaps `list[i]`/`list[j]` in place, returns `true` on success; returns `false` (no mutation) if `list` is null or either index is out of range; returns `true` with no mutation when `i == j`. Consumed by Task 6 for reorder wiring.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/view/StepReorderTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.runnerup.workout.Step;

public class StepReorderTest {
  private static List<Step> threeSteps() {
    List<Step> list = new ArrayList<>();
    list.add(new Step());
    list.add(new Step());
    list.add(new Step());
    return list;
  }

  @Test
  public void swapMovesElementsWithinList() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    Step second = list.get(1);
    assertTrue(StepReorder.swapIndex(list, 0, 1));
    assertSame(second, list.get(0));
    assertSame(first, list.get(1));
  }

  @Test
  public void swapIsReversible() {
    List<Step> list = threeSteps();
    Step a = list.get(0);
    Step b = list.get(2);
    StepReorder.swapIndex(list, 0, 2);
    StepReorder.swapIndex(list, 0, 2);
    assertSame(a, list.get(0));
    assertSame(b, list.get(2));
  }

  @Test
  public void outOfRangeIsNoOp() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    assertFalse(StepReorder.swapIndex(list, 0, 5));
    assertFalse(StepReorder.swapIndex(list, -1, 1));
    assertSame(first, list.get(0));
    assertEquals(3, list.size());
  }

  @Test
  public void nullListIsNoOp() {
    assertFalse(StepReorder.swapIndex(null, 0, 1));
  }

  @Test
  public void equalIndicesReturnTrueWithoutChange() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    assertTrue(StepReorder.swapIndex(list, 0, 0));
    assertSame(first, list.get(0));
    assertEquals(3, list.size());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.StepReorderTest`
Expected: FAIL — `cannot find symbol: class StepReorder` (or similar compile error).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/view/StepReorder.java`:

```java
package org.runnerup.view;

import java.util.List;
import org.runnerup.workout.Step;

public final class StepReorder {

  private StepReorder() {}

  public static boolean swapIndex(List<Step> list, int i, int j) {
    if (list == null || i < 0 || j < 0 || i >= list.size() || j >= list.size()) {
      return false;
    }
    if (i == j) {
      return true;
    }
    Step tmp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, tmp);
    return true;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.StepReorderTest`
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/StepReorder.java app/test/java/org/runnerup/view/StepReorderTest.java
git commit -m "feat: add same-level step reorder helper"
```

---

### Task 2: New strings, menu, and drawable tint verification

**Files:**
- Modify: `common/src/main/res/values/strings.xml`
- Create: `app/res/menu/workout_editor_menu.xml`

**Interfaces:**
- Produces (strings in `common` module, referenced via `org.runnerup.common.R.string`):
  - `Inside_repeat` = "Inside repeat"
  - `Add_step_inside_repeat` = "Add step inside repeat"
  - `No_steps_yet` = "No steps yet. Tap + to add a step or a repeat."
  - `Move_up` = "Move up"
  - `Move_down` = "Move down"
- Produces (app menu `R.menu.workout_editor_menu`): items `menu_rename_workout` (title `@string/Rename`) and `menu_discard_workout` (title `@string/Discard`). Consumed by Task 3.
- Reuses existing `common` strings: `Add_step`, `Add_repeat`, `Save`, `Discard`, `Rename`, `repeat_times` ("Repeat %1$d times"). Confirmed present (`common/src/main/res/values/strings.xml:38-39, 287-288`).

- [ ] **Step 1: Add the five strings**

Append to `common/src/main/res/values/strings.xml`:

```xml
<string name="Inside_repeat">Inside repeat</string>
<string name="Add_step_inside_repeat">Add step inside repeat</string>
<string name="No_steps_yet">No steps yet. Tap + to add a step or a repeat.</string>
<string name="Move_up">Move up</string>
<string name="Move_down">Move down</string>
```

- [ ] **Step 2: Create the editor menu**

Create `app/res/menu/workout_editor_menu.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/menu_rename_workout"
        android:title="@string/Rename" />
    <item
        android:id="@+id/menu_discard_workout"
        android:title="@string/Discard" />
</menu>
```

- [ ] **Step 3: Verify drawables exist**

Run: `ls app/res/drawable/ic_check.xml app/res/drawable/ic_add_white_24dp.xml app/res/drawable/ic_expand_up_white_24dp.xml app/res/drawable/ic_expand_down_white_24dp.xml`
Expected: all four files listed. If any is missing, stop and flag it — do not add new drawables unless required.

- [ ] **Step 4: Build check**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (compiles with the new strings + menu).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/res/values/strings.xml app/res/menu/workout_editor_menu.xml
git commit -m "feat: add workout editor strings and toolbar menu"
```

---

### Task 3: Toolbar layout + activity wiring

**Files:**
- Rewrite: `app/res/layout/create_advanced_workout.xml`
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: menu `R.menu.workout_editor_menu` (Task 2); strings `Rename`, `Discard` (existing); `ic_check` drawable.
- Produces: toolbar `R.id.actionbar`; RecyclerView `R.id.new_advnced_workout_steps` (existing id kept); FAB `R.id.add_workout_fab`; empty-state view `R.id.empty_state_text`; root `R.id.create_advanced_workout_view` (existing id kept for `ViewUtil.Insets`). Toolbar overflow routes to existing `renameWorkoutButtonClick` / `discardWorkoutButtonClick` handlers (existing fields, unchanged bodies). Up navigation must call `persistCurrentWorkoutName()` + `finish()` (same as the existing back callback).

- [ ] **Step 1: Write the new layout**

Rewrite `app/res/layout/create_advanced_workout.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/create_advanced_workout_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/actionbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:title="@string/Workout" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/new_advnced_workout_steps"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingTop="8dp"
            android:paddingBottom="88dp" />

        <TextView
            android:id="@+id/empty_state_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/No_steps_yet"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:visibility="gone" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/add_workout_fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="16dp"
            android:contentDescription="@string/Add_step"
            app:backgroundTint="?attr/colorPrimaryContainer"
            app:srcCompat="@drawable/ic_add_white_24dp"
            app:tint="?attr/colorOnPrimaryContainer" />
    </FrameLayout>
</LinearLayout>
```

- [ ] **Step 2: Rewire onCreate in CreateAdvancedWorkout.java**

Replace the block in `onCreate` (`CreateAdvancedWorkout.java:53-95`) that wires `advancedWorkoutSpinner`, the four `Button` fields, and `workoutEditMode` visibility, with:

```java
    Intent intent = getIntent();
    String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);
    boolean workoutEditMode =
        intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EDIT_MODE, false);

    MaterialToolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    toolbar.setTitle(advWorkoutName);

    RecyclerView advancedStepList = findViewById(R.id.new_advnced_workout_steps);
    advancedStepList.setLayoutManager(new LinearLayoutManager(this));
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

    FloatingActionButton addWorkoutFab = findViewById(R.id.add_workout_fab);
    addWorkoutFab.setOnClickListener(addWorkoutFabClick);
```

Add `import com.google.android.material.appbar.MaterialToolbar;` and `import com.google.android.material.floatingactionbutton.FloatingActionButton;`.

- [ ] **Step 3: Add options menu handling**

Add these methods to `CreateAdvancedWorkout`:

```java
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.workout_editor_menu, menu);
    menu.findItem(R.id.menu_rename_workout).setVisible(workoutEditMode);
    menu.findItem(R.id.menu_discard_workout).setVisible(!workoutEditMode);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.menu_rename_workout) {
      renameWorkoutButtonClick.onClick(null);
      return true;
    } else if (itemId == R.id.menu_discard_workout) {
      discardWorkoutButtonClick.onClick(null);
      return true;
    } else if (itemId == android.R.id.home) {
      persistCurrentWorkoutName();
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
```

Note: `workoutEditMode` must become a field (currently a local in `onCreate`). Add `private boolean workoutEditMode = false;` next to `dontAskAgain` (`CreateAdvancedWorkout.java:42`) and assign it in `onCreate`.

Add imports: `android.view.Menu`, `android.view.MenuItem`, `androidx.annotation.NonNull`.

- [ ] **Step 4: Remove dead button wiring**

Delete from `onCreate`:
- `advancedWorkoutSpinner` field and its assignments (`CreateAdvancedWorkout.java:40, 58-60`).
- `advancedWorkoutSpinner` references in `persistCurrentWorkoutName` (`:113-125`) → replace with a stored `String currentWorkoutName` field set in `onCreate`, and use it in `persistCurrentWorkoutName`, `onWorkoutChanged` (`:251`), `saveWorkoutButtonClick` (`:282`), `discardWorkoutButtonClick` (`:307`), `renameWorkoutButtonClick` (`:320,324,332,363,371,375`).
- The old `Button` field wiring: `addStepButton`/`addRepeatButton`/`saveWorkoutButton`/`discardWorkoutButton`/`renameWorkoutButton` `findViewById` + `setOnClickListener` + visibility lines (`:68-89`).
- The now-unused methods `addStepButtonClick` (`:267-271`) and `addRepeatStepButtonClick` (`:273-277`) — replaced by the FAB chooser (Task 4). `saveWorkoutButtonClick` and `discardWorkoutButtonClick`/`renameWorkoutButtonClick` stay (their handler bodies are reused), but they become the dialog-launching lambdas only; the button fields are gone.

Temporarily keep `addWorkoutFabClick` as a stub (no-op) — Task 4 replaces it.

- [ ] **Step 5: Build check**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. (The `workoutEditMode` field and `currentWorkoutName` field must compile — fix any leftover references to the removed spinner/buttons.)

- [ ] **Step 6: Commit**

```bash
git add app/res/layout/create_advanced_workout.xml app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "feat: toolbar layout for workout editor"
```

---

### Task 4: FAB + ModalBottomSheet add chooser

**Files:**
- Create: `app/res/layout/workout_add_sheet.xml`
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: FAB `R.id.add_workout_fab` and stub `addWorkoutFabClick` (Task 3); strings `Add_step`, `Add_repeat` (existing).
- Produces: replaces the stub — tapping the FAB shows a `ModalBottomSheet` with two rows (`add_step_sheet_row`, `add_repeat_sheet_row`); each appends to the workout, refreshes, and dismisses the sheet.

- [ ] **Step 1: Create the bottom-sheet layout**

Create `app/res/layout/workout_add_sheet.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="16dp"
    android:paddingBottom="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="24dp"
        android:paddingEnd="24dp"
        android:paddingBottom="8dp"
        android:textAppearance="?attr/textAppearanceTitleSmall"
        android:text="@string/Add_step" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_step_sheet_row"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="start"
        android:gravity="start|center_vertical"
        android:paddingStart="24dp"
        android:paddingEnd="24dp"
        android:text="@string/Add_step" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_repeat_sheet_row"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="start"
        android:gravity="start|center_vertical"
        android:paddingStart="24dp"
        android:paddingEnd="24dp"
        android:text="@string/Add_repeat" />
</LinearLayout>
```

- [ ] **Step 2: Implement the FAB click**

Replace the stub with:

```java
  private final View.OnClickListener addWorkoutFabClick =
      v -> {
        View sheetView =
            getLayoutInflater().inflate(R.layout.workout_add_sheet, null);
        ModalBottomSheet sheet = new ModalBottomSheet(this);
        sheet.setContentView(sheetView);
        sheetView
            .findViewById(R.id.add_step_sheet_row)
            .setOnClickListener(
                view -> {
                  advancedWorkout.addStep(new Step());
                  advancedWorkoutStepsAdapter.refreshSteps();
                  sheet.dismiss();
                });
        sheetView
            .findViewById(R.id.add_repeat_sheet_row)
            .setOnClickListener(
                view -> {
                  advancedWorkout.addStep(new RepeatStep());
                  advancedWorkoutStepsAdapter.refreshSteps();
                  sheet.dismiss();
                });
        sheet.show();
      };
```

Add `import com.google.android.material.bottomsheet.ModalBottomSheet;`.

- [ ] **Step 3: Delete the two old button handlers**

Remove `addStepButtonClick` and `addRepeatStepButtonClick` if not already removed in Task 3.

- [ ] **Step 4: Build check**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/workout_add_sheet.xml app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "feat: FAB bottom-sheet add chooser for workout editor"
```

---

### Task 5: Row layouts for steps, repeat headers, and repeat footers

**Files:**
- Rewrite: `app/res/layout/advanced_workout_row.xml`
- Create: `app/res/layout/advanced_workout_repeat_row.xml`
- Create: `app/res/layout/advanced_workout_repeat_footer.xml`
- Create: `app/res/drawable/bg_repeat_group_top.xml`, `bg_repeat_group_middle.xml`, `bg_repeat_group_bottom.xml`, `bg_repeat_chip.xml`, `bg_add_inside_dashed.xml`

**Interfaces:**
- Produces (view IDs consumed by Task 6):
  - Step row (`advanced_workout_row.xml`): `move_up_button`, `move_down_button`, `workout_step_button` (the existing `StepButton` view, id unchanged), `del_button`, `add_button`.
  - Repeat header row (`advanced_workout_repeat_row.xml`): `move_up_button`, `move_down_button`, `repeat_chip` (TextView), `del_button`, `add_button`.
  - Repeat footer row (`advanced_workout_repeat_footer.xml`): `add_step_inside_repeat_button`.
- Group container drawables (`bg_repeat_group_top/middle/bottom`) use `?attr/colorSurfaceContainer` fills so dark/light themes work.

- [ ] **Step 1: Rewrite the step row**

Rewrite `app/res/layout/advanced_workout_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <ImageButton
            android:id="@+id/move_up_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/Move_up"
            android:padding="10dp"
            android:src="@drawable/ic_expand_up_white_24dp"
            app:tint="?attr/colorOnSurfaceVariant" />

        <ImageButton
            android:id="@+id/move_down_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/Move_down"
            android:padding="10dp"
            android:src="@drawable/ic_expand_down_white_24dp"
            app:tint="?attr/colorOnSurfaceVariant" />
    </LinearLayout>

    <view
        android:id="@+id/workout_step_button"
        class="org.runnerup.view.StepButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/del_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="4dp"
        android:layout_marginEnd="4dp"
        app:backgroundTint="?attr/colorError"
        android:text="@string/_sign_minus"
        android:textColor="@color/btn_text_color"
        android:textStyle="bold" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="4dp"
        android:layout_marginEnd="4dp"
        app:backgroundTint="?attr/colorPrimary"
        android:text="@string/_sign_plus"
        android:textColor="@color/btn_text_color"
        android:textStyle="bold" />
</LinearLayout>
```

- [ ] **Step 2: Create the repeat header row**

Create `app/res/layout/advanced_workout_repeat_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_repeat_group_top"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <ImageButton
                android:id="@+id/move_up_button"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/Move_up"
                android:padding="10dp"
                android:src="@drawable/ic_expand_up_white_24dp"
                app:tint="?attr/colorOnSurfaceVariant" />

            <ImageButton
                android:id="@+id/move_down_button"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/Move_down"
                android:padding="10dp"
                android:src="@drawable/ic_expand_down_white_24dp"
                app:tint="?attr/colorOnSurfaceVariant" />
        </LinearLayout>

        <TextView
            android:id="@+id/repeat_chip"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:background="@drawable/bg_repeat_chip"
            android:paddingStart="16dp"
            android:paddingTop="10dp"
            android:paddingEnd="16dp"
            android:paddingBottom="10dp"
            android:textAppearance="?attr/textAppearanceTitleSmall"
            android:textColor="?attr/colorPrimary" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/del_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            app:backgroundTint="?attr/colorError"
            android:text="@string/_sign_minus"
            android:textColor="@color/btn_text_color"
            android:textStyle="bold" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/add_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            app:backgroundTint="?attr/colorPrimary"
            android:text="@string/_sign_plus"
            android:textColor="@color/btn_text_color"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="16dp"
        android:paddingTop="4dp"
        android:paddingBottom="8dp"
        android:text="@string/Inside_repeat"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorPrimary" />
</LinearLayout>
```

- [ ] **Step 3: Create the repeat footer row**

Create `app/res/layout/advanced_workout_repeat_footer.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_repeat_group_bottom"
    android:orientation="vertical">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_step_inside_repeat_button"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="4dp"
        android:layout_marginEnd="16dp"
        android:layout_marginBottom="8dp"
        android:background="@drawable/bg_add_inside_dashed"
        android:text="@string/Add_step_inside_repeat"
        android:textColor="?attr/colorPrimary" />
</LinearLayout>
```

- [ ] **Step 4: Create the container drawables**

Create `app/res/drawable/bg_repeat_group_top.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:topLeftRadius="12dp" android:topRightRadius="12dp" />
    <solid android:color="?attr/colorSurfaceContainer" />
    <stroke android:width="1dp" android:color="?attr/colorOutlineVariant" />
</shape>
```

Create `app/res/drawable/bg_repeat_group_middle.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorSurfaceContainer" />
    <stroke android:width="1dp" android:color="?attr/colorOutlineVariant" />
</shape>
```

Create `app/res/drawable/bg_repeat_group_bottom.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:bottomLeftRadius="12dp" android:bottomRightRadius="12dp" />
    <solid android:color="?attr/colorSurfaceContainer" />
    <stroke android:width="1dp" android:color="?attr/colorOutlineVariant" />
</shape>
```

Create `app/res/drawable/bg_repeat_chip.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="8dp" />
    <solid android:color="?attr/colorPrimaryContainer" />
</shape>
```

Create `app/res/drawable/bg_add_inside_dashed.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="8dp" />
    <solid android:color="?attr/colorSurfaceContainer" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorPrimary"
        android:dashWidth="6dp"
        android:dashGap="4dp" />
</shape>
```

- [ ] **Step 5: Build check**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/res/layout/advanced_workout_row.xml app/res/layout/advanced_workout_repeat_row.xml app/res/layout/advanced_workout_repeat_footer.xml app/res/drawable/bg_repeat_group_top.xml app/res/drawable/bg_repeat_group_middle.xml app/res/drawable/bg_repeat_group_bottom.xml app/res/drawable/bg_repeat_chip.xml app/res/drawable/bg_add_inside_dashed.xml
git commit -m "feat: row and repeat group layouts for workout editor"
```

---

### Task 6: Adapter rewrite — view types, reorder, add/delete wiring

**Files:**
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: layouts + ids from Task 5; `StepReorder.swapIndex` (Task 1); strings `Add_step_inside_repeat`, `repeat_times`, `Inside_repeat` (Task 2); existing `addStep`/`confirmDeleteStep`/`deleteStep`/`onWorkoutChanged` behavior.
- Produces (public methods on the inner adapter, all package-private): `refreshSteps()` (rebuilds the mixed item list, toggles empty state, `notifyDataSetChanged()`), plus activity methods `moveStep(Workout.StepListEntry entry, int delta)`, `addStepAfter(Workout.StepListEntry entry)`, `addStepInsideRepeat(RepeatStep repeat)`.
- New semantics (replaces implicit inside-append):
  - Repeat header `+` → insert new `Step` AFTER the repeat (top-level sibling).
  - Step row `+` → insert new `Step` after it, same parent list.
  - Group footer → append new `Step` to the repeat's children.
- Reorder: up/down swap within the same parent list only; disable at level boundaries.

- [ ] **Step 1: Rework the item model and adapter**

Replace the inner `WorkoutStepsAdapter` class (`CreateAdvancedWorkout.java:138-185`) with:

```java
  private static final int VIEW_TYPE_STEP = 0;
  private static final int VIEW_TYPE_REPEAT = 1;
  private static final int VIEW_TYPE_FOOTER = 2;

  private static final class FooterItem {
    final RepeatStep repeat;

    FooterItem(RepeatStep repeat) {
      this.repeat = repeat;
    }
  }

  final class WorkoutStepsAdapter
      extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    final List<Object> items = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    void refreshSteps() {
      items.clear();
      RepeatStep openRepeat = null;
      for (Workout.StepListEntry entry : advancedWorkout.getStepList()) {
        if (openRepeat != null && entry.parent() != openRepeat) {
          items.add(new FooterItem(openRepeat));
          openRepeat = null;
        }
        items.add(entry);
        if (entry.step() instanceof RepeatStep) {
          openRepeat = (RepeatStep) entry.step();
        }
      }
      if (openRepeat != null) {
        items.add(new FooterItem(openRepeat));
      }
      updateEmptyState();
      notifyDataSetChanged();
    }

    private void updateEmptyState() {
      View empty = findViewById(R.id.empty_state_text);
      if (empty != null) {
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
      }
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      Object item = items.get(position);
      if (item instanceof FooterItem) {
        return VIEW_TYPE_FOOTER;
      }
      return ((Workout.StepListEntry) item).step() instanceof RepeatStep
          ? VIEW_TYPE_REPEAT
          : VIEW_TYPE_STEP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      if (viewType == VIEW_TYPE_REPEAT) {
        return new RepeatRowViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false));
      } else if (viewType == VIEW_TYPE_FOOTER) {
        return new FooterRowViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_footer, parent, false));
      }
      return new StepRowViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
      if (viewHolder instanceof StepRowViewHolder) {
        StepRowViewHolder holder = (StepRowViewHolder) viewHolder;
        Workout.StepListEntry entry = (Workout.StepListEntry) items.get(position);
        holder.stepEntry = entry;
        holder.button.setStep(entry.step());
        holder.itemView.setBackgroundResource(
            entry.parent() != null ? R.drawable.bg_repeat_group_middle : 0);
        bindArrows(holder.moveUp, holder.moveDown, entry);
      } else if (viewHolder instanceof RepeatRowViewHolder) {
        RepeatRowViewHolder holder = (RepeatRowViewHolder) viewHolder;
        Workout.StepListEntry entry = (Workout.StepListEntry) items.get(position);
        holder.repeatStep = (RepeatStep) entry.step();
        holder.chip.setText(
            getString(
                org.runnerup.common.R.string.repeat_times,
                holder.repeatStep.getRepeatCount()));
        bindArrows(holder.moveUp, holder.moveDown, entry);
      } else {
        FooterRowViewHolder holder = (FooterRowViewHolder) viewHolder;
        FooterItem footer = (FooterItem) items.get(position);
        holder.repeat = footer.repeat;
      }
    }

    private void bindArrows(ImageButton up, ImageButton down, Workout.StepListEntry entry) {
      List<Step> list = listFor(entry);
      int index = list.indexOf(entry.step());
      up.setEnabled(index > 0);
      down.setEnabled(index >= 0 && index < list.size() - 1);
    }
  }
```

- [ ] **Step 2: Add the three view holders**

Add these inner classes inside `CreateAdvancedWorkout` (siblings of `WorkoutStepsAdapter`):

```java
  class StepRowViewHolder extends RecyclerView.ViewHolder {
    final StepButton button;
    final ImageButton moveUp;
    final ImageButton moveDown;
    final Button add;
    final Button del;
    Workout.StepListEntry stepEntry;

    StepRowViewHolder(@NonNull View itemView) {
      super(itemView);
      button = itemView.findViewById(R.id.workout_step_button);
      button.setOnChangedListener(onWorkoutChanged);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnClickListener(v -> moveStep(stepEntry, -1));
      moveDown = itemView.findViewById(R.id.move_down_button);
      moveDown.setOnClickListener(v -> moveStep(stepEntry, 1));
      add = itemView.findViewById(R.id.add_button);
      add.setOnClickListener(v -> addStepAfter(stepEntry));
      del = itemView.findViewById(R.id.del_button);
      del.setOnClickListener(v -> confirmDeleteStep(stepEntry.step()));
    }
  }

  class RepeatRowViewHolder extends RecyclerView.ViewHolder {
    final ImageButton moveUp;
    final ImageButton moveDown;
    final TextView chip;
    final Button add;
    final Button del;
    RepeatStep repeatStep;

    RepeatRowViewHolder(@NonNull View itemView) {
      super(itemView);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnClickListener(v -> moveStep(entryFor(repeatStep), -1));
      moveDown = itemView.findViewById(R.id.move_down_button);
      moveDown.setOnClickListener(v -> moveStep(entryFor(repeatStep), 1));
      chip = itemView.findViewById(R.id.repeat_chip);
      chip.setOnClickListener(v -> editRepeatCount(repeatStep));
      add = itemView.findViewById(R.id.add_button);
      add.setOnClickListener(v -> addStepAfter(entryFor(repeatStep)));
      del = itemView.findViewById(R.id.del_button);
      del.setOnClickListener(v -> confirmDeleteStep(repeatStep));
    }

    private Workout.StepListEntry entryFor(Step step) {
      for (Object item : advancedWorkoutStepsAdapter.items) {
        if (item instanceof Workout.StepListEntry
            && ((Workout.StepListEntry) item).step() == step) {
          return (Workout.StepListEntry) item;
        }
      }
      return null;
    }
  }

  class FooterRowViewHolder extends RecyclerView.ViewHolder {
    final Button addInside;
    RepeatStep repeat;

    FooterRowViewHolder(@NonNull View itemView) {
      super(itemView);
      addInside = itemView.findViewById(R.id.add_step_inside_repeat_button);
      addInside.setOnClickListener(v -> addStepInsideRepeat(repeat));
    }
  }
```

- [ ] **Step 3: Add the move/add/edit handlers**

Add these methods to `CreateAdvancedWorkout`:

```java
  private List<Step> listFor(Workout.StepListEntry entry) {
    return entry.parent() != null
        ? ((RepeatStep) entry.parent()).getSteps()
        : advancedWorkout.getSteps();
  }

  private void moveStep(Workout.StepListEntry entry, int delta) {
    if (entry == null) {
      return;
    }
    List<Step> list = listFor(entry);
    int index = list.indexOf(entry.step());
    if (index < 0 || !StepReorder.swapIndex(list, index, index + delta)) {
      return;
    }
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }

  private void addStepAfter(Workout.StepListEntry entry) {
    if (entry == null) {
      return;
    }
    List<Step> list = listFor(entry);
    int index = list.indexOf(entry.step());
    if (index < 0) {
      return;
    }
    list.add(index + 1, new Step());
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }

  private void addStepInsideRepeat(RepeatStep repeat) {
    repeat.getSteps().add(new Step());
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }

  private void editRepeatCount(RepeatStep repeat) {
    final NumberPicker numberPicker = new NumberPicker(this, null);
    numberPicker.setOrientation(VERTICAL);
    numberPicker.setDigits(4);
    numberPicker.setRange(0, 9999, true);
    numberPicker.setValue(repeat.getRepeatCount());
    new MaterialAlertDialogBuilder(this)
        .setTitle(org.runnerup.common.R.string.repeat)
        .setView(numberPicker)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, whichButton) -> {
              repeat.setRepeatCount(numberPicker.getValue());
              dialog.dismiss();
              advancedWorkoutStepsAdapter.refreshSteps();
              onWorkoutChanged.run();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
        .show();
  }
```

Add imports: `android.view.View.GONE`, `android.widget.ImageButton`, `android.widget.TextView`, `org.runnerup.widget.NumberPicker`. Replace the old `addStep(StepButton)` method (`CreateAdvancedWorkout.java:187-206`) and `deleteStep(StepButton)` signature — change `deleteStep`/`confirmDeleteStep` to take `Step` instead of `StepButton`:

```java
  private void confirmDeleteStep(Step step) {
    if (!dontAskAgain) {
      new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
          .setMultiChoiceItems(
              new String[] {"Don't ask again"},
              new boolean[] {dontAskAgain},
              (dialog, indexSelected, isChecked) -> dontAskAgain = isChecked)
          .setTitle(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                deleteStep(step);
              })
          .setNegativeButton(
              org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else {
      deleteStep(step);
    }
  }

  private void deleteStep(Step s) {
    for (Step se : advancedWorkout.getSteps()) {
      if (se instanceof RepeatStep) {
        for (Step subStep : ((RepeatStep) se).getSteps()) {
          if (subStep.equals(s)) {
            ((RepeatStep) se).getSteps().remove(s);
            break;
          }
        }
      }
      if (se.equals(s)) {
        advancedWorkout.getSteps().remove(se);
        break;
      }
    }
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }
```

Note: `deleteStep` now calls `onWorkoutChanged.run()` so deletes autosave too (currently they did not).

- [ ] **Step 4: Remove dead code**

- Delete the old `WorkoutRowViewHolder` and the old `onBindViewHolder` padding logic (level-based padding is no longer needed — the group container provides indentation).
- Remove the old `addStep(StepButton stepButton)` body entirely (replaced by `addStepAfter`/`addStepInsideRepeat`).
- Remove `import android.widget.Button;` only if no longer referenced (it still is — used by holders). Keep `import android.widget.EditText;`.

- [ ] **Step 5: Build check**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. Fix compile errors from signature changes (all `confirmDeleteStep`/`deleteStep` callers now pass `Step`).

- [ ] **Step 6: Run unit tests**

Run: `./gradlew :app:testLatestDebugUnitTest`
Expected: PASS — `StepReorderTest` (Task 1) still green; no regressions.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "feat: repeat groups, reorder, and explicit add semantics in workout editor"
```

---

### Task 7: Full verification

**Files:** none (verification only).

**Interfaces:** verifies the complete feature against the spec and AGENTS.md gates.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL. Check the report for NEW issues (do not fail on the 25 baseline items in `app/lint-baseline.xml`). If `InlinedApi`/`InconsistentArrays` (promoted to fatal by `app/lint.xml`) appear, fix them.

- [ ] **Step 3: Run spotless**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: `spotlessCheck` passes (googleJavaFormat applied).

- [ ] **Step 4: Build map variant**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. APK at `app/build/outputs/apk/latest/debug/app-latest-debug.apk`.

- [ ] **Step 5: Build nomap variant (run last)**

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Device smoke test**

On device serial `025b46e24edcbca6` (`org.runnerup.debug`, map-variant APK from Step 4):

1. `adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`
2. Launch app → Settings → Manage workouts → create workout "TestInterval".
3. Add Warm-up step (FAB → Step). Verify it appears as a row with up/down arrows, `+`, `−`.
4. Add Repeat (FAB → Repeat). Verify the repeat chip ("Repeat 1 times") + inset container + "Inside repeat" label + "+ Add step inside repeat" button render.
5. Tap chip → set count 4. Verify text updates to "Repeat 4 times".
6. Tap "+ Add step inside repeat" → verify a sub-step row appears INSIDE the container (not after the repeat).
7. On a top-level step row, tap `+` → verify a new sibling appears AFTER it (top level).
8. Tap repeat row `+` → verify a new top-level step appears AFTER the whole repeat.
9. Up/down arrows: verify moves stay within level; verify boundary arrows disable.
10. Save (✓). Back. Reopen workout in edit mode. Verify structure persisted (repeat + children + reorder intact).
11. Rename via ⋮ (edit mode): verify validation and preference update. Discard via ⋮ (create mode): verify file deletion + confirmation dialog.
12. Empty state: create a fresh workout with no steps → verify "No steps yet. Tap +…" hint shows; FAB still adds.

Use `adb shell uiautomator dump /sdcard/ui.xml` + `adb shell dumpsys activity top` to verify visibility/text/bounds as needed.

- [ ] **Step 7: Final commit if verification changed anything**

Run: `git status --porcelain`
If any files changed by spotlessApply or device-driven fixes are uncommitted, commit them:

```bash
git add -A -- app/src common/src app/res
git commit -m "fix: verification fixes for workout editor redesign"
```

(If nothing changed, skip this step.)

- [ ] **Step 8: Update the ledger**

Append a completion entry to `.superpowers/sdd/2026-08-15-workout-editor-redesign/progress.md` (create the directory if needed): task completion status, gates run and results, device-verification results, and any deviations from this plan.

---

## Self-Review

- **Spec coverage:** Toolbar (§1) → Task 3. FAB + ModalBottomSheet (§2) → Task 4. Repeat groups (§3) → Tasks 5-6. Row layout (§4) → Tasks 5-6. Reordering (§5) → Task 1 + Task 6. Empty state (§6) → Task 3 (view) + Task 6 (toggle). Unchanged behaviors (§7) → preserved across Tasks 3-6 (autosave, back persists name, dialogs untouched, model untouched). Testing (§) → Task 1 unit tests + Task 7 gates. Strings/assets → Task 2 + Task 5.
- **Placeholder scan:** No TBD/TODO; every code step shows full code; every test step has assertions.
- **Type consistency:** `StepReorder.swapIndex(List<Step>, int, int)` defined in Task 1 and used identically in Task 6. `FooterItem.repeat`/`RepeatRowViewHolder.repeatStep`/`StepRowViewHolder.stepEntry` used consistently. Layout IDs from Task 5 match `findViewById` in Task 6 (`move_up_button`, `move_down_button`, `repeat_chip`, `del_button`, `add_button`, `add_step_inside_repeat_button`). `confirmDeleteStep(Step)`/`deleteStep(Step)` signatures consistent across the rewrite. `addStepAfter(Workout.StepListEntry)`, `moveStep(Workout.StepListEntry, int)`, `addStepInsideRepeat(RepeatStep)` match their call sites.
