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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import java.util.function.Consumer
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.db.Statistics.BucketPeriod
import org.runnerup.db.Statistics.Metric
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

object ProgressScreenHost {
  fun attach(
    composeView: ComposeView,
    viewModel: ProgressViewModel,
    onActivityClick: Consumer<Long>,
    onDayClick: Consumer<Int>,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      RunnerUpTheme {
        ProgressContent(
          state = state,
          onMetricChange = { viewModel.setMetric(it) },
          onPeriodChange = { viewModel.setPeriod(it) },
          onBarModeToggle = { viewModel.toggleBarMode() },
          onMonthChange = { viewModel.changeMonth(it) },
          onActivityClick = { onActivityClick.accept(it) },
          onDayClick = { onDayClick.accept(it) },
        )
      }
    }
  }
}

private sealed interface RecordRow {
  data class Header(val sport: Int) : RecordRow

  data class Badges(val records: List<ProgressRecord>) : RecordRow
}

@Composable
private fun ProgressContent(
  state: ProgressUiState,
  onMetricChange: (Metric) -> Unit,
  onPeriodChange: (BucketPeriod) -> Unit,
  onBarModeToggle: () -> Unit,
  onMonthChange: (Int) -> Unit,
  onActivityClick: (Long) -> Unit,
  onDayClick: (Int) -> Unit,
) {
  val context = LocalContext.current
  val formatter = remember(context) { Formatter(context) }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    TotalCards(totals = state.totals, metric = state.metric, formatter = formatter)
    MetricToggle(
      selected = state.metric,
      onSelect = onMetricChange,
      modifier = Modifier.padding(top = 12.dp),
    )
    ChartHeader(
      titleRes = state.chartTitleRes,
      barMode = state.barMode,
      onToggle = onBarModeToggle,
      modifier = Modifier.padding(top = 16.dp),
    )
    Chart(state = state, formatter = formatter, modifier = Modifier.padding(top = 8.dp))
    PeriodToggle(
      selected = state.period,
      onSelect = onPeriodChange,
      modifier = Modifier.padding(top = 16.dp),
    )
    CalendarSection(
      state = state,
      formatter = formatter,
      onMonthChange = onMonthChange,
      onDayClick = onDayClick,
      modifier = Modifier.padding(top = 32.dp),
    )
    RecordsSection(
      records = state.records,
      sport = state.sport,
      onActivityClick = onActivityClick,
      modifier = Modifier.padding(top = 16.dp),
    )
  }
}

