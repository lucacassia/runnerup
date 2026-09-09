package org.runnerup.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.content.WorkoutFileProvider;
import org.runnerup.util.ViewUtil;
import org.runnerup.widget.NumberPicker;
import org.runnerup.workout.RepeatStep;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutOrder;
import org.runnerup.workout.WorkoutSerializer;

public class CreateAdvancedWorkout extends AppCompatActivity {

  private Workout advancedWorkout = null;
  private String currentWorkoutName = null;
  private final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
  private ItemTouchHelper itemTouchHelper;
  private boolean reorderDirty = false;
  private boolean dontAskAgain = false;
  private boolean workoutEditMode = false;
  private final Runnable onWorkoutChanged =
      () -> {
        String advWorkoutName = currentWorkoutName;
        if (advancedWorkout != null) {
          Context ctx = getApplicationContext();
          try {
            WorkoutSerializer.writeFile(ctx, advWorkoutName, advancedWorkout);
          } catch (Exception ex) {
            new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
                .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
                .setMessage("" + ex)
                .setPositiveButton(
                    org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
          }
        }
      };
  private final View.OnClickListener addWorkoutFabClick =
      v -> {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.workout_add_sheet);
        View sheetView = sheet.findViewById(android.R.id.content);
        sheetView
            .findViewById(R.id.add_step_sheet_row)
            .setOnClickListener(
                view -> {
                  advancedWorkout.addStep(new Step());
                  advancedWorkoutStepsAdapter.refreshSteps();
                  onWorkoutChanged.run();
                  sheet.dismiss();
                });
        sheetView
            .findViewById(R.id.add_repeat_sheet_row)
            .setOnClickListener(
                view -> {
                  advancedWorkout.addStep(new RepeatStep());
                  advancedWorkoutStepsAdapter.refreshSteps();
                  onWorkoutChanged.run();
                  sheet.dismiss();
                });
        sheet.show();
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    setContentView(R.layout.create_advanced_workout);

    Intent intent = getIntent();
    String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);
    workoutEditMode = intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EDIT_MODE, false);
    currentWorkoutName = advWorkoutName;

    dontAskAgain = false;

    MaterialToolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    toolbar.setTitle(advWorkoutName);

    RecyclerView advancedStepList = findViewById(R.id.new_advnced_workout_steps);
    advancedStepList.setLayoutManager(new LinearLayoutManager(this));
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

    itemTouchHelper =
        new ItemTouchHelper(
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
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

              @Override
              public void clearView(
                  @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
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
    itemTouchHelper.attachToRecyclerView(advancedStepList);

    FloatingActionButton addWorkoutFab = findViewById(R.id.add_workout_fab);
    addWorkoutFab.setOnClickListener(addWorkoutFabClick);

    try {
      createAdvancedWorkout(advWorkoutName, workoutEditMode);
    } catch (Exception e) {
      handleWorkoutFileException(e);
    }

    // Persist the currently displayed workout name when leaving via the back button so that
    // StartFragment's advanced tab points at (and shows) this workout when we return.
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                persistCurrentWorkoutName();
                finish();
              }
            });

    ViewUtil.Insets(findViewById(R.id.create_advanced_workout_view), true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.workout_editor_menu, menu);
    TypedValue colorOnSurface = new TypedValue();
    if (getTheme()
        .resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface, colorOnSurface, true)) {
      menu.findItem(R.id.menu_save_workout)
          .setIconTintList(ColorStateList.valueOf(colorOnSurface.data));
    }
    menu.findItem(R.id.menu_share_workout).setVisible(workoutEditMode);
    menu.findItem(R.id.menu_rename_workout).setVisible(workoutEditMode);
    menu.findItem(R.id.menu_delete_workout).setVisible(workoutEditMode);
    menu.findItem(R.id.menu_discard_workout).setVisible(!workoutEditMode);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.menu_save_workout) {
      saveWorkoutButtonClick.onClick(null);
      return true;
    } else if (itemId == R.id.menu_share_workout) {
      shareWorkout();
      return true;
    } else if (itemId == R.id.menu_rename_workout) {
      renameWorkoutButtonClick.onClick(null);
      return true;
    } else if (itemId == R.id.menu_delete_workout) {
      deleteWorkoutButtonClick.onClick(null);
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

  private void persistCurrentWorkoutName() {
    if (currentWorkoutName == null) {
      return;
    }
    try {
      SharedPreferences prefs =
          PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      prefs.edit().putString(getString(R.string.pref_advanced_workout), currentWorkoutName).apply();
    } catch (Exception ignored) {
      // If the spinner value can't be read, fall back to the default back behaviour.
    }
  }

  private void createAdvancedWorkout(String name, boolean workoutEditMode)
      throws JSONException, IOException, WorkoutSerializer.UnsupportedFormatException {
    if (workoutEditMode) {
      advancedWorkout = WorkoutSerializer.readFile(getApplicationContext(), name);
    } else {
      advancedWorkout = new Workout();
      WorkoutSerializer.writeFile(getApplicationContext(), name, advancedWorkout);
    }
    advancedWorkoutStepsAdapter.refreshSteps();
  }

  private static final int VIEW_TYPE_STEP = 0;
  private static final int VIEW_TYPE_REPEAT = 1;

  final class WorkoutStepsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    final List<Object> items = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    void refreshSteps() {
      items.clear();
      items.addAll(advancedWorkout.entriesAtLevel(null));
      updateEmptyState();
      notifyDataSetChanged();
    }

    private void updateEmptyState() {
      View empty = findViewById(R.id.empty_state_layout);
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
      Workout.StepListEntry entry = (Workout.StepListEntry) items.get(position);
      return entry.step() instanceof RepeatStep ? VIEW_TYPE_REPEAT : VIEW_TYPE_STEP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      if (viewType == VIEW_TYPE_REPEAT) {
        return new RepeatGroupViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false), itemTouchHelper);
      }
      return new StepRowViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false), itemTouchHelper);
    }

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
  }

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
              new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
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

  final class RepeatChildrenAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<Workout.StepListEntry> items = new ArrayList<>();
    final ItemTouchHelper innerItemTouchHelper;

    RepeatChildrenAdapter(ItemTouchHelper innerItemTouchHelper) {
      this.innerItemTouchHelper = innerItemTouchHelper;
    }

    @SuppressLint("NotifyDataSetChanged")
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

  private void addStepInsideRepeat(RepeatStep repeat) {
    repeat.getSteps().add(new Step());
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }

  private void editRepeatCount(RepeatStep repeat) {
    final NumberPicker numberPicker = new NumberPicker(this, null);
    numberPicker.setOrientation(LinearLayout.VERTICAL);
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
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
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

  private final View.OnClickListener saveWorkoutButtonClick =
      v -> {
        try {
          String advWorkoutName = currentWorkoutName;
          WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName, advancedWorkout);
          finish();
        } catch (Exception e) {
          handleWorkoutFileException(e);
        }
      };

  private void handleWorkoutFileException(Exception e) {
    new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
        .setTitle(getString(org.runnerup.common.R.string.Failed_to_create_workout))
        .setMessage(e.toString())
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private final View.OnClickListener discardWorkoutButtonClick =
      view ->
          new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
              .setTitle(org.runnerup.common.R.string.Delete_workout)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    dialog.dismiss();
                    String name = currentWorkoutName;
                    File f = WorkoutSerializer.getFile(getApplicationContext(), name);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
              .show();

  private void shareWorkout() {
    if (currentWorkoutName == null) {
      return;
    }
    try {
      WorkoutSerializer.writeFile(getApplicationContext(), currentWorkoutName, advancedWorkout);
    } catch (Exception e) {
      handleWorkoutFileException(e);
      return;
    }
    final String name = currentWorkoutName;
    final Intent intent = new Intent(Intent.ACTION_SEND);

    intent.putExtra(
        Intent.EXTRA_SUBJECT,
        getString(org.runnerup.common.R.string.RunnerUp_workout) + ": " + name);
    intent.putExtra(
        Intent.EXTRA_TEXT,
        getString(org.runnerup.common.R.string.HinHere_is_a_workout_I_think_you_might_like));

    intent.setType(WorkoutFileProvider.MIME);
    Uri uri = Uri.parse("content://" + WorkoutFileProvider.AUTHORITY + "/" + name + ".json");
    intent.putExtra(Intent.EXTRA_STREAM, uri);
    startActivity(
        Intent.createChooser(intent, getString(org.runnerup.common.R.string.Share_workout)));
  }

  private final View.OnClickListener deleteWorkoutButtonClick =
      v -> {
        if (currentWorkoutName == null) {
          return;
        }
        new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
            .setTitle(
                getString(org.runnerup.common.R.string.Delete_workout) + " " + currentWorkoutName)
            .setMessage(org.runnerup.common.R.string.Are_you_sure)
            .setPositiveButton(
                org.runnerup.common.R.string.Yes,
                (dialog, which) -> {
                  dialog.dismiss();
                  String name = currentWorkoutName;
                  File f = WorkoutSerializer.getFile(getApplicationContext(), name);
                  //noinspection ResultOfMethodCallIgnored
                  f.delete();
                  try {
                    WorkoutOrder.remove(WorkoutOrder.orderFile(getApplicationContext()), name);
                  } catch (IOException ignored) {
                  }
                  SharedPreferences prefs =
                      PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                  String key = getString(R.string.pref_advanced_workout);
                  if (name.contentEquals(prefs.getString(key, ""))) {
                    prefs.edit().putString(key, "").apply();
                  }
                  finish();
                })
            .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
            .show();
      };

  private final View.OnClickListener renameWorkoutButtonClick =
      view -> {
        final EditText newWorkoutNameEditText = new EditText(CreateAdvancedWorkout.this);
        newWorkoutNameEditText.setHint(org.runnerup.common.R.string.Enter_new_workout_name);
        newWorkoutNameEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        newWorkoutNameEditText.setSingleLine(true);
        newWorkoutNameEditText.setMaxLines(1);
        newWorkoutNameEditText.setText(currentWorkoutName);

        new MaterialAlertDialogBuilder(CreateAdvancedWorkout.this)
            .setView(newWorkoutNameEditText)
            .setPositiveButton(
                org.runnerup.common.R.string.OK,
                (dialog, which) -> {
                  String newWorkoutName = newWorkoutNameEditText.getText().toString().trim();
                  String oldWorkoutName = currentWorkoutName.trim();
                  if (newWorkoutName.isEmpty()
                      || newWorkoutName.contains("/")
                      || newWorkoutName.contains("\\")
                      || newWorkoutName.contains("..")) {
                    Toast.makeText(
                            CreateAdvancedWorkout.this,
                            org.runnerup.common.R.string.Invalid_workout_name,
                            Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                  if (newWorkoutName.equals(oldWorkoutName)) {
                    Toast.makeText(
                            CreateAdvancedWorkout.this,
                            org.runnerup.common.R.string
                                .New_workout_name_is_the_same_as_the_old_one,
                            Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                  File f = WorkoutSerializer.getFile(getApplicationContext(), newWorkoutName);
                  if (f.exists()) {
                    Toast.makeText(
                            CreateAdvancedWorkout.this,
                            org.runnerup.common.R.string.Workout_name_already_in_use,
                            Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                  try {
                    WorkoutSerializer.writeFile(
                        getApplicationContext(), newWorkoutName, advancedWorkout);
                    File oldFile =
                        WorkoutSerializer.getFile(getApplicationContext(), oldWorkoutName);
                    if (!oldFile.delete())
                      throw new IOException("Failed to delete old workout file");
                    try {
                      WorkoutOrder.replace(
                          WorkoutOrder.orderFile(getApplicationContext()),
                          oldWorkoutName,
                          newWorkoutName);
                    } catch (IOException ignored) {
                    }
                    SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    String key = getString(R.string.pref_advanced_workout);
                    if (oldWorkoutName.contentEquals(prefs.getString(key, ""))) {
                      prefs.edit().putString(key, newWorkoutName).apply();
                    }
                    currentWorkoutName = newWorkoutName;
                    dialog.dismiss();
                    finish();
                  } catch (Exception e) {
                    handleWorkoutFileException(e);
                  }
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
            .show();
      };
}
