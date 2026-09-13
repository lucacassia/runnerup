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

import android.content.SharedPreferences;
import android.content.res.Resources;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;

/** Race-ready start configuration: no pre-fix gate, in-run wait for GPS. */
public final class RaceReady {

  private RaceReady() {}

  public static boolean enabled(Resources res, SharedPreferences prefs) {
    return prefs.getBoolean(res.getString(R.string.pref_race_ready_start), true);
  }

  public static boolean gpsLockedCueEnabled(Resources res, SharedPreferences prefs) {
    return prefs.getBoolean(res.getString(R.string.pref_gps_locked_cue), true);
  }

  /**
   * @return true if the workout needs the pre-fix gate (GPS sport, race-ready on).
   */
  public static boolean gating(Workout w) {
    return !Sport.isWithoutGps(w.sport);
  }

  /**
   * @param sportWithoutGps workout uses manual distance
   * @param raceReady race-ready start enabled
   * @param trackerConnected tracker state is CONNECTED
   */
  public static boolean connected(
      boolean sportWithoutGps, boolean raceReady, boolean trackerConnected) {
    return sportWithoutGps || !raceReady || trackerConnected;
  }

  /** Wall-clock countdown seconds from settings (0 = off). */
  public static int countdownSeconds(Resources res, SharedPreferences prefs) {
    if (!prefs.getBoolean(res.getString(R.string.pref_countdown_active), false)) {
      return 0;
    }
    long val;
    String vals = prefs.getString(res.getString(R.string.pref_countdown_time), "0");
    try {
      val = Long.parseLong(vals);
    } catch (NumberFormatException e) {
      val = 0;
    }
    return (int) Math.max(0, val);
  }

  /** Basic workout built from settings, used when no saved workout is selected. */
  public static Workout defaultWorkout(Resources res, SharedPreferences prefs) {
    Workout w = new Workout();
    w.sport = prefs.getInt(res.getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
    w.steps.add(new Step());
    return w;
  }
}
