package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import android.content.res.Resources;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.runnerup.common.util.Constants;
import org.runnerup.workout.feedback.AudioFeedback;
import org.runnerup.workout.feedback.VibrationFeedback;

public class WorkoutBuilderGatesTest {

  private static final Map<Integer, String> KEYS = new HashMap<>();

  static {
    KEYS.put(org.runnerup.R.string.pref_race_ready_start, "pref_race_ready_start");
    KEYS.put(org.runnerup.R.string.pref_gps_locked_cue, "pref_gps_locked_cue");
    KEYS.put(org.runnerup.R.string.pref_countdown_active, "pref_countdown_active");
    KEYS.put(org.runnerup.R.string.pref_countdown_time, "pref_countdown_time");
    KEYS.put(org.runnerup.R.string.pref_sport, "pref_sport");
    KEYS.put(org.runnerup.R.string.pref_autolap_active, "pref_autolap_active");
    KEYS.put(org.runnerup.R.string.pref_autolap, "pref_autolap");
    KEYS.put(org.runnerup.R.string.pref_autopause_active, "pref_autopause_active");
    KEYS.put(org.runnerup.R.string.pref_step_countdown_active, "pref_step_countdown_active");
    KEYS.put(org.runnerup.R.string.pref_step_countdown_time, "pref_step_countdown_time");
    KEYS.put(
        org.runnerup.R.string.pref_convert_advanced_distance_rest_to_recovery,
        "pref_convert_advanced_distance_rest_to_recovery");
  }

  private final Map<String, Object> store = new HashMap<>();
  private Resources res;
  private SharedPreferences prefs;

  @Before
  public void setUp() {
    store.clear();
    res = mock(Resources.class);
    when(res.getString(anyInt()))
        .thenAnswer(i -> KEYS.getOrDefault(i.getArgument(0), "unknown#" + i.getArgument(0)));
    prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (Boolean) store.get(i.getArgument(0))
                    : i.getArgument(1));
    when(prefs.getString(anyString(), anyString()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (String) store.get(i.getArgument(0))
                    : i.getArgument(1));
    when(prefs.getInt(anyString(), anyInt()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (Integer) store.get(i.getArgument(0))
                    : i.getArgument(1));
  }

  private Workout workout() {
    Workout w = new Workout();
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    w.steps.add(new Step());
    return w;
  }

  @Test
  public void noGatesWhenDisabled() {
    store.put("pref_race_ready_start", false);
    Workout w = workout();
    WorkoutBuilder.injectRaceReadyGates(res, prefs, w);
    assertEquals(1, w.steps.size());
  }

  @Test
  public void noGatesForGymWorkouts() {
    Workout w = workout();
    w.sport = org.runnerup.common.util.Constants.DB.ACTIVITY.SPORT_TREADMILL;
    WorkoutBuilder.injectRaceReadyGates(res, prefs, w);
    assertEquals(1, w.steps.size());
  }

  @Test
  public void countdownPlusGate() {
    store.put("pref_countdown_active", true);
    store.put("pref_countdown_time", "5");
    Workout w = workout();
    WorkoutBuilder.injectRaceReadyGates(res, prefs, w);
    assertEquals(3, w.steps.size());
    assertTrue(w.steps.get(0).isPauseStep());
    assertEquals(5.0d, w.steps.get(0).durationValue, 1e-9);
    assertTrue(w.steps.get(1) instanceof GpsWaitStep);
    assertFalse(w.steps.get(2).isPauseStep());
  }

  @Test
  public void gateOnlyWhenCountdownOff() {
    store.put("pref_countdown_active", false);
    Workout w = workout();
    WorkoutBuilder.injectRaceReadyGates(res, prefs, w);
    assertEquals(2, w.steps.size());
    assertTrue(w.steps.get(0) instanceof GpsWaitStep);
  }

  @Test
  public void gateCarriesCueAndVibration() {
    Workout w = workout();
    WorkoutBuilder.injectRaceReadyGates(res, prefs, w);
    GpsWaitStep gate = (GpsWaitStep) w.steps.get(0);
    assertEquals(1, gate.triggers.size());
    EventTrigger ev = (EventTrigger) gate.triggers.get(0);
    assertSame(Event.COMPLETED, ev.event);
    assertSame(Scope.STEP, ev.scope);
    assertEquals(1, ev.maxCounter);
    assertEquals(2, ev.triggerAction.size());
    assertTrue(ev.triggerAction.get(0) instanceof VibrationFeedback);
    assertTrue(ev.triggerAction.get(1) instanceof AudioFeedback);
  }

  @Test
  public void gateSkipsAutolapAndAutopause() {
    store.put("pref_autolap_active", true);
    store.put("pref_autopause_active", true);
    store.put("pref_race_ready_start", true);
    Workout w = workout();
    WorkoutBuilder.prepareWorkout(res, prefs, w);
    GpsWaitStep gate = (GpsWaitStep) w.steps.get(0);
    assertEquals(0.0d, gate.getAutolap(), 1e-9);
    for (Trigger t : gate.triggers) {
      assertFalse(t instanceof AutoPauseTrigger);
    }
  }
}
