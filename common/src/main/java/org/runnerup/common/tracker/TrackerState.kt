/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.common.tracker

import org.runnerup.common.util.Constants

/** Created by jonas on 12/12/14. */
enum class TrackerState(private val value: Int) {
  INIT(Constants.TRACKER_STATE.INIT), // initial state
  INITIALIZING(Constants.TRACKER_STATE.INITIALIZING), // initializing components
  INITIALIZED(Constants.TRACKER_STATE.INITIALIZED), // initialized
  CONNECTING(Constants.TRACKER_STATE.CONNECTING), // connecting to e.g GPS
  CONNECTED(Constants.TRACKER_STATE.CONNECTED), // connected, ready to start
  STARTED(Constants.TRACKER_STATE.STARTED), // Workout started
  PAUSED(Constants.TRACKER_STATE.PAUSED), // Workout paused
  STOPPED(Constants.TRACKER_STATE.STOPPED), // Workout stopped (i.e save screen open)
  CLEANUP(Constants.TRACKER_STATE.CLEANUP), // Cleaning up components
  ERROR(Constants.TRACKER_STATE.ERROR); // Components failed to initialize

  fun getValue(): Int = value

  companion object {
    @JvmStatic
    fun valueOf(value: Int): TrackerState? =
      when (value) {
        Constants.TRACKER_STATE.INIT -> INIT
        Constants.TRACKER_STATE.INITIALIZING -> INITIALIZING
        Constants.TRACKER_STATE.INITIALIZED -> INITIALIZED
        Constants.TRACKER_STATE.CONNECTING -> CONNECTING
        Constants.TRACKER_STATE.CONNECTED -> CONNECTED
        Constants.TRACKER_STATE.STARTED -> STARTED
        Constants.TRACKER_STATE.PAUSED -> PAUSED
        Constants.TRACKER_STATE.STOPPED -> STOPPED
        Constants.TRACKER_STATE.CLEANUP -> CLEANUP
        Constants.TRACKER_STATE.ERROR -> ERROR
        else -> null
      }

    @JvmStatic
    fun equals(oldVal: TrackerState?, newVal: TrackerState?): Boolean =
      if (oldVal != null && newVal != null) {
        oldVal.value == newVal.value
      } else {
        oldVal == null && newVal == null
      }
  }
}
