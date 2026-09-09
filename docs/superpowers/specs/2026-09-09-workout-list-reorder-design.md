# Manage Workouts Drag-to-Reorder — Design Spec

**Goal:** Let users reorder workouts in the Manage Workouts list by dragging a row. The saved order becomes the single source of truth and is shared with the Start-screen workout picker.

**Scope:** `app` module only. Touches `ManageWorkoutsActivity.java`, `manage_workouts_list_row.xml`, `WorkoutListAdapter.java` (the `BaseAdapter` used by the Start picker), `CreateAdvancedWorkout.java`, a new helper class `org.runnerup.workout.WorkoutOrder`, and one unit test.

## Background (current state)

- Manage Workouts renders a flat `RecyclerView` whose items are either group `ContentValues` headers (one per account: "My phone" plus configured cloud providers) or `SyncManager.WorkoutRef` rows. Rows are `MaterialCardView`s (name + trailing chevron) that open the editor on tap (`ManageWorkoutsActivity.java:540`).
- Local workout files live in `app_workouts/` (via `WorkoutSerializer.WORKOUTS_DIR`). `WorkoutListAdapter.load(Context)` (`app/src/main/org/runnerup/view/WorkoutListAdapter.java:93`) returns them from `File.list(...)` — arbitrary filesystem order. Both Manage Workouts (`listLocal()`) and the Start picker (`StartFragment` reload) consume this loader.
- The editor already implements drag-to-reorder with the platform `ItemTouchHelper`: a `SimpleCallback(UP|DOWN, 0)` with `startDrag(holder)` invoked from a handle's `OnTouchListener` on `ACTION_DOWN`, position swaps restricted to valid targets, and persistence in `clearView` (`CreateAdvancedWorkout.java:129`, `:394`). The `ic_drag_handle` drawable already exists.
- There is no workout table in SQLite (`DBHelper` creates ACCOUNT/ACTIVITY/LOCATION/LAP/EXPORT only) and no existing ordering metadata.
- The editor's rename and delete handlers (`renameWorkoutButtonClick`, `deleteWorkoutButtonClick` in `CreateAdvancedWorkout.java`) move/remove files; any order store must be kept in sync there.

## Decisions (confirmed with user)

1. **Interaction:** a dedicated drag-handle icon on each local row (not long-press). Tap-to-open stays unambiguous.
2. **Scope:** reorderable only within the local "My phone" group. Cloud group rows are not draggable (their order is provider-owned and transient).
3. **Persistence:** a `.order` JSON file (`app_workouts/.order`, `["name1","name2",...]`) beside the workouts, and it is the single source of truth applied everywhere the workout list is shown (Manage Workouts and the Start picker).

## Design

### 1. Reorder mechanics (`ManageWorkoutsActivity.java`)

- Attach an `ItemTouchHelper` with `SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)` to the target `RecyclerView` (`R.id.workout_list`) in `onCreate`.
- `isLongPressDragEnabled = false`; `isItemViewSwipeEnabled = false`. Drag starts only from the handle.
- `onMove`: accept only when both the dragged and target items are `WorkoutRef`s belonging to the "My phone" group (same `synchronizer()` == `PHONE_STRING`); otherwise return `false`. On accept, swap the two entries in the adapter's flat `items` list and `notifyItemMoved(from, to)`.
- `clearView`: write the resulting "My phone" order to disk via `WorkoutOrder` and call `adapter.refresh()` (group memberships don't change; the visual list already reflects the move, and `refresh()` keeps header state intact — an alternative is no-op since `items` already matches, but a serialized drop of free-order movement across positions already applied to `items`; a full refresh guarantees the persisted order and list agree). ItemTouchHelper's built-in elevation/translation feedback is used as-is.
- `bindWorkout`: set the handle visible only when `workout.synchronizer().contentEquals(PHONE_STRING)`; wire the handle `OnTouchListener` → `itemTouchHelper.startDrag(holder)` on `ACTION_DOWN` (mirrors `CreateAdvancedWorkout.java:394`). `WorkoutViewHolder` gains a `dragHandle` field (`R.id.workout_drag_handle`).

### 2. Row layout (`manage_workouts_list_row.xml`)

- Add a leading `ImageButton` (`@+id/workout_drag_handle`, `48dp`, `ic_drag_handle`, `?attr/colorOnSurfaceVariant` tint, `selectableItemBackgroundBorderless`) before the name `TextView`; keep the trailing chevron and tap-to-open behavior on the card. The handle and name sit in a horizontal `LinearLayout` inside the `MaterialCardView` (the compound-drawable optimization from the previous redesign no longer applies once there are two interactive children).

### 3. Order store helper (`org.runnerup.workout.WorkoutOrder`)

Pure-Java class so it is unit-testable without Android. Thin I/O + list semantics around `app_workouts/.order`:

- `File orderFile(Context)` → the `.order` file next to `WorkoutSerializer.getFile`'s directory.
- `List<String> load(File)` → names in order; empty list when absent or unparseable (treat as "no custom order").
- `List<String> apply(List<String> filenames, File orderFile)` → the filenames re-sorted: those present in the order file keep the file's order, and every name absent from the order file appended afterwards in input (filesystem) order. Missing/dangling names in the order file are ignored.
- `void write(File, List<String>)` → atomically write (temp file + rename) the JSON array.
- `void replace(File, String oldName, String newName)` → rename a single entry in place.
- `void remove(File, String name)` → drop a single entry.
- JSON via `org.json` (`JSONArray`), matching `WorkoutSerializer`'s use of the bundled `org.json:json`.

### 4. Apply the order everywhere (`WorkoutListAdapter.load`)

`WorkoutListAdapter.load(Context)` (the shared funnel for both screens) sorts its `File.list(...)` result with `WorkoutOrder.apply(...)`. This makes the Start picker respect the same order with no change to `StartFragment`.

### 5. Keep in sync on rename/delete (`CreateAdvancedWorkout.java`)

- `renameWorkoutButtonClick`: after the file rename succeeds, call `WorkoutOrder.replace(orderFile, oldName, newName)`.
- `deleteWorkoutButtonClick`: after deleting the file, call `WorkoutOrder.remove(orderFile, name)`.

Both are best-effort (`File` ops); a missing `.order` file means nothing to update.

### 6. Testing

- New JVM unit test `WorkoutOrderTest` under `app/test/java/org/runnerup/workout/` covering: `apply` ordering with all/partial/unknown entries and dangling names, `write`+`load` round-trip, `replace`, `remove`, and absent/unparseable file → empty order.
- No UI instrumentation tests (matches repo convention).

## Verification

Gate, in order: `./gradlew test` → `:app:lintLatestDebug` (baseline `lint-baseline.xml` only; new issues fatal) → `spotlessApply`/`spotlessCheck` → `:app:assembleLatestDebug`.

Device smoke test on `org.runnerup.debug`: drag a local workout within "My phone", force-stop and relaunch Manage Workouts to confirm the order persisted, confirm the Start picker shows the same order, and confirm cloud rows (if any configured) and group headers are unaffected and non-draggable.