/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.workout;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.util.HRZones;
import org.runnerup.util.SafeParse;

public class WorkoutSerializer {

  private static final String TAG = "WorkoutSerializer";

  public static final String WORKOUTS_DIR = "workouts";

  /** Thrown when a workout file is not in the supported modern Garmin format. */
  public static class UnsupportedFormatException extends Exception {
    public UnsupportedFormatException(String message) {
      super(message);
    }
  }

  private static String getString(JSONObject obj, String key) {
    try {
      Object val = obj.get(key);
      return val == null ? null : val.toString();
    } catch (JSONException e) {
    }
    return null;
  }

  private static Integer getInt(JSONObject obj, String key) {
    try {
      return obj.getInt(key);
    } catch (JSONException e) {
    }
    return null;
  }

  private static JSONObject getObject(JSONObject obj, String key) {
    try {
      return obj.getJSONObject(key);
    } catch (JSONException e) {
    }
    return null;
  }

  public static Workout readJSON(Reader in) throws JSONException, UnsupportedFormatException {
    return readJSON(in, null);
  }

  public static Workout readJSON(Reader in, HRZones zones)
      throws JSONException, UnsupportedFormatException {
    JSONObject obj = SyncHelper.parse(in);
    Workout w = new Workout();
    JSONArray segments = obj.optJSONArray("workoutSegments");
    if (segments == null) {
      throw new UnsupportedFormatException("workout root missing workoutSegments");
    }
    for (int i = 0; i < segments.length(); i++) {
      JSONObject segment = segments.optJSONObject(i);
      if (segment == null) continue;
      JSONArray steps = segment.optJSONArray("workoutSteps");
      if (steps == null) continue;
      for (int j = 0; j < steps.length(); j++) {
        JSONObject step = steps.optJSONObject(j);
        if (step == null) continue;
        Step s = parseStep(step, zones);
        if (s != null) {
          w.steps.add(s);
        }
      }
    }
    return w;
  }

  private static Step parseStep(JSONObject obj, HRZones zones) throws JSONException {
    String type = getString(obj, "type");
    if ("RepeatGroupDTO".equalsIgnoreCase(type)) {
      return parseRepeatGroup(obj, zones);
    }
    Intensity intensity = getIntensity(obj);
    if (intensity == null) return null;
    DurationData duration = getDuration(obj);
    TargetData target = getTarget(obj, zones);
    switch (intensity) {
      case REPEAT:
        {
          RepeatStep rs = new RepeatStep();
          rs.repeatCount = (int) duration.val();
          return rs;
        }
      case RESTING:
        {
          boolean rest = duration.dim() != Dimension.DISTANCE;
          return Step.createRestStep(duration.dim(), duration.val(), !rest);
        }
      case ACTIVE:
      case WARMUP:
      case COOLDOWN:
      case RECOVERY:
        {
          Step s = new Step();
          s.intensity = intensity;
          s.durationType = duration.dim();
          s.durationValue = duration.val();
          s.targetType = target.dim();
          s.targetValue = target.range();
          return s;
        }
    }
    return null;
  }

  private static RepeatStep parseRepeatGroup(JSONObject obj, HRZones zones) throws JSONException {
    RepeatStep rs = new RepeatStep();
    rs.repeatCount = (int) SafeParse.parseDouble(getString(obj, "numberOfIterations"), 0);
    if (rs.repeatCount < 1) {
      rs.repeatCount = (int) getDuration(obj).val();
    }
    JSONArray steps = obj.optJSONArray("workoutSteps");
    if (steps != null) {
      for (int i = 0; i < steps.length(); i++) {
        JSONObject step = steps.optJSONObject(i);
        if (step == null) continue;
        Step s = parseStep(step, zones);
        if (s != null) {
          rs.steps.add(s);
        }
      }
    }
    return rs;
  }

