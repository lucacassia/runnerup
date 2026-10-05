package org.runnerup.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.content.WorkoutFileProvider;
import org.runnerup.util.Formatter;
import org.runnerup.util.ViewUtil;
import org.runnerup.widget.NumberPicker;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.RepeatStep;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutOrder;
import org.runnerup.workout.WorkoutSerializer;

public class CreateAdvancedWorkout extends AppCompatActivity {

  private Workout advancedWorkout = null;
  private String currentWorkoutName = null;
  private final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
  private Formatter formatter = null;
  private final WorkoutEditorPageState pageState = new WorkoutEditorPageState();
  private final Map<String, Step> stepsByKey = new HashMap<>();
  private final Map<Step, String> keysByStep = new IdentityHashMap<>();
  private View overflowAnchor = null;
  private PopupMenu overflowMenu = null;
  private boolean reorderDirty = false;
  private boolean dontAskAgain = false;
  private boolean workoutEditMode = false;
  private byte[] originalWorkoutSnapshot;
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

    formatter = new Formatter(this);

    pageState.setTitle(advWorkoutName);
    WorkoutEditorScreenHost.INSTANCE.attach(
        (ComposeView) findViewById(R.id.create_advanced_workout_root),
        pageState,
        new WorkoutEditorBindings(
            this::showBackPressDialog,
            () -> saveWorkoutButtonClick.onClick(null),
            this::showOverflowMenu,
            this::openStepEditor,
            this::confirmDeleteEntry,
            this::editRepeatCount,
            this::addStepInsideRepeat,
            this::onDragStart,
            this::onDrag,
            this::onDragEnd,
            () -> addWorkoutFabClick.onClick(null)));

