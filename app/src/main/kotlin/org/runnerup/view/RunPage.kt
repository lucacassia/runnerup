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

import androidx.appcompat.R as AppCompatR
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** `RunStatLabel`: `textAppearanceLabelMedium`, upper case, in `colorOnSurfaceVariant`. */
private val statLabelStyle =
  TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.04166667.em,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** `textAppearanceHeadlineLarge` in bold, which the collapsed statistic values autosize from. */
private val compactValueStyle =
  TextStyle(
    fontSize = 32.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** `textAppearanceTitleLarge` in bold, which the lap values autosize from. */
private val lapValueStyle =
  TextStyle(
    fontSize = 22.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** `textAppearanceDisplayMedium` in bold, which the expanded statistic values autosize from. */
private val expandedValueStyle =
  TextStyle(
    fontSize = 45.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** `current_pace` and `current_hr`: `textAppearanceTitleMedium` in bold. */
private val infoValueStyle =
  TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** The heart rate zone pill, which sets its own 11sp bold size. */
private val hrZonePillStyle =
  TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** The collapsed statistic values keep the height of their widest autosize step. */
private val compactValueHeight = 43.dp

/** A lap value keeps the height of its widest autosize step. */
private val lapValueHeight = 29.5.dp

/** An expanded statistic row: the value area plus an 8dp margin below the label. */
private val expandedRowHeight = 127.5.dp

/** The value area of an expanded statistic row. */
private val expandedValueHeight = 102.5.dp

/** The width of one third of the card content, which bounds the collapsed values. */
private val columnWidth = 96.dp

/** The width of one expanded value, which is four sixths of the card content. */
private val expandedValueWidth = 192.dp

/** The duration `toggleStatsExpanded` animates the card and the value area over. */
private const val EXPAND_DURATION_MS = 250

/** The GPS wait banner above the statistics card. */
class RunWaitBanner {
  var visible by mutableStateOf(false)
  var gpsIcon by mutableStateOf(0)
  var title by mutableStateOf("")
  var subtitle by mutableStateOf("")
  var enableGpsVisible by mutableStateOf(false)
}

/** Everything the wait banner and the statistics card draw. */
class RunPageState {
  var expanded by mutableStateOf(false)

  /** How much taller the card becomes, measured from the sheet host by the activity. */
  var statsDelta by mutableStateOf(0)

  /** The collapsed height of the card, measured from the first composed layout. */
  var cardNaturalHeight by mutableStateOf(0)

  /** The collapsed height of the statistic value area, likewise measured once. */
  var areaNaturalHeight by mutableStateOf(0)

  /** Room left below the card for its shadow, in pixels, taken from `run_overlay_shadow_room`. */
  var shadowRoomPx by mutableStateOf(0)

  var distance by mutableStateOf("")
  var time by mutableStateOf("")
  var pace by mutableStateOf("")
  var lapDistance by mutableStateOf("")
  var lapTime by mutableStateOf("")
  var lapPace by mutableStateOf("")
  var currentPace by mutableStateOf("")
  var currentHr by mutableStateOf("")
  var hrZonePill by mutableStateOf("")
  var hrZonePillVisible by mutableStateOf(false)
  var hrZonePillTextColor by mutableStateOf(0)
  var hrZonePillBgColor by mutableStateOf(0)

  val waitBanner = RunWaitBanner()
}

/** The activity callbacks the banner and the card need. */
class RunBindings(
  val onToggleExpanded: Runnable,
  val onCancelWait: Runnable,
  val onEnableGps: Runnable,
)

/**
 * Attaches [RunOverlay] to the `ComposeView` that replaces the first two `run_overlay` children.
 */
object RunScreenHost {
  @JvmStatic
  fun attach(composeView: ComposeView, state: RunPageState, bindings: RunBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent { RunnerUpTheme { RunOverlay(state = state, bindings = bindings) } }
  }
}

/**
 * The wait banner and the statistics card, which replace the first two children of `run_overlay`.
 */
@Composable
fun RunOverlay(state: RunPageState, bindings: RunBindings) {
  Column(modifier = Modifier.fillMaxWidth()) {
    if (state.waitBanner.visible) {
      WaitBannerCard(banner = state.waitBanner, bindings = bindings)
    }
    StatsCard(state = state, bindings = bindings)
    // The card shadow falls below the card, and a composition is clipped to its own bounds, so the
    // overlay leaves room for it. The sheet host is pulled up by the same amount, which leaves
    // everything below the card exactly where the platform puts it.
    Spacer(Modifier.height(with(LocalDensity.current) { state.shadowRoomPx.toDp() }))
  }
}

/**
 * The statistics card. The platform grows it downwards over the bottom sheet host by pairing a
 * taller card with a negative bottom margin, which compose expresses as a taller card in a slot
 * that keeps the collapsed height, so that nothing below the slot moves.
 */
@Composable
private fun StatsCard(state: RunPageState, bindings: RunBindings) {
  val density = LocalDensity.current
  val natural = state.cardNaturalHeight
  val collapsed = with(density) { natural.toDp() }
  val grown = with(density) { (natural + state.statsDelta).toDp() }
  // Until the natural height has been measured the card is left unconstrained, because the card
  // itself is the only thing that can report it. The first layout therefore measures the content
  // at its natural size, and every later one is driven by the height that first layout reported.
  val measured = natural > 0
  val animated by
    animateDpAsState(
      targetValue = if (measured) (if (state.expanded) grown else collapsed) else 0.dp,
      animationSpec = tween(EXPAND_DURATION_MS),
    )
  val height = if (measured) animated else Dp.Unspecified
  Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(height)
          .onSizeChanged {
            // Only the collapsed layout has a natural height, so the first pass measures it.
            // Later passes report the height the expand animation has reached.
            if (state.cardNaturalHeight == 0) {
              state.cardNaturalHeight = it.height
            }
          }
          .cardChrome(
            fill = elevatedCardFill(4f),
            radius = 12.dp,
            stroke = themeAttrColor(AppCompatR.attr.colorPrimary),
            strokeWidth = 3.dp,
          )
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = bindings.onToggleExpanded::run,
          )
          .padding(20.dp)
    ) {
      CardContent(state = state)
    }
  }
}

