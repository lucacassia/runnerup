package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.runnerup.R;
import org.runnerup.util.HRZones;

public class WorkoutSerializerTest {

  private static final String HRZ_KEY = "hrz_values";
  private static final String HRZ_VALUES = "120,140,160,180,200";

  private HRZones configuredZones() {
    Resources res = mock(Resources.class);
    when(res.getString(R.string.pref_hrz_values)).thenReturn(HRZ_KEY);
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString(HRZ_KEY, null)).thenReturn(HRZ_VALUES);
    return new HRZones(res, prefs);
  }

  private Workout workoutWithSteps() {
    Workout w = new Workout();

    Step warmup = new Step();
    warmup.intensity = Intensity.WARMUP;

    Step interval = new Step();
    interval.intensity = Intensity.ACTIVE;
    interval.durationType = Dimension.TIME;
    interval.durationValue = 180;
    interval.targetType = Dimension.PACE;
    interval.targetValue = new Range(0.24, 0.26);

    Step recovery = new Step();
    recovery.intensity = Intensity.RECOVERY;
    recovery.durationType = Dimension.TIME;
    recovery.durationValue = 60;

    RepeatStep repeat = new RepeatStep();
    repeat.repeatCount = 5;
    repeat.steps.add(interval);
    repeat.steps.add(recovery);

    Step hr = new Step();
    hr.intensity = Intensity.ACTIVE;
    hr.durationType = Dimension.DISTANCE;
    hr.durationValue = 400;
    hr.targetType = Dimension.HR;
    hr.targetValue = new Range(140, 160);

    Step cooldown = new Step();
    cooldown.intensity = Intensity.COOLDOWN;
    cooldown.durationType = Dimension.DISTANCE;
    cooldown.durationValue = 1600;

    w.addStep(warmup);
    w.addStep(repeat);
    w.addStep(hr);
    w.addStep(cooldown);
    return w;
  }

  @Test
  public void roundTripPreservesStructureAndValues() throws Exception {
    HRZones zones = configuredZones();
    JSONObject obj = WorkoutSerializer.createJSON(workoutWithSteps(), zones);

    Workout w = WorkoutSerializer.readJSON(new StringReader(obj.toString()), zones);
    assertEquals(4, w.steps.size());

    Step warmup = w.steps.get(0);
    assertEquals(Intensity.WARMUP, warmup.intensity);
    assertNull(warmup.durationType);

    RepeatStep repeat = (RepeatStep) w.steps.get(1);
    assertEquals(5, repeat.repeatCount);
    assertEquals(2, repeat.steps.size());
    Step interval = repeat.steps.get(0);
    assertEquals(Intensity.ACTIVE, interval.intensity);
    assertEquals(Dimension.TIME, interval.durationType);
    assertEquals(180.0, interval.durationValue, 0.001);
    assertEquals(Dimension.PACE, interval.targetType);
    assertEquals(0.24, interval.targetValue.minValue, 0.001);
    assertEquals(0.26, interval.targetValue.maxValue, 0.001);
    Step recovery = repeat.steps.get(1);
    assertEquals(Intensity.RECOVERY, recovery.intensity);
    assertEquals(60.0, recovery.durationValue, 0.001);

    Step hr = w.steps.get(2);
    assertEquals(Dimension.HR, hr.targetType);
    assertEquals(140.0, hr.targetValue.minValue, 0.001);
    assertEquals(160.0, hr.targetValue.maxValue, 0.001);

    Step cooldown = w.steps.get(3);
    assertEquals(Intensity.COOLDOWN, cooldown.intensity);
    assertEquals(Dimension.DISTANCE, cooldown.durationType);
    assertEquals(1600.0, cooldown.durationValue, 0.001);
  }

  @Test
  public void writesModernNestedFormat() throws Exception {
    JSONObject obj = WorkoutSerializer.createJSON(workoutWithSteps(), configuredZones());

    assertEquals("", obj.getString("workoutName"));
    assertEquals(1, obj.getJSONObject("sportType").getInt("sportTypeId"));

    JSONArray segments = obj.getJSONArray("workoutSegments");
    assertEquals(1, segments.length());
    JSONArray steps = segments.getJSONObject(0).getJSONArray("workoutSteps");
    assertEquals(4, steps.length());

    JSONObject warmup = steps.getJSONObject(0);
    assertEquals("ExecutableStepDTO", warmup.getString("type"));
    assertEquals("warmup", warmup.getJSONObject("stepType").getString("stepTypeKey"));
    assertEquals("lap.button", warmup.getJSONObject("endCondition").getString("conditionTypeKey"));

    JSONObject repeat = steps.getJSONObject(1);
    assertEquals("RepeatGroupDTO", repeat.getString("type"));
    assertEquals(5, repeat.getInt("numberOfIterations"));
    assertEquals("iterations", repeat.getJSONObject("endCondition").getString("conditionTypeKey"));
    JSONArray children = repeat.getJSONArray("workoutSteps");
    assertEquals(2, children.length());
    JSONObject interval = children.getJSONObject(0);
    assertEquals(
        "pace.zone", interval.getJSONObject("targetType").getString("workoutTargetTypeKey"));
    assertEquals(180.0, interval.getDouble("endConditionValue"), 0.001);
    assertEquals(1.0 / 0.24, interval.getDouble("targetValueOne"), 0.001);
    assertEquals(1.0 / 0.26, interval.getDouble("targetValueTwo"), 0.001);

    JSONObject hr = steps.getJSONObject(2);
    assertEquals(
        "heart.rate.zone", hr.getJSONObject("targetType").getString("workoutTargetTypeKey"));
    assertEquals(3, hr.getInt("zoneNumber"));

    JSONObject cooldown = steps.getJSONObject(3);
    assertEquals(3, cooldown.getJSONObject("endCondition").getInt("conditionTypeId"));
    assertEquals(1600.0, cooldown.getDouble("endConditionValue"), 0.001);
  }

  @Test
  public void parsesGarminConnectCliFixture() throws Exception {
    String fixture =
        "{"
            + "\"workoutName\": \"Intervals 5x(3'run + 1'walk)\","
            + "\"sportType\": {\"sportTypeId\": 1, \"sportTypeKey\": \"running\"},"
            + "\"workoutSegments\": [{"
            + "\"segmentOrder\": 1,"
            + "\"sportType\": {\"sportTypeId\": 1, \"sportTypeKey\": \"running\"},"
            + "\"workoutSteps\": ["
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeId\": 1, \"stepTypeKey\": \"warmup\"},"
            + " \"endCondition\": {\"conditionTypeId\": 2, \"conditionTypeKey\": \"time\"}, \"endConditionValue\": 300.0,"
            + " \"targetType\": {\"workoutTargetTypeId\": 1, \"workoutTargetTypeKey\": \"no.target\"}},"
            + "{\"type\": \"RepeatGroupDTO\", \"stepType\": {\"stepTypeId\": 6, \"stepTypeKey\": \"repeat\"},"
            + " \"numberOfIterations\": 5,"
            + " \"endCondition\": {\"conditionTypeId\": 7, \"conditionTypeKey\": \"iterations\"}, \"endConditionValue\": 5.0,"
            + " \"workoutSteps\": ["
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeId\": 3, \"stepTypeKey\": \"interval\"},"
            + " \"endCondition\": {\"conditionTypeId\": 2, \"conditionTypeKey\": \"time\"}, \"endConditionValue\": 180.0,"
            + " \"targetType\": {\"workoutTargetTypeId\": 4, \"workoutTargetTypeKey\": \"heart.rate.zone\"}, \"zoneNumber\": 2},"
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeId\": 4, \"stepTypeKey\": \"recovery\"},"
            + " \"endCondition\": {\"conditionTypeId\": 2, \"conditionTypeKey\": \"time\"}, \"endConditionValue\": 60.0,"
            + " \"targetType\": {\"workoutTargetTypeId\": 1, \"workoutTargetTypeKey\": \"no.target\"}}"
            + "]},"
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeId\": 2, \"stepTypeKey\": \"cooldown\"},"
            + " \"endCondition\": {\"conditionTypeId\": 2, \"conditionTypeKey\": \"time\"}, \"endConditionValue\": 300.0,"
            + " \"targetType\": {\"workoutTargetTypeId\": 1, \"workoutTargetTypeKey\": \"no.target\"}}"
            + "]}]}";

    Workout w = WorkoutSerializer.readJSON(new StringReader(fixture), configuredZones());
    assertEquals(3, w.steps.size());
    assertEquals(Intensity.WARMUP, w.steps.get(0).intensity);
    assertEquals(Dimension.TIME, w.steps.get(0).durationType);
    assertEquals(300.0, w.steps.get(0).durationValue, 0.001);

    RepeatStep repeat = (RepeatStep) w.steps.get(1);
    assertEquals(5, repeat.repeatCount);
    Step interval = repeat.steps.get(0);
    assertEquals(Dimension.HR, interval.targetType);
    assertEquals(120.0, interval.targetValue.minValue, 0.001);
    assertEquals(140.0, interval.targetValue.maxValue, 0.001);

    assertEquals(Intensity.COOLDOWN, w.steps.get(2).intensity);
    assertEquals(300.0, w.steps.get(2).durationValue, 0.001);
  }

  @Test
  public void dropsHrTargetWhenZonesUnconfigured() throws Exception {
    String fixture =
        "{"
            + "\"workoutSegments\": [{\"workoutSteps\": ["
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeKey\": \"interval\"},"
            + " \"endCondition\": {\"conditionTypeKey\": \"time\"}, \"endConditionValue\": 60.0,"
            + " \"targetType\": {\"workoutTargetTypeKey\": \"heart.rate.zone\"}, \"zoneNumber\": 2}"
            + "]}]}";

    Workout w = WorkoutSerializer.readJSON(new StringReader(fixture), null);
    assertEquals(1, w.steps.size());
    assertNull(w.steps.get(0).targetType);
    assertNull(w.steps.get(0).targetValue);
  }

  @Test
  public void rejectsLegacyClassicFormat() throws Exception {
    String classic =
        "{"
            + "\"com.garmin.connect.workout.json.UserWorkoutJson\": {"
            + "\"workoutSteps\": []"
            + "}}";
    try {
      WorkoutSerializer.readJSON(new StringReader(classic));
      fail("expected UnsupportedFormatException");
    } catch (WorkoutSerializer.UnsupportedFormatException expected) {
      assertTrue(expected.getMessage().contains("workoutSegments"));
    }
  }

  @Test
  public void rejectsArbitraryJson() throws Exception {
    try {
      WorkoutSerializer.readJSON(new StringReader("{\"foo\": 1}"));
      fail("expected UnsupportedFormatException");
    } catch (WorkoutSerializer.UnsupportedFormatException expected) {
      assertTrue(expected.getMessage().contains("workoutSegments"));
    }
  }

  @Test
  public void ignoresRepeatGroupWithoutEndCondition() throws Exception {
    String fixture =
        "{"
            + "\"workoutSegments\": [{\"workoutSteps\": ["
            + "{\"type\": \"RepeatGroupDTO\", \"numberOfIterations\": 3, \"workoutSteps\": ["
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeKey\": \"interval\"},"
            + " \"endCondition\": {\"conditionTypeKey\": \"distance\"}, \"endConditionValue\": 400.0}"
            + "]}]}]}";

    Workout w = WorkoutSerializer.readJSON(new StringReader(fixture));
    assertEquals(1, w.steps.size());
    RepeatStep repeat = (RepeatStep) w.steps.get(0);
    assertEquals(3, repeat.repeatCount);
    assertEquals(1, repeat.steps.size());
    assertEquals(Dimension.DISTANCE, repeat.steps.get(0).durationType);
    assertEquals(400.0, repeat.steps.get(0).durationValue, 0.001);
  }

  @Test
  public void parsesRealGarminPaceOrdering() throws Exception {
    // Real Garmin Connect exports write pace.zone as targetValueOne = FAST speed
    // (higher m/s, faster pace) and targetValueTwo = SLOW speed (lower m/s).
    // 4:00/km = 4.1667 m/s (fast), 4:20/km = 3.8462 m/s (slow).
    String fixture =
        "{"
            + "\"workoutSegments\": [{\"workoutSteps\": ["
            + "{\"type\": \"ExecutableStepDTO\", \"stepType\": {\"stepTypeKey\": \"interval\"},"
            + " \"endCondition\": {\"conditionTypeKey\": \"time\"}, \"endConditionValue\": 300.0,"
            + " \"targetType\": {\"workoutTargetTypeKey\": \"pace.zone\"},"
            + " \"targetValueOne\": 4.1666667, \"targetValueTwo\": 3.8461538}"
            + "]}]}";

    Workout w = WorkoutSerializer.readJSON(new StringReader(fixture));
    Step s = w.steps.get(0);
    assertEquals(Dimension.PACE, s.targetType);
    assertEquals(0.24, s.targetValue.minValue, 0.001);
    assertEquals(0.26, s.targetValue.maxValue, 0.001);
  }

  @Test
  public void parsesRealGarminExportFile() throws Exception {
    File f = new File("test/java/org/runnerup/workout/GarminClassicInterval.json");
    assertTrue("missing fixture " + f.getPath(), f.exists());
    Workout w = WorkoutSerializer.readJSON(new FileReader(f), configuredZones());
    assertEquals(3, w.steps.size());

    Step warmup = w.steps.get(0);
    assertEquals(Intensity.WARMUP, warmup.intensity);
    assertEquals(Dimension.TIME, warmup.durationType);
    assertEquals(600.0, warmup.durationValue, 0.001);
    assertNull(warmup.targetType);

    RepeatStep repeat = (RepeatStep) w.steps.get(1);
    assertEquals(8, repeat.repeatCount);
    assertEquals(2, repeat.steps.size());

    Step interval = repeat.steps.get(0);
    assertEquals(Dimension.DISTANCE, interval.durationType);
    assertEquals(400.0, interval.durationValue, 0.001);
    assertEquals(Dimension.PACE, interval.targetType);
    assertEquals(0.24, interval.targetValue.minValue, 0.001);
    assertEquals(0.26, interval.targetValue.maxValue, 0.001);

    Step recovery = repeat.steps.get(1);
    assertEquals(Dimension.TIME, recovery.durationType);
    assertEquals(90.0, recovery.durationValue, 0.001);

    Step cooldown = w.steps.get(2);
    assertEquals(Intensity.COOLDOWN, cooldown.intensity);
    assertEquals(300.0, cooldown.durationValue, 0.001);
    assertEquals(Dimension.HR, cooldown.targetType);
    assertEquals(120.0, cooldown.targetValue.minValue, 0.001);
    assertEquals(140.0, cooldown.targetValue.maxValue, 0.001);
  }

  @Test
  public void parsesBundledAssetWorkouts() throws Exception {
    String[] names = {"4x4", "8-6-4-2", "MalinEwerlov", "Super1000"};
    for (String name : names) {
      File f = new File("assets/bundled/app_workouts/" + name + ".json");
      assertTrue("missing bundled asset " + f.getPath(), f.exists());
      Workout w = WorkoutSerializer.readJSON(new FileReader(f), configuredZones());
      assertTrue("no steps parsed from " + name, w.steps.size() > 0);
    }
  }
}
