# Manage Workouts Drag-to-Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users reorder local workouts in Manage Workouts by dragging a handle, persist the order in an `app_workouts/.order` JSON file, and have that order apply to the Start-screen workout picker too.

**Architecture:** A pure-Java helper `org.runnerup.workout.WorkoutOrder` owns the `.order` file I/O (read/write/replace/remove/apply) and is unit-tested without Android. `WorkoutListAdapter.load()` (the shared funnel for both screens) applies the sort. `ManageWorkoutsActivity` gets an `ItemTouchHelper` driving drag from a per-row handle, persisting on drop. `CreateAdvancedWorkout`'s rename/delete handlers keep the order file in sync.

**Tech Stack:** Java, AndroidX RecyclerView (`ItemTouchHelper`, already depended on), Material 3 (`MaterialCardView`, `ImageButton`), `org.json:json` (already bundled and used by `WorkoutSerializer`), JUnit4 + Mockito for JVM tests.

## Global Constraints

- Gate, in order, after any change: `./gradlew test` → `:app:lintLatestDebug` (baseline `app/lint-baseline.xml` pre-existing issues only; new issues fatal) → `spotlessApply`/`spotlessCheck` (googleJavaFormat) → `:app:assembleLatestDebug`.
- No code comments unless the user asks (AGENTS.md). Context-free `catch (Exception ignored) {}` is allowed (pattern exists at `CreateAdvancedWorkout.persistCurrentWorkoutName`).
- Unit tests live under `app/test/java` (non-standard sourceSet; `app/sourceSets` root is `test`).
- Conventional commits (`feat:`, `refactor:`, `docs:`, `test:`, `fix:`).
- Workout files are stored by `WorkoutSerializer.getFile` under `ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0)` (`app_workouts/`). The order file is `app_workouts/.order` and is naturally excluded by the `.json` listing filter.
- Spec: `docs/superpowers/specs/2026-09-09-workout-list-reorder-design.md`. Reorderable only within the "My phone" group; cloud rows must not be draggable.

---

### Task 1: WorkoutOrder helper + unit tests

**Files:**
- Create: `app/src/main/org/runnerup/workout/WorkoutOrder.java`
- Test: `app/test/java/org/runnerup/workout/WorkoutOrderTest.java`

**Interfaces:**
- Consumes: `WorkoutSerializer.WORKOUTS_DIR` (String constant), `org.json.JSONArray`, `android.content.Context`.
- Produces:
  - `public static File orderFile(Context ctx)` → `new File(ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0), ".order")`
  - `public static List<String> load(File orderFile)` → names in stored order; empty list when the file is absent or unparseable. Never null.
  - `public static List<String> apply(List<String> filenames, File orderFile)` → filenames re-sorted: entries whose name (filename minus trailing `.json`) appears in the order file keep the order file's order; all other filenames keep their input relative order and are appended after the ranked ones. Dangling order-file names are ignored. Returns the same `String` instances passed in.
  - `public static void write(File orderFile, List<String> names) throws IOException` → atomically write the JSON array (temp file + `renameTo`).
  - `public static void replace(File orderFile, String oldName, String newName) throws IOException` → rename one entry in place; does nothing (no file write) if `oldName` is not present.
  - `public static void remove(File orderFile, String name) throws IOException` → drop one entry; does nothing (no file write) if `name` is not present.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/workout/WorkoutOrderTest.java`:

```java
package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;

public class WorkoutOrderTest {

  private static File tempDir() throws IOException {
    return Files.createTempDirectory("workout-order").toFile();
  }

  private static void writeRaw(File orderFile, String content) throws IOException {
    try (FileWriter w = new FileWriter(orderFile)) {
      w.write(content);
    }
  }

