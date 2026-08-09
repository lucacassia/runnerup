# Manage Workouts Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Manage Workouts screen with a contextual action bar + Create FAB and enforce true single selection (visual and logical).

**Architecture:** Extract the single-selection rule into a small pure class `WorkoutSelection` (unit-testable), rewrite the bottom action area in `manage_workouts.xml` as a FAB + contextual bar, and rewire `ManageWorkoutsActivity` so every selection change re-renders the list (fixing the multi-selection visual bug) and toggles the bar/FAB visibility.

**Tech Stack:** Java, AndroidX, Material Components 1.14.0 (`Theme.Material3.DayNight`), RecyclerView, JUnit 4 + Mockito (unit tests), Gradle.

## Global Constraints

- Verify after finishing (AGENTS.md): `./gradlew test` then `./gradlew :app:lintLatestDebug` then `./gradlew spotlessApply && spotlessCheck`. Lint must NOT fail on `app/lint-baseline.xml` (25 pre-existing issues); only new issues matter.
- No code comments unless explicitly asked. googleJavaFormat via spotless.
- User-visible strings live in `common/src/main/res/values/strings.xml` and are referenced as `org.runnerup.common.R.string.*`. `Close` does not exist yet; all other needed strings (`Share`, `Edit`, `Delete`, `Create`, `Create_new_workout`, `Delete_workout`, `Share_workout`, `Are_you_sure`, `Yes`, `No`) already exist there.
- Keep existing view ids used by `ManageWorkoutsActivity`: `actionbar`, `workout_list`, `share_workout_button`, `edit_workout_button`, `delete_workout_button`, `create_workout_button`. Root view keeps id `manage_workouts_view` (used by `ViewUtil.Insets`).
- No new dependencies; root layout stays `RelativeLayout` (the codebase uses FABs inside RelativeLayout, e.g. `run.xml`).
- Device for smoke test: `025b46e24edcbca6`. Workout files are stored via `WorkoutSerializer.getFile()` → app-internal `files/workouts/<name>.json` (list names are filenames without the `.json` suffix).
- Conventional commits (`feat:`, `refactor:`, `docs:`). Do not stage `gradle.properties` or `gradle/gradle-daemon-jvm.properties`.

---

### Task 1: `WorkoutSelection` class + unit tests (TDD)

**Files:**
- Create: `app/src/main/org/runnerup/view/WorkoutSelection.java`
- Test: `app/test/java/org/runnerup/view/WorkoutSelectionTest.java`

**Interfaces:**
- Consumes: `org.runnerup.export.SyncManager.WorkoutRef` — a record `(String synchronizer, String workoutKey, String workoutName)` constructible directly, e.g. `new SyncManager.WorkoutRef("My phone", null, "A")`. Unit tests may reference it without touching Android (it is a plain nested record).
- Produces: `org.runnerup.view.WorkoutSelection` with:
  - `void onChecked(SyncManager.WorkoutRef workout, boolean isChecked)` — if `isChecked`, `workout` becomes the sole selection; if unchecked and it was selected, selection empties.
  - `SyncManager.WorkoutRef getSelected()`
  - `void clear()`

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/view/WorkoutSelectionTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.export.SyncManager;

public class WorkoutSelectionTest {
  private static final SyncManager.WorkoutRef A =
      new SyncManager.WorkoutRef("My phone", null, "A");
  private static final SyncManager.WorkoutRef B =
      new SyncManager.WorkoutRef("My phone", null, "B");

  @Test
  public void selectingAThenBLeavesOnlyBSelected() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(B, true);
    assertEquals(B, selection.getSelected());
  }

  @Test
  public void reselectingSelectedItemDeselects() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(A, false);
    assertNull(selection.getSelected());
  }

  @Test
  public void uncheckingUnselectedItemDoesNotChangeSelection() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(B, false);
    assertEquals(A, selection.getSelected());
  }

  @Test
  public void clearEmptiesSelection() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.clear();
    assertNull(selection.getSelected());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests org.runnerup.view.WorkoutSelectionTest`
Expected: FAIL (cannot find symbol `WorkoutSelection`).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/view/WorkoutSelection.java`:

```java
package org.runnerup.view;

import org.runnerup.export.SyncManager;

public class WorkoutSelection {
  private SyncManager.WorkoutRef selected = null;

  public void onChecked(SyncManager.WorkoutRef workout, boolean isChecked) {
    if (isChecked) {
      selected = workout;
    } else if (selected == workout) {
      selected = null;
    }
  }

  public SyncManager.WorkoutRef getSelected() {
    return selected;
  }

  public void clear() {
    selected = null;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests org.runnerup.view.WorkoutSelectionTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/WorkoutSelection.java app/test/java/org/runnerup/view/WorkoutSelectionTest.java
git commit -m "feat: extract single-selection state for the workout list"
```

