# Workout Editor Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the advanced-workout editor step list into the "Refined outline" look: intensity badge inline on the main-text line, repeat blocks that wrap their sub-steps, and ghost row actions.

**Architecture:** Pure-layout and custom-view changes, plus one adapter restructure in `CreateAdvancedWorkout`. Repeat groups stop being flat "header + footer" items and become one wrapper item containing a non-scrolling nested `RecyclerView` for their sub-steps; drag reorder is preserved via an inner `ItemTouchHelper` per group. A new `Workout.entriesAtLevel(Step parent)` method (with JVM tests) supplies the tree access the adapters need.

**Tech Stack:** Java, AndroidX `RecyclerView`/`ItemTouchHelper`, Material 3, XML drawables/color state lists. `app` module resources use non-transitive `org.runnerup.R`; common strings use `org.runnerup.common.R`.

## Global Constraints

- GPLv3 license header required on every new source/layout/drawable file (copy the header block from any existing file, e.g. `app/res/layout/step_button.xml` lines 1-16). Never strip it from edited files.
- No code comments unless a task's code block includes them.
- googleJavaFormat: run `./gradlew spotlessApply`, then gates on `spotlessCheck`.
- Unit tests live at `app/test/java` (non-standard root; Gradle `sourceSets` root is `test`).
- Gate order every task must end on: `./gradlew test` → `./gradlew :app:lintLatestDebug` (29 pre-existing issues in `app/lint-baseline.xml` are ignored; only NEW issues fail; `app/lint.xml` promotes `InlinedApi`/`InconsistentArrays` to fatal) → `./gradlew spotlessApply && ./gradlew spotlessCheck` → `./gradlew :app:assembleLatestDebug`.
- Conventional commits only (`feat:`, `refactor:`, `fix:`, `style:`).
- Do NOT stage user-local files (`gradle.properties`, untracked `gradle/gradle-daemon-jvm.properties`).
- Device smoke target: `org.runnerup.debug` on device serial `6a6743fd`. Keep screen awake with `adb shell svc power stayon true`.
- Resources: app drawables/layouts live under `app/res/`; app colors under `app/res/values/colors.xml` and `app/res/values-night/colors.xml`; intensity strings live in `common/src/main/res/values/strings.xml` (`repeat_times` = `Repeat %1$d times`, `Inside_repeat`, `Add_step_inside_repeat`, `Drag_to_reorder`, `Delete`). Badge colors (`stepActive*` etc.) are app colors used as-is; do not change them.
- Intensity keys parsed by `WorkoutSerializer.getIntensity`: `warmup`, `repeat`, `rest`, `recovery`, `cooldown`, `interval`, `other`.
- minSdk 28. `android:alpha` on `<item>` in a color state list is supported.

---

### Task 1: Move the intensity badge onto the main-text line

No `.java` change. Restructure `step_button.xml` so the badge sits right-aligned on the duration line instead of on its own row above.

**Files:**
- Modify: `app/res/layout/step_button.xml` (whole file)

**Interfaces:**
- Consumes: nothing new — view ids `step_intensity_badge`, `step_duration_value`, `step_goal_value` stay identical so `StepButton.java` (which finds them by id) keeps working unchanged.
- Produces: a two-line step card: line 1 = duration text (left, weight) + badge (right), line 2 = goal text. `StepButton.java` untouched.

- [ ] **Step 1: Rewrite `step_button.xml` to this exact content**

Keep the GPLv3 XML comment header exactly as it is today (lines 1-16), then:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/step_button_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_step_card"
    android:clickable="true"
    android:focusable="true"
    android:orientation="vertical"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/step_duration_value"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:textStyle="bold"
            android:textColor="?attr/colorOnSurface" />

        <TextView
            android:id="@+id/step_intensity_badge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:paddingStart="8dp"
            android:paddingTop="2dp"
            android:paddingEnd="8dp"
            android:paddingBottom="2dp"
            android:textAllCaps="true"
            android:textSize="11sp"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:id="@+id/step_goal_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />
