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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.db.Statistics

private const val DAYS_PER_WEEK = Statistics.CALENDAR_COLUMNS
private const val WEEKS = Statistics.CALENDAR_ROWS
private const val INTENSITY_STEPS = 4
private const val CELL_COUNT = DAYS_PER_WEEK * WEEKS
private val ALPHA_STEPS = floatArrayOf(42f, 92f, 150f, 214f)
private val WEEKDAY_LABELS = charArrayOf('M', 'T', 'W', 'T', 'F', 'S', 'S')
private val HEADER_HEIGHT = 20.dp
private val CELL_HEIGHT = 44.dp
private val CORNER_RADIUS = 6.dp
private val GAP = 3.dp
private val LABEL_INSET = 4.dp
private val DAY_BASELINE = 16.dp
private val DISTANCE_BASELINE = 7.dp
private val CALENDAR_HEIGHT = HEADER_HEIGHT + CELL_HEIGHT * WEEKS

@Composable
fun ProgressCalendar(
  cells: List<Statistics.CalendarDay>,
  distanceFormatter: (Double) -> String,
  onDayClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val fillColor = colorScheme.tertiary
  val onSurfaceColor = colorScheme.onSurface
  val onSurfaceVariantColor = colorScheme.onSurfaceVariant
  val weekdayStyle =
    remember(onSurfaceVariantColor) {
      TextStyle(fontSize = 11.sp, color = onSurfaceVariantColor)
    }
  val dayStyle =
    remember(onSurfaceColor) {
      TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor)
    }
  val distanceStyle =
    remember(onSurfaceVariantColor) {
      TextStyle(fontSize = 9.sp, color = onSurfaceVariantColor)
    }
  val textMeasurer = rememberTextMeasurer()
  val monthMax = remember(cells) { cells.maxOfOrNull { it.distance } ?: 0.0 }
  val visibleCells = remember(cells) { cells.take(CELL_COUNT) }
  Canvas(
    modifier =
      modifier.fillMaxWidth().height(CALENDAR_HEIGHT).pointerInput(visibleCells, onDayClick) {
        detectTapGestures { offset ->
          val headerHeight = HEADER_HEIGHT.toPx()
          if (offset.y < headerHeight) {
            return@detectTapGestures
          }
          val cellWidth = size.width / DAYS_PER_WEEK.toFloat()
          val col = (offset.x / cellWidth).toInt()
          val row = ((offset.y - headerHeight) / CELL_HEIGHT.toPx()).toInt()
          val cell = visibleCells.getOrNull(row * DAYS_PER_WEEK + col)
          if (cell != null && cell.day != 0) {
            onDayClick(cell.day)
          }
        }
      }
  ) {
    val cellWidth = size.width / DAYS_PER_WEEK.toFloat()
    val cellHeight = CELL_HEIGHT.toPx()
    val headerHeight = HEADER_HEIGHT.toPx()
    val gap = GAP.toPx()
    val radius = CORNER_RADIUS.toPx()
    val inactiveDayColor = onSurfaceColor.copy(alpha = 96f / 255f)
    val emptyCellColor = onSurfaceVariantColor.copy(alpha = 26f / 255f)

    for (col in 0 until DAYS_PER_WEEK) {
      drawCenteredText(
        measurer = textMeasurer,
        text = WEEKDAY_LABELS[col].toString(),
        style = weekdayStyle,
        baselineY = headerHeight - LABEL_INSET.toPx(),
        centerX = cellWidth * col + cellWidth / 2f,
      )
    }

    for (i in visibleCells.indices) {
      val cell = visibleCells[i]
      if (cell.day == 0) {
        continue
      }
      val col = i % DAYS_PER_WEEK
      val row = i / DAYS_PER_WEEK
      val left = col * cellWidth + gap
      val right = (col + 1) * cellWidth - gap
      val top = headerHeight + row * cellHeight + gap
      val bottom = headerHeight + (row + 1) * cellHeight - gap
      val centerX = (left + right) / 2f
      val active = cell.distance > 0

      val cellFill =
        if (active) {
          val bucket = Statistics.distanceBucket(cell.distance, monthMax, INTENSITY_STEPS)
          fillColor.copy(alpha = ALPHA_STEPS[bucket - 1] / 255f)
        } else {
          emptyCellColor
        }
      drawRoundRect(
        color = cellFill,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(radius, radius),
      )

      drawCenteredText(
        measurer = textMeasurer,
        text = cell.day.toString(),
        style = if (active) dayStyle else dayStyle.copy(color = inactiveDayColor),
        baselineY = top + DAY_BASELINE.toPx(),
        centerX = centerX,
      )
      if (active) {
        drawCenteredText(
          measurer = textMeasurer,
          text = distanceFormatter(cell.distance),
          style = distanceStyle,
          baselineY = bottom - DISTANCE_BASELINE.toPx(),
          centerX = centerX,
        )
      }
    }
  }
}

private fun DrawScope.drawCenteredText(
  measurer: TextMeasurer,
  text: String,
  style: TextStyle,
  baselineY: Float,
  centerX: Float,
) {
  drawBaselineText(
    measurer = measurer,
    text = text,
    style = style,
    baselineY = baselineY,
    leftX = { width -> centerX - width / 2f },
  )
}