  @Test
  public void applyWithFullOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"c\",\"a\"]");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json", "b.json", "c.json"), orderFile);
    assertEquals(Arrays.asList("c.json", "a.json", "b.json"), result);
  }

  @Test
  public void applyWithPartialOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"b\"]");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json", "b.json", "c.json"), orderFile);
    assertEquals(Arrays.asList("b.json", "a.json", "c.json"), result);
  }

  @Test
  public void applyIgnoresDanglingOrderNames() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"zzz\",\"a\"]");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json"), orderFile);
    assertEquals(Collections.singletonList("a.json"), result);
  }

  @Test
  public void applyWithMissingOrderFilePreservesInputOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json", "b.json"), orderFile);
    assertEquals(Arrays.asList("a.json", "b.json"), result);
  }

  @Test
  public void applyKeepsUnrankedFilesInInputRelativeOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"b\"]");
    List<String> result = WorkoutOrder.apply(Arrays.asList("c.json", "a.json", "b.json"), orderFile);
    assertEquals(Arrays.asList("b.json", "c.json", "a.json"), result);
  }

  @Test
  public void writeAndLoadRoundTrip() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    assertEquals(Arrays.asList("a", "b", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void replaceUpdatesEntry() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    WorkoutOrder.replace(orderFile, "b", "renamed");
    assertEquals(Arrays.asList("a", "renamed", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void replaceUnknownNameDoesNotCreateFile() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.replace(orderFile, "nope", "other");
    assertFalse(orderFile.exists());
  }

  @Test
  public void removeDropsEntry() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    WorkoutOrder.remove(orderFile, "b");
    assertEquals(Arrays.asList("a", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void removeUnknownNameDoesNotCreateFile() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.remove(orderFile, "nope");
    assertFalse(orderFile.exists());
  }

  @Test
  public void loadUnparseableFileReturnsEmpty() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "{not json");
    assertTrue(WorkoutOrder.load(orderFile).isEmpty());
  }

  @Test
  public void jsonArrayIsUsableSerialization() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b"));
    assertEquals("[\"a\",\"b\"]", new JSONArray(WorkoutOrder.load(orderFile)).toString());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class WorkoutOrder`.

- [ ] **Step 3: Write minimal `WorkoutOrder` implementation**

Create `app/src/main/org/runnerup/workout/WorkoutOrder.java`:

```java
/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.workout;

import android.content.Context;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** User-defined display order for local workouts, stored in app_workouts/.order. */
public class WorkoutOrder {

  private static final String ORDER_FILE = ".order";
  private static final String JSON_SUFFIX = ".json";

  public static File orderFile(Context ctx) {
    return new File(ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0), ORDER_FILE);
  }

  public static List<String> load(File orderFile) {
    List<String> order = new ArrayList<>();
    if (orderFile == null || !orderFile.isFile()) return order;
    try (Reader in = new FileReader(orderFile)) {
      JSONArray arr = new JSONArray(readAll(in));
      for (int i = 0; i < arr.length(); i++) order.add(arr.getString(i));
    } catch (IOException | JSONException e) {
      return new ArrayList<>();
    }
    return order;
  }

  public static List<String> apply(List<String> filenames, File orderFile) {
    List<String> order = load(orderFile);
    List<String> result = new ArrayList<>(filenames.size());
    for (String name : order) {
      for (String f : filenames) {
        if (name.contentEquals(stripSuffix(f)) && !result.contains(f)) result.add(f);
      }
    }
    for (String f : filenames) {
      if (!result.contains(f)) result.add(f);
    }
    return result;
  }

  public static void write(File orderFile, List<String> names) throws IOException {
    File tmp = new File(orderFile.getParentFile(), ORDER_FILE + ".tmp");
    try (Writer out = new FileWriter(tmp)) {
      out.write(new JSONArray(names).toString());
      out.flush();
    }
    if (!tmp.renameTo(orderFile)) {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      throw new IOException("Failed to write " + orderFile);
    }
  }

  public static void replace(File orderFile, String oldName, String newName) throws IOException {
    List<String> order = load(orderFile);
    boolean changed = false;
    for (int i = 0; i < order.size(); i++) {
      if (oldName.contentEquals(order.get(i))) {
        order.set(i, newName);
        changed = true;
        break;
      }
    }
    if (changed) write(orderFile, order);
  }

  public static void remove(File orderFile, String name) throws IOException {
    List<String> order = load(orderFile);
    if (order.remove(name)) write(orderFile, order);
  }

  private static String stripSuffix(String filename) {
    if (filename.endsWith(JSON_SUFFIX)) {
      return filename.substring(0, filename.length() - JSON_SUFFIX.length());
    }
    return filename;
  }

  private static String readAll(Reader in) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[1024];
    int n;
    while ((n = in.read(buf)) > 0) sb.append(buf, 0, n);
    return sb.toString();
  }
}
```

Note: the Javadoc on the class is a doc comment, not a code comment — allowed and consistent with the codebase's license headers.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test`
Expected: PASS (all `WorkoutOrderTest` cases green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/workout/WorkoutOrder.java app/test/java/org/runnerup/workout/WorkoutOrderTest.java
git commit -m "feat: add WorkoutOrder helper for workout display order"
```

---

### Task 2: Apply the order in the shared workout loader

**Files:**
- Modify: `app/src/main/org/runnerup/view/WorkoutListAdapter.java:93-96` (`load`)

**Interfaces:**
- Consumes: `WorkoutOrder.apply(List<String>, File)`, `WorkoutOrder.orderFile(Context)`.
- Produces: `WorkoutListAdapter.load(Context)` returns the directory's `.json` filenames sorted by the order file (unknown names appended in filesystem order). Null stays null.

- [ ] **Step 1: Modify `load`**

In `app/src/main/org/runnerup/view/WorkoutListAdapter.java`, replace the body of `load` so the listing is sorted by the order file:

```java
  public static String[] load(Context ctx) {
    File f = ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0);
    String[] list = f.list((dir, filename) -> filename.endsWith(".json"));
    if (list == null) return null;
    return WorkoutOrder.apply(java.util.Arrays.asList(list), WorkoutOrder.orderFile(ctx))
        .toArray(new String[0]);
  }