</LinearLayout>
```

(Note the badge's old `android:layout_marginBottom="6dp"` is gone; it now has `android:layout_marginStart="8dp"` inside the horizontal line.)

- [ ] **Step 2: Build and verify on device**

```bash
./gradlew :app:assembleLatestDebug
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell "svc power stayon true && am force-stop org.runnerup.debug"
adb shell "am start -n org.runnerup.debug/org.runnerup.view.ManageWorkoutsActivity"
```

Open **8-6-4-2** (8 steps, includes warmup/interval/rest/cooldown). Dump the editor:

```bash
adb shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1
adb shell "cat /sdcard/ui.xml"
```

Expected: for each step card, texts like `5:00` / `8:00` (duration) appear with `WARMUP`/`ACTIVE`/`REST`/`COOLDOWN` to the right on the SAME text line — check bounds in the dump: the badge's `bounds` top coordinate equals the duration text's top coordinate (same line), and badge `left` > duration `right`. The goal line (`4:00 /km` etc., or nothing when no target) is a separate line below.

- [ ] **Step 3: Run the style gate and commit**

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:lintLatestDebug
git status --short
git add app/res/layout/step_button.xml
git commit -m "feat: move step intensity badge onto the duration line"
```

---

### Task 2: Add `Workout.entriesAtLevel` with JVM tests

Pure data-model accessor that the restructured adapters use to extract a parent's direct children (or the workout's top-level steps). No Android dependencies.

**Files:**
- Modify: `app/src/main/org/runnerup/workout/Workout.java` (add method next to `getStepList()`, ~line 612)
- Test: Create `app/test/java/org/runnerup/workout/WorkoutTreeTest.java`

**Interfaces:**
- Consumes: `Workout.getStepList()` and the record `Workout.StepListEntry(int index, Step step, int level, Step parent)` — both exist today.
- Produces: `public List<Workout.StepListEntry> entriesAtLevel(Step parent)` returning entries whose `parent()` field is the same object as `parent` (identity `==`), in the depth-first order of `getStepList()`. `parent == null` returns the workout's top-level steps. Later tasks call it via `advancedWorkout.entriesAtLevel(null)` (outer adapter) and `advancedWorkout.entriesAtLevel(repeat)` (group's child adapter).

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/workout/WorkoutTreeTest.java`:

```java
package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;
import org.junit.Test;

public class WorkoutTreeTest {

  @Test
  public void topLevelEntriesHaveNullParentInInsertionOrder() {
    Workout w = new Workout();
    Step a = new Step();
    Step b = new Step();
    w.addStep(a);
    w.addStep(b);

    List<Workout.StepListEntry> top = w.entriesAtLevel(null);

    assertEquals(2, top.size());
    assertSame(a, top.get(0).step());
    assertSame(b, top.get(1).step());
    assertNull(top.get(0).parent());
  }

  @Test
  public void repeatChildrenHaveRepeatAsParent() {
    Workout w = new Workout();
    RepeatStep rep = new RepeatStep();
    rep.setRepeatCount(3);
    Step s1 = new Step();
    Step s2 = new Step();
    rep.getSteps().add(s1);
    rep.getSteps().add(s2);
    w.addStep(rep);

    List<Workout.StepListEntry> children = w.entriesAtLevel(rep);

    assertEquals(2, children.size());
    assertSame(rep, children.get(0).parent());
    assertSame(s1, children.get(0).step());
    assertSame(s2, children.get(1).step());
  }

  @Test
  public void topLevelDoesNotIncludeRepeatChildren() {
    Workout w = new Workout();
    RepeatStep rep = new RepeatStep();
    Step sub = new Step();
    rep.getSteps().add(sub);
    w.addStep(rep);

    List<Workout.StepListEntry> top = w.entriesAtLevel(null);

    assertEquals(1, top.size());
    assertSame(rep, top.get(0).step());
  }