    try {
      createAdvancedWorkout(advWorkoutName, workoutEditMode);
      snapshotOriginalWorkout(advWorkoutName);
    } catch (Exception e) {
      handleWorkoutFileException(e);
    }

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                showBackPressDialog();
              }
            });

    ViewUtil.Insets(findViewById(R.id.create_advanced_workout_view), true);
  }

  private void showOverflowMenu(int left, int top, int right, int bottom) {
    ViewGroup decor = (ViewGroup) getWindow().getDecorView();
    if (overflowAnchor == null) {
      overflowAnchor = new View(this);
      overflowAnchor.setVisibility(View.INVISIBLE);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
      params.gravity = Gravity.TOP | Gravity.START;
      params.leftMargin = right - 8;
      params.topMargin = top;
      decor.addView(overflowAnchor, params);

      overflowMenu = new PopupMenu(this, overflowAnchor, Gravity.END);
      overflowMenu.setOnMenuItemClickListener(
          item -> {
            onOptionsItemSelected(item);
            return true;
          });
      overflowMenu.getMenuInflater().inflate(R.menu.workout_editor_menu, overflowMenu.getMenu());
      // `menu_save_workout` is `showAsAction="always"`, so the platform keeps it in the
      // toolbar rather than the overflow popup.
      overflowMenu.getMenu().removeItem(R.id.menu_save_workout);
    }
    overflowMenu.getMenu().findItem(R.id.menu_share_workout).setVisible(workoutEditMode);
    overflowMenu.getMenu().findItem(R.id.menu_rename_workout).setVisible(workoutEditMode);
    overflowMenu.getMenu().findItem(R.id.menu_delete_workout).setVisible(workoutEditMode);
    overflowMenu.getMenu().findItem(R.id.menu_discard_workout).setVisible(!workoutEditMode);
    overflowMenu.show();
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
      showBackPressDialog();
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

  private void snapshotOriginalWorkout(String name) {
    if (name == null) {
      return;
    }
    try {
      File f = WorkoutSerializer.getFile(getApplicationContext(), name);
      if (f.exists()) {
        FileInputStream fis = new FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = fis.read(buf)) != -1) {
          bos.write(buf, 0, len);
        }
        fis.close();
        originalWorkoutSnapshot = bos.toByteArray();
      }
    } catch (Exception ignored) {
    }
  }

  private void showBackPressDialog() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(
            org.runnerup.common.R.string.Save,
            (dialog, which) -> {
              persistCurrentWorkoutName();
              finish();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Discard,
            (dialog, which) -> {
              revertToSnapshot();
              finish();
            })
        .setNeutralButton(org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void revertToSnapshot() {
    if (currentWorkoutName == null || originalWorkoutSnapshot == null) {
      return;
    }
    try {
      File f = WorkoutSerializer.getFile(getApplicationContext(), currentWorkoutName);
      java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
      fos.write(originalWorkoutSnapshot);
      fos.close();
    } catch (Exception ignored) {
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

  final class WorkoutStepsAdapter {

    void refreshSteps() {
      stepsByKey.clear();
      keysByStep.clear();
      pageState.setItems(buildEntries(null));
    }

    /** Builds the compose rows of one nesting level, keyed by the identity of each step. */
    private java.util.List<WorkoutEntryItem> buildEntries(Step parent) {
      java.util.List<WorkoutEntryItem> out = new java.util.ArrayList<>();
      java.util.List<Workout.StepListEntry> entries = advancedWorkout.entriesAtLevel(parent);
      for (Workout.StepListEntry entry : entries) {
        Step step = entry.step();
        // The key has to survive a reorder, so it is derived from the step object itself.
        String key = "s" + System.identityHashCode(step);
        stepsByKey.put(key, step);
        keysByStep.put(step, key);
        if (step instanceof RepeatStep) {
          RepeatStep repeat = (RepeatStep) step;
          out.add(
              new WorkoutEntryItem.Repeat(
                  key,
                  getString(org.runnerup.common.R.string.repeat_x, repeat.getRepeatCount()),
                  buildEntries(repeat)));
        } else {
          out.add(buildStep(key, step));
        }
      }
      return out;
    }

    private WorkoutEntryItem.Step buildStep(String key, Step step) {
      String duration;
      if (step.getDurationType() == null) {
        duration = getString(org.runnerup.common.R.string.Until_press);
      } else {
        duration =
            formatter.format(
                Formatter.Format.TXT_LONG, step.getDurationType(), step.getDurationValue());
      }
      String goal = null;
      Dimension goalType = step.getTargetType();
      if (goalType != null) {
        String prefix = (goalType == Dimension.HR || goalType == Dimension.HRZ) ? "HR " : "";
        goal =
            String.format(
                Locale.getDefault(),
                "%s%s-%s",
                prefix,
                formatter.format(
                    Formatter.Format.TXT_SHORT, goalType, step.getTargetValue().minValue),
                formatter.format(
                    Formatter.Format.TXT_LONG, goalType, step.getTargetValue().maxValue));
      }
      Intensity intensity = step.getIntensity();
      return new WorkoutEntryItem.Step(
          key,
          duration,
          goal,
          // The platform badge is `textAllCaps`, so it uppercases the intensity name.
          getString(intensity.getTextId()).toUpperCase(Locale.getDefault()),
          ContextCompat.getColor(CreateAdvancedWorkout.this, badgeTextColor(intensity)),
          ContextCompat.getColor(CreateAdvancedWorkout.this, badgeBgColor(intensity)));
    }

    private int badgeTextColor(Intensity intensity) {
      switch (intensity) {
        case ACTIVE:
          return R.color.stepActive;
        case REPEAT:
          return R.color.stepRepeat;
        case WARMUP:
          return R.color.stepWarmup;
        case COOLDOWN:
          return R.color.stepCooldown;
        case RECOVERY:
          return R.color.stepRecovery;
        default:
          return R.color.stepResting;
      }
    }

    private int badgeBgColor(Intensity intensity) {
      switch (intensity) {
        case ACTIVE:
          return R.color.stepActiveBg;
        case WARMUP:
          return R.color.stepWarmupBg;
        case COOLDOWN:
          return R.color.stepCooldownBg;
        case RECOVERY:
          return R.color.stepRecoveryBg;
        default:
          return R.color.stepRestingBg;
      }
    }
  }

  private void openStepEditor(String key) {
    Step step = stepsByKey.get(key);
    if (step != null) {
      StepEditor.showStepDialog(this, step, onWorkoutChanged);
    }
  }

  private void confirmDeleteEntry(String key) {
    Step step = stepsByKey.get(key);
    if (step != null) {
      confirmDeleteStep(step);
    }
  }

  private void onDragStart(String key) {
    pageState.setDraggedKey(key);
    pageState.setDragOffset(0f);
  }

  private void onDrag(String key, float dy) {
    Step step = stepsByKey.get(key);
    if (step == null) {
      return;
    }
    List<Step> siblings = siblingList(step);
    if (siblings == null) {
      return;
    }
    int index = siblings.indexOf(step);
    float offset = pageState.getDragOffset() + dy;
    // Swap once the dragged row has travelled past the middle of its neighbour.
    while (index + 1 < siblings.size()) {
      int threshold = dragThreshold(key, keysByStep.get(siblings.get(index + 1)));
      if (offset <= threshold) {
        break;
      }
      StepReorder.swapIndex(siblings, index, index + 1);
      index++;
      offset -= threshold;
      reorderDirty = true;
      advancedWorkoutStepsAdapter.refreshSteps();
    }
    while (index - 1 >= 0) {
      int threshold = dragThreshold(key, keysByStep.get(siblings.get(index - 1)));
      if (offset >= -threshold) {
        break;
      }
      StepReorder.swapIndex(siblings, index, index - 1);
      index--;
      offset += threshold;
      reorderDirty = true;
      advancedWorkoutStepsAdapter.refreshSteps();
    }
    pageState.setDragOffset(offset);
  }

  private int dragThreshold(String draggedKey, String neighbourKey) {
    Integer draggedHeight = pageState.getItemHeights().get(draggedKey);
    Integer neighbourHeight =
        neighbourKey == null ? null : pageState.getItemHeights().get(neighbourKey);
    if (draggedHeight == null || neighbourHeight == null) {
      return Math.round(getResources().getDisplayMetrics().density * 72f);
    }
    return (draggedHeight + neighbourHeight) / 2;
  }

  private void onDragEnd() {
    pageState.setDraggedKey(null);
    pageState.setDragOffset(0f);
    if (reorderDirty) {
      reorderDirty = false;
      onWorkoutChanged.run();
    }
  }

  /** The step list the given step lives in, which is where a reorder applies. */
  private List<Step> siblingList(Step step) {
    for (Step candidate : advancedWorkout.getSteps()) {
      if (candidate == step) {
        return advancedWorkout.getSteps();
      }
      if (candidate instanceof RepeatStep) {
        if (((RepeatStep) candidate).getSteps().contains(step)) {
          return ((RepeatStep) candidate).getSteps();
        }
      }
    }
    return null;
  }

  private void addStepInsideRepeat(String key) {
    Step step = stepsByKey.get(key);
    if (!(step instanceof RepeatStep)) {
      return;
    }
    RepeatStep repeat = (RepeatStep) step;
    repeat.getSteps().add(new Step());
    advancedWorkoutStepsAdapter.refreshSteps();
    onWorkoutChanged.run();
  }

  private void editRepeatCount(String key) {
    Step step = stepsByKey.get(key);
    if (!(step instanceof RepeatStep)) {
      return;
    }
    RepeatStep repeat = (RepeatStep) step;
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