```

(No imports change: `java.util.Arrays` is referred to fully-qualified; `File`, `WorkoutSerializer`, and the new `WorkoutOrder` are in scope because the file already imports `java.io.File` and `org.runnerup.workout.WorkoutSerializer`, and it shares the package root with `org.runnerup.workout` via an import — add `import org.runnerup.workout.WorkoutOrder;` next to the `WorkoutSerializer` import if the compiler asks, since `WorkoutOrder` is in another package.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. Behavior is unchanged on-device until an `.order` file exists, so no runtime check yet.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/WorkoutListAdapter.java
git commit -m "feat: sort workout file listing by .order file"
```

---

### Task 3: Drag-to-reorder in Manage Workouts

**Files:**
- Modify: `app/res/layout/manage_workouts_list_row.xml`
- Modify: `app/src/main/org/runnerup/view/ManageWorkoutsActivity.java`

**Interfaces:**
- Consumes: `WorkoutOrder.write(File, List<String>)`, `WorkoutOrder.orderFile(Context)`; adapter field `items` (an `ArrayList<Object>` of `ContentValues` group headers and `SyncManager.WorkoutRef` rows); `PHONE_STRING` field; existing `bindWorkout(WorkoutViewHolder, WorkoutRef)` and `WorkoutViewHolder`.
- Produces: rows in the "My phone" group show a drag handle (`R.id.workout_drag_handle`); dragging it reorders within the phone group only; on drop the new order is persisted to the `.order` file.

- [ ] **Step 1: Add the drag handle to the row layout**

