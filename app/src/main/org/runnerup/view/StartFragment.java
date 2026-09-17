/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.ValueModel;
import org.runnerup.db.DBHelper;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.util.TickListener;
import org.runnerup.widget.NumberPicker;
import org.runnerup.workout.RaceReady;
import org.runnerup.workout.RepeatStep;
import org.runnerup.workout.Sport;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

public class StartFragment extends Fragment implements TickListener {

  private enum GpsLevel {
    NOT_FIXED,
    POOR,
    ACCEPTABLE,
    GOOD
  }

  private enum Page {
    RECORD,
    SETUP,
    PICKER
  }

  private enum PickerKind {
    SPORT,
    AUDIO,
    WORKOUT
  }

  static final String TAB_ADVANCED = "advanced";

  // StartFragment normally stop GPS in onDestroy (or onStop)
  // but if the fragment stop as it has started a RunActivity
  // it should not!
  // TODO Figure out a way to do this prettier
  private boolean runActivityPending = false;
  private Tracker mTracker = null;
  private org.runnerup.tracker.GpsStatus mGpsStatus = null;

  private final ArrayDeque<Page> pageStack = new ArrayDeque<>();
  private View recordRoot = null;
  private View setupRoot = null;
  private View pickerRoot = null;
  private View startButton = null;

  private PickerKind pickerKind = PickerKind.SPORT;
  private String[] pickerItems = new String[0];
  private int pickerSelected = 0;

  private MaterialButton startRunButton = null;
  private TextView setupSportValue = null;
  private TextView setupAudioValue = null;
  private TextView setupWorkoutValue = null;
  private TextView setupStepsHint = null;
  private View setupGpsChip = null;
  private ImageView setupGpsIndicator = null;
  private TextView setupGpsMessage = null;
  private View setupGpsPopup = null;
  private ImageView setupGpsPopupIndicator = null;
  private TextView setupGpsPopupMessage = null;
  private TextView setupGpsPopupSatellites = null;

  private String selectedWorkoutName = "";

  private TextView noDevicesConnected = null;

  private View hrIndicator = null;
  private TextView hrMessage = null;

  private View wearOsIndicator = null;
  private TextView wearOsMessage = null;
  private TrackerWear.WearNotifier mWearNotifier = null;

  boolean sportWithoutGps = false;
  boolean batteryLevelMessageShown = false;

  private SharedPreferences appPrefs = null;

  Workout advancedWorkout = null;
  RecyclerView advancedStepList = null;
  WorkoutPlanAdapter advancedWorkoutStepsAdapter = null;
  AudioSchemeListAdapter advancedAudioListAdapter = null;

  SQLiteDatabase mDB = null;

