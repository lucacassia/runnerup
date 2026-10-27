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
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.runnerup.common.R as CommonR
import org.runnerup.db.DBHelper
import org.runnerup.db.Statistics
import org.runnerup.db.Statistics.BucketPeriod
import org.runnerup.db.Statistics.Metric
import org.runnerup.util.Formatter

data class ProgressUiState(
  val sport: Int? = null,
  val loaded: Boolean = false,
  val metric: Metric = Metric.DISTANCE,
  val period: BucketPeriod = BucketPeriod.DAY,
  val barMode: Boolean = false,
  val totals: List<Double> = listOf(0.0, 0.0, 0.0),
  val buckets: List<Double> = emptyList(),
  val xLabels: List<String> = emptyList(),
  val chartTitleRes: Int = CommonR.string.Statistics_last_12_days,
  val monthLabel: String = "",
  val calendarCells: List<Statistics.CalendarDay> = emptyList(),
  val records: List<ProgressRecord> = emptyList(),
  val dateFormat: String = "",
)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
  private val _state = MutableStateFlow(ProgressUiState())
  val state: StateFlow<ProgressUiState> = _state.asStateFlow()

  private val db: SQLiteDatabase by lazy { DBHelper.getReadableDatabase(getApplication()) }
  private val formatter: Formatter by lazy { Formatter(getApplication()) }
  private val prefs by lazy {
    PreferenceManager.getDefaultSharedPreferences(getApplication())
  }

  private val metricPref by lazy {
    getApplication<Application>().getString(CommonR.string.pref_statistics_metric)
  }

  private val chartPref by lazy {
    getApplication<Application>().getString(CommonR.string.pref_statistics_chart)
  }

  @Volatile private var statRows: List<Statistics.ActivityRow> = emptyList()

  @Volatile private var byDay: Map<LocalDate, List<Statistics.ActivityRow>> = emptyMap()

  @Volatile private var holderIds: Set<Long> = emptySet()

  private var metric = Metric.DISTANCE
  private var period = BucketPeriod.DAY
  private var barMode = false
  private var shownMonth = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)

  init {
    val savedMetric = prefs.getInt(metricPref, 0)
    metric = Metric.values().getOrElse(savedMetric) { Metric.DISTANCE }
    barMode = prefs.getBoolean(chartPref, false)
    _state.update { it.copy(metric = metric, barMode = barMode) }
  }

  fun load(sport: Int?) {
    viewModelScope.launch(Dispatchers.IO) {
      val zone = ZoneId.systemDefault()
      val now = System.currentTimeMillis() / 1000
      val from = Statistics.bucketStarts(BucketPeriod.YEAR, now, zone)[0]
      val rows = Statistics.queryActivities(db, from, sport)
      Statistics.computeMissingElevation(db, rows)
      statRows = rows
      byDay = Statistics.groupActivitiesByDay(Statistics.queryActivities(db, 0L, sport), zone)
      refreshChart(sport)
      refreshCalendar()
    }
  }

  fun setRecords(records: List<ProgressRecord>, sport: Int?) {
    val visible = records.filter { sport == null || it.sport == sport }
    holderIds = visible.mapTo(LinkedHashSet()) { it.activityId }
    _state.update { it.copy(records = visible) }
  }

  fun setDateFormat(value: String) {
    _state.update { it.copy(dateFormat = value) }
  }

  fun recordHolderIds(): Set<Long> = holderIds

  fun setMetric(value: Metric) {
    if (value == metric) {
      return
    }
    metric = value
    prefs.edit { putInt(metricPref, value.ordinal) }
    refreshChart(_state.value.sport)
  }

  fun setPeriod(value: BucketPeriod) {
    if (value == period) {
      return
    }
    period = value
    refreshChart(_state.value.sport)
  }

  fun toggleBarMode() {
    barMode = !barMode
    prefs.edit { putBoolean(chartPref, barMode) }
    refreshChart(_state.value.sport)
  }

  fun changeMonth(delta: Int) {
    shownMonth = shownMonth.plusMonths(delta.toLong())
    refreshCalendar()
  }

  fun activitiesOn(dayOfMonth: Int): List<Statistics.ActivityRow> =
    byDay[shownMonth.withDayOfMonth(dayOfMonth)] ?: emptyList()

  private fun refreshChart(sport: Int?) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis() / 1000
    val buckets = Statistics.bucketize(statRows, metric, period, now, zone)
    val starts = Statistics.bucketStarts(period, now, zone)
    _state.update {
      it.copy(
        sport = sport,
        loaded = true,
        metric = metric,
        period = period,
        barMode = barMode,
        totals = Statistics.totals(statRows, metric, now, zone).toList(),
        buckets = buckets.toList(),
        xLabels = starts.map { start -> xLabel(start) },
        chartTitleRes = chartTitleRes(period),
      )
    }
  }

  private fun refreshCalendar() {
    val cells = Statistics.calendarDays(shownMonth, byDay)
    val label =
      formatter.formatMonth(Date.from(shownMonth.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    _state.update { it.copy(monthLabel = label, calendarCells = cells.toList()) }
  }

  private fun xLabel(startSeconds: Long): String {
    val date = Date(startSeconds * 1000)
    return when (period) {
      BucketPeriod.MONTH -> formatter.formatMonthShort(date)
      BucketPeriod.YEAR -> formatter.formatYear(date)
      else -> formatter.formatDayOfMonth(date)
    }
  }

  private fun chartTitleRes(period: BucketPeriod): Int =
    when (period) {
      BucketPeriod.WEEK -> CommonR.string.Statistics_last_12_weeks
      BucketPeriod.MONTH -> CommonR.string.Statistics_last_12_months
      BucketPeriod.YEAR -> CommonR.string.Statistics_last_12_years
      BucketPeriod.DAY -> CommonR.string.Statistics_last_12_days
    }
}
