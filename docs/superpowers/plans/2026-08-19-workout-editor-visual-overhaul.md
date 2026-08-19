# Workout Editor Visual Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the visual design of the CreateAdvancedWorkout editor screen with modern Material 3 cards, an indented repeat container, drag-to-reorder handles, and a clean empty-state layout.

**Architecture:** Create new color and drawable resources for step card container backgrounds and step intensity badges. Update step row layouts to replace vertical up/down arrow buttons with a drag handle. Redesign the `StepButton` custom view class and layout for structured card content. Integrate an AndroidX `ItemTouchHelper` in `CreateAdvancedWorkout.java` to support drag-to-reorder via handles, restricting moves to items sharing the same parent list.

**Tech Stack:** Java, Android XML layout/drawables/colors, Material Components, AndroidX RecyclerView + ItemTouchHelper.

## Global Constraints

- No comments added to code unless asked.
- Do not stage user-local files (`gradle.properties`, etc.).
- Spotless formatting passes: run spotlessApply before committing.
- Ensure DayNight themes both resolve all new color resources correctly.

---

### Task 1: Color resources & drawables scaffolding

**Files:**
- Modify: `app/res/values/colors.xml`
- Modify: `app/res/values-night/colors.xml`
- Create: `app/res/drawable/ic_drag_handle.xml`
- Create: `app/res/drawable/bg_step_card.xml`
- Create: `app/res/drawable/bg_repeat_group.xml`

**Interfaces:**
- Produces: color resources (`stepWarmupBg`, `stepActiveBg`, etc.) and drawables (`ic_drag_handle`, `bg_step_card`, `bg_repeat_group`).

- [ ] **Step 1: Add new color resources**

In `app/res/values/colors.xml`, append before `</resources>`:

```xml
    <color name="stepWarmupBg">#FBE9E7</color>
    <color name="stepActiveBg">#FFEBEE</color>
    <color name="stepCooldownBg">#E0F2F1</color>
    <color name="stepRestingBg">#E3F2FD</color>
    <color name="stepRecoveryBg">#FFFDE7</color>
```

In `app/res/values-night/colors.xml`, append before `</resources>`:

```xml
    <color name="stepWarmupBg">#3D1B11</color>
    <color name="stepActiveBg">#3E1516</color>
    <color name="stepCooldownBg">#0E3331</color>
    <color name="stepRestingBg">#12293F</color>
    <color name="stepRecoveryBg">#3E3411</color>
```

- [ ] **Step 2: Create drag handle vector drawable**

Create `app/res/drawable/ic_drag_handle.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:fillColor="#FFFFFFFF"
      android:pathData="M20,9H4v2h16V9z M20,13H4v2h16V13z"/>
</vector>
```

- [ ] **Step 3: Create step card background drawable**

Create `app/res/drawable/bg_step_card.xml` (ripple oval-rect card):

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="?attr/colorSurfaceContainerLow" />
            <stroke android:width="1dp" android:color="?attr/colorOutlineVariant" />
            <corners android:radius="12dp" />
        </shape>
    </item>
</ripple>
```

- [ ] **Step 4: Create repeat group container drawable**

Create `app/res/drawable/bg_repeat_group.xml` (solid unified group container):

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorSurfaceContainerLowest" />
    <stroke android:width="1dp" android:color="?attr/colorOutlineVariant" />
    <corners android:radius="14dp" />
</shape>
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/res/values/colors.xml app/res/values-night/colors.xml app/res/drawable/ic_drag_handle.xml app/res/drawable/bg_step_card.xml app/res/drawable/bg_repeat_group.xml
git commit -m "feat: add visual overhaul colors and drawables"
```

---

### Task 2: Redesign StepButton Custom View & Layout

**Files:**
- Modify: `app/res/layout/step_button.xml`
- Modify: `app/src/main/org/runnerup/view/StepButton.java`

**Interfaces:**
- Consumes: Colors and drawables from Task 1.
- Produces: `StepButton` showing intensity badge pill, primary duration, and target subtitles inside a Material 3 card container.

- [ ] **Step 1: Rewrite step_button.xml layout**

In `app/res/layout/step_button.xml`, replace entire content with a vertical structured card layout:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/step_button_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_step_card"
    android:clickable="true"
    android:focusable="true"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/step_intensity_badge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="8dp"
        android:paddingTop="2dp"
        android:paddingEnd="8dp"
        android:paddingBottom="2dp"
        android:layout_marginBottom="6dp"
        android:textAllCaps="true"
        android:textSize="10sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/step_duration_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceTitleMedium"
        android:textStyle="bold"
        android:textColor="?attr/colorOnSurface" />

    <TextView
        android:id="@+id/step_goal_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />
