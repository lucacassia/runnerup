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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.util.Formatter;
import org.runnerup.util.LiveMap;
import org.runnerup.util.MapViewWrapper;
import org.runnerup.util.TickListener;
import org.runnerup.util.ViewUtil;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;

public class RunActivity extends AppCompatActivity implements TickListener {
  private Workout workout = null;
  private Tracker mTracker = null;
  private final Handler handler = new Handler();

  private static final long HOLD_TO_STOP_MILLIS = 1500L;

  private final ActivityResultLauncher<Intent> saveLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> onWorkoutResult(result.getResultCode(), result.getData()));

  private ExtendedFloatingActionButton pauseButton = null;
  private ExtendedFloatingActionButton nextLapButton = null;
  private StopProgressDrawable stopProgressDrawable = null;
  private HoldToStopListener holdToStopListener = null;
  private TextView activityTime = null;
  private TextView activityDistance = null;
  private TextView activityPace = null;
  private TextView activityTimeExpanded = null;
  private TextView activityDistanceExpanded = null;
  private TextView activityPaceExpanded = null;
  private View statsCard = null;
  private ImageView statsExpandIndicator = null;
  private View stats3Horizontal = null;
  private View stats3Vertical = null;
  private View stats3Area = null;
  private boolean statsExpanded = false;
  private boolean statsAnimating = false;
  private int statsCardNaturalHeight = 0;
  private int stats3AreaNaturalHeight = 0;
  private int statsDelta = 0;
  private TextView lapTime = null;
  private TextView lapDistance = null;
  private TextView lapPace = null;
  private TextView currentPace = null;
  private RecyclerView workoutList = null;
  private org.runnerup.workout.Step currentStep = null;
  private Formatter formatter = null;
  private TextView currentHr;
  private TextView hrDebug;
  private BottomSheetBehavior<?> runBottomSheetBehavior = null;
  private MapViewWrapper runMapview = null;
  private LiveMap liveMap = null;

  static final class ButtonState {
    final int leftText;
    final int leftIcon;
    final int rightText;
    final int rightIcon;
    final int rightBg;
    final int rightFg;

    ButtonState(
        int leftText, int leftIcon, int rightText, int rightIcon, int rightBg, int rightFg) {
      this.leftText = leftText;
      this.leftIcon = leftIcon;
      this.rightText = rightText;
      this.rightIcon = rightIcon;
      this.rightBg = rightBg;
      this.rightFg = rightFg;
    }
  }

  static ButtonState buttonState(boolean paused) {
    return paused
        ? new ButtonState(
            org.runnerup.common.R.string.Resume,
            R.drawable.ic_play_arrow,
            org.runnerup.common.R.string.Stop,
            R.drawable.ic_stop,
            androidx.appcompat.R.attr.colorError,
            com.google.android.material.R.attr.colorOnError)
        : new ButtonState(
            org.runnerup.common.R.string.Pause,
            R.drawable.ic_pause,
            org.runnerup.common.R.string.NextLap,
            R.drawable.ic_skip_next,
            androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary);
  }

  class WorkoutRow {
    org.runnerup.workout.Step step = null;
    ContentValues lap = null;
    public int level;
  }

  private final ArrayList<WorkoutRow> workoutRows = new ArrayList<>();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    Window window = getWindow();
    super.onCreate(savedInstanceState);
    if (!isLargeScreen()) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    setContentView(R.layout.run);
    View rootView = findViewById(R.id.start_view);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        new OnApplyWindowInsetsListener() {
          @NonNull
          @Override
          public WindowInsetsCompat onApplyWindowInsets(
              @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
          }
        });
    formatter = new Formatter(this);

    pauseButton = findViewById(R.id.pause_button);
    pauseButton.setOnClickListener(pauseButtonClick);
    nextLapButton = findViewById(R.id.next_lap_button);
    stopProgressDrawable = new StopProgressDrawable();
    holdToStopListener =
        new HoldToStopListener(
            nextLapButton,
            stopProgressDrawable,
            HOLD_TO_STOP_MILLIS,
            () -> workout != null && workout.isPaused(),
            this::doStop,
            this::showPressHoldToStopHint);
    nextLapButton.setOnTouchListener(holdToStopListener);
    activityTime = findViewById(R.id.run_activity_time);
    activityDistance = findViewById(R.id.run_activity_distance);
    activityPace = findViewById(R.id.run_activity_pace);
    activityTimeExpanded = findViewById(R.id.run_activity_time_expanded);
    activityDistanceExpanded = findViewById(R.id.run_activity_distance_expanded);
    activityPaceExpanded = findViewById(R.id.run_activity_pace_expanded);
    statsCard = findViewById(R.id.table_layout1);
    statsExpandIndicator = findViewById(R.id.stats_expand_indicator);
    stats3Horizontal = findViewById(R.id.stats_3_horizontal);
    stats3Vertical = findViewById(R.id.stats_3_vertical);
    stats3Area = findViewById(R.id.stats_3_area);
    statsCard.setOnClickListener(v -> toggleStatsExpanded());
    lapTime = findViewById(R.id.lap_time);
    lapDistance = findViewById(R.id.lap_distance);
    lapPace = findViewById(R.id.lap_pace);
    currentPace = findViewById(R.id.current_pace);
    currentHr = findViewById(R.id.current_hr);
    workoutList = findViewById(R.id.workout_list);
    hrDebug = findViewById(R.id.hr_debug);
    workoutList.setLayoutManager(new LinearLayoutManager(this));
    WorkoutAdapter adapter = new WorkoutAdapter(workoutRows);
    workoutList.setAdapter(adapter);
    workoutList.setVisibility(View.VISIBLE);
    runBottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.run_bottom_sheet));
    runBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    statsCard
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (statsCardNaturalHeight > 0) {
                  statsCard.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                  return;
                }
                measureStatsExpansion();
              }
            });

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    final boolean showOnLockScreen =
        prefs.getBoolean(res.getString(R.string.pref_show_on_lock_screen), true);
    final boolean keepScreenOn =
        prefs.getBoolean(res.getString(R.string.pref_keep_screen_on), false);

    if (!prefs.getBoolean(res.getString(R.string.pref_bt_debug), false)) {
      hrDebug = null;
    }

    bindGpsTracker();

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                // Ignore back while in an activity and the device is unlocked
                if (km.isKeyguardLocked()) {
                  // Original state is re-enabled on resume
                  showOnLockScreen(false);
                }
              }
            });
    ViewUtil.Insets(findViewById(R.id.start_view), true);

    showOnLockScreen(showOnLockScreen);
    if (keepScreenOn) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  private boolean isLargeScreen() {
    int screenSize =
        getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
    return screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.d(getClass().getName(), "onConfigurationChange => do NOTHING!!");
  }

  @Override
  public void onPause() {
    super.onPause();
    if (holdToStopListener != null) {
      holdToStopListener.cancel();
    }
  }

  @Override
  public void onResume() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean showOnLockScreen =
        prefs.getBoolean(res.getString(R.string.pref_show_on_lock_screen), true);

    super.onResume();
    showOnLockScreen(showOnLockScreen);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (holdToStopListener != null) {
      holdToStopListener.cancel();
    }
    unbindGpsTracker();
    stopTimer();
  }

  private void onGpsTrackerBound() {
    if (mTracker == null) {
      // should not happen
      return;
    }

    workout = mTracker.getWorkout();
    if (workout == null) {
      // should not happen
      return;
    }

    startTimer();

    populateWorkoutList();
    nextLapButton.setOnClickListener(nextLapButtonClick);
    updateButtons();
    mTracker.displayNotificationState();
  }

  private void populateWorkoutList() {
    List<Workout.StepListEntry> list = workout.getStepList();
    for (Workout.StepListEntry aList : list) {
      WorkoutRow row = new WorkoutRow();
      row.level = aList.level();
      row.step = aList.step();
      row.lap = null;
      workoutRows.add(row);
    }
  }

  private Timer timer = null;

  private void startTimer() {
    timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            RunActivity.this.handler.post(RunActivity.this::onTick);
          }
        },
        0,
        500);
  }

  private void stopTimer() {
    if (timer != null) {
      timer.cancel();
      timer.purge();
      timer = null;
    }
  }

  private Location l = null;

  public void onTick() {
    if (workout != null) {
      workout.onTick();
      updateView();

      if (mTracker != null) {
        Location l2 = mTracker.getLastKnownLocation();
        if (l2 != null && !l2.equals(l)) {
          l = l2;
        }
      }
    }
  }

  private void measureStatsExpansion() {
    View sheetHost = findViewById(R.id.run_sheet_host);
    statsCardNaturalHeight = statsCard.getHeight();
    stats3AreaNaturalHeight = stats3Area.getHeight();
    int hostHeight = sheetHost.getHeight();
    int peek = runBottomSheetBehavior.getPeekHeight();
    statsDelta = Math.max(0, hostHeight - peek);
  }

  private void toggleStatsExpanded() {
    if (statsAnimating || statsDelta <= 0 || statsCardNaturalHeight <= 0) {
      return;
    }
    statsAnimating = true;
    statsExpanded = !statsExpanded;

    final ViewGroup.LayoutParams cardLp = statsCard.getLayoutParams();
    final ViewGroup.LayoutParams areaLp = stats3Area.getLayoutParams();
    final int cardNatural = statsCardNaturalHeight;
    final int areaNatural = stats3AreaNaturalHeight;
    final int delta = statsDelta;

    final int cardStart = statsExpanded ? cardNatural : cardNatural + delta;
    final int cardEnd = statsExpanded ? cardNatural + delta : cardNatural;
    final int areaStart = statsExpanded ? areaNatural : areaNatural + delta;
    final int areaEnd = statsExpanded ? areaNatural + delta : areaNatural;

    cardLp.height = cardStart;
    ((ViewGroup.MarginLayoutParams) cardLp).bottomMargin = -(cardStart - cardNatural);
    statsCard.setLayoutParams(cardLp);
    areaLp.height = areaStart;
    stats3Area.setLayoutParams(areaLp);

    final View gone = statsExpanded ? stats3Horizontal : stats3Vertical;
    final View shown = statsExpanded ? stats3Vertical : stats3Horizontal;
    gone.animate().alpha(0f).setDuration(150).withEndAction(() -> gone.setVisibility(View.GONE));
    shown.setAlpha(0f);
    shown.setVisibility(View.VISIBLE);
    shown.animate().alpha(1f).setDuration(150).start();

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(250);
    animator.addUpdateListener(
        a -> {
          float f = a.getAnimatedFraction();
          int cardH = Math.round(cardStart + (cardEnd - cardStart) * f);
          int areaH = Math.round(areaStart + (areaEnd - areaStart) * f);
          cardLp.height = cardH;
          ((ViewGroup.MarginLayoutParams) cardLp).bottomMargin = -(cardH - cardNatural);
          statsCard.setLayoutParams(cardLp);
          areaLp.height = areaH;
          stats3Area.setLayoutParams(areaLp);
        });
    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            statsAnimating = false;
          }
        });

    animator.start();
    statsExpandIndicator.animate().rotation(statsExpanded ? 180f : 0f).setDuration(250).start();
  }

  private void onWorkoutResult(int resultCode, Intent data) {
    if (workout == null) {
      // "should not happen"
      finish();
      return;
    }
    if (resultCode == AppCompatActivity.RESULT_OK) {
      /*
       * they saved
       */
      Double manualDistance = null;
      if (data != null && data.hasExtra("MANUAL_DISTANCE")) {
        manualDistance = data.getDoubleExtra("MANUAL_DISTANCE", 0);
      }
      workout.onComplete(Scope.ACTIVITY, workout);
      if (mTracker != null) {
        mTracker.completeActivity(/* save= */ true, manualDistance);
      }
      mTracker = null;
      finish();
    } else {
      /*
       * they discarded
       */
      workout.onComplete(Scope.ACTIVITY, workout);
      if (mTracker != null) {
        mTracker.completeActivity(/* save= */ false, /* manualDistance= */ null);
      }
      mTracker = null;
      finish();
    }
  }

  private void newLap() {
    if (workout != null) {
      workout.onNewLapOrNextStep();
    }
  }

  private void showPressHoldToStopHint() {
    Toast.makeText(this, R.string.press_hold_to_stop, Toast.LENGTH_SHORT).show();
  }

  private ColorStateList themeColor(int attr) {
    TypedValue tv = new TypedValue();
    getTheme().resolveAttribute(attr, tv, true);
    return ColorStateList.valueOf(tv.data);
  }

  private Boolean buttonsPaused = null;

  private void updateButtons() {
    boolean paused = workout.isPaused();
    if (buttonsPaused != null && buttonsPaused == paused) {
      return;
    }
    buttonsPaused = paused;
    ButtonState s = buttonState(paused);
    pauseButton.setIconResource(s.leftIcon);
    pauseButton.setText(s.leftText);
    if (paused) {
      stopProgressDrawable.setProgress(0f);
      nextLapButton.setIcon(stopProgressDrawable);
    } else {
      nextLapButton.setIconResource(s.rightIcon);
    }
    nextLapButton.setText(s.rightText);
    nextLapButton.setBackgroundTintList(themeColor(s.rightBg));
    nextLapButton.setIconTint(themeColor(s.rightFg));
    nextLapButton.setTextColor(themeColor(s.rightFg));
  }

  private void togglePause() {
    if (workout == null) {
      // "should not happen"
      return;
    }

    if (workout.isPaused()) {
      workout.onResume(workout);
    } else {
      workout.onPause(workout);
    }
    updateButtons();
  }

  private void doStop() {
    if (timer != null) {
      workout.onStop(workout);
      stopTimer(); // set timer=null;
      mTracker.stopForeground(true); // remove notification
      Intent intent = new Intent(RunActivity.this, DetailActivity.class);
      /*
       * The same activity is used to show details and to save
       * activity they show almost the same information
       */
      intent.putExtra("mode", "save");
      intent.putExtra("ID", mTracker.getActivityId());
      saveLauncher.launch(intent);
    }
  }

  private final OnClickListener pauseButtonClick = v -> togglePause();
  private final OnClickListener nextLapButtonClick =
      v -> {
        if (workout != null && !workout.isPaused()) {
          newLap();
        }
      };

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean volumeButtonControls =
        prefs.getBoolean(res.getString(R.string.pref_volume_button_controls), false);

    if (volumeButtonControls
        && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean volumeButtonControls =
        prefs.getBoolean(res.getString(R.string.pref_volume_button_controls), false);

    if (volumeButtonControls
        && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
          newLap();
          break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
          togglePause();
      }
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  private void showOnLockScreen(boolean enabled) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(enabled);
    } else {
      Window window = getWindow();
      if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
      }
    }
  }

  @SuppressLint("NotifyDataSetChanged")
  private void updateView() {
    boolean isPaused = workout != null && workout.isPaused();
    if (mTracker.getState() == TrackerState.STOPPED && !isPaused) {
      doStop();
    } else {
      if (workout == null) {
        // "should not happen"
        return;
      }

      updateButtons();
      double ad = workout.getDistance(Scope.ACTIVITY);
      double at = workout.getTime(Scope.ACTIVITY);
      double ap = workout.getSpeed(Scope.ACTIVITY);
      activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at)));
      activityDistance.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
      activityPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap));
      activityTimeExpanded.setText(activityTime.getText());
      activityDistanceExpanded.setText(activityDistance.getText());
      activityPaceExpanded.setText(activityPace.getText());

      double ld = workout.getDistance(Scope.LAP);
      double lt = workout.getTime(Scope.LAP);
      double lp = workout.getSpeed(Scope.LAP);
      lapTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(lt)));
      lapDistance.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ld)));
      lapPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, lp));

      double cp = workout.getSpeed(Scope.CURRENT);
      currentPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, cp));

      if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
        double chr = workout.getHeartRate(Scope.CURRENT);
        currentHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, chr));
        currentHr.setVisibility(View.VISIBLE);
        if (hrDebug != null) {
          hrDebug.setVisibility(View.VISIBLE);
          mTracker.setHrDebug(hrDebug);
        }
      } else {
        currentHr.setVisibility(View.GONE);
      }

      Step curr = workout.getCurrentStep();
      if (curr != currentStep) {
        ((WorkoutAdapter) workoutList.getAdapter()).notifyDataSetChanged();
        currentStep = curr;
        if (workoutList.getLayoutManager() instanceof LinearLayoutManager) {
          LinearLayoutManager lm = (LinearLayoutManager) workoutList.getLayoutManager();
          RecyclerView.SmoothScroller scroller = new LinearSmoothScroller(this);
          scroller.setTargetPosition(getPosition(workoutRows, currentStep));
          lm.startSmoothScroll(scroller);
        }
      }
    }
  }

  private int getPosition(
      ArrayList<WorkoutRow> workoutRows, org.runnerup.workout.Step currentActivity) {
    for (int i = 0; i < workoutRows.size(); i++) {
      if (workoutRows.get(i).step == currentActivity) return i;
    }
    return 0;
  }

  private boolean mIsBound = false;

  private final ServiceConnection mConnection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
          // This is called when the connection with the service has been
          // established, giving us the service object we can use to
          // interact with the service. Because we have bound to a explicit
          // service that we know is running in our own process, we can
          // cast its IBinder to a concrete class and directly access it.
          if (mTracker == null) {
            mTracker = ((Tracker.LocalBinder) service).getService();
            // Tell the user about this for our demo.
            RunActivity.this.onGpsTrackerBound();
          }
        }

        public void onServiceDisconnected(ComponentName className) {
          // This is called when the connection with the service has been
          // unexpectedly disconnected -- that is, its process crashed.
          // Because it is running in our same process, we should never
          // see this happen.
          mIsBound = false;
          mTracker = null;
        }
      };

  private void bindGpsTracker() {
    // Establish a connection with the service. We use an explicit
    // class name because we want a specific service implementation that
    // we know will be running in our own process (and thus won't be
    // supporting component replacement by other applications).
    mIsBound =
        getApplicationContext()
            .bindService(new Intent(this, Tracker.class), mConnection, Context.BIND_AUTO_CREATE);
  }

  private void unbindGpsTracker() {
    if (mIsBound) {
      // Detach our existing connection.
      getApplicationContext().unbindService(mConnection);
      mIsBound = false;
    }
  }

  class WorkoutAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STEP = 0;
    private static final int TYPE_LAP = 1;

    final ArrayList<WorkoutRow> rows;

    WorkoutAdapter(ArrayList<WorkoutRow> workoutRows) {
      this.rows = workoutRows;
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
      return rows.get(position).step != null ? TYPE_STEP : TYPE_LAP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == TYPE_LAP) {
        return new LapRowViewHolder(inflater.inflate(R.layout.laplist_row, parent, false));
      }
      return new WorkoutRowViewHolder(inflater.inflate(R.layout.workout_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      WorkoutRow tmp = rows.get(position);
      if (holder instanceof WorkoutRowViewHolder) {
        bindWorkoutRow((WorkoutRowViewHolder) holder, tmp.step, tmp.level);
      }
    }

    private void bindWorkoutRow(WorkoutRowViewHolder holder, Step step, int level) {
      TextView intensity = holder.intensity;
      TextView durationType = holder.durationType;
      TextView durationValue = holder.durationValue;
      TextView targetPace = holder.targetPace;
      intensity.setPadding(level * 10, 0, 0, 0);
      intensity.setText(getResources().getText(step.getIntensity().getTextId()));
      boolean isCurrent = currentStep == step;
      if (isCurrent) {
        holder.itemView.setBackgroundResource(R.drawable.workout_row_active_bg);
        intensity.setTextColor(ContextCompat.getColor(RunActivity.this, R.color.colorPrimary));
        intensity.setTypeface(Typeface.DEFAULT_BOLD);
      } else {
        holder.itemView.setBackgroundResource(android.R.color.transparent);
        intensity.setTextColor(ContextCompat.getColor(RunActivity.this, R.color.colorText));
        intensity.setTypeface(Typeface.DEFAULT);
      }
      if (step.getDurationType() != null) {
        durationType.setText(getResources().getText(step.getDurationType().getTextId()));
        durationValue.setText(
            formatter.format(
                Formatter.Format.TXT_LONG, step.getDurationType(), step.getDurationValue()));
      } else {
        durationType.setText("");
        durationValue.setText("");
      }

      if (step.getTargetType() == null) {
        targetPace.setText("");
      } else {
        double minValue = step.getTargetValue().minValue;
        double maxValue = step.getTargetValue().maxValue;
        if (minValue == maxValue) {
          targetPace.setText(
              formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), minValue));
        } else {
          targetPace.setText(
              String.format(
                  Locale.getDefault(),
                  "%s-%s",
                  formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), minValue),
                  formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), maxValue)));
        }
      }
      if (step.getIntensity() == Intensity.REPEAT) {
        if (step.getCurrentRepeat() >= step.getRepeatCount()) {
          durationValue.setText(org.runnerup.common.R.string.Finished);
        } else {
          durationValue.setText(
              String.format(
                  Locale.getDefault(),
                  "%d/%d",
                  (step.getCurrentRepeat() + 1),
                  step.getRepeatCount()));
        }
      }
    }
  }

  class WorkoutRowViewHolder extends RecyclerView.ViewHolder {
    final TextView intensity;
    final TextView durationType;
    final TextView durationValue;
    final TextView targetPace;

    WorkoutRowViewHolder(@NonNull View itemView) {
      super(itemView);
      intensity = itemView.findViewById(R.id.workout_step_intensity);
      durationType = itemView.findViewById(R.id.workout_step_duration_type);
      durationValue = itemView.findViewById(R.id.workout_step_duration_value);
      targetPace = itemView.findViewById(R.id.workout_step_pace);
    }
  }

  class LapRowViewHolder extends RecyclerView.ViewHolder {

    LapRowViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }
}