Rewrite `app/res/layout/manage_workouts_list_row.xml` to wrap the name in a horizontal row with a leading handle (keep the card's existing style/attrs as-is):

```xml
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/workout_card"
    style="?attr/materialCardViewFilledStyle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    android:layout_marginTop="8dp"
    android:layout_marginEnd="16dp"
    android:layout_marginBottom="8dp"
    android:clickable="true"
    android:focusable="true"
    app:cardBackgroundColor="?attr/colorSurfaceContainerHigh"
    app:cardCornerRadius="12dp"
    app:cardElevation="0dp"
    app:rippleColor="?attr/colorControlHighlight">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        tools:ignore="UseCompoundDrawables">

        <ImageButton
            android:id="@+id/workout_drag_handle"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/Drag_to_reorder"
            android:padding="12dp"
            android:src="@drawable/ic_drag_handle"
            app:tint="?attr/colorOnSurfaceVariant" />

        <TextView
            android:id="@+id/workout_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:drawablePadding="12dp"
            android:ellipsize="end"
            android:gravity="center_vertical"
            android:maxLines="1"
            android:paddingStart="12dp"
            android:paddingTop="16dp"
            android:paddingEnd="12dp"
            android:paddingBottom="16dp"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:textColor="?attr/colorOnSurface"
            app:drawableEndCompat="@drawable/ic_chevron_right_24dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

`@string/Drag_to_reorder` exists (used by the editor rows). `tools:ignore="UseCompoundDrawables"` is required: the handle is interactive, so a compound drawable is not applicable.

- [ ] **Step 2: Add imports to `ManageWorkoutsActivity.java`**

Add:
- `android.view.MotionEvent`
- `android.widget.ImageButton`
- `androidx.recyclerview.widget.ItemTouchHelper`
- `java.util.Collections`

- [ ] **Step 3: Add the `itemTouchHelper` field**

Add a field next to the existing `private WorkoutListAdapter adapter = null;`:

```java
  private ItemTouchHelper itemTouchHelper = null;
```

- [ ] **Step 4: Attach the touch helper in `onCreate`**

After `list.setAdapter(adapter);` (`ManageWorkoutsActivity.java:134`), add:

```java
    itemTouchHelper =
        new ItemTouchHelper(
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
              @Override
              public boolean isLongPressDragEnabled() {
                return false;
              }

              @Override
              public boolean isItemViewSwipeEnabled() {
                return false;
              }

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
                Object fromItem = adapter.items.get(fromPos);
                Object toItem = adapter.items.get(toPos);
                if (!(fromItem instanceof WorkoutRef) || !(toItem instanceof WorkoutRef)) {
                  return false;
                }
                WorkoutRef fromRef = (WorkoutRef) fromItem;
                WorkoutRef toRef = (WorkoutRef) toItem;
                if (!PHONE_STRING.contentEquals(fromRef.synchronizer())
                    || !PHONE_STRING.contentEquals(toRef.synchronizer())) {
                  return false;
                }
                Collections.swap(adapter.items, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                adapter.orderDirty = true;
                return true;
              }

              @Override
              public void onSwiped(
                  @NonNull RecyclerView recyclerView,
                  @NonNull RecyclerView.ViewHolder viewHolder,
                  int direction) {}

              @Override
              public void clearView(
                  @NonNull RecyclerView recyclerView,
                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                adapter.persistOrder();
              }
            });
    itemTouchHelper.attachToRecyclerView(list);
```

- [ ] **Step 5: Add `orderDirty` + `persistOrder` to `WorkoutListAdapter`**

In the inner `WorkoutListAdapter` class, add a boolean field next to `items`:

```java
    private boolean orderDirty = false;
```

Add this method to the adapter (next to `bindWorkout`):

```java
    private void persistOrder() {
      if (!orderDirty) return;
      orderDirty = false;
      try {
        ArrayList<String> names = new ArrayList<>();
        for (Object item : items) {
          if (item instanceof WorkoutRef) {
            WorkoutRef ref = (WorkoutRef) item;
            if (PHONE_STRING.contentEquals(ref.synchronizer())) {
              names.add(ref.workoutName());
            }
          }
        }
        WorkoutOrder.write(WorkoutOrder.orderFile(context), names);
      } catch (IOException e) {
        Log.e(getClass().getName(), "persistOrder: " + e.getMessage());
      }
    }
```

Imports to add for this step in `ManageWorkoutsActivity.java`: `org.runnerup.workout.WorkoutOrder` (next to the existing `org.runnerup.workout.Workout` and `org.runnerup.workout.WorkoutSerializer` imports).

- [ ] **Step 6: Show/wire the handle in `bindWorkout` and the holder**

Replace `bindWorkout`:

```java
    private void bindWorkout(WorkoutViewHolder holder, WorkoutRef workout) {
      holder.name.setText(workout.workoutName());
      holder.itemView.setOnClickListener(v -> openEditor(workout));
      if (PHONE_STRING.contentEquals(workout.synchronizer())) {
        holder.dragHandle.setVisibility(View.VISIBLE);
        holder.dragHandle.setOnTouchListener(
            (v, event) -> {
              if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemTouchHelper.startDrag(holder);
              }
              return false;
            });
      } else {
        holder.dragHandle.setVisibility(View.GONE);
        holder.dragHandle.setOnTouchListener(null);
      }
    }