</LinearLayout>
```

- [ ] **Step 2: Update StepButton.java custom class**

In `app/src/main/org/runnerup/view/StepButton.java`:
- Declare a private field: `private final TextView mIntensityBadge;` (and find it in the constructor R.id.step_intensity_badge).
- Remove `mIntensityIcon` field declaration, lookup, and image setting calls (or keep the field but set visibility GONE/unused, better to remove lookup of R.id.step_icon since it's deleted from layout).
- Inside `setStep(Step step)`:
  - Find matching background and text colors for `mIntensityBadge` depending on step intensity:
    - `ACTIVE`: text `@color/stepActive`, background `@color/stepActiveBg`
    - `RESTING`: text `@color/stepResting`, background `@color/stepRestingBg`
    - `REPEAT`: text `@color/stepRepeat`, background `@color/stepRestingBg` (or resting colors)
    - `WARMUP`: text `@color/stepWarmup`, background `@color/stepWarmupBg`
    - `COOLDOWN`: text `@color/stepCooldown`, background `@color/stepCooldownBg`
    - `RECOVERY`: text `@color/stepRecovery`, background `@color/stepRecoveryBg`
  - Apply colors to `mIntensityBadge`:
    ```java
    mIntensityBadge.setText(step.getIntensity().getTextId());
    mIntensityBadge.setTextColor(ContextCompat.getColor(mContext, textColorId));
    android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
    badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
    badgeBg.setCornerRadius(formatter.dp_to_px(6));
    badgeBg.setColor(ContextCompat.getColor(mContext, bgColorId));
    mIntensityBadge.setBackground(badgeBg);
    ```
  - For `DurationType` null: set `mDurationValue.setText(org.runnerup.common.R.string.Until_press)`.
  - For `GoalType` null: set `mGoalValue.setVisibility(View.GONE)` (to omit target line cleanly). If not null, set target string and make `mGoalValue.setVisibility(View.VISIBLE)`.
- Make sure `mDurationValue` and `mGoalValue` visibility logic behaves cleanly without crashing.

- [ ] **Step 3: Run Spotless & verify build**

Run: `./gradlew spotlessApply && ./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/step_button.xml app/src/main/org/runnerup/view/StepButton.java
git commit -m "feat: redesign StepButton layout and custom text/badge styling"
```

---

### Task 3: Redesign Step and Repeat Row Layouts

**Files:**
- Modify: `app/res/layout/advanced_workout_row.xml`
- Modify: `app/res/layout/advanced_workout_repeat_row.xml`
- Modify: `app/res/layout/advanced_workout_repeat_footer.xml`

**Interfaces:**
- Consumes: `ic_drag_handle` from Task 1.
- Produces: rows showing a drag handle on the left instead of up/down arrows.

- [ ] **Step 1: Overhaul advanced_workout_row.xml**

Replace vertical arrows linear layout with a single drag handle:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="4dp">

    <ImageButton
        android:id="@+id/move_up_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/Move_up"
        android:padding="8dp"
        android:src="@drawable/ic_drag_handle"
        app:tint="?attr/colorOnSurfaceVariant" />

    <view
        android:id="@+id/workout_step_button"
        class="org.runnerup.view.StepButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="4dp"
        android:layout_marginEnd="4dp" />

    <ImageButton
        android:id="@+id/del_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="@drawable/bg_delete_tonal"
        android:contentDescription="@string/Delete"
        android:padding="9dp"
        android:src="@drawable/ic_delete"
        app:tint="?attr/colorError" />
</LinearLayout>
```

- [ ] **Step 2: Overhaul advanced_workout_repeat_row.xml**

Apply single drag handle, tonal repeat count chip, delete button, and group container card background:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_repeat_group"
    android:padding="8dp"
    android:layout_marginTop="6dp"
    android:layout_marginBottom="2dp"
    android:orientation="vertical">

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
            android:contentDescription="@string/Move_up"
            android:padding="8dp"
            android:src="@drawable/ic_drag_handle"
            app:tint="?attr/colorOnSurfaceVariant" />

        <TextView
            android:id="@+id/repeat_chip"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:layout_marginEnd="8dp"
            android:background="@drawable/bg_repeat_chip"
            android:paddingStart="16dp"
            android:paddingTop="10dp"
            android:paddingEnd="16dp"
            android:paddingBottom="10dp"
            android:textAppearance="?attr/textAppearanceTitleSmall"
            android:textColor="?attr/colorPrimary" />

        <ImageButton
            android:id="@+id/del_button"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="@drawable/bg_delete_tonal"
            android:contentDescription="@string/Delete"
            android:padding="9dp"
            android:src="@drawable/ic_delete"
            app:tint="?attr/colorError" />
    </LinearLayout>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingStart="48dp"
        android:paddingEnd="16dp"
        android:paddingTop="4dp"
        android:paddingBottom="4dp"
        android:text="@string/Inside_repeat"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorPrimary" />