@Composable
private fun CardContent(state: RunPageState) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth().height(24.dp),
    ) {
      ExpandIndicator(expanded = state.expanded)
    }
    Spacer(Modifier.height(8.dp))
    StatsArea(state = state)
    Spacer(Modifier.height(16.dp))
    LapRow(state = state)
    Spacer(Modifier.height(16.dp))
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(1.dp)
          .background(themeAttrColor(com.google.android.material.R.attr.colorOutlineVariant))
    )
    Spacer(Modifier.height(12.dp))
    CurrentRow(state = state)
  }
}

@Composable
private fun ExpandIndicator(expanded: Boolean) {
  val rotation by
    animateFloatAsState(
      targetValue = if (expanded) 180f else 0f,
      animationSpec = tween(EXPAND_DURATION_MS),
    )
  Image(
    painter = painterResource(R.drawable.ic_expand_down_white_24dp),
    contentDescription = null,
    colorFilter =
      ColorFilter.tint(themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)),
    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
  )
}

/** The three statistics, side by side when collapsed and stacked when expanded. */
@Composable
private fun StatsArea(state: RunPageState) {
  val values = listOf(state.distance, state.time, state.pace)
  val labels =
    listOf(
      stringResource(CommonR.string.Distance),
      stringResource(CommonR.string.Time),
      stringResource(CommonR.string.Pace),
    )
  val density = LocalDensity.current
  val natural = state.areaNaturalHeight
  val collapsed = with(density) { natural.toDp() }
  val grown = with(density) { (natural + state.statsDelta).toDp() }
  // Unconstrained until the collapsed row has measured itself, for the same reason as the card.
  val measured = natural > 0
  val animated by
    animateDpAsState(
      targetValue = if (measured) (if (state.expanded) grown else collapsed) else 0.dp,
      animationSpec = tween(EXPAND_DURATION_MS),
    )
  val height = if (measured) animated else Dp.Unspecified
  Box(modifier = Modifier.fillMaxWidth().height(height)) {
    // The crossfade is deliberately not clipped: the platform lifts the collapsing values out of
    // the value area while the morph runs, so the outgoing values may overflow it.
    Crossfade(
      targetState = state.expanded,
      animationSpec = tween(EXPAND_DURATION_MS),
      label = "stats",
      modifier = Modifier.fillMaxWidth(),
    ) { isExpanded ->
      if (isExpanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
          values.forEachIndexed { index, value ->
            Box(
              modifier = Modifier.fillMaxWidth().height(expandedRowHeight).padding(bottom = 8.dp)
            ) {
              Column(modifier = Modifier.fillMaxSize()) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth().height(expandedValueHeight),
                ) {
                  Spacer(Modifier.weight(1f))
                  AutoSizeValue(
                    text = value,
                    style = expandedValueStyle,
                    minSize = 24,
                    maxSize = 96,
                    maxWidth = expandedValueWidth,
                    maxHeight = expandedValueHeight,
                    modifier = Modifier.weight(4f).fillMaxWidth(),
                  )
                  Spacer(Modifier.weight(1f))
                }
                StatLabel(labels[index])
              }
            }
          }
        }
      } else {
        Row(
          modifier =
            Modifier.fillMaxWidth().onSizeChanged {
              // The collapsed value area is the one with a natural height, so it is measured
              // here; the expanded rows are as tall as the animation makes the area.
              if (state.areaNaturalHeight == 0) {
                state.areaNaturalHeight = it.height
              }
            }
        ) {
          values.forEachIndexed { index, value ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.weight(1f),
            ) {
              AutoSizeValue(
                text = value,
                style = compactValueStyle,
                minSize = 16,
                maxSize = 32,
                maxWidth = columnWidth,
                modifier = Modifier.fillMaxWidth().height(compactValueHeight),
              )
              Spacer(Modifier.height(4.dp))
              StatLabel(labels[index])
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatLabel(label: String) {
  Text(
    text = label.uppercase(),
    style = statLabelStyle,
    color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
    textAlign = TextAlign.Center,
    maxLines = 1,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun LapRow(state: RunPageState) {
  val values = listOf(state.lapDistance, state.lapTime, state.lapPace)
  val labels =
    listOf(
      stringResource(CommonR.string.LapDistance),
      stringResource(CommonR.string.LapTime),
      stringResource(CommonR.string.LapPace),
    )
  Row(modifier = Modifier.fillMaxWidth()) {
    values.forEachIndexed { index, value ->
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        AutoSizeValue(
          text = value,
          style = lapValueStyle,
          minSize = 14,
          maxSize = 22,
          maxWidth = columnWidth,
          modifier = Modifier.fillMaxWidth().height(lapValueHeight),
        )
        Spacer(Modifier.height(4.dp))
        StatLabel(labels[index])
      }
    }
  }
}

@Composable
private fun CurrentRow(state: RunPageState) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = stringResource(CommonR.string.Current).uppercase(),
      style = statLabelStyle,
      color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
      textAlign = TextAlign.Start,
      maxLines = 1,
      modifier = Modifier.weight(1f),
    )
    Text(text = state.currentPace, style = infoValueStyle, maxLines = 1)
    if (state.currentHr.isNotEmpty()) {
      Spacer(Modifier.width(12.dp))
      Text(text = state.currentHr, style = infoValueStyle, maxLines = 1)
    }
    if (state.hrZonePillVisible) {
      Spacer(Modifier.width(6.dp))
      Text(
        text = state.hrZonePill,
        style = hrZonePillStyle,
        color = Color(state.hrZonePillTextColor),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(Color(state.hrZonePillBgColor))
            .padding(start = 6.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
      )
    }
  }
}

@Composable
private fun WaitBannerCard(banner: RunWaitBanner, bindings: RunBindings) {
  Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .cardChrome(
            fill =
              elevatedCardFill(
                2f,
                themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh),
              ),
            radius = 12.dp,
            stroke = themeAttrColor(com.google.android.material.R.attr.colorOutlineVariant),
            strokeWidth = 1.dp,
          )
          .padding(16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
          painter = painterResource(banner.gpsIcon),
          contentDescription = banner.title,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = banner.title,
            style = infoValueStyle,
            color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
          )
          Text(
            text = banner.subtitle,
            style = XmlText.bodySmall,
            color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
          )
        }
        if (banner.enableGpsVisible) {
          BannerButton(stringResource(CommonR.string.Enable_GPS), bindings.onEnableGps)
        }
        BannerButton(stringResource(CommonR.string.Cancel), bindings.onCancelWait)
      }
    }
  }
}

