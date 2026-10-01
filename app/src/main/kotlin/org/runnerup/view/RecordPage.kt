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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme

class RecordPageState {
  var deviceStatusVisible by mutableStateOf(true)
  var hrIndicatorVisible by mutableStateOf(false)
  var hrMessage by mutableStateOf<String?>(null)
  var wearIndicatorVisible by mutableStateOf(false)
  var wearMessage by mutableStateOf<String?>(null)
}

object RecordScreenHost {
  fun attach(composeView: ComposeView, state: RecordPageState, onStartClick: Runnable) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        RecordPage(state = state, onStartClick = { onStartClick.run() })
      }
    }
  }
}

@Composable
fun RecordPage(state: RecordPageState, onStartClick: () -> Unit) {
  val startLabel = stringResource(CommonR.string.Start)
  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Text(
        text = stringResource(R.string.Setup_steps_hint_record),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center),
      )
      ExtendedFloatingActionButton(
        onClick = onStartClick,
        text = { Text(text = startLabel) },
        icon = {
          Icon(
            painter = painterResource(R.drawable.ic_start_white_24dp),
            contentDescription = null,
            tint = Color.Unspecified,
          )
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .semantics { contentDescription = startLabel },
      )
    }
    StatusBar(state)
  }
}

@Composable
private fun StatusBar(state: RecordPageState) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
    Row(
      verticalAlignment = Alignment.Bottom,
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
      if (state.deviceStatusVisible) {
        StatusText(text = stringResource(CommonR.string.No_devices_connected))
      }
      if (state.hrIndicatorVisible) {
        Icon(
          painter = painterResource(R.drawable.ic_heartrate_white_24dp),
          contentDescription = stringResource(CommonR.string.Heart_Rate_Monitor),
          tint = Color.Unspecified,
          modifier = Modifier.padding(end = 4.dp).size(14.dp),
        )
        state.hrMessage?.let { StatusText(text = it, modifier = Modifier.padding(end = 8.dp)) }
      }
      if (state.wearIndicatorVisible) {
        Icon(
          painter = painterResource(R.drawable.ic_watch_white_24dp),
          contentDescription = stringResource(CommonR.string.Wear_OS_Device),
          tint = Color.Unspecified,
          modifier = Modifier.padding(end = 4.dp).size(14.dp),
        )
        state.wearMessage?.let { StatusText(text = it, modifier = Modifier.padding(end = 8.dp)) }
      }
    }
  }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier,
  )
}
