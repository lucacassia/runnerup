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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.util.ViewUtil;
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
  private final View.OnClickListener addWorkoutFabClick = v -> {};

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

  final class WorkoutStepsAdapter
      extends RecyclerView.Adapter<WorkoutStepsAdapter.WorkoutRowViewHolder> {

    List<Workout.StepListEntry> steps = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    void refreshSteps() {
      steps = advancedWorkout.getStepList();
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return steps.size();
    }

    @NonNull
    @Override
    public WorkoutRowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = getLayoutInflater();
      return new WorkoutRowViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WorkoutRowViewHolder viewHolder, int position) {
      Workout.StepListEntry entry = steps.get(position);
      viewHolder.button.setStep(entry.step());
      float pxToDp = getResources().getDisplayMetrics().density;
      viewHolder.button.setPadding((int) (entry.level() * 8 * pxToDp + 0.5f), 0, 0, 0);
    }

    class WorkoutRowViewHolder extends RecyclerView.ViewHolder {
      final StepButton button;
      final Button add;
      final Button del;

      WorkoutRowViewHolder(@NonNull View itemView) {
        super(itemView);
        button = itemView.findViewById(R.id.workout_step_button);
        button.setOnChangedListener(onWorkoutChanged);
        add = itemView.findViewById(R.id.add_button);
        add.setOnClickListener(v -> addStep(button));
        del = itemView.findViewById(R.id.del_button);
        del.setOnClickListener(v -> confirmDeleteStep(button));
      }
    }
  }

  private void addStep(StepButton stepButton) {
    Step currentStep = stepButton.getStep();
    if (currentStep instanceof RepeatStep rs) {
      rs.getSteps().add(new Step());
    } else {

      int index = advancedWorkout.getSteps().indexOf(currentStep);
      if (index < 0) {
        for (Step se : advancedWorkout.getSteps()) {
          if (se instanceof RepeatStep) {
            index = ((RepeatStep) se).getSteps().indexOf(currentStep);
            ((RepeatStep) se).getSteps().add(index + 1, new Step());
          }
        }
      } else {
        advancedWorkout.getSteps().add(index + 1, new Step());
      }
    }
    advancedWorkoutStepsAdapter.refreshSteps();
  }

  private void confirmDeleteStep(StepButton stepButton) {
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
                deleteStep(stepButton);
              })
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else {
      deleteStep(stepButton);
    }
  }

  private void deleteStep(StepButton stepButton) {
    Step s = stepButton.getStep();
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
  }

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