@Composable
private fun BannerButton(label: String, onClick: Runnable) {
  TextButton(
    onClick = onClick::run,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
  ) {
    Text(
      text = label,
      style = XmlText.labelLarge,
      color = themeAttrColor(AppCompatR.attr.colorPrimary),
      maxLines = 1,
    )
  }
}

/**
 * Material tints an elevated card toward `elevationOverlayColor`, which this theme points at
 * `colorPrimary`. The factor is calibrated against the platform rendering of this card.
 */
@Composable
private fun elevatedCardFill(elevationDp: Float, base: Color = Color.White): Color {
  val primary = themeAttrColor(AppCompatR.attr.colorPrimary)
  val factor = 0.0246f * elevationDp
  return Color(
    red = base.red + (primary.red - base.red) * factor,
    green = base.green + (primary.green - base.green) * factor,
    blue = base.blue + (primary.blue - base.blue) * factor,
    alpha = 1f,
  )
}

/**
 * The card the way material draws one: the fill, a rounded outline, and a soft shadow that falls
 * off outside the shape on every side.
 */
private fun Modifier.cardChrome(
  fill: Color,
  radius: Dp,
  stroke: Color,
  strokeWidth: Dp,
): Modifier =
  // The shadow has to be drawn ahead of the clip, otherwise the clip keeps it from reaching
  // outside the shape where it belongs.
  drawBehind { drawShadow(radius) }
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .drawWithContent {
      val inset = strokeWidth.toPx() / 2f
      val corner = radius.toPx() - inset
      drawPath(
        path = roundedRect(inset, size.width - inset, inset, size.height - inset, corner),
        color = stroke,
        style = Stroke(width = strokeWidth.toPx()),
      )
      drawContent()
    }

