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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText

class HistoryOverlayState {
  var emptyVisible by mutableStateOf(false)
  var fabVisible by mutableStateOf(false)
}

object HistoryOverlayHost {
  fun attach(
    composeView: ComposeView,
    state: HistoryOverlayState,
    onAddManualEntry: Runnable,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        HistoryOverlay(state = state, onAddManualEntry = { onAddManualEntry.run() })
      }
    }
  }
}

/** The empty state and the button that adds an activity, which the platform draws over the list. */
@Composable
fun HistoryOverlay(state: HistoryOverlayState, onAddManualEntry: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize()) {
    if (state.emptyVisible) {
      HistoryEmptyState(onAddManualEntry = onAddManualEntry)
    }
    if (state.fabVisible) {
      FloatingActionButton(
        onClick = onAddManualEntry,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_add_white_24dp),
          contentDescription = stringResource(CommonR.string.Add_manual_entry),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}

@Composable
private fun BoxScope.HistoryEmptyState(onAddManualEntry: () -> Unit) {
  val addManualEntry = stringResource(CommonR.string.Add_manual_entry)
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.align(Alignment.Center).padding(24.dp),
  ) {
    Box(
      modifier =
        Modifier.size(96.dp)
          .background(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            RoundedCornerShape(percent = 50),
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_tab_history_24dp),
        contentDescription = stringResource(CommonR.string.History),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(48.dp),
      )
    }
    Spacer(Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.history_empty),
      style = XmlText.title,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.offset(x = (-0.5).dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.history_empty_description),
      style = XmlText.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    // The platform button is 40dp high inside a 48dp slot, which the insets of the tonal style add.
    Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
      Button(
        onClick = onAddManualEntry,
        shape = RoundedCornerShape(20.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          ),
        modifier = Modifier.height(40.dp),
      ) {
        // The platform button centers the label ink, Compose centers the line box, which is a
        // little wider on the right.
        Text(text = addManualEntry, modifier = Modifier.offset(x = 0.5.dp, y = 1.dp))
      }
    }
  }
}
