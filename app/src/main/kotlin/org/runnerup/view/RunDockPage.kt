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

import android.os.SystemClock
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** The height of an extended floating action button. */
private val fabHeight = 56.dp

/** The corner radius material gives the extended floating action buttons. */
private val fabCorner = 20.dp

/** The icon size the extended floating action buttons use. */
private val fabIcon = 24.dp

/** How long the stop button has to be held, which the platform used as well. */
private const val HOLD_MILLIS = 750L

/** What the dock shows. */
class RunDockState {
  var paused by mutableStateOf(false)

  /** How far the press and hold has progressed, which the stop icon draws as a ring. */
  var holdProgress by mutableFloatStateOf(0f)
}

/** The activity callbacks the dock needs. */
class RunDockBindings(
  val onTogglePause: Runnable,
  val onNewLap: Runnable,
  val onStop: Runnable,
  val onHoldHint: Runnable,
)

object RunDockHost {
  @JvmStatic
  fun attach(composeView: ComposeView, state: RunDockState, bindings: RunDockBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { RunDock(state = state, bindings = bindings) }
    }
  }
}

/** The row of two extended buttons below the bottom sheet. */
@Composable
fun RunDock(state: RunDockState, bindings: RunDockBindings) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .background(themeAttrColor(com.google.android.material.R.attr.colorSurface))
        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
  ) {
    val stopFill = themeAttrColor(AppCompatR.attr.colorError)
    val stopForeground = themeAttrColor(com.google.android.material.R.attr.colorOnError)
    val primaryFill = themeAttrColor(AppCompatR.attr.colorPrimary)
    val primaryForeground = themeAttrColor(com.google.android.material.R.attr.colorOnPrimary)
    if (state.paused) {
      HoldToStopButton(
        label = stringResource(CommonR.string.Stop),
        state = state,
        fill = stopFill,
        foreground = stopForeground,
        onStop = bindings.onStop,
        onEarlyRelease = bindings.onHoldHint,
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
    } else {
      ExtendedButton(
        label = stringResource(CommonR.string.NextLap),
        icon = { tint ->
          Image(
            painter = painterResource(R.drawable.ic_skip_next),
            contentDescription = null,
            modifier = Modifier.size(fabIcon),
            colorFilter = ColorFilter.tint(tint),
          )
        },
        fill = primaryFill,
        foreground = primaryForeground,
        onClick = { bindings.onNewLap.run() },
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
    }
    ExtendedButton(
      label = stringResource(if (state.paused) CommonR.string.Resume else CommonR.string.Pause),
      icon = { tint ->
        Image(
          painter =
            painterResource(if (state.paused) R.drawable.ic_play_arrow else R.drawable.ic_pause),
          contentDescription = null,
          modifier = Modifier.size(fabIcon),
          colorFilter = ColorFilter.tint(tint),
        )
      },
      fill = primaryFill,
      foreground = primaryForeground,
      onClick = { bindings.onTogglePause.run() },
      modifier = Modifier.weight(1f).padding(start = 8.dp),
    )
  }
}

@Composable
private fun ExtendedButton(
  label: String,
  icon: @Composable (Color) -> Unit,
  fill: Color,
  foreground: Color,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  Box(
    modifier =
      modifier
        .height(fabHeight)
        .fabShadow()
        .clip(fabShape)
        .background(fill)
        .outline(themeAttrColor(com.google.android.material.R.attr.colorOutline))
        .then(
          if (onClick != null) {
            Modifier.pointerInput(onClick) {
              detectTapGestures(onTap = { onClick() })
            }
          } else {
            Modifier
          }
        ),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
    ) {
      Box(modifier = Modifier.size(fabIcon), contentAlignment = Alignment.Center) {
        icon(foreground)
      }
      Text(
        text = label,
        style = XmlText.labelLarge,
        color = foreground,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

/**
 * The stop button of a paused run: the same button, but held rather than tapped, with the ring
 * around the square filling as it is held.
 */
@Composable
private fun HoldToStopButton(
  label: String,
  state: RunDockState,
  fill: Color,
  foreground: Color,
  onStop: Runnable,
  onEarlyRelease: Runnable,
  modifier: Modifier = Modifier,
) {
  val hold = remember { HoldState(HOLD_MILLIS) }
  var pressing by remember { mutableStateOf(false) }
  // The ring fills with the clock rather than with pointer events, so a press that never moves
  // still fills it.
  LaunchedEffect(pressing) {
    if (!pressing) return@LaunchedEffect
    while (true) {
      val now = SystemClock.uptimeMillis()
      state.holdProgress = hold.progress(now)
      if (hold.onTick(now) == HoldState.Result.COMPLETE) {
        state.holdProgress = 0f
        pressing = false
        onStop.run()
        break
      }
      withFrameNanos {}
    }
  }
  Box(
    modifier =
      modifier
        .height(fabHeight)
        .fabShadow()
        .clip(fabShape)
        .background(fill)
        .outline(themeAttrColor(com.google.android.material.R.attr.colorOutline))
        .pointerInput(hold) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            hold.onDown(down.uptimeMillis)
            state.holdProgress = 0f
            pressing = true
            while (pressing) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null) {
                hold.onCancel()
                pressing = false
                break
              }
              if (change.isOutOfBounds(size)) {
                hold.onCancel()
                state.holdProgress = 0f
                pressing = false
                break
              }
              if (!change.pressed) {
                when (hold.onUp(change.uptimeMillis)) {
                  HoldState.Result.EARLY_RELEASE -> onEarlyRelease.run()
                  HoldState.Result.COMPLETE -> onStop.run()
                  HoldState.Result.NONE -> Unit
                }
                state.holdProgress = 0f
                pressing = false
                break
              }
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
    ) {
      Box(modifier = Modifier.size(fabIcon), contentAlignment = Alignment.Center) {
        StopIcon(progress = state.holdProgress, tint = foreground)
      }
      Text(
        text = label,
        style = XmlText.labelLarge,
        color = foreground,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

private val fabShape = RoundedCornerShape(fabCorner)

/** The coverage the platform button shadow has below it, at each pixel outside the outline. */
private val SHADOW_BOTTOM =
  floatArrayOf(
    0.208f,
    0.200f,
    0.192f,
    0.180f,
    0.169f,
    0.161f,
    0.149f,
    0.141f,
    0.133f,
    0.122f,
    0.114f,
    0.102f,
    0.094f,
    0.082f,
    0.075f,
    0.063f,
    0.055f,
    0.047f,
    0.043f,
    0.035f,
    0.031f,
    0.024f,
    0.020f,
    0.016f,
    0.012f,
  )

/** Beside the button, which falls off sooner than the bottom does. */
private val SHADOW_SIDE =
  floatArrayOf(
    0.122f,
    0.106f,
    0.086f,
    0.071f,
    0.059f,
    0.047f,
    0.039f,
    0.035f,
    0.031f,
    0.024f,
    0.020f,
    0.016f,
  )

/** Above the button, which is weaker still. */
private val SHADOW_TOP = floatArrayOf(0.031f, 0.024f, 0.016f, 0.008f, 0.004f)

/**
 * The shadow the platform buttons cast, which is material drawing a spot shadow outside the shape.
 * It is drawn as one ring per pixel so that each ring contributes the coverage the platform has at
 * that distance, and the four clips tile the area around the button without overlapping.
 */
private fun Modifier.fabShadow(): Modifier = drawBehind {
  val corner = fabCorner.toPx()
  val steps = maxOf(SHADOW_TOP.size, SHADOW_SIDE.size, SHADOW_BOTTOM.size)
  val width = size.width
  val height = size.height
  for (step in 1..steps) {
    val inner = (step - 1).toFloat()
    val outer = step.toFloat()
    val ring =
      Path().apply {
        op(
          Path().apply {
            addRoundRect(
              RoundRect(
                left = -outer,
                top = -outer,
                right = width + outer,
                bottom = height + outer,
                cornerRadius = CornerRadius(corner + outer, corner + outer),
              )
            )
          },
          Path().apply {
            addRoundRect(
              RoundRect(
                left = -inner,
                top = -inner,
                right = width + inner,
                bottom = height + inner,
                cornerRadius = CornerRadius(corner + inner, corner + inner),
              )
            )
          },
          PathOperation.Difference,
        )
      }
    if (step <= SHADOW_BOTTOM.size) {
      val alpha = Color.Black.copy(alpha = SHADOW_BOTTOM[step - 1])
      clipRect(left = 0f, top = height, right = width, bottom = height + outer) {
        drawPath(path = ring, color = alpha)
      }
    }
    if (step <= SHADOW_SIDE.size) {
      val alpha = Color.Black.copy(alpha = SHADOW_SIDE[step - 1])
      clipRect(left = -outer, top = 0f, right = 0f, bottom = height) {
        drawPath(path = ring, color = alpha)
      }
      clipRect(left = width, top = 0f, right = width + outer, bottom = height) {
        drawPath(path = ring, color = alpha)
      }
    }
    if (step <= SHADOW_TOP.size) {
      val alpha = Color.Black.copy(alpha = SHADOW_TOP[step - 1])
      clipRect(left = 0f, top = -outer, right = width, bottom = 0f) {
        drawPath(path = ring, color = alpha)
      }
    }
  }
}

/**
 * The 1dp outline the platform buttons draw. Unlike `Modifier.border` this straddles the shape
 * rather than sitting inside it, so the fill keeps the full size the platform button has.
 */
private fun Modifier.outline(color: Color): Modifier = drawWithContent {
  val half = 0.5.dp.toPx()
  drawPath(
    path =
      Path().apply {
        addRoundRect(
          RoundRect(
            left = -half,
            top = -half,
            right = size.width + half,
            bottom = size.height + half,
            cornerRadius = CornerRadius(fabCorner.toPx() + half, fabCorner.toPx() + half),
          )
        )
      },
    color = color,
    style = Stroke(width = 1.dp.toPx()),
  )
  drawContent()
}

/**
 * `StopProgressDrawable`: a square with a ring around it that fills as the button is held, drawn on
 * the same 24dp viewport and with the same square and ring measurements the drawable uses.
 */
@Composable
private fun StopIcon(progress: Float, tint: Color) {
  Canvas(modifier = Modifier.size(fabIcon)) {
    val scale = size.minDimension / VIEWPORT
    val left = SQUARE_LEFT * scale
    val top = SQUARE_TOP * scale
    val right = SQUARE_RIGHT * scale
    val bottom = SQUARE_BOTTOM * scale
    drawRect(color = tint, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
    if (progress > 0f) {
      val radius = RING_RADIUS * scale
      val centre = Offset(size.width / 2f, size.height / 2f)
      drawArc(
        color = tint,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = RING_STROKE_WIDTH * scale, cap = StrokeCap.Round),
      )
    }
  }
}

private const val VIEWPORT = 24f
private const val SQUARE_LEFT = 6f
private const val SQUARE_TOP = 6f
private const val SQUARE_RIGHT = 18f
private const val SQUARE_BOTTOM = 18f
private const val RING_RADIUS = 10f
private const val RING_STROKE_WIDTH = 2.5f
