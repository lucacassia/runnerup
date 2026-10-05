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

import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.runnerup.R
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** One heart rate zone: its label next to the two platform inputs. */
class HRZoneItem(
  val label: String,
  val loInput: View,
  val hiInput: View,
)

class HRZonesPageState {
  var title by mutableStateOf("")

  /** The inflated container of the three platform spinners. */
  var spinnerInputs by mutableStateOf<View?>(null)

  var zones by mutableStateOf<List<HRZoneItem>>(emptyList())

  /** The window bounds of the overflow button, which anchor the platform popup menu. */
  var overflowBounds by mutableStateOf(Rect.Zero)
}

fun interface HRZonesOverflowMenu {
  fun onOverflowMenu(left: Int, top: Int, right: Int, bottom: Int)
}

class HRZonesBindings(val onNavigateUp: Runnable, val onOverflowMenu: HRZonesOverflowMenu)

object HRZonesScreenHost {
  fun attach(composeView: ComposeView, state: HRZonesPageState, bindings: HRZonesBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent { RunnerUpTheme { HRZonesPage(state = state, bindings = bindings) } }
  }
}

@Composable
fun HRZonesPage(state: HRZonesPageState, bindings: HRZonesBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    HRZonesToolbar(state = state, bindings = bindings)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
      val spinners = state.spinnerInputs
      if (spinners != null) {
        AndroidView(factory = { spinners }, modifier = Modifier.fillMaxWidth())
      }
      Spacer(Modifier.height(8.dp))
      // The platform rows carry no margin; the outlined box leaves its own label gap.
      state.zones.forEach { zone -> HRZoneRow(item = zone) }
    }
  }
}

@Composable
private fun HRZonesToolbar(state: HRZonesPageState, bindings: HRZonesBindings) {
  val upLabel = stringResource(AppCompatR.string.abc_action_bar_up_description)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(64.dp),
  ) {
    Box(
      modifier =
        Modifier.padding(start = 4.dp)
          .size(48.dp)
          .clickable(onClick = bindings.onNavigateUp::run)
          .semantics { contentDescription = upLabel },
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
      text = state.title,
      style = XmlText.title,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
    Box(
      modifier =
        Modifier.size(width = 40.dp, height = 48.dp)
          .onGloballyPositioned { state.overflowBounds = it.boundsInWindow() }
          .clickable {
            val bounds = state.overflowBounds
            bindings.onOverflowMenu.onOverflowMenu(
              bounds.left.toInt(),
              bounds.top.toInt(),
              bounds.right.toInt(),
              bounds.bottom.toInt(),
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_more_vert),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(24.dp).offset(x = (-2).dp),
      )
    }
  }
}

@Composable
private fun HRZoneRow(item: HRZoneItem) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    // The platform row weighs the label against both inputs, with the two 8dp gaps
    // outside of the weighed widths, so the spacers stay separate here as well.
    Text(
      text = item.label,
      style = XmlText.titleSmall,
      color = themeAttrColor(AppCompatR.attr.colorPrimary),
      modifier = Modifier.weight(1.2f).offset(y = (-0.5).dp),
    )
    Spacer(Modifier.width(8.dp))
    AndroidView(factory = { item.loInput }, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(8.dp))
    AndroidView(factory = { item.hiInput }, modifier = Modifier.weight(1f))
  }
}