  private static Intensity getIntensity(JSONObject obj) {
    JSONObject stepType = getObject(obj, "stepType");
    String stepTypeKey = stepType == null ? null : getString(stepType, "stepTypeKey");
    if (stepTypeKey == null) return null;
    if (stepTypeKey.equalsIgnoreCase("warmup")) return Intensity.WARMUP;
    else if (stepTypeKey.equalsIgnoreCase("repeat")) return Intensity.REPEAT;
    else if (stepTypeKey.equalsIgnoreCase("rest")) return Intensity.RESTING;
    else if (stepTypeKey.equalsIgnoreCase("recovery")) return Intensity.RECOVERY;
    else if (stepTypeKey.equalsIgnoreCase("cooldown")) return Intensity.COOLDOWN;

    // @TODO look at intensityTypeKey too??
    else if (stepTypeKey.equalsIgnoreCase("interval")) return Intensity.ACTIVE;
    else if (stepTypeKey.equalsIgnoreCase("other")) return Intensity.ACTIVE;
    return null;
  }

  private record DurationData(Dimension dim, double val) {}

  private static final DurationData DURATION_NONE = new DurationData(null, 0.0);

  private static DurationData getDuration(JSONObject obj) {
    JSONObject endCondition = getObject(obj, "endCondition");
    if (endCondition == null) return DURATION_NONE;
    String endConditionTypeKey = getString(endCondition, "conditionTypeKey");
    if (endConditionTypeKey == null) return DURATION_NONE;
    double val = SafeParse.parseDouble(getString(obj, "endConditionValue"), 0);
    if (endConditionTypeKey.equalsIgnoreCase("lap.button")) {
      return DURATION_NONE;
    } else if (endConditionTypeKey.equalsIgnoreCase("iterations")) {
      // Used only as fallback for repeat groups; no Dimension.
      return new DurationData(null, val);
    } else if (endConditionTypeKey.equalsIgnoreCase("distance")) {
      return new DurationData(Dimension.DISTANCE, val);
    } else if (endConditionTypeKey.equalsIgnoreCase("time")) {
      return new DurationData(Dimension.TIME, val);
    }
    // calories / heart.rate not implemented
    return DURATION_NONE;
  }

  private record TargetData(Dimension dim, Range range) {}

  private static final TargetData TARGET_NONE = new TargetData(null, null);

  private static TargetData getTarget(JSONObject obj, HRZones zones) {
    JSONObject targetTypeObj = getObject(obj, "targetType");
    if (targetTypeObj == null) return TARGET_NONE;
    String targetTypeKey = getString(targetTypeObj, "workoutTargetTypeKey");
    if (targetTypeKey == null || targetTypeKey.equalsIgnoreCase("no.target")) {
      return TARGET_NONE;
    }

    Dimension dim = null;
    Range range = null;
    if (targetTypeKey.equalsIgnoreCase("pace.zone")) {
      dim = Dimension.PACE;
      range =
          new Range(
              SafeParse.parseDouble(getString(obj, "targetValueOne"), 0),
              SafeParse.parseDouble(getString(obj, "targetValueTwo"), 0));
      invertToPace(range);
    } else if (targetTypeKey.equalsIgnoreCase("speed.zone")
        || targetTypeKey.equalsIgnoreCase("speed")) {
      dim = Dimension.SPEED;
      range =
          new Range(
              SafeParse.parseDouble(getString(obj, "targetValueOne"), 0),
              SafeParse.parseDouble(getString(obj, "targetValueTwo"), 0));
    } else if (targetTypeKey.equalsIgnoreCase("heart.rate.zone")) {
      Integer zone = getInt(obj, "zoneNumber");
      if (zones != null && zone != null && zone > 0) {
        int[] hr = zones.getHRValues(zone);
        if (hr != null) {
          dim = Dimension.HR;
          range = new Range(hr[0], hr[1]);
        }
      }
      if (dim == null) {
        Log.w(TAG, "dropping heart.rate.zone target, HR zones not configured: zone=" + zone);
        return TARGET_NONE;
      }
    } else {
      // cadence / power.zone etc not implemented
      return TARGET_NONE;
    }

    return new TargetData(dim, range);
  }