  @Test
  public void nestedRepeatIsChildOfOuterRepeat() {
    Workout w = new Workout();
    RepeatStep outer = new RepeatStep();
    RepeatStep inner = new RepeatStep();
    Step deep = new Step();
    outer.getSteps().add(inner);
    inner.getSteps().add(deep);
    w.addStep(outer);

    assertSame(inner, w.entriesAtLevel(outer).get(0).step());
    assertSame(deep, w.entriesAtLevel(inner).get(0).step());
    assertEquals(1, w.entriesAtLevel(null).size());
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test
```

Expected: compilation failure in `WorkoutTreeTest` — `entriesAtLevel(Step)` does not exist yet.

- [ ] **Step 3: Implement `entriesAtLevel`**

Add to `Workout.java`, directly after `getStepList()` (line 612):

```java
  public List<StepListEntry> entriesAtLevel(Step parent) {
    ArrayList<StepListEntry> out = new ArrayList<>();
    for (StepListEntry entry : getStepList()) {
      if (entry.parent() == parent) {
        out.add(entry);
      }
    }
    return out;
  }
```

- [ ] **Step 4: Run it to verify it passes**

```bash
./gradlew test
```

Expected: all tests PASS, including the four new `WorkoutTreeTest` cases.

- [ ] **Step 5: Run the style gate and commit**

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:lintLatestDebug
git status --short
git add app/src/main/org/runnerup/workout/Workout.java app/test/java/org/runnerup/workout/WorkoutTreeTest.java
git commit -m "feat: add Workout.entriesAtLevel for step tree access"
```

---

### Task 3: Render repeat groups as wrapped containers with nested sub-step lists

The core restructure. Repeat steps become single items whose container encloses the header, a non-scrolling nested `RecyclerView` of their (borderless) sub-steps, and the "Add step inside repeat" button. Sub-step drag reorder is preserved within a group via an inner `ItemTouchHelper`. Footer/item-flattening machinery is removed.

**Files:**
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`
- Modify: `app/res/layout/advanced_workout_repeat_row.xml` (whole rewrite)
- Modify: `app/res/drawable/bg_repeat_group.xml` (corner radius)
- Create: `app/res/drawable/bg_step_card_nested.xml`
- Delete: `app/res/layout/advanced_workout_repeat_footer.xml`
- Modify: `app/src/main/org/runnerup/view/StepButton.java` (add `setNested`)

**Interfaces:**
- Consumes: `Workout.entriesAtLevel(Step)` (Task 2); existing `StepReorder.swapIndex(List<Step>, int, int)`; existing activity methods `addStepInsideRepeat(RepeatStep)`, `editRepeatCount(RepeatStep)`, `confirmDeleteStep(Step)`, `onWorkoutChanged`, field `advancedWorkout`.
- Produces: `CreateAdvancedWorkout.WorkoutStepsAdapter.items` now contains only top-level `Workout.StepListEntry` objects. New inner classes: `RepeatGroupViewHolder` (constructor `(View, ItemTouchHelper)`, method `void bind(RepeatStep)`), `RepeatChildrenAdapter` (constructor `(ItemTouchHelper)`, method `void bind(RepeatStep)`), updated `StepRowViewHolder` (constructor `(View, ItemTouchHelper)`). New `StepButton.setNested(boolean)`. Removed: `FooterItem`, `VIEW_TYPE_FOOTER`, `FooterRowViewHolder`, `listFor(StepListEntry)`, and usages of id `repeat_chip` (renamed to `repeat_title`).

- [ ] **Step 1: Add `setNested` and the borderless card drawable**

Create `app/res/drawable/bg_step_card_nested.xml` (GPL header, then):

```xml
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="12dp" />
        </shape>
    </item>
</ripple>
```

In `app/src/main/org/runnerup/view/StepButton.java`, after the `setOnChangedListener(Runnable)` method (~line 88), add:

```java
  public void setNested(boolean nested) {
    mLayout.setBackgroundResource(nested ? R.drawable.bg_step_card_nested : R.drawable.bg_step_card);
  }
```

No comments. `org.runnerup.R` is already imported in this file.

- [ ] **Step 2: Rewrite `advanced_workout_repeat_row.xml` into the wrapper layout**

Keep the GPL header if present, else add it; then:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_repeat_group"
    android:orientation="vertical"
    android:padding="8dp"
    android:layout_marginTop="6dp"
    android:layout_marginBottom="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <ImageButton
            android:id="@+id/move_up_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/Drag_to_reorder"
            android:padding="8dp"
            android:src="@drawable/ic_drag_handle"
            app:tint="?attr/colorOnSurfaceVariant" />

        <TextView
            android:id="@+id/repeat_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:paddingTop="10dp"
            android:paddingBottom="10dp"
            android:layout_marginStart="8dp"
            android:layout_marginEnd="8dp"
            android:textAppearance="?attr/textAppearanceTitleSmall"
            android:textStyle="bold"
            android:textColor="?attr/colorPrimary" />

        <ImageButton
            android:id="@+id/del_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/Delete"
            android:padding="9dp"
            android:src="@drawable/ic_delete"
            app:tint="?attr/colorError" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/repeat_children_host"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:nestedScrollingEnabled="false"
        android:paddingTop="4dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_step_inside_repeat_button"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="48dp"
        android:layout_marginEnd="16dp"
        android:layout_marginTop="4dp"
        android:text="@string/Add_step_inside_repeat"
        android:textColor="?attr/colorPrimary" />
</LinearLayout>
```

- [ ] **Step 3: Unify the repeat-container corner radius**

In `app/res/drawable/bg_repeat_group.xml`, change `<corners android:radius="14dp" />` to `<corners android:radius="12dp" />`.

- [ ] **Step 4: Delete the now-orphaned footer layout**

```bash
rm app/res/layout/advanced_workout_repeat_footer.xml
```

Verify nothing references it: `rg -n "advanced_workout_repeat_footer" app/src` must output nothing after the adapter changes in Step 6.

- [ ] **Step 5: Rewrite the adapter in `CreateAdvancedWorkout.java`**

Add the import `com.google.android.material.button.MaterialButton` after the
existing `com.google.android.material.bottomsheet.BottomSheetDialog`
import (line 34). The new group holder uses `MaterialButton` for the
`addInside` field.

Constants and removed `FooterItem` (lines 285-295 become):

```java
  private static final int VIEW_TYPE_STEP = 0;
  private static final int VIEW_TYPE_REPEAT = 1;
```

`refreshSteps()` (lines 301-320 become):

```java
    @SuppressLint("NotifyDataSetChanged")
    void refreshSteps() {
      items.clear();
      items.addAll(advancedWorkout.entriesAtLevel(null));
      updateEmptyState();
      notifyDataSetChanged();
    }
```

`getItemViewType(int)` (lines 334-343 become):

```java
    @Override
    public int getItemViewType(int position) {
      Workout.StepListEntry entry = (Workout.StepListEntry) items.get(position);
      return entry.step() instanceof RepeatStep ? VIEW_TYPE_REPEAT : VIEW_TYPE_STEP;
    }
```

`onCreateViewHolder` (lines 345-357 become):

```java
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      if (viewType == VIEW_TYPE_REPEAT) {
        return new RepeatGroupViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false),
            itemTouchHelper);
      }
      return new StepRowViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false), itemTouchHelper);
    }
```

`onBindViewHolder` (lines 359-379 become):

```java
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
      Workout.StepListEntry entry = (Workout.StepListEntry) items.get(position);
      if (viewHolder instanceof StepRowViewHolder) {
        StepRowViewHolder holder = (StepRowViewHolder) viewHolder;
        holder.stepEntry = entry;
        holder.button.setStep(entry.step());
        holder.button.setNested(false);
        holder.nestedGuide.setVisibility(View.GONE);
      } else {
        RepeatGroupViewHolder holder = (RepeatGroupViewHolder) viewHolder;
        holder.bind((RepeatStep) entry.step());
      }
    }
```

Replace `StepRowViewHolder` (lines 382-405) with:

```java
  class StepRowViewHolder extends RecyclerView.ViewHolder {
    final StepButton button;
    final ImageButton moveUp;
    final ImageButton del;
    final View nestedGuide;
    Workout.StepListEntry stepEntry;

    StepRowViewHolder(@NonNull View itemView, @NonNull ItemTouchHelper itemTouchHelper) {
      super(itemView);
      button = itemView.findViewById(R.id.workout_step_button);
      button.setOnChangedListener(onWorkoutChanged);
      nestedGuide = itemView.findViewById(R.id.nested_guide);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
              itemTouchHelper.startDrag(this);
            }
            return false;
          });
      del = itemView.findViewById(R.id.del_button);
      del.setOnClickListener(v -> confirmDeleteStep(stepEntry.step()));
    }
  }
```

Replace `RepeatRowViewHolder` (lines 407-428) with the new group holder:

```java
  class RepeatGroupViewHolder extends RecyclerView.ViewHolder {
    final ImageButton moveUp;
    final TextView title;
    final ImageButton del;
    final RecyclerView childrenHost;
    final MaterialButton addInside;
    final ItemTouchHelper innerTouchHelper;
    RepeatStep repeatStep;

    RepeatGroupViewHolder(@NonNull View itemView, @NonNull ItemTouchHelper itemTouchHelper) {
      super(itemView);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
              itemTouchHelper.startDrag(this);
            }
            return false;
          });
      title = itemView.findViewById(R.id.repeat_title);
      title.setOnClickListener(v -> editRepeatCount(repeatStep));
      del = itemView.findViewById(R.id.del_button);
      del.setOnClickListener(v -> confirmDeleteStep(repeatStep));
      addInside = itemView.findViewById(R.id.add_step_inside_repeat_button);
      addInside.setOnClickListener(v -> addStepInsideRepeat(repeatStep));

      childrenHost = itemView.findViewById(R.id.repeat_children_host);
      childrenHost.setLayoutManager(new LinearLayoutManager(CreateAdvancedWorkout.this));
      childrenHost.setNestedScrollingEnabled(false);
      innerTouchHelper =
          new ItemTouchHelper(
              new ItemTouchHelper.SimpleCallback(
                  ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                  int fromPos = viewHolder.getBindingAdapterPosition();
                  int toPos = target.getBindingAdapterPosition();
                  if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false;
                  }
                  if (fromPos == toPos) {
                    return true;
                  }
                  if (!(recyclerView.getAdapter() instanceof RepeatChildrenAdapter)) {
                    return false;
                  }
                  RepeatChildrenAdapter childAdapter =
                      (RepeatChildrenAdapter) recyclerView.getAdapter();
                  if (fromPos >= childAdapter.items.size() || toPos >= childAdapter.items.size()) {
                    return false;
                  }
                  Workout.StepListEntry fromEntry = childAdapter.items.get(fromPos);
                  Workout.StepListEntry toEntry = childAdapter.items.get(toPos);
                  List<Step> list = repeatStep.getSteps();
                  int fromIndex = list.indexOf(fromEntry.step());
                  int toIndex = list.indexOf(toEntry.step());
                  if (fromIndex >= 0
                      && toIndex >= 0
                      && StepReorder.swapIndex(list, fromIndex, toIndex)) {
                    Collections.swap(childAdapter.items, fromPos, toPos);
                    childAdapter.notifyItemMoved(fromPos, toPos);
                    reorderDirty = true;
                    return true;
                  }
                  return false;
                }

                @Override
                public void clearView(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder) {
                  super.clearView(recyclerView, viewHolder);
                  if (reorderDirty) {
                    reorderDirty = false;
                    onWorkoutChanged.run();
                  }
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

                @Override
                public boolean isLongPressDragEnabled() {
                  return false;
                }
              });
      innerTouchHelper.attachToRecyclerView(childrenHost);
    }

    void bind(RepeatStep repeat) {
      repeatStep = repeat;
      title.setText(getString(org.runnerup.common.R.string.repeat_times, repeat.getRepeatCount()));
      RecyclerView.Adapter<?> adapter = childrenHost.getAdapter();
      RepeatChildrenAdapter childAdapter;
      if (adapter == null) {
        childAdapter = new RepeatChildrenAdapter(innerTouchHelper);
        childrenHost.setAdapter(childAdapter);
      } else {
        childAdapter = (RepeatChildrenAdapter) adapter;
      }
      childAdapter.bind(repeat);
    }
  }
```

Delete `FooterRowViewHolder` (lines 430-439), replace it with the child adapter:

```java
  final class RepeatChildrenAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<Workout.StepListEntry> items = new ArrayList<>();
    final ItemTouchHelper innerItemTouchHelper;

    RepeatChildrenAdapter(ItemTouchHelper innerItemTouchHelper) {
      this.innerItemTouchHelper = innerItemTouchHelper;
    }

    void bind(RepeatStep repeat) {
      items.clear();
      items.addAll(advancedWorkout.entriesAtLevel(repeat));
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      Workout.StepListEntry entry = items.get(position);
      return entry.step() instanceof RepeatStep ? VIEW_TYPE_REPEAT : VIEW_TYPE_STEP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      if (viewType == VIEW_TYPE_REPEAT) {
        return new RepeatGroupViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false),
            innerItemTouchHelper);
      }
      return new StepRowViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false), innerItemTouchHelper);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
      Workout.StepListEntry entry = items.get(position);
      if (viewHolder instanceof StepRowViewHolder) {
        StepRowViewHolder holder = (StepRowViewHolder) viewHolder;
        holder.stepEntry = entry;
        holder.button.setStep(entry.step());
        holder.button.setNested(true);
        holder.nestedGuide.setVisibility(View.VISIBLE);
      } else {
        RepeatGroupViewHolder holder = (RepeatGroupViewHolder) viewHolder;
        holder.bind((RepeatStep) entry.step());
      }
    }
  }
```

Delete `listFor(Workout.StepListEntry)` (lines 441-445).

- [ ] **Step 6: Simplify the outer `ItemTouchHelper.onMove`**

In `onCreate` (lines 134-171), replace the `onMove` body with:

```java
              @Override
              public boolean onMove(
                  @NonNull RecyclerView recyclerView,
                  @NonNull RecyclerView.ViewHolder viewHolder,
                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                  return false;
                }
                if (fromPos == toPos) {
                  return true;
                }
                Workout.StepListEntry fromEntry =
                    (Workout.StepListEntry) advancedWorkoutStepsAdapter.items.get(fromPos);
                Workout.StepListEntry toEntry =
                    (Workout.StepListEntry) advancedWorkoutStepsAdapter.items.get(toPos);
                List<Step> list = advancedWorkout.getSteps();
                int fromIndex = list.indexOf(fromEntry.step());
                int toIndex = list.indexOf(toEntry.step());
                if (fromIndex >= 0
                    && toIndex >= 0
                    && StepReorder.swapIndex(list, fromIndex, toIndex)) {
                  Collections.swap(advancedWorkoutStepsAdapter.items, fromPos, toPos);
                  advancedWorkoutStepsAdapter.notifyItemMoved(fromPos, toPos);
                  reorderDirty = true;
                  return true;
                }
                return false;
              }
```

Leave `clearView`, `onSwiped`, and `isLongPressDragEnabled` unchanged.

- [ ] **Step 7: Build and run the standard gates**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```

Expected: build green, no NEW lint issues, tests pass.

- [ ] **Step 8: Device smoke — repeat wrapper + in-group drag**

Install and push a repeat fixture. On the host, create `/tmp/opencode/RedesignProbe.json` (used by later tasks too):

```json
{
  "workoutName": "RedesignProbe",
  "workoutSegments": [
    {
      "segmentOrder": 1,
      "workoutSteps": [
        {
          "type": "ExecutableStepDTO",
          "stepOrder": 1,
          "stepType": { "stepTypeId": 1, "stepTypeKey": "warmup" },
          "endCondition": { "conditionTypeId": 3, "conditionTypeKey": "time" },
          "endConditionValue": 600,
          "targetType": { "workoutTargetTypeId": 1, "workoutTargetTypeKey": "no.target" }
        },
        {
          "type": "RepeatGroupDTO",
          "stepOrder": 2,
          "numberOfIterations": 3,
          "workoutSteps": [
            {
              "type": "ExecutableStepDTO",
              "stepOrder": 1,
              "stepType": { "stepTypeId": 3, "stepTypeKey": "interval" },
              "endCondition": { "conditionTypeId": 3, "conditionTypeKey": "time" },
              "endConditionValue": 60,
              "targetType": { "workoutTargetTypeId": 6, "workoutTargetTypeKey": "pace.zone" },
              "targetValueOne": 4.1667,
              "targetValueTwo": 4.1667
            },
            {
              "type": "ExecutableStepDTO",
              "stepOrder": 2,
              "stepType": { "stepTypeId": 5, "stepTypeKey": "recovery" },
              "endCondition": { "conditionTypeId": 3, "conditionTypeKey": "time" },
              "endConditionValue": 30,
              "targetType": { "workoutTargetTypeId": 1, "workoutTargetTypeKey": "no.target" }
            }
          ]
        },
        {
          "type": "ExecutableStepDTO",
          "stepOrder": 3,
          "stepType": { "stepTypeId": 2, "stepTypeKey": "cooldown" },
          "endCondition": { "conditionTypeId": 3, "conditionTypeKey": "time" },
          "endConditionValue": 300,
          "targetType": { "workoutTargetTypeId": 1, "workoutTargetTypeKey": "no.target" }
        }
      ]
    }
  ]
}
```

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell "am force-stop org.runnerup.debug"
adb push /tmp/opencode/RedesignProbe.json /data/local/tmp/RedesignProbe.json
adb shell "chmod 666 /data/local/tmp/RedesignProbe.json && run-as org.runnerup.debug cp /data/local/tmp/RedesignProbe.json app_workouts/RedesignProbe.json"
adb shell "am start -n org.runnerup.debug/org.runnerup.view.ManageWorkoutsActivity"
```

Open **RedesignProbe**, wait 2s, dump UI:

```bash
adb shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1
adb shell "cat /sdcard/ui.xml"
```

Verify from the dump (text + bounds):
- `Repeat 3 times` renders bold in primary blue; the two sub-step rows (`1:00` ACTIVE with pace goal, `0:30` RECOVERY) render BETWEEN the repeat title and the `＋ ADD STEP INSIDE REPEAT` button row, bordered by the same container bounds (their x range is inside the wrapper; the wrapper starts at the repeat title's row and ends at the add-button's row — one continuous outline).
- Sub-step rows show the badge at the line END (right side), i.e., for `1:00` the `ACTIVE` badge bounds sit right of the `1:00` text at the same y.
- The warmup row above the wrapper and the cooldown row below it show their badges inline on the duration line.

Drag a sub-step inside the repeat: tap-hold the sub-step's drag handle (`≡`, left of `1:00`), drag down one row, release. Dump again — `0:30` must now precede `1:00`. Force-stop, reopen the workout, dump — order must persist (the in-group swap was serialized).

Delete the probe after all future checks (last task): `adb shell "run-as org.runnerup.debug rm app_workouts/RedesignProbe.json"`.

- [ ] **Step 9: Commit**

```bash
git status --short
git add app/src/main/org/runnerup/view/CreateAdvancedWorkout.java app/src/main/org/runnerup/view/StepButton.java app/res/layout/advanced_workout_repeat_row.xml app/res/layout/advanced_workout_repeat_footer.xml app/res/drawable/bg_repeat_group.xml app/res/drawable/bg_step_card_nested.xml
git commit -m "feat: wrap repeat groups around their sub-steps in the editor"
```

---

### Task 4: Ghost row-action controls and dead-resource cleanup

Row drag/delete controls rest at low alpha and strengthen on press; the old tonal delete disc and the repeat chip drawable are removed.

**Files:**
- Create: `app/res/color/step_move_up_tint.xml`, `app/res/color/step_delete_tint.xml`
- Modify: `app/res/layout/advanced_workout_row.xml`
- Modify: `app/res/layout/advanced_workout_repeat_row.xml`
- Delete: `app/res/drawable/bg_delete_tonal.xml`, `app/res/drawable/bg_repeat_chip.xml`
- Delete: `deleteButtonContainer` color in `app/res/values/colors.xml` and `app/res/values-night/colors.xml`

**Interfaces:**
- Consumes: ids `move_up_button` and `del_button` in both row layouts (Task 3).
- Produces: `@color/step_move_up_tint` (theme onSurfaceVariant at 35% alpha, full when pressed) and `@color/step_delete_tint` (theme onSurfaceVariant at 35% alpha, `colorError` when pressed), applied via `app:tint` on the row buttons.

- [ ] **Step 1: Create the ghost tint state lists**

`app/res/color/step_move_up_tint.xml` (GPL header, then):

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:color="?attr/colorOnSurfaceVariant" />
    <item android:color="?attr/colorOnSurfaceVariant" android:alpha="0.35" />
</selector>
```

`app/res/color/step_delete_tint.xml` (GPL header, then):

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:color="?attr/colorError" />
    <item android:color="?attr/colorOnSurfaceVariant" android:alpha="0.35" />
</selector>
```

- [ ] **Step 2: Apply tints to the step row layout**

In `app/res/layout/advanced_workout_row.xml`:

- On `move_up_button`: replace `app:tint="?attr/colorOnSurfaceVariant"` with `app:tint="@color/step_move_up_tint"`.
- On `del_button`: replace `android:background="@drawable/bg_delete_tonal"` with `android:background="?attr/selectableItemBackgroundBorderless"`, and replace `app:tint="?attr/colorError"` with `app:tint="@color/step_delete_tint"`.

- [ ] **Step 3: Apply tints to the repeat wrapper layout**

In `app/res/layout/advanced_workout_repeat_row.xml` (as rewritten in Task 3):

- On `move_up_button`: replace `app:tint="?attr/colorOnSurfaceVariant"` with `app:tint="@color/step_move_up_tint"`.
- On `del_button`: replace `app:tint="?attr/colorError"` with `app:tint="@color/step_delete_tint"` (background is already `?attr/selectableItemBackgroundBorderless` from Task 3).

- [ ] **Step 4: Delete abandoned resources**

```bash
rm app/res/drawable/bg_delete_tonal.xml app/res/drawable/bg_repeat_chip.xml
```

In `app/res/values/colors.xml`: remove the `deleteButtonContainer` color entry. Same in `app/res/values-night/colors.xml`. (The plan's Task 3 rewrite removed the only usages of `bg_delete_tonal`/`deleteButtonContainer`; `bg_repeat_chip` is no longer referenced.)

Verify orphans:

```bash
rg -n "bg_delete_tonal|deleteButtonContainer|bg_repeat_chip" app/src app/res
```

Expected: no matches.

- [ ] **Step 5: Build, gates, and device smoke**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell "am force-stop org.runnerup.debug && am start -n org.runnerup.debug/org.runnerup.view.ManageWorkoutsActivity"
```

Open **RedesignProbe** and dump. Verify the drag handle and delete icons render at low alpha (screenshot: `adb exec-out screencap -p > /tmp/opencode/editor.png` and eyeball the icons — visibly lighter than the step text). Then: press and hold the `≡` handle of a row → the handle visually darkens (screenshot again optional); release. Tap the `✕` of a row → the confirm dialog appears with the ✕ behavior unchanged.

- [ ] **Step 6: Commit**

```bash
git status --short
git add app/res/color/step_move_up_tint.xml app/res/color/step_delete_tint.xml app/res/layout/advanced_workout_row.xml app/res/layout/advanced_workout_repeat_row.xml app/res/drawable/bg_delete_tonal.xml app/res/drawable/bg_repeat_chip.xml app/res/values/colors.xml app/res/values-night/colors.xml
git commit -m "style: ghost row-action controls in workout editor"
```

---

### Task 5: Full verification and cleanup

Final gate, comprehensive device checklist, and removal of the smoke-test fixture.

**Files:**
- None (verification only).

- [ ] **Step 1: Full gate**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```

Expected: all green; lint shows only the 29 baseline issues.

- [ ] **Step 2: Device checklist (light + dark)**

Light mode (`adb shell "cmd uimode night no"`):

1. Open **8-6-4-2**: badges sit right-aligned on the duration line (warmup/active/rest/cooldown); a goal line shows below.
2. Open **RedesignProbe**: repeat wrapper contains title + both borderless sub-steps + add button; sub-steps have the left guide line and a tappable ripple (tap one → step-edit dialog opens; cancel).
3. Drag sub-step order inside the repeat; force-stop and reopen → order persisted.
4. Drag a top-level row in 8-6-4-2 → rows reorder; nothing inside any repeat moves.
5. Press `✕` on a sub-step → confirm dialog → delete → wrapper re-renders with one sub-step; undo by re-adding via `＋ ADD STEP INSIDE REPEAT`.
6. `Repeat 3 times` title tap → repeat-count dialog opens; set to 2 → title updates to `Repeat 2 times`.

Dark mode (`adb shell "cmd uimode night yes"`): repeat the badge-inline and wrapper checks — badges and containers must read cleanly (the existing `values-night` colors handle contrast; just confirm nothing becomes indistinguishable). Then `adb shell "cmd uimode night no"`.

- [ ] **Step 3: Remove the probe and confirm repo state**

```bash
adb shell "run-as org.runnerup.debug rm -f app_workouts/RedesignProbe.json"
git status --short
```

Expected: working tree only shows the plan's intended files (no stray `.superpowers`/`gradle` user-local files staged).

- [ ] **Step 4: Commit any verification-driven fixes**

If any verification step surfaced a defect, fix it in a new conventional commit (`fix:`/`style:`) and re-run Step 1. If everything passed, there is nothing to commit.