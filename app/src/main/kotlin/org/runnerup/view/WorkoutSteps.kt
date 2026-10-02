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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.util.Formatter
import org.runnerup.workout.Dimension
import org.runnerup.workout.Intensity
import org.runnerup.workout.Range
import org.runnerup.workout.RepeatStep
import org.runnerup.workout.Step
import org.runnerup.workout.Workout

/**
 * One entry of the workout step list, with the steps of a repeat group expanded into it.
 *
 * The workout editor changes [Step] objects in place, so the row snapshots everything it renders
 * and two rows compare equal only when they would look the same.
 */
data class StepRow(
  val step: Step,
  val intensity: Intensity,
  val durationType: Dimension?,
  val durationValue: Double,
  val targetType: Dimension?,
  val targetValue: Range?,
  val repeatCount: Int,
  val children: List<StepRow>,
) {
  constructor(
    step: Step,
    children: List<StepRow>,
  ) : this(
    step = step,
    intensity = step.intensity,
    durationType = step.durationType,
    durationValue = step.durationValue,
    targetType = step.targetType,
    targetValue = step.targetValue,
    repeatCount = step.repeatCount,
    children = children,
  )
}

/** Java friendly callback for a tapped step. */
fun interface StepEdit {
  fun onEditStep(step: Step)
}

/** Java friendly callback for a tapped repeat group title. */
fun interface RepeatEdit {
  fun onEditRepeat(repeat: RepeatStep)
}

fun buildStepRows(workout: Workout): List<StepRow> = workout.stepRows(null)

private fun Workout.stepRows(parent: Step?): List<StepRow> =
  entriesAtLevel(parent).map { entry ->
    val step = entry.step
    StepRow(step, (step as? RepeatStep)?.let { repeat -> stepRows(repeat) } ?: emptyList())
  }

@Composable
fun WorkoutStepsList(rows: List<StepRow>, onEditStep: StepEdit, onEditRepeat: RepeatEdit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    rows.forEach { row ->
      if (row.step is RepeatStep) {
        RepeatGroupCard(row, onEditStep, onEditRepeat)
      } else {
        StepCard(row, onClick = { onEditStep.onEditStep(row.step) })
      }
    }
  }
}

@Composable
private fun StepCard(row: StepRow, onClick: () -> Unit) {
  val isRepeat = row.intensity == Intensity.REPEAT
  val context = LocalContext.current
  val formatter = remember(context) { Formatter(context) }
  val duration =
    if (row.durationType == null) {
      stringResource(CommonR.string.Until_press)
    } else {
      formatter.format(Formatter.Format.TXT_LONG, row.durationType, row.durationValue)
    }
  val targetType = row.targetType
  val targetRange = row.targetValue
  val goal =
    if (targetType == null || targetRange == null) {
      null
    } else {
      val prefix = if (targetType == Dimension.HR || targetType == Dimension.HRZ) "HR " else ""
      prefix +
        formatter.format(Formatter.Format.TXT_SHORT, targetType, targetRange.minValue) +
        "-" +
        formatter.format(Formatter.Format.TXT_LONG, targetType, targetRange.maxValue)
    }
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .cardSurface()
        .clickable(onClick = onClick)
        .padding(12.dp)
  ) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isRepeat) {
          Text(
            text = duration,
            style = MaterialTheme.typography.titleMedium.copy(bold = true),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
        }
        IntensityBadge(row.intensity)
      }
      if (isRepeat) {
        Text(
          text = stringResource(CommonR.string.repeat_times, row.repeatCount),
          style = MaterialTheme.typography.bodySmall.withFontPadding(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp),
        )
      } else if (goal != null) {
        Text(
          text = goal,
          style = MaterialTheme.typography.bodySmall.withFontPadding(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun RepeatGroupCard(
  row: StepRow,
  onEditStep: StepEdit,
  onEditRepeat: RepeatEdit,
) {
  val repeat = row.step as RepeatStep
  val outline = MaterialTheme.colorScheme.outlineVariant
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .drawBehind { drawCardBorder(outline) }
        .padding(bottom = 12.dp)
  ) {
    // The header bleeds out of the card padding and repeats the top corner radius.
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
          .background(MaterialTheme.colorScheme.primary)
          .clickable { onEditRepeat.onEditRepeat(repeat) }
          .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
      Text(
        text = stringResource(CommonR.string.repeat_x, row.repeatCount),
        style = MaterialTheme.typography.titleSmall.copy(bold = true),
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp)) {
      WorkoutStepsList(row.children, onEditStep, onEditRepeat)
    }
  }
}

@Composable
private fun IntensityBadge(intensity: Intensity) {
  val textColorId =
    when (intensity) {
      Intensity.ACTIVE -> R.color.stepActive
      Intensity.WARMUP -> R.color.stepWarmup
      Intensity.COOLDOWN -> R.color.stepCooldown
      Intensity.RECOVERY -> R.color.stepRecovery
      Intensity.REPEAT -> R.color.stepRepeat
      else -> R.color.stepResting
    }
  val bgColorId =
    when (intensity) {
      Intensity.ACTIVE -> R.color.stepActiveBg
      Intensity.WARMUP -> R.color.stepWarmupBg
      Intensity.COOLDOWN -> R.color.stepCooldownBg
      Intensity.RECOVERY -> R.color.stepRecoveryBg
      else -> R.color.stepRestingBg
    }
  Text(
    text = stringResource(intensity.textId).uppercase(locale()),
    style = TextStyle(fontSize = 11.sp).withFontPadding().copy(fontWeight = FontWeight.Bold),
    color = colorResource(textColorId),
    modifier =
      Modifier.padding(start = 8.dp)
        // The platform centers with integer rounding (2.5px falls down), Compose rounds up.
        .offset(y = (-0.5).dp)
        .background(colorResource(bgColorId), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 2.dp),
  )
}

/** The locale the platform would render `textAllCaps` with. */
@Composable
private fun locale(): Locale {
  val configuration = LocalConfiguration.current
  // A configuration always carries at least one locale.
  return remember(configuration) { ConfigurationCompat.getLocales(configuration)[0]!! }
}

/** `bg_step_card`: a filled card with a thin outline and rounded corners. */
@Composable
private fun Modifier.cardSurface(): Modifier {
  val outline = MaterialTheme.colorScheme.outlineVariant
  return this.clip(RoundedCornerShape(12.dp))
    .background(MaterialTheme.colorScheme.surfaceContainerLow)
    .drawBehind { drawCardBorder(outline) }
}

private fun DrawScope.drawCardBorder(color: Color) {
  val stroke = 1.dp.toPx()
  val inset = stroke / 2f
  drawRoundRect(
    color = color,
    topLeft = Offset(inset, inset),
    size = Size(size.width - stroke, size.height - stroke),
    cornerRadius = CornerRadius(12.dp.toPx()),
    style = Stroke(width = stroke),
  )
}

private fun TextStyle.copy(bold: Boolean) =
  copy(
    fontWeight = if (bold) FontWeight.Bold else null,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

private fun TextStyle.withFontPadding() =
  copy(platformStyle = PlatformTextStyle(includeFontPadding = true))
