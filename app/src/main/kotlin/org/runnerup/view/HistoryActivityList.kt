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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.function.Consumer
import org.runnerup.R
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

object HistoryScreenHost {
  fun attach(
    composeView: ComposeView,
    viewModel: HistoryViewModel,
    onActivityClick: Consumer<Long>,
    onStateChanged: Consumer<HistoryUiState>,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      LaunchedEffect(state) { onStateChanged.accept(state) }
      RunnerUpTheme {
        HistoryActivityList(state = state, onActivityClick = { onActivityClick.accept(it) })
      }
    }
  }
}

@Composable
private fun HistoryActivityList(state: HistoryUiState, onActivityClick: (Long) -> Unit) {
  val context = LocalContext.current
  val formatter = remember(context) { Formatter(context) }
  LazyColumn(
    contentPadding = PaddingValues(bottom = 88.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    items(state.rows, key = { it.id }) { row ->
      when (row) {
        is HistorySection -> HistorySectionHeader(monthText = row.monthText)
        is HistoryActivityRow ->
          HistoryActivityCard(
            row = row,
            isRecordHolder = row.id in state.recordHolderIds,
            formatter = formatter,
            onClick = { onActivityClick(row.id) },
          )
      }
    }
  }
}

@Composable
private fun HistorySectionHeader(monthText: String) {
  Text(
    text = monthText,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier =
      Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp),
  )
}

@Composable
private fun HistoryActivityCard(
  row: HistoryActivityRow,
  isRecordHolder: Boolean,
  formatter: Formatter,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val sportColor =
    remember(context, row.sport) {
      Color(ContextCompat.getColor(context, Sport.colorOf(row.sport)))
    }
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
      ) {
        Icon(
          painter = painterResource(Sport.drawableColored16Of(row.sport)),
          contentDescription = null,
          tint = sportColor,
          modifier = Modifier.size(16.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isRecordHolder) {
            Icon(
              painter = painterResource(R.drawable.ic_trophy_20dp),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(20.dp).padding(end = 6.dp),
            )
          }
          Text(
            text = distanceText(row, formatter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = sportColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Icon(
            painter = painterResource(R.drawable.ic_chevron_right_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
          )
        }
        Text(
          text = formatter.formatDateTime(row.startTime),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp),
        )
        Row(modifier = Modifier.padding(top = 2.dp)) {
          MetricText(
            text =
              row.time?.let { formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, it) } ?: "",
            modifier = Modifier.weight(3f),
          )
          MetricText(
            text = paceText(row, formatter),
            modifier = Modifier.weight(4f).padding(start = 8.dp),
          )
          MetricText(
            text =
              row.avgHr?.let {
                formatter.formatHeartRate(Formatter.Format.TXT_SHORT, it.toDouble())
              } ?: "",
            icon = if (row.avgHr != null) R.drawable.ic_history_hr else null,
            modifier = Modifier.weight(2f).padding(start = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun MetricText(text: String, modifier: Modifier = Modifier, icon: Int? = null) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
    if (icon != null) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp).padding(end = 4.dp),
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun distanceText(row: HistoryActivityRow, formatter: Formatter): String =
  row.distance?.let { formatter.formatDistance(Formatter.Format.TXT_SHORT, it.toLong()) } ?: ""

private fun paceText(row: HistoryActivityRow, formatter: Formatter): String {
  val distance = row.distance
  val time = row.time
  return if (distance != null && time != null && time != 0L) {
    formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, distance / time)
  } else {
    ""
  }
}
