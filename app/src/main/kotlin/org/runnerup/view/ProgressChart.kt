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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private const val MAX_Y_LABELS = 4
private const val X_LABEL_SKIP_THRESHOLD = 8
private const val BAR_WIDTH_FRACTION = 0.6f

private val LEFT_PAD = 48.dp
private val RIGHT_PAD = 8.dp
private val TOP_PAD = 16.dp
private val BOTTOM_PAD = 20.dp
private val LABEL_INSET = 4.dp
private val TICK_LENGTH = 4.dp
private val X_LABEL_BASELINE = 14.dp
private val GRID_STROKE = 1.dp
private val LINE_STROKE = 2.5.dp
private val DOT_RADIUS = 4.dp
private val DOT_STROKE = 2.dp
private val BAR_CORNER_RADIUS = 2.dp

object ChartMath {
  @JvmStatic
  fun niceMax(value: Double): Double {
    if (value <= 0) {
      return 1.0
    }
    val exp = floor(log10(value))
    val base = 10.0.pow(exp)
    val fraction = value / base
    val niceFraction =
      when {
        fraction <= 1 -> 1.0
        fraction <= 2 -> 2.0
        fraction <= 3 -> 3.0
        fraction <= 4 -> 4.0
        fraction <= 5 -> 5.0
        fraction <= 6 -> 6.0
        fraction <= 8 -> 8.0
        else -> 10.0
      }
    return niceFraction * base
  }

  @JvmStatic
  fun plotPoints(
    values: DoubleArray,
    maxValue: Double,
    chartLeft: Float,
    slot: Float,
    chartHeight: Float,
    chartBottom: Float,
  ): FloatArray {
    val points = FloatArray(values.size * 2)
    for (i in values.indices) {
      points[i * 2] = chartLeft + slot * i + slot / 2
      points[i * 2 + 1] = chartBottom - (values[i] / maxValue * chartHeight).toFloat()
    }
    return points
  }

  @JvmStatic
  fun plotBarRects(
    values: DoubleArray,
    maxValue: Double,
    chartLeft: Float,
    slot: Float,
    chartHeight: Float,
    chartBottom: Float,
  ): FloatArray {
    val rects = FloatArray(values.size * 4)
    val barWidth = slot * BAR_WIDTH_FRACTION
    for (i in values.indices) {
      val centerX = chartLeft + slot * i + slot / 2
      rects[i * 4] = centerX - barWidth / 2
      rects[i * 4 + 1] = chartBottom - (values[i] / maxValue * chartHeight).toFloat()
      rects[i * 4 + 2] = centerX + barWidth / 2
      rects[i * 4 + 3] = chartBottom
    }
    return rects
  }
}

@Composable
fun ProgressChart(
  values: List<Double>,
  xLabels: List<String>,
  barMode: Boolean,
  labelFormatter: (Double) -> String,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val lineColor = colorScheme.primary
  val labelColor = colorScheme.onSurfaceVariant
  val gridColor = colorScheme.outlineVariant
  val labelStyle = remember(labelColor) { TextStyle(fontSize = 11.sp, color = labelColor) }
  val textMeasurer = rememberTextMeasurer()
  val data = remember(values) { values.toDoubleArray() }
  Canvas(modifier = modifier) {
    val chartLeft = LEFT_PAD.toPx()
    val chartRight = size.width - RIGHT_PAD.toPx()
    val chartTop = TOP_PAD.toPx()
    val chartBottom = size.height - BOTTOM_PAD.toPx()
    val chartWidth = chartRight - chartLeft
    val chartHeight = chartBottom - chartTop
    if (chartWidth <= 0f || chartHeight <= 0f) {
      return@Canvas
    }
    val count = data.size
    val maxValue = ChartMath.niceMax(max(0.0, data.maxOrNull() ?: 0.0))

    for (i in 0..MAX_Y_LABELS) {
      val ratio = i / MAX_Y_LABELS.toFloat()
      val y = chartBottom - ratio * chartHeight
      drawLine(
        color = gridColor,
        start = Offset(chartLeft, y),
        end = Offset(chartRight, y),
        strokeWidth = GRID_STROKE.toPx(),
      )
      drawBaselineText(
        measurer = textMeasurer,
        text = labelFormatter(maxValue * ratio),
        style = labelStyle,
        baselineY = y - LABEL_INSET.toPx(),
        leftX = { LABEL_INSET.toPx() },
      )
    }

    if (count > 0) {
      val slot = chartWidth / count
      if (barMode) {
        val rects =
          ChartMath.plotBarRects(data, maxValue, chartLeft, slot, chartHeight, chartBottom)
        val corner = BAR_CORNER_RADIUS.toPx()
        for (i in 0 until count) {
          val left = rects[i * 4]
          val top = rects[i * 4 + 1]
          val bottom = rects[i * 4 + 3]
          if (bottom - top <= 0f) {
            continue
          }
          drawRoundRect(
            color = lineColor,
            topLeft = Offset(left, top),
            size = Size(rects[i * 4 + 2] - left, bottom - top),
            cornerRadius = CornerRadius(corner, corner),
          )
        }
      } else {
        val points = ChartMath.plotPoints(data, maxValue, chartLeft, slot, chartHeight, chartBottom)
        val linePath = Path()
        val fillPath = Path()
        fillPath.moveTo(points[0], chartBottom)
        for (i in 0 until count) {
          val x = points[i * 2]
          val y = points[i * 2 + 1]
          if (i == 0) {
            linePath.moveTo(x, y)
          } else {
            linePath.lineTo(x, y)
          }
          fillPath.lineTo(x, y)
        }
        fillPath.lineTo(points[(count - 1) * 2], chartBottom)
        fillPath.close()
        drawPath(
          path = fillPath,
          brush =
            Brush.verticalGradient(
              colors = listOf(lineColor.copy(alpha = 64f / 255f), lineColor.copy(alpha = 0f)),
              startY = chartTop,
              endY = chartBottom,
            ),
        )
        drawPath(
          path = linePath,
          color = lineColor,
          style =
            Stroke(width = LINE_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val dotRadius = DOT_RADIUS.toPx()
        for (i in 0 until count) {
          val center = Offset(points[i * 2], points[i * 2 + 1])
          drawCircle(color = Color.White, radius = dotRadius, center = center)
          drawCircle(
            color = lineColor,
            radius = dotRadius,
            center = center,
            style = Stroke(width = DOT_STROKE.toPx()),
          )
        }
      }
    }

    if (count > 0 && xLabels.size == count) {
      val skipOdd = count > X_LABEL_SKIP_THRESHOLD
      val slot = chartWidth / count
      for (i in 0 until count) {
        if (skipOdd && i % 2 == 1) {
          continue
        }
        val centerX = chartLeft + slot * i + slot / 2
        drawLine(
          color = gridColor,
          start = Offset(centerX, chartBottom),
          end = Offset(centerX, chartBottom + TICK_LENGTH.toPx()),
          strokeWidth = GRID_STROKE.toPx(),
        )
        drawBaselineText(
          measurer = textMeasurer,
          text = xLabels[i],
          style = labelStyle,
          baselineY = chartBottom + X_LABEL_BASELINE.toPx(),
          leftX = { width -> centerX - width / 2f },
        )
      }
    }
  }
}

internal fun DrawScope.drawBaselineText(
  measurer: TextMeasurer,
  text: String,
  style: TextStyle,
  baselineY: Float,
  leftX: (Int) -> Float,
) {
  val layout = measurer.measure(text, style)
  drawText(
    layout,
    topLeft = Offset(leftX(layout.size.width), baselineY - layout.firstBaseline),
  )
}