  Formatter formatter = null;
  private final ActivityResultLauncher<String[]> permissionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(),
          result -> {
            if (isAdded()
                && getView() != null
                && result.values().stream().anyMatch(Boolean::booleanValue)) {
              // Permission granted: retry the sport-driven GPS auto start
              updateView();
              autoStartGpsForSport();
            }
          });

  private final SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
      (sharedPrefs, key) -> {
        assert key != null;
        if (key.equals(getString(R.string.pref_advanced_workout))) {
          String newName = sharedPrefs.getString(key, "");
          loadAdvanced(newName);
          updateView();
        }
      };

  public StartFragment() {
    super(R.layout.start);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    Context context = requireContext();
    mDB = DBHelper.getWritableDatabase(context);
    formatter = new Formatter(context);
    appPrefs = PreferenceManager.getDefaultSharedPreferences(context);

    bindGpsTracker();
    mGpsStatus = new org.runnerup.tracker.GpsStatus(context);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    recordRoot = view.findViewById(R.id.record_root);
    setupRoot = view.findViewById(R.id.setup_root);
    pickerRoot = view.findViewById(R.id.picker_root);

    int initialSport =
        prefs.getInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    sportWithoutGps = Sport.isWithoutGps(initialSport);

    startButton = view.findViewById(R.id.start_button);
    startButton.setOnClickListener(startButtonClick);

    noDevicesConnected = view.findViewById(R.id.device_status);

    hrMessage = view.findViewById(R.id.hr_message);
    hrIndicator = view.findViewById(R.id.hr_indicator);

    wearOsIndicator = view.findViewById(R.id.wearos_indicator);
    wearOsMessage = view.findViewById(R.id.wearos_message);

    LayoutInflater inflater = getLayoutInflater();
    advancedAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    advancedAudioListAdapter.reload();

    advancedStepList = view.findViewById(R.id.advanced_step_list);
    advancedStepList.setLayoutManager(new LinearLayoutManager(context));
    advancedWorkoutStepsAdapter = new WorkoutPlanAdapter(this::editRepeatCount, onWorkoutChanged);
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

    setupSportValue = view.findViewById(R.id.setup_sport_value);
    setupAudioValue = view.findViewById(R.id.setup_audio_value);
    setupWorkoutValue = view.findViewById(R.id.setup_workout_value);
    setupStepsHint = view.findViewById(R.id.setup_steps_hint);
    setupGpsChip = view.findViewById(R.id.setup_gps_chip);
    setupGpsIndicator = view.findViewById(R.id.setup_gps_indicator);
    setupGpsMessage = view.findViewById(R.id.setup_gps_message);
    setupGpsPopup = view.findViewById(R.id.setup_gps_popup);
    setupGpsPopupIndicator = view.findViewById(R.id.setup_gps_popup_indicator);
    setupGpsPopupMessage = view.findViewById(R.id.setup_gps_popup_message);
    setupGpsPopupSatellites = view.findViewById(R.id.setup_gps_popup_satellites);
    startRunButton = view.findViewById(R.id.start_run_button);
    startRunButton.setOnClickListener(startRunClick);

    setupGpsChip.setOnClickListener(v -> toggleSetupGpsPopup());

    view.findViewById(R.id.setup_sport_row).setOnClickListener(v -> openPicker(PickerKind.SPORT));
    view.findViewById(R.id.setup_audio_row).setOnClickListener(v -> openPicker(PickerKind.AUDIO));
    view.findViewById(R.id.setup_workout_row)
        .setOnClickListener(v -> openPicker(PickerKind.WORKOUT));

    setupSportValue.setOnClickListener(v -> openPicker(PickerKind.SPORT));
    updateSetupSportIcon(initialSport);

    ((MaterialToolbar) view.findViewById(R.id.setup_toolbar))
        .setNavigationOnClickListener(
            v -> {
              if (!pageStack.isEmpty()) {
                popPage();
              }
            });
    ((MaterialToolbar) view.findViewById(R.id.picker_toolbar))
        .setNavigationOnClickListener(
            v -> {
              if (!pageStack.isEmpty()) {
                popPage();
              }
            });

    mWearNotifier = new TrackerWear.WearNotifier(requireActivity().getApplicationContext());
    mWearNotifier.onViewCreated();

    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (!pageStack.isEmpty()) {
                  popPage();
                } else {
                  setEnabled(false);
                }
              }
            });
  }

  private void setGpsNotRequired(boolean val) {
    if (sportWithoutGps == val) {
      return;
    }

    sportWithoutGps = val;
    autoStartGpsForSport();
  }

  private void autoStartGpsForSport() {
    if (mTracker == null) {
      return;
    }
    if (sportWithoutGps) {
      // Turn GPS off for non-GPS sports but keep the tracker connected for runs
      mTracker.setWithoutGps(true);
      if (mGpsStatus != null && mGpsStatus.isStarted()) {
        mGpsStatus.stop(this);
      }
      if (mTracker.getState() != TrackerState.CONNECTED) {
        mTracker.connect();
      }
      updateView();
      return;
    }
    // GPS-requiring sport: make sure the satellite listener and tracker are running
    mTracker.setWithoutGps(false);
    if (mGpsStatus == null || !mGpsStatus.isStarted()) {
      if (checkPermissions(true)) {
        updateView();
        return;
      }
      startGps();
    } else if (mTracker.getState() != TrackerState.CONNECTED) {
      mTracker.connect();
    }
    updateView();
  }

  private void updateSetupSportIcon(int sport) {
    if (setupSportValue == null) return;
    ImageView icon = setupSportValue.getRootView().findViewById(R.id.setup_sport_icon);
    if (icon == null) return;
    Drawable d = AppCompatResources.getDrawable(requireContext(), Sport.drawableColored16Of(sport));
    if (d != null) d.setTint(ContextCompat.getColor(requireContext(), Sport.colorOf(sport)));
    icon.setImageDrawable(d);
  }

  @Override
  public void onStart() {
    super.onStart();
    registerStartEventListener();
  }

  @Override
  public void onResume() {
    super.onResume();
    advancedAudioListAdapter.reload();

    loadAdvanced(null);
    if (!mIsBound || mTracker == null) {
      bindGpsTracker();
    } else {
      onGpsTrackerBound();
    }
    this.updateView();
    if (!pageStack.isEmpty() && pageStack.peek() == Page.PICKER) {
      refreshPicker();
    }
    PreferenceManager.getDefaultSharedPreferences(requireContext())
        .registerOnSharedPreferenceChangeListener(prefChangeListener);
    mWearNotifier.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(requireContext())
        .unregisterOnSharedPreferenceChangeListener(prefChangeListener);

    if (getAutoStartGps()) {
      // If autoStartGps, then stop it during pause
      stopGps();
    } else if (!runActivityPending
        && mTracker != null
        && (mTracker.getState() == TrackerState.INITIALIZED
            || mTracker.getState() == TrackerState.INITIALIZING)) {
      // While handing off to RunActivity (deferred start), reset() would clear the
      // workout the run was built from, stranding the run screen without a workout.
      Log.i(getClass().getName(), "mTracker.reset()");
      mTracker.reset();
    }
    mWearNotifier.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    unregisterStartEventListener();
  }

  @Override
  public void onDestroy() {
    stopGps();
    unbindGpsTracker();
    mGpsStatus = null;

    DBHelper.closeDB(mDB);
    super.onDestroy();
    mWearNotifier.onDestroy();
  }

  private final BroadcastReceiver startEventBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          requireActivity()
              .runOnUiThread(
                  () -> {
                    if (mTracker == null || !pageStack.isEmpty()) {
                      return;
                    }
                    handleExternalStartRequest();
                  });
        }
      };

  private void handleExternalStartRequest() {
    boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
    TrackerState st = mTracker.getState();
    if (raceReady && !sportWithoutGps) {
      if (mGpsStatus == null || !mGpsStatus.isStarted()) {
        if (checkPermissions(true)) {
          updateView();
          return;
        }
        startGps();
      }
      startWorkout();
      return;
    }
    if (st == TrackerState.CONNECTED) {
      startWorkout();
      return;
    }
    updateView();
  }

  private void registerStartEventListener() {
    IntentFilter intentFilter = new IntentFilter();
    // START_WORKOUT is used by Wear when GPS is captured
    // START_ACTIVITY should also start GPS if not done
    intentFilter.addAction(Constants.Intents.START_ACTIVITY);
    intentFilter.addAction(Constants.Intents.START_WORKOUT);
    ContextCompat.registerReceiver(
        requireActivity(),
        startEventBroadcastReceiver,
        intentFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  private void unregisterStartEventListener() {
    try {
      requireActivity().unregisterReceiver(startEventBroadcastReceiver);
    } catch (Exception ignored) {
    }
  }

  private void onGpsTrackerBound() {
    // check and request permissions at startup
    boolean missingEssentialPermission = checkPermissions(false);
    mTracker.setWithoutGps(sportWithoutGps);
    if (!missingEssentialPermission && getAutoStartGps()) {
      startGps();
    } else {
      Log.d(getClass().getName(), "onGpsTrackerBound state: " + mTracker.getState());
      switch (mTracker.getState()) {
        case INIT:
        case CLEANUP:
          mTracker.setup();
          break;
        case INITIALIZING:
        case INITIALIZED:
          break;
        case CONNECTING:
        case CONNECTED:
        case STARTED:
        case PAUSED:
          break;
        case ERROR:
          break;
      }
    }
    updateView();
  }

  public boolean getAutoStartGps() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    return pref.getBoolean(getString(R.string.pref_startgps), false);
  }

  private void startGps() {
    Log.d(getClass().getName(), "StartFragment.startGps()");
    if (!sportWithoutGps) {
      if (!mGpsStatus.isEnabled()) {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
      }
    }

    if (mGpsStatus != null && !mGpsStatus.isStarted()) {
      mGpsStatus.start(this);
    }

    if (mTracker != null) {
      mTracker.setWithoutGps(sportWithoutGps);
      mTracker.connect();
    }
  }

  public void stopGps() {
    Log.d(getClass().getName(), "StartFragment.stopGps() skipStop: " + this.runActivityPending);
    if (runActivityPending) {
      return;
    }

    if (mGpsStatus != null) {
      mGpsStatus.stop(this);
    }

    if (mTracker != null) {
      mTracker.reset();
    }
  }

  public boolean isGpsLogging() {
    if (mGpsStatus == null) {
      // If mGpsStatus is null, GPS logging is not possible.
      return false;
    }
    return mGpsStatus.isLogging();
  }

  private void notificationBatteryLevel(int batteryLevel) {
    if ((batteryLevel < 0) || (batteryLevel > 100)) {
      return;
    }

    Context context = requireContext();
    final String pref_key = getString(R.string.pref_battery_level_low_notification_discard);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    int batteryLevelHighThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_high_threshold), "75"), 75);
    if ((batteryLevel > batteryLevelHighThreshold) && (prefs.contains(pref_key))) {
      prefs.edit().remove(pref_key).apply();
      return;
    }

    int batteryLevelLowThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_low_threshold), "15"), 15);
    if (batteryLevel > batteryLevelLowThreshold) {
      return;
    }

    if (prefs.getBoolean(pref_key, false)) {
      return;
    }

    final CheckBox dontShowAgain = new CheckBox(context);
    dontShowAgain.setText(getResources().getText(org.runnerup.common.R.string.Do_not_show_again));

    new MaterialAlertDialogBuilder(context)
        .setView(dontShowAgain)
        .setCancelable(false)
        .setTitle(org.runnerup.common.R.string.Warning)
        .setMessage(
            getResources().getText(org.runnerup.common.R.string.Low_HRM_battery_level)
                + "\n"
                + getResources().getText(org.runnerup.common.R.string.Battery_level)
                + ": "
                + batteryLevel
                + "%")
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              if (dontShowAgain.isChecked()) {
                prefs.edit().putBoolean(pref_key, true).apply();
              }
            })
        .show();
  }

  private Workout prepareWorkout() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    SharedPreferences audioPref =
        WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_advanced_audio));
    Workout w = advancedWorkout;
    if (w == null) {
      w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, null);
    }
    WorkoutBuilder.prepareWorkout(getResources(), pref, w);
    WorkoutBuilder.addAudioCuesToWorkout(getResources(), w, audioPref, pref);
    return w;
  }

  private void startWorkout() {
    mGpsStatus.stop(StartFragment.this);

    // unregister receivers
    unregisterStartEventListener();

    Workout w = prepareWorkout();
    // If GPS was already locked by the time the run starts (auto-started from the Setup
    // page), the race-ready gate has nothing to wait for: drop it so the run starts
    // immediately, without a pause/"Waiting for GPS" flash or a spurious lock cue.
    if (RaceReady.enabled(getResources(), appPrefs) && !sportWithoutGps && mTracker.isGpsFixed()) {
      WorkoutBuilder.removeGpsWaitGates(w);
    }
    // This will set the workout on the tracker
    mTracker.setWorkout(w);

    boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
    boolean startNow =
        RaceReady.connected(
            sportWithoutGps, raceReady, mTracker.getState() == TrackerState.CONNECTED);

    Intent intent = new Intent(requireContext(), RunActivity.class);
    if (!startNow) {
      intent.putExtra(RunActivity.EXTRA_DEFERRED_START, true);
    } else {
      mTracker.start();
    }

    runActivityPending = true;
    runLauncher.launch(intent);
  }

  private final OnClickListener startButtonClick =
      v -> {
        if (mTracker == null) return;
        pushPage(Page.SETUP);
        autoStartGpsForSport();
      };

  private final OnClickListener startRunClick =
      v -> {
        if (mTracker == null) return;
        boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
        if (raceReady && !sportWithoutGps) {
          if (mGpsStatus == null || !mGpsStatus.isStarted()) {
            if (checkPermissions(true)) {
              updateView();
              return;
            }
            startGps();
          }
          startWorkout();
          return;
        }
        if (mTracker.getState() == TrackerState.CONNECTED) {
          startWorkout();
          return;
        }
        updateView();
      };

  private int currentSportId() {
    return appPrefs.getInt(
        getString(R.string.pref_sport),
        org.runnerup.common.util.Constants.DB.ACTIVITY.SPORT_RUNNING);
  }

  private void pushPage(Page page) {
    pageStack.push(page);
    showPage(page);
  }

  private void popPage() {
    if (pageStack.isEmpty()) return;
    pageStack.pop();
    showPage(pageStack.isEmpty() ? Page.RECORD : pageStack.peek());
  }

  private void goRecord() {
    pageStack.clear();
    showPage(Page.RECORD);
  }

  private void showPage(Page page) {
    boolean record = page == Page.RECORD;
    animateRoot(recordRoot, record);
    animateRoot(setupRoot, page == Page.SETUP);
    animateRoot(pickerRoot, page == Page.PICKER);
    requireView().findViewById(R.id.status_layout).setVisibility(record ? View.VISIBLE : View.GONE);
    if (getActivity() instanceof MainLayout mainLayout) {
      mainLayout.setStartFlowActive(!record);
    }
    updateView();
  }

  private void animateRoot(View root, boolean show) {
    if (root == null) return;
    if (show) {
      root.setVisibility(View.VISIBLE);
      root.setAlpha(0f);
      root.animate().alpha(1f).setDuration(180).start();
    } else {
      root.animate().cancel();
      root.setVisibility(View.GONE);
    }
  }

  private List<String> getPermissions() {
    List<String> requiredPerms = new ArrayList<>();
    requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION);
    requiredPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

    Context ctx = requireContext();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      boolean enabled =
          prefs.getBoolean(
              this.getString(org.runnerup.R.string.pref_use_cadence_step_sensor), true);
      if (enabled && TrackerCadence.isAvailable(ctx)) {
        requiredPerms.add(Manifest.permission.ACTIVITY_RECOGNITION);
      }
    }

    PackageManager packageManager = requireContext().getPackageManager();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S /* Android12, sdk31*/
        && (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            || packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      final String btDeviceName = prefs.getString(getString(R.string.pref_bt_name), null);
      if (btDeviceName != null && !btDeviceName.isEmpty()) {
        requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT);
        requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS);
    }

    return requiredPerms;
  }

  /**
   * Check that required permissions are allowed
   *
   * @param popup
   * @return
   */
  private boolean checkPermissions(boolean popup) {
    boolean missingEssentialPermission = false;
    boolean missingAnyPermission = false;
    List<String> requiredPerms = getPermissions();
    List<String> requestPerms = new ArrayList<>();
    Context ctx = requireContext();

    for (final String perm : requiredPerms) {
      if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
        missingAnyPermission = true;
        // Filter non essential permissions for result
        boolean nonEssential =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && perm.equals(Manifest.permission.ACTIVITY_RECOGNITION)
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && (perm.equals(Manifest.permission.BLUETOOTH_CONNECT)
                        || perm.equals(Manifest.permission.BLUETOOTH_SCAN))
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && perm.equals(Manifest.permission.POST_NOTIFICATIONS));
        missingEssentialPermission = missingEssentialPermission || !nonEssential;
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), perm)) {
          // A denied permission, show motivation in a popup
          String s = "Permission " + perm + " is explicitly denied";
          Log.i(getClass().getName(), s);
        } else {
          requestPerms.add(perm);
        }
      }
    }

    if (missingAnyPermission) {
      final String[] permissions = new String[requestPerms.size()];
      requestPerms.toArray(permissions);

      if (popup && missingEssentialPermission || !requestPerms.isEmpty()) {
        // Essential or requestable permissions missing
        String baseMessage =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getString(org.runnerup.common.R.string.GPS_permission_text_Android12)
                : Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? getString(org.runnerup.common.R.string.GPS_permission_text)
                    : getString(org.runnerup.common.R.string.GPS_permission_text_pre_Android10);

        AlertDialog.Builder builder =
            new MaterialAlertDialogBuilder(ctx)
                .setTitle(org.runnerup.common.R.string.GPS_permission_required)
                .setNegativeButton(
                    org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss());
        if (!requestPerms.isEmpty()) {
          // Let Android request the permissions
          builder
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, id) -> {
                    if (getActivity() != null) {
                      permissionLauncher.launch(permissions);
                    }
                  })
              .setMessage(
                  baseMessage
                      + "\n"
                      + getString(org.runnerup.common.R.string.Request_permission_text));
        } else {
          // Open settings for the app (no direct shortcut to permissions)
          Intent intent =
              new Intent()
                  .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                  .setData(Uri.fromParts("package", ctx.getPackageName(), null));
          builder
              .setPositiveButton(
                  org.runnerup.common.R.string.OK, (dialog, id) -> startActivity(intent))
              .setMessage(
                  baseMessage
                      + "\n\n"
                      + getString(org.runnerup.common.R.string.Request_permission_text));
        }
        builder.show();
      }
    }

    // https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases
    // Permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS requires special approval in Play
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    final Resources res = this.getResources();
    final boolean suppressOptimizeBatteryPopup =
        prefs.getBoolean(res.getString(R.string.pref_suppress_battery_optimization_popup), false);
    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
    if ((popup || getAutoStartGps())
        && !suppressOptimizeBatteryPopup
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
      Intent intent;
      int msgId;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Around Android 9 the Battery usage setting is available in the app setting too, which is
        // easier to
        // change than in the system settings
        intent =
            new Intent()
                .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ctx.getPackageName(), null));
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text_Android9;
      } else {
        intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text;
      }

      new MaterialAlertDialogBuilder(ctx)
          .setTitle(org.runnerup.common.R.string.Battery_optimization_check)
          .setMessage(msgId)
          .setPositiveButton(
              org.runnerup.common.R.string.OK, (dialog, which) -> this.startActivity(intent))
          .setNeutralButton(
              org.runnerup.common.R.string.Do_not_show_again,
              (dialog, which) ->
                  prefs
                      .edit()
                      .putBoolean(
                          res.getString(R.string.pref_suppress_battery_optimization_popup), true)
                      .apply())
          .setNegativeButton(
              org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
          .show();
    }

    return missingEssentialPermission;
  }

  private GpsLevel getGpsLevel(double gpsAccuracyMeters, int sats) {
    if (!mGpsStatus.isFixed()) {
      return GpsLevel.NOT_FIXED;
    }
    if (gpsAccuracyMeters <= 7 && sats > 7) {
      return GpsLevel.GOOD;
    } else if (gpsAccuracyMeters <= 15 && sats > 4) {
      return GpsLevel.ACCEPTABLE;
    } else {
      return GpsLevel.POOR;
    }
  }

  public void updateView() {
    updateStartButtonView();
    updateSetupValues();
    updateStartRunButtonView();
    updateSetupGpsChip();
    boolean hrPresent = updateHRView();
    boolean wearPresent = updateWearOSView();

    noDevicesConnected.setVisibility(hrPresent || wearPresent ? View.GONE : View.VISIBLE);
  }

  private void updateStartButtonView() {
    startButton.setVisibility(View.VISIBLE);
  }

  private void updateSetupValues() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    int sportId = prefs.getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    if (setupSportValue != null) {
      setupSportValue.setText(Sport.getStringArray(requireContext().getResources())[sportId]);
    }
    if (setupAudioValue != null) {
      String audio = prefs.getString(getString(R.string.pref_advanced_audio), null);
      setupAudioValue.setText(
          audio == null ? requireContext().getString(org.runnerup.common.R.string.Default) : audio);
    }
    if (setupWorkoutValue != null) {
      setupWorkoutValue.setText(
          selectedWorkoutName == null || selectedWorkoutName.isEmpty()
              ? requireContext().getString(R.string.None)
              : selectedWorkoutName);
    }
    int hasSteps = advancedWorkout == null ? View.GONE : View.VISIBLE;
    if (setupStepsHint != null) setupStepsHint.setVisibility(hasSteps);
    if (advancedStepList != null) advancedStepList.setVisibility(hasSteps);
  }

  private void updateStartRunButtonView() {
    if (mTracker == null || !mIsBound) {
      if (startRunButton != null) startRunButton.setEnabled(false);
      return;
    }
    if (startRunButton == null) return;
    boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
    if (raceReady || sportWithoutGps) {
      startRunButton.setEnabled(true);
      return;
    }
    boolean ready =
        mGpsStatus.isStarted()
            && mGpsStatus.isLogging()
            && mGpsStatus.isFixed()
            && mTracker.getState() == TrackerState.CONNECTED;
    startRunButton.setEnabled(ready);
  }

  private void toggleSetupGpsPopup() {
    if (setupGpsPopup == null) return;
    boolean show = setupGpsPopup.getVisibility() != View.VISIBLE;
    setupGpsPopup.setVisibility(show ? View.VISIBLE : View.GONE);
    if (show) {
      populateGpsPopup();
    }
  }

  private void populateGpsPopup() {
    boolean gpsRunning = mGpsStatus.isEnabled() && mGpsStatus.isStarted() && mGpsStatus.isLogging();
    if (setupGpsPopupIndicator != null) {
      setupGpsPopupIndicator.setImageResource(gpsRunning ? gpsPopupIcon() : R.drawable.ic_gps_0);
    }
    if (setupGpsPopupMessage != null) {
      setupGpsPopupMessage.setText(setupGpsMessage.getText());
    }
    if (setupGpsPopupSatellites == null) return;
    if (!gpsRunning) {
      setupGpsPopupSatellites.setVisibility(View.GONE);
      return;
    }
    setupGpsPopupSatellites.setVisibility(View.VISIBLE);
    int satFixedCount = mGpsStatus.getSatellitesFixed();
    int satAvailCount = mGpsStatus.getSatellitesAvailable();
    float accuracy = getGpsAccuracy();
    String gpsAccuracy = getGpsAccuracyString(accuracy);
    String gpsDetail =
        gpsAccuracy.isEmpty()
            ? String.format(
                getString(org.runnerup.common.R.string.GPS_status_no_accuracy),
                satFixedCount,
                satAvailCount)
            : String.format(
                getString(org.runnerup.common.R.string.GPS_status_accuracy),
                satFixedCount,
                satAvailCount,
                gpsAccuracy);
    setupGpsPopupSatellites.setText(gpsDetail);
  }

  private int gpsPopupIcon() {
    int satFixedCount = mGpsStatus.getSatellitesFixed();
    var gpsLevel = getGpsLevel(getGpsAccuracy(), satFixedCount);
    switch (gpsLevel) {
      case NOT_FIXED:
        return R.drawable.ic_gps_0;
      case POOR:
        return R.drawable.ic_gps_1;
      case ACCEPTABLE:
        return R.drawable.ic_gps_2;
      default:
        return R.drawable.ic_gps_3;
    }
  }

  private void updateSetupGpsChip() {
    if (setupGpsChip == null) return;
    if (sportWithoutGps) {
      setupGpsChip.setVisibility(View.GONE);
      if (setupGpsPopup != null) setupGpsPopup.setVisibility(View.GONE);
      return;
    }
    setupGpsChip.setVisibility(View.VISIBLE);
    if (!mGpsStatus.isEnabled() || !mGpsStatus.isStarted() || !mGpsStatus.isLogging()) {
      setupGpsIndicator.setImageResource(R.drawable.ic_gps_0);
      setupGpsMessage.setText(org.runnerup.common.R.string.GPS_indicator_off);
    } else {
      var gpsLevel = getGpsLevel(getGpsAccuracy(), mGpsStatus.getSatellitesFixed());
      switch (gpsLevel) {
        case NOT_FIXED:
          setupGpsIndicator.setImageResource(R.drawable.ic_gps_0);
          setupGpsMessage.setText(org.runnerup.common.R.string.Waiting_for_GPS);
          break;
        case POOR:
          setupGpsIndicator.setImageResource(R.drawable.ic_gps_1);
          setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_poor);
          break;
        case ACCEPTABLE:
          setupGpsIndicator.setImageResource(R.drawable.ic_gps_2);
          setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_acceptable);
          break;
        case GOOD:
          setupGpsIndicator.setImageResource(R.drawable.ic_gps_3);
          setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_good);
          break;
      }
    }
    // Keep the popup in sync while it is open
    if (setupGpsPopup != null && setupGpsPopup.getVisibility() == View.VISIBLE) {
      populateGpsPopup();
    }
  }

  private void openPicker(PickerKind kind) {
    pickerKind = kind;
    getPickerItems();
    if (pickerItems.length == 0) return;
    String title;
    switch (kind) {
      case SPORT:
        title = requireContext().getString(org.runnerup.common.R.string.Sport);
        break;
      case AUDIO:
        title = requireContext().getString(R.string.Audio_cues);
        break;
      default:
        title = requireContext().getString(org.runnerup.common.R.string.Workout);
        break;
    }
    TextView titleView = pickerRoot.findViewById(R.id.picker_title);
    titleView.setText(title);
    bindPickerList();
    pushPage(Page.PICKER);
  }

  private void refreshPicker() {
    if (pickerRoot == null || pickerRoot.getVisibility() != View.VISIBLE) return;
    getPickerItems();
    bindPickerList();
  }

  private void bindPickerList() {
    ListView list = pickerRoot.findViewById(R.id.picker_list);
    PickerListAdapter adapter = new PickerListAdapter(pickerItems, pickerSelected);
    list.setOnItemClickListener((parent, view, position, id) -> onPickerItemClick(position));
    list.setAdapter(adapter);
  }

  private void getPickerItems() {
    switch (pickerKind) {
      case SPORT:
        {
          String[] sports = Sport.getStringArray(requireContext().getResources());
          pickerItems = sports;
          pickerSelected = currentSportId();
          break;
        }
      case AUDIO:
        {
          List<String> items = new ArrayList<>();
          items.add(requireContext().getString(org.runnerup.common.R.string.Default));
          advancedAudioListAdapter.reload();
          for (int i = 1; i < advancedAudioListAdapter.getCount() - 1; i++) {
            items.add((String) advancedAudioListAdapter.getItem(i));
          }
          items.add(
              String.format(
                  requireContext().getString(org.runnerup.common.R.string.dialog_ellipsis),
                  requireContext().getString(org.runnerup.common.R.string.Manage_audio_cues)));
          pickerItems = items.toArray(new String[0]);
          String current = appPrefs.getString(getString(R.string.pref_advanced_audio), null);
          pickerSelected =
              current == null ? 0 : (items.contains(current) ? items.indexOf(current) : 0);
          break;
        }
      default:
        {
          List<String> items = new ArrayList<>();
          items.add(requireContext().getString(R.string.None));
          String[] workouts = WorkoutListAdapter.load(requireContext());
          if (workouts != null) {
            for (String w : workouts) {
              items.add(w.substring(0, w.length() - ".json".length()));
            }
          }
          items.add(
              String.format(
                  requireContext().getString(org.runnerup.common.R.string.dialog_ellipsis),
                  requireContext().getString(org.runnerup.common.R.string.Manage_workouts)));
          pickerItems = items.toArray(new String[0]);
          pickerSelected =
              selectedWorkoutName == null || selectedWorkoutName.isEmpty()
                  ? 0
                  : (items.contains(selectedWorkoutName) ? items.indexOf(selectedWorkoutName) : 0);
          break;
        }
    }
  }

  private void onPickerItemClick(int position) {
    if (position < 0 || position >= pickerItems.length) return;
    switch (pickerKind) {
      case SPORT:
        applySportChoice(position);
        break;
      case AUDIO:
        if (position == pickerItems.length - 1) {
          startActivity(new Intent(requireContext(), AudioCueSettingsActivity.class));
          return;
        }
        applyAudioChoice(position);
        break;
      default:
        if (position == pickerItems.length - 1) {
          startActivity(new Intent(requireContext(), ManageWorkoutsActivity.class));
          return;
        }
        applyWorkoutChoice(position);
        break;
    }
    popPage();
  }

  private void applySportChoice(int position) {
    appPrefs.edit().putInt(getString(R.string.pref_sport), position).apply();
    setGpsNotRequired(Sport.isWithoutGps(position));
    updateSetupSportIcon(position);
    updateView();
  }

  private void applyAudioChoice(int position) {
    String name = pickerItems[position];
    if (name.contentEquals(requireContext().getString(org.runnerup.common.R.string.Default))) {
      appPrefs.edit().remove(getString(R.string.pref_advanced_audio)).apply();
    } else {
      appPrefs.edit().putString(getString(R.string.pref_advanced_audio), name).apply();
    }
    updateView();
  }

  private void applyWorkoutChoice(int position) {
    String name = pickerItems[position];
    if (name.contentEquals(requireContext().getString(R.string.None))) {
      appPrefs.edit().remove(getString(R.string.pref_advanced_workout)).apply();
      loadAdvanced("");
      return;
    }
    appPrefs.edit().putString(getString(R.string.pref_advanced_workout), name).apply();
    loadAdvanced(name);
  }

  private class PickerListAdapter extends BaseAdapter {
    private final String[] items;
    private final int selected;

    PickerListAdapter(String[] items, int selected) {
      this.items = items;
      this.selected = selected;
    }

    @Override
    public int getCount() {
      return items.length;
    }

    @Override
    public Object getItem(int position) {
      return items[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView =
            LayoutInflater.from(parent.getContext()).inflate(R.layout.picker_item, parent, false);
      }
      TextView text = (TextView) convertView;
      text.setText(items[position]);
      Drawable check = null;
      if (position == selected) {
        check = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_check);
      }
      text.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, check, null);
      return text;
    }
  }

  private boolean updateHRView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerHRM.NAME)) {
      hrIndicator.setVisibility(View.GONE);
      hrMessage.setVisibility(View.GONE);
      return false;
    }
    Integer hrVal = null;
    if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
      hrVal = mTracker.getCurrentHRValue();
    }
    if (hrVal != null) {
      if (!batteryLevelMessageShown) {
        batteryLevelMessageShown = true;
        notificationBatteryLevel(mTracker.getCurrentBatteryLevel());
      }
    }

    hrMessage.setText(getHRDetailString());
    hrIndicator.setVisibility(View.VISIBLE);
    hrMessage.setVisibility(View.VISIBLE);

    return true;
  }

  private boolean updateWearOSView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerWear.NAME)) {
      wearOsIndicator.setVisibility(View.GONE);
      wearOsMessage.setVisibility(View.GONE);
      return false;
    }

    wearOsIndicator.setVisibility(View.VISIBLE);

    if (!mTracker.isComponentConnected(TrackerWear.NAME)) {
      wearOsMessage.setVisibility(View.VISIBLE);
      wearOsMessage.setText("?");
    } else {
      // wearOsMessage.setText(""); //todo show device name
      wearOsMessage.setVisibility(View.GONE);
    }

    return true;
  }

  public float getGpsAccuracy() {
    if (mTracker != null) {
      Location l = mTracker.getLastKnownLocation();

      if (l != null) {
        return l.getAccuracy();
      }
    }
    return -1;
  }

  public String getGpsAccuracyString(float accuracy) {
    String res = "";
    if (accuracy > 0) {
      String accString = formatter.formatElevation(Formatter.Format.TXT_LONG, accuracy);
      if (mTracker.getCurrentElevation() != null) {
        res =
            String.format(
                Locale.getDefault(),
                getString(org.runnerup.common.R.string.GPS_accuracy_elevation),
                accString,
                formatter.formatElevation(
                    Formatter.Format.TXT_LONG, mTracker.getCurrentElevation()));
      } else {
        res =
            String.format(
                Locale.getDefault(),
                getString(org.runnerup.common.R.string.GPS_accuracy_no_elevation),
                accString);
      }
    }
    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Extra info in debug builds
      if (mTracker != null) {
        Location l = mTracker.getLastKnownLocation();

        if (l != null) {
          res +=
              String.format(
                  Locale.getDefault(),
                  " [%1$s, %2$s/%3$s, %4$.1f/%5$.1f deg]",
                  formatter.formatElevation(
                      Formatter.Format.TXT_LONG, l.getVerticalAccuracyMeters()),
                  formatter.formatSpeed(Formatter.Format.TXT_SHORT, l.getSpeed()),
                  formatter.formatSpeed(
                      Formatter.Format.TXT_LONG, l.getSpeedAccuracyMetersPerSecond()),
                  l.getBearing(),
                  l.getBearingAccuracyDegrees());
        }
      }
    }
    return res;
  }

  private String getHRDetailString() {
    StringBuilder str = new StringBuilder();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    final String btDeviceName = prefs.getString(getString(R.string.pref_bt_name), null);

    if (btDeviceName != null) {
      str.append(btDeviceName);
    } else if (MockHRProvider.NAME.contentEquals(
        prefs.getString(getString(R.string.pref_bt_provider), ""))) {
      str.append("mock: ").append(prefs.getString(getString(R.string.pref_bt_address), "???"));
    }

    if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
      Integer hrVal = mTracker.getCurrentHRValue();
      if (hrVal != null) {
        str.append(" ").append(hrVal);
        Integer batteryLevel = mTracker.getCurrentBatteryLevel();

        if (batteryLevel != HRProvider.BATTERY_LEVEL_UNAVAILABLE) {
          str.append(" ").append(batteryLevel).append("%");
        }
      }
    }
    return str.toString();
  }

  private boolean mIsBound = false;

  private final ActivityResultLauncher<Intent> runLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Intent data = result.getData();
            registerStartEventListener();

            if (data != null) {
              if (data.getStringExtra("url") != null)
                Log.d(
                    getClass().getName(),
                    "data.getStringExtra(\"url\") => " + data.getStringExtra("url"));
              if (data.getStringExtra("ex") != null)
                Log.d(
                    getClass().getName(),
                    "data.getStringExtra(\"ex\") => " + data.getStringExtra("ex"));
              if (data.getStringExtra("obj") != null)
                Log.d(
                    getClass().getName(),
                    "data.getStringExtra(\"obj\") => " + data.getStringExtra("obj"));
            }
            runActivityPending = false;
            goRecord();
            if (!mIsBound || mTracker == null) {
              bindGpsTracker();
            } else {
              onGpsTrackerBound();
            }
            updateView();
          });

  private final ServiceConnection mConnection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
          // This is called when the connection with the service has been
          // established, giving us the service object we can use to
          // interact with the service. Because we have bound to a explicit
          // service that we know is running in our own process, we can
          // cast its IBinder to a concrete class and directly access it.
          mTracker = ((Tracker.LocalBinder) service).getService();
          // Tell the user about this for our demo.
          StartFragment.this.onGpsTrackerBound();
          if (mTracker != null) {
            mTracker.registerTrackerStateListener(trackerStateListener);
          }
        }

        public void onServiceDisconnected(ComponentName className) {
          // This is called when the connection with the service has been
          // unexpectedly disconnected -- that is, its process crashed.
          // Because it is running in our same process, we should never
          // see this happen.
          if (mTracker != null) {
            mTracker.unregisterTrackerStateListener(trackerStateListener);
          }
          mTracker = null;
        }
      };

  private void bindGpsTracker() {
    // Establish a connection with the service. We use an explicit
    // class name because we want a specific service implementation that
    // we know will be running in our own process (and thus won't be
    // supporting component replacement by other applications).
    mIsBound =
        requireActivity()
            .getApplicationContext()
            .bindService(
                new Intent(requireContext(), Tracker.class), mConnection, Context.BIND_AUTO_CREATE);
  }

  private void unbindGpsTracker() {
    if (mIsBound) {
      // Detach our existing connection.
      requireActivity().getApplicationContext().unbindService(mConnection);
      mIsBound = false;
    }
    if (mTracker != null) {
      mTracker.unregisterTrackerStateListener(trackerStateListener);
    }
    mTracker = null;
  }

  @Override
  public void onTick() {
    updateView();
  }

  @SuppressLint("NotifyDataSetChanged")
  private void loadAdvanced(String name) {
    Context ctx = requireActivity().getApplicationContext();
    if (name == null) {
      name = appPrefs.getString(getString(R.string.pref_advanced_workout), "");
    }
    selectedWorkoutName = name;
    advancedWorkout = null;
    if (name.isEmpty()) {
      advancedWorkoutStepsAdapter.clear();
      updateView();
      return;
    }
    try {
      advancedWorkout = WorkoutSerializer.readFile(ctx, name);
      advancedWorkoutStepsAdapter.setWorkout(advancedWorkout);
      updateView();
    } catch (Exception ex) {
      ex.printStackTrace();
      new MaterialAlertDialogBuilder(requireActivity())
          .setTitle(getString(org.runnerup.common.R.string.Failed_to_load_workout))
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }

  private void editRepeatCount(RepeatStep repeat) {
    final NumberPicker numberPicker = new NumberPicker(requireContext(), null);
    numberPicker.setOrientation(LinearLayout.VERTICAL);
    numberPicker.setDigits(4);
    numberPicker.setRange(0, 9999, true);
    numberPicker.setValue(repeat.getRepeatCount());
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(org.runnerup.common.R.string.repeat)
        .setView(numberPicker)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, whichButton) -> {
              repeat.setRepeatCount(numberPicker.getValue());
              dialog.dismiss();
              advancedWorkoutStepsAdapter.setWorkout(advancedWorkout);
              onWorkoutChanged.run();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
        .show();
  }

  final class WorkoutPlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface EditRepeatCount {
      void update(RepeatStep repeat);
    }

    static final int VIEW_TYPE_STEP = 0;
    static final int VIEW_TYPE_REPEAT = 1;

    private final EditRepeatCount onEdit;
    final Runnable changed;
    private final List<StepListEntry> items = new ArrayList<>();
    private Workout workout = null;

    WorkoutPlanAdapter(EditRepeatCount onEdit, Runnable changed) {
      this.onEdit = onEdit;
      this.changed = changed;
    }

    @SuppressLint("NotifyDataSetChanged")
    void setWorkout(Workout workout) {
      this.workout = workout;
      items.clear();
      items.addAll(workout.entriesAtLevel(null));
      notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    void clear() {
      workout = null;
      items.clear();
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      return items.get(position).step() instanceof RepeatStep ? VIEW_TYPE_REPEAT : VIEW_TYPE_STEP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == VIEW_TYPE_REPEAT) {
        return new PlanRepeatViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, parent, false), this);
      }
      return new PlanStepViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, parent, false), this.changed);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
      StepListEntry entry = items.get(position);
      if (viewHolder instanceof PlanStepViewHolder) {
        ((PlanStepViewHolder) viewHolder).bind(entry.step());
      } else {
        ((PlanRepeatViewHolder) viewHolder).bind((RepeatStep) entry.step());
      }
    }

    void editRepeatCount(RepeatStep repeat) {
      onEdit.update(repeat);
    }

    List<StepListEntry> entriesOf(Step parent) {
      return workout.entriesAtLevel(parent);
    }
  }

  static class PlanStepViewHolder extends RecyclerView.ViewHolder {

    final ImageButton moveUpButton;
    final ImageButton delButton;
    final StepButton button;

    PlanStepViewHolder(@NonNull View itemView, Runnable changed) {
      super(itemView);
      moveUpButton = itemView.findViewById(R.id.move_up_button);
      delButton = itemView.findViewById(R.id.del_button);
      moveUpButton.setVisibility(View.GONE);
      delButton.setVisibility(View.GONE);
      button = itemView.findViewById(R.id.workout_step_button);
      button.setOnChangedListener(changed);
    }

    void bind(Step step) {
      button.setStep(step);
      View buttonLayout = button.findViewById(R.id.step_button_layout);
      buttonLayout.setBackground(null);
      buttonLayout.setPadding(0, 0, 0, 0);
    }
  }

  static class PlanRepeatViewHolder extends RecyclerView.ViewHolder {

    final ImageButton moveUpButton;
    final ImageButton delButton;
    final MaterialButton addInsideButton;
    final RecyclerView childrenHost;
    final PlanChildrenAdapter childAdapter;
    private final WorkoutPlanAdapter outerAdapter;
    private final TextView title;

    PlanRepeatViewHolder(@NonNull View itemView, WorkoutPlanAdapter outerAdapter) {
      super(itemView);
      this.outerAdapter = outerAdapter;
      moveUpButton = itemView.findViewById(R.id.move_up_button);
      delButton = itemView.findViewById(R.id.del_button);
      addInsideButton = itemView.findViewById(R.id.add_step_inside_repeat_button);
      moveUpButton.setVisibility(View.GONE);
      delButton.setVisibility(View.GONE);
      addInsideButton.setVisibility(View.GONE);
      title = itemView.findViewById(R.id.repeat_title);
      childrenHost = itemView.findViewById(R.id.repeat_children_host);
      childrenHost.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
      childAdapter = new PlanChildrenAdapter(this);
      childrenHost.setAdapter(childAdapter);
    }

    void bind(RepeatStep repeat) {
      title.setText(
          itemView
              .getContext()
              .getString(org.runnerup.common.R.string.repeat_x, repeat.getRepeatCount()));
      title.setOnClickListener(v -> outerAdapter.editRepeatCount(repeat));
      LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) title.getLayoutParams();
      if (titleLp.getMarginStart() != 0) {
        titleLp.setMarginStart(0);
        title.setLayoutParams(titleLp);
      }
      childAdapter.bind(repeat);
    }
  }

  static class PlanChildrenAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<StepListEntry> items = new ArrayList<>();
    private final PlanRepeatViewHolder parent;

    PlanChildrenAdapter(PlanRepeatViewHolder parent) {
      this.parent = parent;
    }

    @SuppressLint("NotifyDataSetChanged")
    void bind(RepeatStep repeat) {
      items.clear();
      items.addAll(parent.outerAdapter.entriesOf(repeat));
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      return items.get(position).step() instanceof RepeatStep
          ? WorkoutPlanAdapter.VIEW_TYPE_REPEAT
          : WorkoutPlanAdapter.VIEW_TYPE_STEP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
      if (viewType == WorkoutPlanAdapter.VIEW_TYPE_REPEAT) {
        return new PlanRepeatViewHolder(
            inflater.inflate(R.layout.advanced_workout_repeat_row, viewGroup, false),
            parent.outerAdapter);
      }
      return new PlanStepViewHolder(
          inflater.inflate(R.layout.advanced_workout_row, viewGroup, false),
          parent.outerAdapter.changed);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
      StepListEntry entry = items.get(position);
      if (viewHolder instanceof PlanStepViewHolder) {
        ((PlanStepViewHolder) viewHolder).bind(entry.step());
      } else {
        ((PlanRepeatViewHolder) viewHolder).bind((RepeatStep) entry.step());
      }
    }
  }

  private final Runnable onWorkoutChanged =
      () -> {
        if (advancedWorkout != null && !selectedWorkoutName.isEmpty()) {
          Context ctx = requireActivity().getApplicationContext();
          try {
            WorkoutSerializer.writeFile(ctx, selectedWorkoutName, advancedWorkout);
          } catch (Exception ex) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
                .setMessage(ex.toString())
                .setPositiveButton(
                    org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
          }
        }
      };

  private final ValueModel.ChangeListener<TrackerState> trackerStateListener =
      new ValueModel.ChangeListener<>() {
        @Override
        public void onValueChanged(
            ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {
          onTick();
        }
      };
}