@Composable
private fun TotalCards(totals: List<Double>, metric: Metric, formatter: Formatter) {
  Row(modifier = Modifier.fillMaxWidth()) {
    TotalCard(
      labelRes = CommonR.string.Statistics_this_week,
      value = formatMetricValue(totals.getOrElse(0) { 0.0 }, metric, formatter),
      modifier = Modifier.weight(1f).padding(end = 8.dp),
    )
    TotalCard(
      labelRes = CommonR.string.Statistics_this_month,
      value = formatMetricValue(totals.getOrElse(1) { 0.0 }, metric, formatter),
      modifier = Modifier.weight(1f).padding(end = 8.dp),
    )
    TotalCard(
      labelRes = CommonR.string.Statistics_this_year,
      value = formatMetricValue(totals.getOrElse(2) { 0.0 }, metric, formatter),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun TotalCard(labelRes: Int, value: String, modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = caps(stringResource(labelRes)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun MetricToggle(
  selected: Metric,
  onSelect: (Metric) -> Unit,
  modifier: Modifier = Modifier,
) {
  val options =
    listOf(
      Metric.DISTANCE to CommonR.string.Statistics_distance,
      Metric.TIME to CommonR.string.Statistics_time,
      Metric.ELEVATION_GAIN to CommonR.string.Statistics_elevation,
    )
  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (metric, labelRes) ->
      SegmentedButton(
        selected = metric == selected,
        onClick = { onSelect(metric) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
        colors = toggleColors(),
        label = { Text(text = stringResource(labelRes), maxLines = 1) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun PeriodToggle(
  selected: BucketPeriod,
  onSelect: (BucketPeriod) -> Unit,
  modifier: Modifier = Modifier,
) {
  val options =
    listOf(
      BucketPeriod.DAY to CommonR.string.Statistics_day,
      BucketPeriod.WEEK to CommonR.string.Statistics_week,
      BucketPeriod.MONTH to CommonR.string.Statistics_month,
      BucketPeriod.YEAR to CommonR.string.Statistics_year,
    )
  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (bucket, labelRes) ->
      SegmentedButton(
        selected = bucket == selected,
        onClick = { onSelect(bucket) },
        shape =
          SegmentedButtonDefaults.itemShape(
            index = index,
            count = options.size,
            baseShape = RoundedCornerShape(8.dp),
          ),
        colors = toggleColors(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        label = { Text(text = stringResource(labelRes), maxLines = 1) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun toggleColors() =
  SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    activeBorderColor = MaterialTheme.colorScheme.secondaryContainer,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outline,
  )

@Composable
private fun ChartHeader(
  titleRes: Int,
  barMode: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
    Text(
      text = stringResource(titleRes),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
      Icon(
        painter =
          painterResource(if (barMode) R.drawable.ic_chart_line else R.drawable.ic_chart_bar),
        contentDescription =
          stringResource(
            if (barMode) CommonR.string.Statistics_switch_to_line
            else CommonR.string.Statistics_switch_to_bars
          ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun Chart(state: ProgressUiState, formatter: Formatter, modifier: Modifier = Modifier) {
  if (state.buckets.all { it <= 0.0 }) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier.fillMaxWidth().height(240.dp),
    ) {
      Text(
        text = stringResource(CommonR.string.Statistics_no_activities),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }
  val labelFormatter =
    remember(state.metric, formatter) {
      DistanceChartView.LabelFormatter { value ->
        formatMetricValue(value, state.metric, formatter)
      }
    }
  AndroidView(
    factory = { context -> DistanceChartView(context) },
    update = { chart ->
      chart.setLabelFormatter(labelFormatter)
      chart.setBarMode(state.barMode)
      chart.setData(state.buckets.toDoubleArray(), state.xLabels.toTypedArray())
    },
    modifier = modifier.fillMaxWidth().height(240.dp),
  )
}

@Composable
private fun CalendarSection(
  state: ProgressUiState,
  formatter: Formatter,
  onMonthChange: (Int) -> Unit,
  onDayClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.calendar_section_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
      IconButton(onClick = { onMonthChange(-1) }, modifier = Modifier.size(40.dp)) {
        Icon(
          painter = painterResource(R.drawable.ic_calendar_prev),
          contentDescription = stringResource(R.string.calendar_prev),
        )
      }
      Text(
        text = state.monthLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { onMonthChange(1) }, modifier = Modifier.size(40.dp)) {
        Icon(
          painter = painterResource(R.drawable.ic_calendar_next),
          contentDescription = stringResource(R.string.calendar_next),
        )
      }
    }
    val dayLabelFormatter =
      remember(formatter) {
        CalendarHeatmapView.DayLabelFormatter { meters -> formatter.getDistanceDisplay(meters) }
      }
    AndroidView(
      factory = { context -> CalendarHeatmapView(context) },
      update = { heatmap ->
        heatmap.setDayLabelFormatter(dayLabelFormatter)
        heatmap.setOnDayTapListener { dayOfMonth -> onDayClick(dayOfMonth) }
        heatmap.setData(state.calendarCells.toTypedArray())
      },
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
  }
}

@Composable
private fun RecordsSection(
  records: List<ProgressRecord>,
  sport: Int?,
  onActivityClick: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (records.isEmpty()) {
    return
  }
  val context = LocalContext.current
  val formatter = remember(context) { Formatter(context) }
  val resources = LocalResources.current
  val rows = remember(records, sport, resources) { recordRows(records, sport) }
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.records_section_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
      rows.forEach { row ->
        when (row) {
          is RecordRow.Header ->
            Text(
              text = Sport.textOf(resources, row.sport),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp),
            )
          is RecordRow.Badges ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
              row.records.forEachIndexed { index, record ->
                RecordBadge(
                  record = record,
                  formatter = formatter,
                  onClick = { onActivityClick(record.activityId) },
                  modifier =
                    Modifier.weight(1f)
                      .padding(
                        start = if (index % 2 == 0) 0.dp else 4.dp,
                        end = if (index % 2 == 0) 4.dp else 0.dp,
                      ),
                )
              }
            }
        }
      }
    }
  }
}

@Composable
private fun RecordBadge(
  record: ProgressRecord,
  formatter: Formatter,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val ringColor =
    remember(context, record.ringColorRes) {
      Color(ContextCompat.getColor(context, record.ringColorRes))
    }
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
      Text(
        text = caps(stringResource(record.recordLabelRes)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.padding(top = 6.dp)
            .size(58.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(4.dp, ringColor, CircleShape),
      ) {
        Text(
          text =
            if (record.medalShowsDistance) {
              formatter.formatDistance(
                Formatter.Format.TXT_SHORT,
                Math.round(record.distance),
              )
            } else {
              formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, record.time)
            },
          fontSize = if (record.medalShowsDistance) 11.sp else 13.sp,
          fontWeight = FontWeight.Bold,
          color = ringColor,
        )
      }
      Text(
        text = badgeMeta(record, formatter),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
      )
      Box(
        modifier =
          Modifier.padding(top = 8.dp)
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(ringColor, lighten(ringColor), ringColor)))
      )
    }
  }
}

private fun recordRows(records: List<ProgressRecord>, sport: Int?): List<RecordRow> {
  val rows = ArrayList<RecordRow>()
  var headerSport = -1
  var pending = ArrayList<ProgressRecord>()
  fun flush() {
    if (pending.isNotEmpty()) {
      rows.add(RecordRow.Badges(pending))
      pending = ArrayList()
    }
  }
  records.forEach { record ->
    if (sport == null && record.sport != headerSport) {
      flush()
      rows.add(RecordRow.Header(record.sport))
      headerSport = record.sport
    }
    pending.add(record)
    if (pending.size == 2) {
      flush()
    }
  }
  flush()
  return rows
}

private fun badgeMeta(record: ProgressRecord, formatter: Formatter): String {
  val builder = StringBuilder(formatter.formatDate(record.startTime))
  if (record.medalShowsDistance && record.time > 0) {
    builder
      .append(" · ")
      .append(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, record.time))
  } else if (!record.medalShowsDistance && record.time > 0 && record.distance > 0) {
    builder
      .append(" · ")
      .append(
        formatter.formatVelocityByPreferredUnit(
          Formatter.Format.TXT_LONG,
          record.distance / record.time.toDouble(),
        )
      )
  }
  return builder.toString()
}

private fun formatMetricValue(value: Double, metric: Metric, formatter: Formatter): String =
  when (metric) {
    Metric.TIME ->
      formatter.formatElapsedTime(Formatter.Format.TXT_LONG, Math.round(value / 60.0) * 60)
    Metric.ELEVATION_GAIN ->
      formatter.formatElevation(Formatter.Format.TXT_LONG, Math.round(value).toDouble())
    else -> formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value))
  }

private fun caps(text: String): String = text.uppercase(Locale.getDefault())

private fun lighten(color: Color): Color =
  Color(
    red = (color.red + 1f) / 2f,
    green = (color.green + 1f) / 2f,
    blue = (color.blue + 1f) / 2f,
  )