---

### Task 2: Close icon, `Close` string, and new layout

**Files:**
- Create: `app/res/drawable/ic_close.xml`
- Modify: `common/src/main/res/values/strings.xml:91` (insert after `<string name="Cancel">Cancel</string>`)
- Modify: `app/res/layout/manage_workouts.xml` (replace the bottom `TableLayout` block)

**Interfaces:**
- Consumes: existing ids `actionbar`, `workout_list`, `share_workout_button`, `edit_workout_button`, `delete_workout_button`, `create_workout_button`; existing strings `Manage_workouts`, `Edit`, `Share`, `Delete`, `Create_new_workout` (all in common module).
- Produces: view ids `workout_action_bar` (contextual bar container), `selected_workout_name` (TextView), `close_selection_button` (ImageButton); drawable `ic_close`; string `Close`. The `TableLayout` id `manage_button_table` is removed (no Java code references it).

- [ ] **Step 1: Create the close icon**

Create `app/res/drawable/ic_close.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:fillColor="#FFFFFFFF"
      android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z" />
</vector>
```

- [ ] **Step 2: Add the `Close` string**

In `common/src/main/res/values/strings.xml`, insert after line 91 (`<string name="Cancel">Cancel</string>`):

```xml
    <string name="Close">Close</string>
```

- [ ] **Step 3: Rewrite `manage_workouts.xml`**

Replace the entire contents of `app/res/layout/manage_workouts.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?><!--
  ~ Copyright (C) 2013 jonas.oreland@gmail.com
  ~
  ~  This program is free software: you can redistribute it and/or modify
  ~  it under the terms of the GNU General Public License as published by
  ~  the Free Software Foundation, either version 3 of the License, or
  ~  (at your option) any later version.
  ~
  ~  This program is distributed in the hope that it will be useful,
  ~  but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~  GNU General Public License for more details.
  ~
  ~  You should have received a copy of the GNU General Public License
  ~  along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/manage_workouts_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/actionbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentTop="true"
        app:title="@string/Manage_workouts" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/workout_list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/manage_workout_actions"
        android:layout_alignParentStart="true"
        android:layout_below="@id/actionbar"
        android:layout_alignParentEnd="true" />

    <LinearLayout
        android:id="@+id/manage_workout_actions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:orientation="vertical">

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/workout_action_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:layout_marginEnd="8dp"
            android:layout_marginBottom="8dp"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorSurfaceContainer"
            app:cardCornerRadius="16dp"
            app:cardElevation="4dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingStart="8dp"
                android:paddingTop="8dp"
                android:paddingEnd="8dp"
                android:paddingBottom="8dp">

                <ImageButton
                    android:id="@+id/close_selection_button"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/Close"
                    android:src="@drawable/ic_close"
                    app:tint="?attr/colorOnSurfaceVariant" />

                <TextView
                    android:id="@+id/selected_workout_name"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:paddingStart="8dp"
                    android:paddingEnd="8dp"
                    android:textAppearance="?attr/textAppearanceTitleMedium" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/edit_workout_button"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/Edit" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/share_workout_button"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/Share" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/delete_workout_button"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/Delete"
                    android:textColor="?attr/colorError" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/create_workout_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="end"
            android:layout_margin="16dp"
            android:contentDescription="@string/Create_new_workout"
            app:backgroundTint="?attr/colorPrimaryContainer"
            app:fabSize="normal"
            app:srcCompat="@drawable/ic_add_white_24dp"
            app:tint="?attr/colorOnPrimaryContainer" />
    </LinearLayout>

</RelativeLayout>
```

- [ ] **Step 4: Build to verify the layout inflates**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (no resource/layout errors).

- [ ] **Step 5: Commit**

```bash
git add app/res/drawable/ic_close.xml common/src/main/res/values/strings.xml app/res/layout/manage_workouts.xml
git commit -m "feat: add contextual action bar and create FAB to manage workouts layout"
```

---

### Task 3: Wire `ManageWorkoutsActivity` to selection + contextual UI

**Files:**
- Modify: `app/src/main/org/runnerup/view/ManageWorkoutsActivity.java`

