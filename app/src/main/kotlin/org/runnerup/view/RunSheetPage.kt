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

import android.widget.TextView
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** `textAppearanceTitleSmall` in bold, which the active step title uses. */
private val stepTitleStyle = XmlText.titleSmall.copy(fontWeight = FontWeight.Bold)

/**
 * The active step target, which the layout sets to `textAppearanceLabelSmall` in `colorPrimary`.
 */
private val stepTargetStyle =
  TextStyle(
    fontSize = 11.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.04545455.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** The intensity badge, which sets its own 11sp bold upper case size. */
private val badgeStyle =
  TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** One row of the workout list, flattened from a step by the activity. */
class RunStepRow(
  /** How far the step is indented, which repeats use to show their nesting. */
  val level: Int,
  val intensity: String,
  val durationType: String,
  val durationValue: String,
  val target: String,
  val isCurrent: Boolean,
)

/** Everything the bottom sheet draws. */
class RunSheetState {
  var badgeText by mutableStateOf("")
  var badgeTextColor by mutableStateOf(0)
  var badgeBgColor by mutableStateOf(0)
  var badgeVisible by mutableStateOf(false)
  var stepTitle by mutableStateOf("")
  var stepTarget by mutableStateOf("")
  var stepProgress by mutableStateOf(0)

  var rows by mutableStateOf(emptyList<RunStepRow>())
  var currentIndex by mutableStateOf(-1)

  /** The heart rate debug text, which the tracker writes into and compose only hosts. */
  var hrDebug: TextView? = null
}

object RunSheetHost {
  @JvmStatic
  fun attach(composeView: ComposeView, state: RunSheetState) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent { RunnerUpTheme { RunSheet(state) } }
  }
}

/** The handle, the active step banner and the workout list that fill the bottom sheet. */
@Composable
fun RunSheet(state: RunSheetState) {
  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.TopCenter) {
      Box(
        modifier =
          Modifier.padding(top = 8.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
              themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
            )
      )
    }
    ActiveStepBanner(state = state)
    Text(
      text = stringResource(CommonR.string.Workout),
      style = XmlText.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
      modifier =
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
    )
    WorkoutList(state = state)
  }
}

@Composable
private fun ActiveStepBanner(state: RunSheetState) {
  Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      if (state.badgeVisible) {
        Text(
          text = state.badgeText.uppercase(),
          style = badgeStyle,
          color = Color(state.badgeTextColor),
          maxLines = 1,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .background(Color(state.badgeBgColor))
              .padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = state.stepTitle,
        style = stepTitleStyle,
        color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = state.stepTarget,
        style = stepTargetStyle,
        color = themeAttrColor(AppCompatR.attr.colorPrimary),
        maxLines = 1,
      )
    }
    Spacer(Modifier.height(8.dp))
    StepProgressBar(progress = state.stepProgress)
  }
}

/**
 * The `LinearProgressIndicator` of the active step banner. Material draws a rounded track with the
 * progress over it, and a stop indicator parked at the end of the track whether or not there is any
 * progress, so the track leaves room for it.
 */
@Composable
private fun StepProgressBar(progress: Int) {
  val track = themeAttrColor(com.google.android.material.R.attr.colorOutlineVariant)
  val indicator = themeAttrColor(AppCompatR.attr.colorPrimary)
  val fraction = progress.coerceIn(0, 100) / 100f
  Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
    val radius = CornerRadius(size.height / 2f, size.height / 2f)
    val trackEnd = size.width - STOP_INDICATOR
    drawRoundRect(
      color = track,
      size = Size(trackEnd, size.height),
      cornerRadius = radius,
    )
    if (fraction > 0f) {
      drawRoundRect(
        color = indicator,
        size = Size(trackEnd * fraction, size.height),
        cornerRadius = radius,
      )
    }
    drawCircle(
      color = indicator,
      radius = size.height / 2f,
      center = Offset(size.width - STOP_INDICATOR / 2f, size.height / 2f),
    )
  }
}

/** The size material reserves at the end of the track for its stop indicator. */
private val STOP_INDICATOR = 8f

@Composable
private fun WorkoutList(state: RunSheetState) {
  val listState = rememberLazyListState()
  val current = state.currentIndex
  LaunchedEffect(current) {
    if (current >= 0) {
      listState.animateScrollToItem(current)
    }
  }
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      modifier =
        Modifier.fillMaxSize().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
    ) {
      // The steps of a workout never reorder, so the position identifies a row. Compose only
      // accepts bundle storable keys, which a step is not.
      itemsIndexed(state.rows, key = { index, _ -> index }) { _, row -> StepRowItem(row = row) }
    }
    state.hrDebug?.let { view ->
      AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun StepRowItem(row: RunStepRow) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        // The layout gives the row a minimum height and margins around it, so the margins have to
        // sit outside the minimum for the row to come out as tall as the platform row.
        .padding(top = 2.dp, bottom = 2.dp)
        .defaultMinSize(minHeight = 44.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(
          if (row.isCurrent) {
            themeAttrColor(com.google.android.material.R.attr.colorPrimaryContainer)
          } else {
            Color.Transparent
          }
        )
        .padding(horizontal = 12.dp),
  ) {
    RowCell(
      text = row.intensity,
      textAlign = TextAlign.Start,
      indent = row.level,
      color =
        if (row.isCurrent) themeAttrColor(AppCompatR.attr.colorPrimary)
        else themeAttrColor(android.R.attr.textColorPrimary),
      bold = row.isCurrent,
      weight = 1f,
    )
    RowCell(text = row.durationType, textAlign = TextAlign.Start, weight = 1f)
    RowCell(text = row.durationValue, textAlign = TextAlign.Start, weight = 1f)
    RowCell(text = row.target, textAlign = TextAlign.End, weight = 1f)
  }
}

@Composable
private fun RowScope.RowCell(
  text: String,
  textAlign: TextAlign,
  modifier: Modifier = Modifier,
  weight: Float = 1f,
  /** Indent in dp, which repeats use to show how deeply they are nested. */
  indent: Int = 0,
  color: Color = Color.Unspecified,
  bold: Boolean = false,
) {
  val density = LocalDensity.current
  Text(
    text = text,
    style = XmlText.body.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    color =
      if (color == Color.Unspecified) themeAttrColor(android.R.attr.textColorPrimary) else color,
    textAlign = textAlign,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.weight(weight).padding(start = with(density) { (indent * 10).dp }),
  )
}
