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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.R
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

class PickerPageState {
  var title by mutableStateOf("")
  var items by mutableStateOf<List<String>>(emptyList())
  var selected by mutableStateOf(0)
}

/** Java friendly callback for a tapped picker row. */
fun interface PickerItemClick {
  fun onItemClick(position: Int)
}

object PickerScreenHost {
  fun attach(
    composeView: ComposeView,
    state: PickerPageState,
    onItemClick: PickerItemClick,
    onNavigateUp: Runnable,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        PickerPage(
          state = state,
          onItemClick = { onItemClick.onItemClick(it) },
          onNavigateUp = { onNavigateUp.run() },
        )
      }
    }
  }
}

@Composable
private fun PickerPage(
  state: PickerPageState,
  onItemClick: (Int) -> Unit,
  onNavigateUp: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    PickerToolbar(onNavigateUp)
    // The platform title box is 27dp tall, with the 20sp bold text 3dp below its top.
    Box(modifier = Modifier.padding(start = 16.dp, top = 12.dp).heightIn(min = 27.dp)) {
      Text(
        text = state.title,
        style =
          MaterialTheme.typography.labelMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp),
      )
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
      itemsIndexed(state.items) { index, item ->
        PickerRow(
          item = item,
          selected = index == state.selected,
          onClick = { onItemClick(index) },
        )
        if (index < state.items.lastIndex) {
          // The platform list divider, which the XML ListView used: black at 12% alpha.
          HorizontalDivider(thickness = 1.dp, color = Color.Black.copy(alpha = 0.12f))
        }
      }
    }
  }
}

@Composable
private fun PickerToolbar(onNavigateUp: () -> Unit) {
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
  }
}

@Composable
private fun PickerRow(item: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp),
  ) {
    Text(
      text = item,
      style = XmlText.body.copy(fontSize = 16.sp),
      color = themeAttrColor(android.R.attr.textColorPrimary),
      maxLines = 1,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
        painter = painterResource(R.drawable.ic_check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