</LinearLayout>
```

- [ ] **Step 3: Refine repeat group footer**

In `app/res/layout/advanced_workout_repeat_footer.xml`, use standard outlined button styling:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_repeat_group"
    android:paddingBottom="8dp"
    android:orientation="vertical">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/add_step_inside_repeat_button"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="48dp"
        android:layout_marginEnd="16dp"
        android:text="@string/Add_step_inside_repeat"
        android:textColor="?attr/colorPrimary" />
</LinearLayout>
```

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/advanced_workout_row.xml app/res/layout/advanced_workout_repeat_row.xml app/res/layout/advanced_workout_repeat_footer.xml
git commit -m "feat: redesign step/repeat rows with drag handle and outlined footer button"
```

---

### Task 4: Java Reordering Logic & Empty State

**Files:**
- Modify: `app/res/layout/create_advanced_workout.xml`
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: layouts and resources from Task 3.
- Produces: drag reordering implementation restricting moves within same parent groups, and beautiful empty state.

- [ ] **Step 1: Replace old empty state text in create_advanced_workout.xml**

In `app/res/layout/create_advanced_workout.xml`:
- Replace R.id.empty_state_text TextView with a centered LinearLayout holding an ImageView (standard icon), Title, and Subtitle:

```xml
        <LinearLayout
            android:id="@+id/empty_state_layout"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="24dp"
            android:visibility="gone">

            <ImageView
                android:layout_width="64dp"
                android:layout_height="64dp"
                android:src="@drawable/ic_flag"
                app:tint="?attr/colorOutline"
                android:layout_marginBottom="16dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/No_steps_yet"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textStyle="bold"
                android:textColor="?attr/colorOnSurface" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:gravity="center"
                android:text="Tap + to add your first step or interval repeat"
                android:textAppearance="?attr/textAppearanceBodyMedium"
                android:textColor="?attr/colorOnSurfaceVariant" />
        </LinearLayout>
```

- [ ] **Step 2: Update R.id.empty_state_text lookup in CreateAdvancedWorkout.java**

In `CreateAdvancedWorkout.java`:
- Inside `updateEmptyState()`: find `R.id.empty_state_layout` instead of `R.id.empty_state_text` and toggle its visibility.
- Keep the `moveUp` and `moveDown` ImageButton declarations but repurpose them: `moveUp` is now our drag handle `ImageButton`. Since we only have one drag handle icon, the layout's `@id/move_up_button` remains as our single handle. Note: `moveDown` is completely unused in the new row layouts — remove all references to `moveDown` field and its lookup in both `StepRowViewHolder` and `RepeatRowViewHolder`.

- [ ] **Step 3: Wire up ItemTouchHelper drag reordering**

In `CreateAdvancedWorkout.java`:
- Declare `private ItemTouchHelper itemTouchHelper;` field.
- In `onCreate(Bundle)`:
  - Initialize the `itemTouchHelper` as specified in planning notes:
    ```java
    itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            int fromPos = viewHolder.getBindingAdapterPosition();
            int toPos = target.getBindingAdapterPosition();
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                return false;
            }
            Object fromItem = advancedWorkoutStepsAdapter.items.get(fromPos);
            Object toItem = advancedWorkoutStepsAdapter.items.get(toPos);
            if (fromItem instanceof Workout.StepListEntry && toItem instanceof Workout.StepListEntry) {
                Workout.StepListEntry fromEntry = (Workout.StepListEntry) fromItem;
                Workout.StepListEntry toEntry = (Workout.StepListEntry) toItem;
                if (fromEntry.parent() == toEntry.parent()) {
                    List<Step> list = listFor(fromEntry);
                    int fromIndex = list.indexOf(fromEntry.step());
                    int toIndex = list.indexOf(toEntry.step());
                    if (fromIndex >= 0 && toIndex >= 0) {
                        if (StepReorder.swapIndex(list, fromIndex, toIndex)) {
                            advancedWorkoutStepsAdapter.refreshSteps();
                            onWorkoutChanged.run();
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        @Override
        public boolean isLongPressDragEnabled() { return false; }
    });
    itemTouchHelper.attachToRecyclerView(recyclerView);
    ```
- In both `StepRowViewHolder` and `RepeatRowViewHolder` constructors:
  - Update `moveUp` touch listener to trigger drag on touch:
    ```java
    moveUp.setOnTouchListener((v, event) -> {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            itemTouchHelper.startDrag(this);
        }
        return false;
    });
    ```
  - Remove all lines referencing `moveDown` because that button no longer exists.

- [ ] **Step 4: Run Spotless & verify build**

Run: `./gradlew spotlessApply && ./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/create_advanced_workout.xml app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "feat: implement ItemTouchHelper drag reordering and modern empty state"
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
Expected: all pass; lint reports only okhttp (do not fix); spotless is clean.

- [ ] **Step 2: Device smoke test**

Install the debug APK. Open Settings → Manage Workouts → create/edit a workout. Verify:
- Beautiful empty state with flag icon, Title, Subtitle.
- Tapping FAB adds a step/repeat group.
- Steps are card-based with color intensity pills, bold duration values, and target subtitle lines.
- Drag handle on the left reorders steps smoothly within their respective groups (nested swaps within the repeat, top-level swaps outside).
- Clean nesting vertical guides and outlined footer buttons.
- Light and dark modes are perfectly color-balanced.
- Push to fork: `git push fork master`
