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

/**
 * A rest step parked at the front of a race-ready workout. The tracker is paused while active; the
 * step completes as soon as a GPS fix is available, which resumes the run automatically.
 */
public class GpsWaitStep extends Step {

  @Override
  public void onInit(Workout s) {
    super.onInit(s);
    intensity = Intensity.RESTING;
  }

  @Override
  public void onStart(Scope what, Workout s) {
    if (what == Scope.STEP) {
      s.tracker.pause();
      for (Trigger t : triggers) {
        t.onStart(what, s);
      }
      return;
    }
    super.onStart(what, s);
  }

  @Override
  public boolean onTick(Workout s) {
    return s.tracker != null && s.tracker.isGpsFixed();
  }

  @Override
  public boolean isPauseStep() {
    return true;
  }
}