```

Update `WorkoutViewHolder`:

```java
  class WorkoutViewHolder extends RecyclerView.ViewHolder {
    final TextView name;
    final ImageButton dragHandle;

    WorkoutViewHolder(@NonNull View itemView) {
      super(itemView);
      name = itemView.findViewById(R.id.workout_name);
      dragHandle = itemView.findViewById(R.id.workout_drag_handle);
    }
  }
```

- [ ] **Step 7: Full gate**

Run: `./gradlew test :app:lintLatestDebug spotlessCheck :app:assembleLatestDebug`
Expected: tests pass; lint "found no new issues"; spotless clean; BUILD SUCCESSFUL. If spotless reformats the Java, run `spotlessApply` first, then `spotlessCheck`.

- [ ] **Step 8: Device smoke test**

Install and verify (device package `org.runnerup.debug`):

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell am start -n org.runnerup.debug/org.runnerup.view.ManageWorkoutsActivity
```

- Dump UI (`adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml`): each "My phone" row shows a drag handle left of the name; rows still open the editor on tap.
- Long-press-drag a handle down two rows via `adb shell input swipe <x1> <y1> <x1> <y2> <ms>`.
- Verify `run-as org.runnerup.debug ls app_workouts` now contains `.order`, and `run-as org.runnerup.debug cat app_workouts/.order` lists names in the new top-to-bottom order.
- Force-stop and relaunch (`adb shell am force-stop org.runnerup.debug` then start again): dragged order persisted.

- [ ] **Step 9: Commit**

```bash
git add app/res/layout/manage_workouts_list_row.xml app/src/main/org/runnerup/view/ManageWorkoutsActivity.java
git commit -m "feat: drag-to-reorder workouts in Manage Workouts"
```

---

### Task 4: Keep the order file in sync on rename/delete

**Files:**
- Modify: `app/src/main/org/runnerup/view/CreateAdvancedWorkout.java`

**Interfaces:**
- Consumes: `WorkoutOrder.replace(File, String, String)`, `WorkoutOrder.remove(File, String)`, `WorkoutOrder.orderFile(Context)`.
- Produces: renaming a workout updates its entry in `.order`; deleting a workout drops its entry. Both no-ops when the order file is absent or the name isn't present.

- [ ] **Step 1: Update `.order` on rename**

In `renameWorkoutButtonClick` (`CreateAdvancedWorkout.java:653-659`), inside the existing `try` block, immediately after `if (!oldFile.delete()) throw new IOException("Failed to delete old workout file");`, add:

```java
                    try {
                      WorkoutOrder.replace(
                          WorkoutOrder.orderFile(getApplicationContext()),
                          oldWorkoutName,
                          newWorkoutName);
                    } catch (IOException ignored) {
                    }
```

- [ ] **Step 2: Update `.order` on delete**

In `deleteWorkoutButtonClick`'s positive-button handler, immediately after `f.delete();`, add:

```java
                  try {
                    WorkoutOrder.remove(WorkoutOrder.orderFile(getApplicationContext()), name);
                  } catch (IOException ignored) {
                  }
```

- [ ] **Step 3: Add the import**

Add `import org.runnerup.workout.WorkoutOrder;` next to the existing `org.runnerup.workout.*` imports in `CreateAdvancedWorkout.java`.

- [ ] **Step 4: Full gate**

Run: `./gradlew test :app:lintLatestDebug spotlessCheck :app:assembleLatestDebug`
Expected: tests pass; lint "found no new issues"; spotless clean; BUILD SUCCESSFUL.

- [ ] **Step 5: Device smoke test**

Use the same install/fresh `ManageWorkoutsActivity` from Task 3 (an `.order` file now exists on device):
- Open a reordered workout in the editor, rename it (`More options` → `Rename`). Verify `adb shell run-as org.runnerup.debug cat app_workouts/.order` shows the new name in the same position.
- Delete it (`More options` → `Delete` → `Yes`). Verify the name is gone from `.order` and from `ls app_workouts`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/CreateAdvancedWorkout.java
git commit -m "feat: keep workout order file in sync on rename and delete"
```