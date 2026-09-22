package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.runnerup.common.util.Constants.DB;

public class RaceReadyTest {

  private static final Map<Integer, String> KEYS = new HashMap<>();

  static {
    KEYS.put(org.runnerup.R.string.pref_race_ready_start, "pref_race_ready_start");
    KEYS.put(org.runnerup.R.string.pref_gps_locked_cue, "pref_gps_locked_cue");
    KEYS.put(org.runnerup.R.string.pref_countdown_active, "pref_countdown_active");
    KEYS.put(org.runnerup.R.string.pref_countdown_time, "pref_countdown_time");
    KEYS.put(org.runnerup.R.string.pref_sport, "pref_sport");
  }

  private final Map<String, Object> store = new HashMap<>();
  private Resources res;

  @Before
  public void setUp() {
    res = mock(Resources.class);
    when(res.getString(anyInt()))
        .thenAnswer(i -> KEYS.getOrDefault(i.getArgument(0), "unknown#" + i.getArgument(0)));
  }

  private SharedPreferences prefs() {
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (Boolean) store.get(i.getArgument(0))
                    : i.getArgument(1));
    when(prefs.getInt(anyString(), anyInt()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (Integer) store.get(i.getArgument(0))
                    : i.getArgument(1));
    when(prefs.getString(anyString(), anyString()))
        .thenAnswer(
            i ->
                store.containsKey((String) i.getArgument(0))
                    ? (String) store.get(i.getArgument(0))
                    : i.getArgument(1));
    return prefs;
  }

  @Test
  public void enabledDefaultsOff() {
    assertFalse(RaceReady.enabled(res, prefs()));
  }

  @Test
  public void enabledHonorsPref() {
    store.put("pref_race_ready_start", false);
    assertFalse(RaceReady.enabled(res, prefs()));
  }

  @Test
  public void gpsLockedCueDefaultsOn() {
    assertTrue(RaceReady.gpsLockedCueEnabled(res, prefs()));
  }

  @Test
  public void gatingWithoutGpsSportIsFalse() {
    Workout w = new Workout();
    w.sport = DB.ACTIVITY.SPORT_TREADMILL;
    assertFalse(RaceReady.gating(w));
  }

  @Test
  public void gatingGpsSportIsTrue() {
    assertTrue(RaceReady.gating(new Workout()));
  }

  @Test
  public void connectedMatrix() {
    assertTrue(RaceReady.connected(true, true, false));
    assertTrue(RaceReady.connected(false, false, false));
    assertTrue(RaceReady.connected(false, true, true));
    assertFalse(RaceReady.connected(false, true, false));
  }

  @Test
  public void countdownOffByDefault() {
    assertEquals(0, RaceReady.countdownSeconds(res, prefs()));
  }

  @Test
  public void countdownParsesSeconds() {
    store.put("pref_countdown_active", true);
    store.put("pref_countdown_time", "10");
    assertEquals(10, RaceReady.countdownSeconds(res, prefs()));
  }

  @Test
  public void countdownIgnoresGarbage() {
    store.put("pref_countdown_active", true);
    store.put("pref_countdown_time", "abc");
    assertEquals(0, RaceReady.countdownSeconds(res, prefs()));
  }

  @Test
  public void countdownClampsNegative() {
    store.put("pref_countdown_active", true);
    store.put("pref_countdown_time", "-3");
    assertEquals(0, RaceReady.countdownSeconds(res, prefs()));
  }

  @Test
  public void defaultWorkoutMatchesConfig() {
    store.put("pref_sport", DB.ACTIVITY.SPORT_BIKING);
    Workout w = RaceReady.defaultWorkout(res, prefs());
    assertEquals(DB.ACTIVITY.SPORT_BIKING, w.sport);
    assertEquals(1, w.steps.size());
    assertEquals(Constants.WORKOUT_TYPE.BASIC, w.getWorkoutType());
  }
}