/**
 * The shadow material casts for a raised card, darkest just outside the outline and gone after a
 * few pixels. It is drawn as one ring per pixel so that each ring contributes the coverage the
 * platform has at that distance, rather than the rings accumulating on top of each other. The
 * platform shadow is not symmetric, so each side uses the falloff that was measured for it.
 */
private fun DrawScope.drawShadow(radius: Dp) {
  val corner = radius.toPx()
  val steps = maxOf(SHADOW_TOP.size, SHADOW_SIDE.size, SHADOW_BOTTOM.size)
  val width = size.width
  val height = size.height
  for (step in 1..steps) {
    val inner = (step - 1).toFloat()
    val outer = step.toFloat()
    val ring =
      Path().apply {
        op(
          roundedRect(-outer, width + outer, -outer, height + outer, corner + outer),
          roundedRect(-inner, width + inner, -inner, height + inner, corner + inner),
          PathOperation.Difference,
        )
      }
    // The four clips tile the area around the card without overlapping, so no ring is ever drawn
    // twice and the coverages do not accumulate.
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

/** The coverage the platform shadow has below the card, at each pixel outside the outline. */
private val SHADOW_BOTTOM =
  floatArrayOf(
    0.141f,
    0.114f,
    0.090f,
    0.071f,
    0.059f,
    0.047f,
    0.035f,
    0.027f,
    0.020f,
    0.016f,
    0.012f,
    0.008f,
    0.004f,
  )

/** The coverage beside the card, which falls off sooner and weaker than the bottom does. */
private val SHADOW_SIDE =
  floatArrayOf(
    0.118f,
    0.090f,
    0.071f,
    0.051f,
    0.039f,
    0.031f,
    0.024f,
    0.016f,
    0.012f,
    0.008f,
    0.004f,
  )

/** The coverage above the card, which is weaker still. */
private val SHADOW_TOP =
  floatArrayOf(0.082f, 0.059f, 0.039f, 0.024f, 0.020f, 0.012f, 0.008f, 0.004f, 0.004f)

private fun roundedRect(
  left: Float,
  right: Float,
  top: Float,
  bottom: Float,
  corner: Float,
): Path =
  Path().apply {
    addRoundRect(
      RoundRect(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        cornerRadius = CornerRadius(corner, corner),
      )
    )
  }

/**
 * The statistic values autosize with `autoSizeTextType="uniform"`, which steps the size down from
 * the maximum until the text fits the box. Compose has no equivalent, so the step down is measured
 * here at the same granularity the platform declares.
 */
@Composable
private fun AutoSizeValue(
  text: String,
  style: TextStyle,
  minSize: Int,
  maxSize: Int,
  maxWidth: Dp,
  modifier: Modifier = Modifier,
  maxHeight: Dp? = null,
) {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface)
  val limitWidth = with(density) { maxWidth.toPx() }
  val limitHeight = maxHeight?.let { with(density) { it.toPx() } }
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    val fitted =
      remember(text, style, minSize, maxSize, limitWidth, limitHeight, density, measurer) {
        fitTextSize(measurer, text, style, minSize, maxSize, limitWidth, limitHeight, density)
      }
    Text(
      text = text,
      style = atSize(style, fitted),
      color = color,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Clip,
    )
  }
}

/** Applies an autosized font size, scaling the line height the way the platform does. */
private fun atSize(style: TextStyle, sizeSp: Float): TextStyle {
  val baseSize = style.fontSize.value
  val baseLineHeight = style.lineHeight.value
  return style.copy(
    fontSize = sizeSp.sp,
    lineHeight = (baseLineHeight * sizeSp / baseSize).sp,
  )
}

/**
 * Finds the largest size in `[minSize, maxSize]` whose layout fits, stepping down a whole sp at a
 * time the way `autoSizeStepGranularity` does. Both dimensions are checked when the platform bounds
 * them, which is what `autoSizeTextType="uniform"` does.
 */
private fun fitTextSize(
  measurer: TextMeasurer,
  text: String,
  style: TextStyle,
  minSize: Int,
  maxSize: Int,
  limitWidth: Float,
  limitHeight: Float?,
  density: Density,
): Float {
  for (size in maxSize downTo minSize) {
    val layout =
      measurer.measure(
        text = text,
        style = atSize(style, size.toFloat()),
        constraints = Constraints(),
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        maxLines = 1,
        softWrap = false,
      )
    val fitsHeight = limitHeight == null || layout.size.height <= limitHeight
    if (layout.size.width <= limitWidth && fitsHeight) {
      return size.toFloat()
    }
  }
  return minSize.toFloat()
}