  /**
   * Convert a m/s target (from file) to internal pace (seconds per meter).
   *
   * <p>The Range constructor normalizes so minValue <= maxValue, i.e. minValue is the LOW speed
   * (m/s) and maxValue the HIGH speed (m/s) regardless of which endpoint the file wrote first.
   * Internal pace maps fast pace (low seconds/meter) to minValue and slow pace to maxValue.
   */
  private static void invertToPace(Range range) {
    double fast = range.maxValue == 0 ? 0 : 1.0 / range.maxValue;
    double slow = range.minValue == 0 ? 0 : 1.0 / range.minValue;
    range.minValue = fast;
    range.maxValue = slow;
  }

  public static File getFile(Context ctx, String name) {
    if (!name.endsWith(".json")) {
      name += ".json";
    }
    return new File(ctx.getDir(WORKOUTS_DIR, 0).getPath() + File.separator + name);
  }

  public static Workout readFile(Context ctx, String name)
      throws FileNotFoundException, JSONException, UnsupportedFormatException {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    File fin = getFile(ctx, name);
    Log.d("WorkoutSerializer", "reading " + fin.getPath());

    Workout w = readJSON(new FileReader(fin), new HRZones(ctx));
    w.sport =
        prefs.getInt(
            ctx.getResources().getString(R.string.pref_sport), Constants.DB.ACTIVITY.SPORT_RUNNING);
    w.setWorkoutType(Constants.WORKOUT_TYPE.ADVANCED);
    return w;
  }

  public static void writeFile(Context ctx, String name, Workout workout)
      throws IOException, JSONException {
    File fout = getFile(ctx, name);
    Log.v("WorkoutSerializer", "writing " + fout.getPath());
    JSONObject obj = createJSONWorkout(workout, new HRZones(ctx));
    if (name.endsWith(".json")) {
      name = name.substring(0, name.length() - ".json".length());
    }
    obj.put("workoutName", name);
    writeJSON(new FileWriter(fout), obj);
  }

  private static void writeJSON(Writer out, JSONObject obj) throws JSONException, IOException {
    out.write(obj.toString());
    out.flush();
  }

  static JSONObject createJSON(Workout workout) throws JSONException {
    return createJSONWorkout(workout, null);
  }

  static JSONObject createJSON(Workout workout, HRZones zones) throws JSONException {
    return createJSONWorkout(workout, zones);
  }

  private static JSONObject createJSONWorkout(Workout workout, HRZones zones) throws JSONException {
    JSONArray segmentSteps = new JSONArray();
    int[] order = {0};
    for (Step s : workout.getSteps()) {
      putStep(segmentSteps, s, zones, order);
    }

    JSONObject segment = new JSONObject();
    segment.put("segmentOrder", 1);
    segment.put("sportType", toSportType(workout.sport));
    segment.put("workoutSteps", segmentSteps);

    JSONArray segments = new JSONArray();
    segments.put(segment);

    JSONObject obj = new JSONObject();
    obj.put("workoutName", "");
    obj.put("sportType", toSportType(workout.sport));
    obj.put("workoutSegments", segments);
    return obj;
  }

  private static void putStep(JSONArray steps, Step step, HRZones zones, int[] order)
      throws JSONException {
    JSONObject obj = new JSONObject();
    if (step instanceof RepeatStep) {
      RepeatStep rs = (RepeatStep) step;
      obj.put("type", "RepeatGroupDTO");
      obj.put("stepOrder", ++order[0]);
      obj.put("stepType", stepType(Intensity.REPEAT));
      obj.put("numberOfIterations", rs.getRepeatCount());
      obj.put("smartRepeat", false);
      putDuration(obj, rs);
      JSONArray children = new JSONArray();
      for (Step s : rs.steps) {
        putStep(children, s, zones, order);
      }
      obj.put("workoutSteps", children);
    } else {
      obj.put("type", "ExecutableStepDTO");
      obj.put("stepOrder", ++order[0]);
      obj.put("stepType", stepType(step.getIntensity()));
      putDuration(obj, step);
      putTarget(obj, step, zones);
    }
    steps.put(obj);
  }

