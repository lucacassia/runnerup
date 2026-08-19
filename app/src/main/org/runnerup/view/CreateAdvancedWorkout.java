package org.runnerup.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.util.ViewUtil;
import org.runnerup.widget.NumberPicker;
import org.runnerup.workout.RepeatStep;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

public class CreateAdvancedWorkout extends AppCompatActivity {

  private Workout advancedWorkout = null;
  private String currentWorkoutName = null;
  private final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
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
    menu.findItem(R.id.menu_rename_workout).setVisible(workoutEditMode);
    menu.findItem(R.id.menu_discard_workout).setVisible(!workoutEditMode);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.menu_save_workout) {
      saveWorkoutButtonClick.onClick(null);
      return true;
    } else if (itemId == R.id.menu_rename_workout) {
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
      throws JSONException, IOException {
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
  private static final int VIEW_TYPE_FOOTER = 2;

  private static final class FooterItem {
    final RepeatStep repeat;

    FooterItem(RepeatStep repeat) {
      this.repeat = repeat;
    }
  }

  final class WorkoutStepsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      if (viewType == VIEW_TYPE_REPEAT) {
        return new RepeatRowViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false));
      } else if (viewType == VIEW_TYPE_FOOTER) {
        return new FooterRowViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_footer, parent, false));
      }
      return new StepRowViewHolder(inflater.inflate(R.layout.advanced_workout_row, parent, false));
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
                org.runnerup.common.R.string.repeat_times, holder.repeatStep.getRepeatCount()));
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

  class StepRowViewHolder extends RecyclerView.ViewHolder {
    final StepButton button;
    final ImageButton moveUp;
    final ImageButton moveDown;
    final ImageButton del;
    Workout.StepListEntry stepEntry;

    StepRowViewHolder(@NonNull View itemView) {
      super(itemView);
      button = itemView.findViewById(R.id.workout_step_button);
      button.setOnChangedListener(onWorkoutChanged);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnClickListener(v -> moveStep(stepEntry, -1));
      moveDown = itemView.findViewById(R.id.move_down_button);
      moveDown.setOnClickListener(v -> moveStep(stepEntry, 1));
      del = itemView.findViewById(R.id.del_button);
      del.setOnClickListener(v -> confirmDeleteStep(stepEntry.step()));
    }
  }

  class RepeatRowViewHolder extends RecyclerView.ViewHolder {
    final ImageButton moveUp;
    final ImageButton moveDown;
    final TextView chip;
    final ImageButton del;
    RepeatStep repeatStep;

    RepeatRowViewHolder(@NonNull View itemView) {
      super(itemView);
      moveUp = itemView.findViewById(R.id.move_up_button);
      moveUp.setOnClickListener(v -> moveStep(entryFor(repeatStep), -1));
      moveDown = itemView.findViewById(R.id.move_down_button);
      moveDown.setOnClickListener(v -> moveStep(entryFor(repeatStep), 1));
      chip = itemView.findViewById(R.id.repeat_chip);
      chip.setOnClickListener(v -> editRepeatCount(repeatStep));
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
