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

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.runnerup.common.util.Constants
import org.runnerup.db.DBHelper
import org.runnerup.db.entities.ActivityEntity
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

data class HistoryUiState(
  val rows: List<HistoryListRow> = emptyList(),
  val recordHolderIds: Set<Long> = emptySet(),
  val loaded: Boolean = false,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
  private val _state = MutableStateFlow(HistoryUiState())
  val state: StateFlow<HistoryUiState> = _state.asStateFlow()

  private val db: SQLiteDatabase by lazy { DBHelper.getReadableDatabase(getApplication()) }
  private val formatter: Formatter by lazy { Formatter(getApplication()) }

  fun load(sport: Int?) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(rows = queryRows(sport), loaded = true) }
    }
  }

  fun setRecordHolders(ids: Set<Long>) {
    _state.update { it.copy(recordHolderIds = ids) }
  }

  private fun queryRows(sport: Int?): List<HistoryListRow> {
    val selection =
      if (sport == null) {
        "deleted == 0"
      } else {
        "deleted == 0 AND " + Constants.DB.ACTIVITY.SPORT + " = ?"
      }
    val selectionArgs = if (sport == null) null else arrayOf(sport.toString())
    val rows = ArrayList<HistoryListRow>()
    db
      .query(
        Constants.DB.ACTIVITY.TABLE,
        arrayOf(
          "_id",
          Constants.DB.ACTIVITY.START_TIME,
          Constants.DB.ACTIVITY.DISTANCE,
          Constants.DB.ACTIVITY.TIME,
          Constants.DB.ACTIVITY.SPORT,
          Constants.DB.ACTIVITY.AVG_HR,
        ),
        selection,
        selectionArgs,
        null,
        null,
        Constants.DB.ACTIVITY.START_TIME + " desc",
      )
      .use { cursor ->
        var prevYear = Int.MIN_VALUE
        var prevMonth = Int.MIN_VALUE
        while (cursor.moveToNext()) {
          val ae = ActivityEntity(cursor)
          val startTime = ae.getStartTime() ?: 0L
          val date = Date(startTime * 1000)
          val cal = Calendar.getInstance()
          cal.time = date
          val year = cal.get(Calendar.YEAR)
          val month = cal.get(Calendar.MONTH)
          if (prevYear == Int.MIN_VALUE || prevYear != year || prevMonth != month) {
            rows.add(
              HistorySection(
                id = -(year * 100L + month),
                monthText = formatter.formatMonth(date),
              )
            )
            prevYear = year
            prevMonth = month
          }
          rows.add(
            HistoryActivityRow(
              id = ae.getId() ?: 0L,
              startTime = startTime,
              distance = ae.getDistance(),
              time = ae.getTime(),
              sport = ae.getSport()?.toInt() ?: Sport.OTHER.getDbValue(),
              avgHr = ae.getAvgHr()?.toInt(),
            )
          )
        }
      }
    return rows
  }
}
