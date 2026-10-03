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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.function.Consumer
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText

class HistoryChromeState {
  var selectedTab by mutableIntStateOf(0)

  fun select(index: Int) {
    selectedTab = index
  }
}

object HistoryChromeHost {
  fun attach(composeView: ComposeView, state: HistoryChromeState, onSelect: Consumer<Int>) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        HistoryChrome(state = state, onSelect = { onSelect.accept(it) })
      }
    }
  }
}

@Composable
fun HistoryChrome(state: HistoryChromeState, onSelect: (Int) -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        )
        .background(MaterialTheme.colorScheme.surface)
  ) {
    // The platform `MaterialToolbar` is a 64dp app bar with a 16dp start margin.
    Box(
      modifier = Modifier.fillMaxWidth().height(64.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      Text(
        text = stringResource(CommonR.string.History),
        style = XmlText.title,
        color = MaterialTheme.colorScheme.onSurface,
        // The toolbar centers the baseline on the middle of the font metrics, a little higher
        // than the middle of the line box Compose centers.
        modifier = Modifier.padding(start = 16.dp).offset(y = (-0.5).dp),
      )
    }
    HistoryTabBar(selectedTab = state.selectedTab, onSelect = onSelect)
  }
}

/**
 * The platform layout draws a `TabLayout` with equally wide tabs, a 1dp `colorOutlineVariant`
 * divider along its bottom edge and a 3dp `colorPrimary` indicator below the selected label.
 */
@Composable
private fun HistoryTabBar(selectedTab: Int, onSelect: (Int) -> Unit) {
  val labels =
    listOf(
      stringResource(CommonR.string.Activities),
      stringResource(CommonR.string.Progress),
    )
  val labelStyle = XmlText.labelLarge
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
    Box(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(1.dp)
          .background(MaterialTheme.colorScheme.outlineVariant)
    )
    Row(modifier = Modifier.fillMaxSize()) {
      labels.forEachIndexed { index, label ->
        val selected = index == selectedTab
        Box(
          modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style = labelStyle,
            color =
              if (selected) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
          if (selected) {
            // The indicator spans the label, inset by the 2dp the indicator drawable adds.
            val width =
              with(density) { (measurer.measure(label, labelStyle).size.width - 10).toDp() }
            Box(
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .width(width)
                  .height(3.dp)
                  .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                  .background(MaterialTheme.colorScheme.primary)
            )
          }
        }
      }
    }
  }
}
