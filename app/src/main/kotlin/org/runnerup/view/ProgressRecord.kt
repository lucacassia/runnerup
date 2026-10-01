/*
 * Copyright (C) 2026
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
package org.runnerup.view

/**
 * Personal record badge, filled in by the record queries in [HistoryFragment] and rendered by the
 * Compose progress screen.
 */
class ProgressRecord {
  @JvmField var activityId: Long = 0
  @JvmField var time: Long = 0
  @JvmField var distance: Double = 0.0
  @JvmField var startTime: Long = 0
  @JvmField var sport: Int = 0
  @JvmField var recordLabelRes: Int = 0
  @JvmField var ringColorRes: Int = 0
  @JvmField var medalShowsDistance: Boolean = false
}