**Interfaces:**
- Consumes: `WorkoutSelection` from Task 1 (`onChecked`, `getSelected`, `clear`); view ids from Task 2 (`workout_action_bar`, `selected_workout_name`, `close_selection_button`).
- Produces: no new public API. Internal behavior: `selectedWorkout` field is removed and replaced by the `selection` object; `handleButtons()` is replaced by `updateSelectionUI()`; selecting/deselecting re-renders the list via `adapter.refresh()`.

Import additions (add to the existing import block, keep alphabetical order):

```java
import android.widget.ImageButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
```

- [ ] **Step 1: Replace the fields**

Replace (lines 88-95):

```java
  private boolean uploading = false;
  private WorkoutRef selectedWorkout = null;
  private Button deleteButton = null;
  private Button shareButton = null;
  private Button editButton = null;
  private Button createButton = null;
```

with:

```java
  private final WorkoutSelection selection = new WorkoutSelection();
  private boolean uploading = false;
  private Button deleteButton = null;
  private Button shareButton = null;
  private Button editButton = null;
  private FloatingActionButton createButton = null;
  private View actionBar = null;
  private TextView selectedNameText = null;
  private ImageButton closeButton = null;
```

- [ ] **Step 2: Wire new views in `onCreate`**

After the existing block (lines 130-141):

```java
    deleteButton = findViewById(R.id.delete_workout_button);
    deleteButton.setOnClickListener(deleteButtonClick);
    createButton = findViewById(R.id.create_workout_button);
    createButton.setOnClickListener(createButtonClick);

    shareButton = findViewById(R.id.share_workout_button);
    shareButton.setOnClickListener(shareButtonClick);

    editButton = findViewById(R.id.edit_workout_button);
    editButton.setOnClickListener(editButtonClick);

    handleButtons();
```

replace `handleButtons();` (the final line above) with:

```java
    actionBar = findViewById(R.id.workout_action_bar);
    selectedNameText = findViewById(R.id.selected_workout_name);
    closeButton = findViewById(R.id.close_selection_button);
    closeButton.setOnClickListener(v -> clearSelection());

    updateSelectionUI();
```

- [ ] **Step 3: Replace `handleButtons()` with `updateSelectionUI()` + `clearSelection()`**

Replace the whole method (lines 293-311):

```java
  private void handleButtons() {
    if (selectedWorkout == null) {
      deleteButton.setEnabled(false);
      shareButton.setEnabled(false);
      editButton.setEnabled(false);
      createButton.setEnabled(true);
      return;
    }

    if (PHONE_STRING.contentEquals(selectedWorkout.synchronizer())) {
      deleteButton.setEnabled(true);
      shareButton.setEnabled(true);
      editButton.setEnabled(true);
    } else {
      deleteButton.setEnabled(false);
      shareButton.setEnabled(false);
      editButton.setEnabled(false);
    }
  }
```

with:

```java
  private void updateSelectionUI() {
    WorkoutRef selected = selection.getSelected();
    boolean hasSelection = selected != null;
    actionBar.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
    createButton.setVisibility(hasSelection ? View.GONE : View.VISIBLE);
    boolean phone =
        hasSelection && PHONE_STRING.contentEquals(selected.synchronizer());
    deleteButton.setEnabled(phone);
    shareButton.setEnabled(phone);
    editButton.setEnabled(phone);
    if (hasSelection) {
      selectedNameText.setText(selected.workoutName());
    }
  }

  private void clearSelection() {
    selection.clear();
    adapter.refresh();
    updateSelectionUI();
  }
```

- [ ] **Step 4: Update `deleteWorkout`**

In `deleteWorkout` (line 462), replace:

```java
    selectedWorkout = null;
    listLocal();
```

with:

```java
    selection.clear();
    listLocal();
    updateSelectionUI();
```

- [ ] **Step 5: Update the button handlers**

In `deleteButtonClick` (lines 429 and 431), replace:

```java
        if (selectedWorkout == null) return;

        final WorkoutRef selected = selectedWorkout;
```

with:

```java
        if (selection.getSelected() == null) return;

        final WorkoutRef selected = selection.getSelected();
```

In `shareButtonClick` (lines 471-472), replace:

```java
        if (selectedWorkout == null) return;

        final WorkoutRef selected = selectedWorkout;
```

with:

```java
        if (selection.getSelected() == null) return;

        final WorkoutRef selected = selection.getSelected();
```

In `editButtonClick` (lines 491-493), replace:

```java
        if (selectedWorkout == null) return;

        final WorkoutRef selected = selectedWorkout;
```

with:

```java
        if (selection.getSelected() == null) return;

        final WorkoutRef selected = selection.getSelected();
```

- [ ] **Step 6: Update `onWorkoutChecked`**

