package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
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

public class WorkoutBuilderStepCountdownTest {

  private static final Map<Integer, String> KEYS = new HashMap<>();

  static {
    KEYS.put(org.runnerup.R.string.pref_step_countdown_active, "pref_step_countdown_active");
    KEYS.put(org.runnerup.R.string.pref_step_countdown_time, "pref_step_countdown_time");
    KEYS.put(
        org.runnerup.R.string.pref_convert_advanced_distance_rest_to_recovery,
        "pref_convert_advanced_distance_rest_to_recovery");
    KEYS.put(org.runnerup.R.string.pref_race_ready_start, "pref_race_ready_start");
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
    store.put("pref_step_countdown_active", true);
    store.put("pref_step_countdown_time", "15");
    store.put("pref_convert_advanced_distance_rest_to_recovery", true);
    store.put("pref_race_ready_start", false);
  }

  private static Step openActiveStep() {
    Step s = new Step();
    s.intensity = Intensity.ACTIVE;
    return s;
  }

  @Test
  public void raceReadyGateDoesNotTriggerLeadingRecovery() {
    store.put("pref_race_ready_start", true);
    Step a = openActiveStep();
    Step b = openActiveStep();
    Workout w = workoutWith(a, b);

    WorkoutBuilder.prepareWorkout(res, prefs, w);

    // Gate parked at the front, then the planned steps with a recovery only between them.
    assertEquals(4, w.steps.size());
    assertSame(GpsWaitStep.class, w.steps.get(0).getClass());
    assertEquals(Intensity.RESTING, w.steps.get(0).intensity);
    assertSame(a, w.steps.get(1));
    assertRecovery(w.steps.get(2));
    assertSame(b, w.steps.get(3));
  }

  @Test
  public void repeatDoesNotGetLeadingRecovery() {
    RepeatStep repeat = new RepeatStep();
    repeat.setRepeatCount(2);
    Step first = openActiveStep();
    Step second = openActiveStep();
    repeat.getSteps().add(first);
    repeat.getSteps().add(second);

    Workout w = new Workout();
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    w.steps.add(repeat);
    WorkoutBuilder.prepareWorkout(res, prefs, w);

    assertEquals(3, repeat.getSteps().size());
    assertSame(first, repeat.getSteps().get(0));
    assertRecovery(repeat.getSteps().get(1));
    assertSame(second, repeat.getSteps().get(2));
  }

  @Test
  public void recoveryGoesBetweenOpenSteps() {
    Step a = openActiveStep();
    Step b = openActiveStep();
    Workout w = workoutWith(a, b);
    WorkoutBuilder.prepareWorkout(res, prefs, w);

    assertEquals(3, w.steps.size());
    assertSame(a, w.steps.get(0));
    assertRecovery(w.steps.get(1));
    assertSame(b, w.steps.get(2));
  }

  @Test
  public void repeatThenOpenStepsKeepsPositions() {
    RepeatStep repeat = new RepeatStep();
    repeat.setRepeatCount(1);
    Step a = openActiveStep();
    Step b = openActiveStep();
    repeat.getSteps().add(a);
    repeat.getSteps().add(b);
    Step s1 = openActiveStep();
    Step s2 = openActiveStep();
    Workout w = new Workout();
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    w.steps.add(repeat);
    w.steps.add(s1);
    w.steps.add(s2);
    WorkoutBuilder.prepareWorkout(res, prefs, w);

    assertEquals(4, repeat.getSteps().size());
    assertSame(a, repeat.getSteps().get(0));
    assertRecovery(repeat.getSteps().get(1));
    assertSame(b, repeat.getSteps().get(2));
    assertRecovery(repeat.getSteps().get(3));
    assertEquals(4, w.steps.size());
    assertSame(repeat, w.steps.get(0));
    assertSame(s1, w.steps.get(1));
    assertRecovery(w.steps.get(2));
    assertSame(s2, w.steps.get(3));
  }

  @Test
  public void countdownDisabledInsertsNothing() {
    store.put("pref_step_countdown_active", false);
    RepeatStep repeat = new RepeatStep();
    repeat.setRepeatCount(1);
    Step first = openActiveStep();
    repeat.getSteps().add(first);
    Workout w = new Workout();
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    w.steps.add(repeat);
    WorkoutBuilder.prepareWorkout(res, prefs, w);

    assertEquals(1, repeat.getSteps().size());
    assertSame(first, repeat.getSteps().get(0));
  }

  private static Workout workoutWith(Step... steps) {
    Workout w = new Workout();
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    for (Step s : steps) {
      w.steps.add(s);
    }
    return w;
  }

  private static void assertRecovery(Step s) {
    assertEquals(Intensity.RECOVERY, s.intensity);
    assertEquals(Dimension.TIME, s.durationType);
    assertEquals(15.0d, s.durationValue, 1e-9);
  }
}