  private static JSONObject stepType(Intensity intensity) throws JSONException {
    int id;
    String key;
    switch (intensity) {
      case WARMUP:
        id = 1;
        key = "warmup";
        break;
      case COOLDOWN:
        id = 2;
        key = "cooldown";
        break;
      case ACTIVE:
        id = 3;
        key = "interval";
        break;
      case RECOVERY:
        id = 4;
        key = "recovery";
        break;
      case RESTING:
        id = 5;
        key = "rest";
        break;
      case REPEAT:
      default:
        id = 6;
        key = "repeat";
        break;
    }
    JSONObject obj = new JSONObject();
    obj.put("stepTypeId", id);
    obj.put("stepTypeKey", key);
    return obj;
  }

  private static JSONObject toSportType(int sport) throws JSONException {
    int id = 1;
    String key = "running";
    switch (sport) {
      case Constants.DB.ACTIVITY.SPORT_BIKING:
        id = 2;
        key = "cycling";
        break;
      case Constants.DB.ACTIVITY.SPORT_WALKING:
        id = 4;
        key = "walking";
        break;
      case Constants.DB.ACTIVITY.SPORT_ORIENTEERING:
        id = 7;
        key = "hiking";
        break;
      default:
        break;
    }
    JSONObject obj = new JSONObject();
    obj.put("sportTypeId", id);
    obj.put("sportTypeKey", key);
    return obj;
  }

  private static void putDuration(JSONObject obj, Step step) throws JSONException {
    if (step.getIntensity() == Intensity.REPEAT) {
      obj.put("endCondition", endCondition("iterations", 7));
      obj.put("endConditionValue", step.getRepeatCount());
      return;
    }
    Dimension durationType = step.getDurationType();
    if (durationType == null) {
      obj.put("endCondition", endCondition("lap.button", 1));
      return;
    }
    switch (durationType) {
      case TIME:
        obj.put("endCondition", endCondition("time", 2));
        obj.put("endConditionValue", step.getDurationValue());
        break;
      case DISTANCE:
        obj.put("endCondition", endCondition("distance", 3));
        obj.put("endConditionValue", step.getDurationValue());
        break;
      default:
        obj.put("endCondition", endCondition("lap.button", 1));
        break;
    }
  }

  private static JSONObject endCondition(String key, int id) throws JSONException {
    JSONObject obj = new JSONObject();
    obj.put("conditionTypeId", id);
    obj.put("conditionTypeKey", key);
    return obj;
  }

  private static void putTarget(JSONObject obj, Step step, HRZones zones) throws JSONException {
    if (step.getIntensity() == Intensity.REPEAT) return;

    Dimension targetType = step.getTargetType();
    Range targetValue = step.getTargetValue();
    if (targetType == null || targetValue == null) {
      obj.put("targetType", targetType("no.target", 1));
      return;
    }

    switch (targetType) {
      case SPEED:
        obj.put("targetType", targetType("speed.zone", 2));
        obj.put("targetValueOne", targetValue.minValue);
        obj.put("targetValueTwo", targetValue.maxValue);
        break;
      case PACE:
        obj.put("targetType", targetType("pace.zone", 6));
        // Real Garmin exports write the FAST speed (m/s, higher value) as
        // targetValueOne and the SLOW speed as targetValueTwo.
        obj.put("targetValueOne", targetValue.minValue != 0 ? 1.0 / targetValue.minValue : 0);
        obj.put("targetValueTwo", targetValue.maxValue != 0 ? 1.0 / targetValue.maxValue : 0);
        break;
      case HR:
        if (zones == null || !zones.isConfigured()) {
          Log.w(TAG, "dropping HR target, HR zones not configured");
          obj.put("targetType", targetType("no.target", 1));
          return;
        }
        int zone = zones.match(targetValue.minValue, targetValue.maxValue);
        obj.put("targetType", targetType("heart.rate.zone", 4));
        obj.put("zoneNumber", zone);
        break;
      default:
        obj.put("targetType", targetType("no.target", 1));
        break;
    }
  }

  private static JSONObject targetType(String key, int id) throws JSONException {
    JSONObject obj = new JSONObject();
    obj.put("workoutTargetTypeId", id);
    obj.put("workoutTargetTypeKey", key);
    return obj;
  }
}
