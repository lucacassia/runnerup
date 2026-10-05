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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

class ParkrunBarcodePageState {
  var title by mutableStateOf("")

  /** The stored barcode, or null while nothing has been scanned yet. */
  var barcode by mutableStateOf<String?>(null)

  /** The width of the barcode area in pixels, needed to rasterise the code. */
  var barcodeWidth by mutableStateOf(0)
}

class ParkrunBarcodeBindings(
  val onNavigateUp: Runnable,
  val onScan: Runnable,
  val onDelete: Runnable,
)

object ParkrunBarcodeScreenHost {
  fun attach(
    composeView: ComposeView,
    state: ParkrunBarcodePageState,
    bindings: ParkrunBarcodeBindings,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { ParkrunBarcodePage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun ParkrunBarcodePage(state: ParkrunBarcodePageState, bindings: ParkrunBarcodeBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    ParkrunToolbar(title = state.title, onNavigateUp = bindings.onNavigateUp::run)
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val barcode = state.barcode
      if (barcode == null) {
        Spacer(Modifier.height(32.dp))
        ParkrunLogo(tint = themeAttrColor(com.google.android.material.R.attr.colorOnSurface))
        Spacer(Modifier.height(24.dp))
        Text(
          text = stringResource(CommonR.string.No_parkrun_barcode_text),
          style = XmlText.body.copy(fontSize = 16.sp),
          color = themeAttrColor(android.R.attr.textColorPrimary),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        FilledButton(
          label = stringResource(CommonR.string.Scan_parkrun_barcode),
          onClick = bindings.onScan::run,
        )
      } else {
        StoredBarcode(state = state, barcode = barcode, bindings = bindings)
      }
    }
  }
}

@Composable
private fun ParkrunToolbar(title: String, onNavigateUp: () -> Unit) {
  val upLabel = stringResource(AppCompatR.string.abc_action_bar_up_description)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(64.dp),
  ) {
    Box(
      modifier =
        Modifier.padding(start = 4.dp).size(48.dp).clickable(onClick = onNavigateUp).semantics {
          contentDescription = upLabel
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_arrow_back_24dp),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(24.dp),
      )
    }
    Text(
      text = title,
      style = XmlText.title,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
  }
}

@Composable
private fun ParkrunLogo(modifier: Modifier = Modifier, tint: Color? = null) {
  val label = stringResource(CommonR.string.Parkrun_barcode)
  Image(
    painter = painterResource(R.drawable.parkrun_logo),
    contentDescription = label,
    // The stored logo has no tint, the empty one is tinted with colorOnSurface.
    colorFilter = tint?.let { ColorFilter.tint(it) },
    modifier = modifier.size(135.dp, 63.dp).semantics { contentDescription = label },
  )
}

@Composable
private fun StoredBarcode(
  state: ParkrunBarcodePageState,
  barcode: String,
  bindings: ParkrunBarcodeBindings,
) {
  val density = LocalDensity.current
  val heightPx = with(density) { 270.dp.roundToPx() }
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .cardShadow()
        .clip(RoundedCornerShape(8.dp))
        // An elevated card is white tinted with 5.12% of colorPrimary.
        .background(elevatedCardFill()),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      ParkrunLogo(modifier = Modifier.padding(top = 24.dp))
      Spacer(Modifier.height(24.dp))
      val width = state.barcodeWidth
      if (width > 0) {
        Image(
          bitmap =
            remember(barcode, width) {
              org.runnerup.util.Code128Barcode.renderToBitmap(
                  org.runnerup.util.Code128Barcode.encode(barcode),
                  width,
                  heightPx,
                )
                .asImageBitmap()
            },
          contentDescription = stringResource(CommonR.string.Parkrun_barcode),
          modifier = Modifier.fillMaxWidth().height(270.dp),
        )
      } else {
        Spacer(
          Modifier.fillMaxWidth().height(270.dp).onGloballyPositioned {
            state.barcodeWidth = it.size.width
          }
        )
      }
      Spacer(Modifier.height(16.dp))
      // The platform value view is `textIsSelectable`.
      SelectionContainer {
        Text(
          text = barcode,
          style = XmlText.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
          color = Color.Black,
          maxLines = 1,
        )
      }
      Spacer(Modifier.height(16.dp))
      OutlinedDeleteButton(
        label = stringResource(CommonR.string.Delete),
        onClick = bindings.onDelete::run,
      )
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun elevatedCardFill(): Color {
  val primary = themeAttrColor(AppCompatR.attr.colorPrimary)
  return Color(
    red = 1f + (primary.red - 1f) * 0.0512f,
    green = 1f + (primary.green - 1f) * 0.0512f,
    blue = 1f + (primary.blue - 1f) * 0.0512f,
    alpha = 1f,
  )
}

/**
 * The platform card is elevated, which material draws as a spot shadow below the shape only. The
 * stops are calibrated so the rendered strip matches the platform falloff, which is four pixels
 * wide; compose interpolates the alpha ramp slightly softer than the material shadow.
 */
private fun Modifier.cardShadow(): Modifier = drawWithContent {
  val radius = 8.dp.toPx()
  val step = 1.dp.toPx()
  val top = size.height
  // Only the falloff below the card is visible, so the shadow is drawn as a strip.
  val path =
    Path().apply {
      addRoundRect(
        RoundRect(
          left = 0f,
          top = top,
          right = size.width,
          bottom = top + 2f * step,
          cornerRadius = CornerRadius(radius, radius),
        )
      )
    }
  drawPath(
    path,
    brush =
      Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.225f),
        0.25f to Color.Black.copy(alpha = 0.145f),
        0.5f to Color.Black.copy(alpha = 0.072f),
        0.75f to Color.Black.copy(alpha = 0.03f),
        1f to Color.Transparent,
        startY = top,
        endY = top + 2f * step,
      ),
    style = Fill,
  )
  drawContent()
}

/** The platform buttons are 48dp tall with the 40dp pill inset, as material does. */
@Composable
private fun FilledButton(label: String, onClick: () -> Unit) {
  Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
    Button(
      onClick = onClick,
      shape = RoundedCornerShape(20.dp),
      colors =
        ButtonDefaults.buttonColors(containerColor = themeAttrColor(AppCompatR.attr.colorPrimary)),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
      modifier = Modifier.height(40.dp),
    ) {
      Text(text = label, color = Color.White, maxLines = 1)
    }
  }
}

@Composable
private fun OutlinedDeleteButton(label: String, onClick: () -> Unit) {
  Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
    OutlinedButton(
      onClick = onClick,
      shape = RoundedCornerShape(20.dp),
      colors =
        ButtonDefaults.outlinedButtonColors(
          contentColor = themeAttrColor(AppCompatR.attr.colorPrimary)
        ),
      border =
        androidx.compose.foundation.BorderStroke(
          1.dp,
          themeAttrColor(com.google.android.material.R.attr.colorOutline),
        ),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
      modifier = Modifier.height(40.dp),
    ) {
      Text(
        text = label,
        color = themeAttrColor(AppCompatR.attr.colorPrimary),
        maxLines = 1,
        modifier = Modifier.offset(y = 1.dp),
      )
    }
  }
}