Replace (lines 501-508):

```java
  private void onWorkoutChecked(WorkoutRef workout, boolean isChecked) {
    if (isChecked) {
      selectedWorkout = workout;
    } else if (selectedWorkout == workout) {
      selectedWorkout = null;
    }
    handleButtons();
  }
```

with:

```java
  private void onWorkoutChecked(WorkoutRef workout, boolean isChecked) {
    selection.onChecked(workout, isChecked);
    adapter.refresh();
    updateSelectionUI();
  }
```

- [ ] **Step 7: Update `collapseGroup`**

In `collapseGroup` (lines 563-566), replace:

```java
      if (selectedWorkout != null && selectedWorkout.synchronizer().contentEquals(name)) {
        selectedWorkout = null;
        handleButtons();
      }
```

with:

```java
      if (selection.getSelected() != null
          && selection.getSelected().synchronizer().contentEquals(name)) {
        selection.clear();
        updateSelectionUI();
        adapter.refresh();
      }
```

- [ ] **Step 8: Update `bindWorkout`**

In `bindWorkout` (line 623), replace:

```java
      cb.setChecked(selectedWorkout == workout);
```

with:

```java
      cb.setChecked(selection.getSelected() == workout);
```

- [ ] **Step 9: Verify no `selectedWorkout` references remain**

Run: `rg -n "selectedWorkout" app/src/main/org/runnerup/view/ManageWorkoutsActivity.java`
Expected: no matches. If any remain, fix them to use `selection`.

- [ ] **Step 10: Build and run tests**

Run: `./gradlew :app:assembleLatestDebug test --tests org.runnerup.view.WorkoutSelectionTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/org/runnerup/view/ManageWorkoutsActivity.java
git commit -m "feat: enforce single selection and contextual actions in manage workouts"
```

---

### Task 4: Full verification + device smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run the verification gate**

Run:

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply && ./gradlew spotlessCheck
```

Expected: all pass. Lint may still show "24 errors filtered by baseline lint-baseline.xml" — that is expected; no NEW lint issues are allowed.

- [ ] **Step 2: Build and install the debug APK**

Run:

```bash
./gradlew :app:assembleLatestDebug
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
```

- [ ] **Step 3: Seed three workouts on the device**

Run:

```bash
adb -s 025b46e24edcbca6 shell "run-as org.runnerup.debug mkdir -p files/workouts"
adb -s 025b46e24edcbca6 push app/assets/bundled/app_workouts/4x4.json /data/local/tmp/4x4.json
adb -s 025b46e24edcbca6 push app/assets/bundled/app_workouts/Super1000.json /data/local/tmp/Super1000.json
adb -s 025b46e24edcbca6 push app/assets/bundled/app_workouts/MalinEwerlov.json /data/local/tmp/MalinEwerlov.json
adb -s 025b46e24edcbca6 shell "run-as org.runnerup.debug cp /data/local/tmp/4x4.json files/workouts/4x4.json"
adb -s 025b46e24edcbca6 shell "run-as org.runnerup.debug cp /data/local/tmp/Super1000.json files/workouts/Super1000.json"
adb -s 025b46e24edcbca6 shell "run-as org.runnerup.debug cp /data/local/tmp/MalinEwerlov.json files/workouts/MalinEwerlov.json"
```

- [ ] **Step 4: Launch the screen and verify single selection**

Run: `adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.ManageWorkoutsActivity`

Then verify with screenshots / `uiautomator dump`:
1. Three rows (`4x4`, `Super1000`, `MalinEwerlov`) are listed; the FAB is visible bottom-right and no contextual bar is shown.
2. Tap row 1 → row 1 is checked, FAB is hidden, contextual bar appears with the workout name and Edit/Share/Delete.
3. Tap row 2 → only row 2 is checked (row 1 is unchecked) — the reported bug is gone.
4. Tap row 2 again → no row is checked, bar hides, FAB returns.
5. Tap a row, then tap the `X` close button → selection cleared, bar hides, FAB returns.
6. Tap a row, then Delete → confirm dialog appears; confirm → row disappears from the list and selection clears.

Note on screen resolution: the device is a OnePlus Nord CE (1080x2400); use `adb exec-out screencap -p > /tmp/manage_workouts.png` and inspect the pixels (icon color analysis as used previously) or `adb shell uiautomator dump` for checked-state (`RadioButton` `checked="true"`) verification.

- [ ] **Step 5: Final gate re-run**

Run: `./gradlew test :app:lintLatestDebug spotlessCheck`
Expected: BUILD SUCCESSFUL.
