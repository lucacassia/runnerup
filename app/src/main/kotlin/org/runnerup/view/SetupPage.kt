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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor
import org.runnerup.workout.Workout

class SetupPageState {
  var sportName by mutableStateOf("")
  var sportIconRes by mutableStateOf(0)
  var sportIconColor by mutableStateOf(0)
  var audioName by mutableStateOf("")
  var workoutName by mutableStateOf("")
  var stepsVisible by mutableStateOf(false)
  var startRunEnabled by mutableStateOf(false)
  var gpsChipVisible by mutableStateOf(true)
  var gpsIndicatorRes by mutableStateOf(R.drawable.ic_gps_0)
  var gpsMessageRes by mutableStateOf(CommonR.string.GPS_indicator_off)
  var gpsPopupVisible by mutableStateOf(false)
  var gpsPopupIndicatorRes by mutableStateOf(R.drawable.ic_gps_0)
  var gpsPopupMessage by mutableStateOf("")
  var gpsPopupSatellites by mutableStateOf<String?>(null)

  /** The steps of the selected workout, expanded into rows. */
  var stepRows by mutableStateOf<List<StepRow>>(emptyList())

  fun setWorkout(workout: Workout?) {
    stepRows = if (workout == null) emptyList() else buildStepRows(workout)
    stepsVisible = workout != null
  }
}

object SetupScreenHost {
  @Suppress("LongParameterList")
  fun attach(
    composeView: ComposeView,
    state: SetupPageState,
    onEditStep: StepEdit,
    onEditRepeat: RepeatEdit,
    onNavigateUp: Runnable,
    onSportClick: Runnable,
    onAudioClick: Runnable,
    onWorkoutClick: Runnable,
    onGpsChipClick: Runnable,
    onStartRunClick: Runnable,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        SetupPage(
          state = state,
          onEditStep = onEditStep,
          onEditRepeat = onEditRepeat,
          onNavigateUp = { onNavigateUp.run() },
          onSportClick = { onSportClick.run() },
          onAudioClick = { onAudioClick.run() },
          onWorkoutClick = { onWorkoutClick.run() },
          onGpsChipClick = { onGpsChipClick.run() },
          onStartRunClick = { onStartRunClick.run() },
        )
      }
    }
  }
}

@Suppress("LongParameterList")
@Composable
fun SetupPage(
  state: SetupPageState,
  onEditStep: StepEdit,
  onEditRepeat: RepeatEdit,
  onNavigateUp: () -> Unit,
  onSportClick: () -> Unit,
  onAudioClick: () -> Unit,
  onWorkoutClick: () -> Unit,
  onGpsChipClick: () -> Unit,
  onStartRunClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    SetupToolbar(onNavigateUp)
    Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
      SectionLabel(text = stringResource(R.string.Setup_section_activity), top = 12.dp)
      SetupRow(
        label = stringResource(CommonR.string.Sport),
        value = state.sportName,
        onClick = onSportClick,
        leading = {
          if (state.sportIconRes != 0) {
            Icon(
              painter = painterResource(state.sportIconRes),
              contentDescription = null,
              tint = Color(state.sportIconColor),
              modifier = Modifier.size(20.dp),
            )
          }
        },
      )
      SectionLabel(text = stringResource(R.string.Setup_section_guidance), top = 16.dp)
      SetupRow(
        label = stringResource(R.string.Audio_cues),
        value = state.audioName,
        onClick = onAudioClick,
      )
      SectionLabel(text = stringResource(CommonR.string.Workout), top = 16.dp)
      SetupRow(
        label = stringResource(CommonR.string.Workout),
        value = state.workoutName,
        onClick = onWorkoutClick,
      )
      if (state.stepsVisible) {
        Text(
          text = stringResource(R.string.Setup_steps_hint),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
          WorkoutStepsList(
            rows = state.stepRows,
            onEditStep = onEditStep,
            onEditRepeat = onEditRepeat,
          )
        }
      }
      Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
    }
    SetupFooter(
      state = state,
      onGpsChipClick = onGpsChipClick,
      onStartRunClick = onStartRunClick,
    )
  }
}

@Composable
private fun SetupToolbar(onNavigateUp: () -> Unit) {
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
      text = stringResource(R.string.Setup_run),
      style = XmlText.title,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

@Composable
private fun SectionLabel(text: String, top: Dp) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, top = top),
  )
}

@Composable
private fun SetupRow(
  label: String,
  value: String,
  onClick: () -> Unit,
  leading: (@Composable () -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 6.dp)
        .background(MaterialTheme.colorScheme.surface)
        .clickable(onClick = onClick)
        .padding(16.dp),
  ) {
    Text(
      text = label,
      style = XmlText.body,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      modifier = Modifier.weight(1f),
    )
    leading?.invoke()
    Text(
      text = value,
      style = XmlText.body,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 8.dp),
    )
    Icon(
      painter = painterResource(R.drawable.ic_chevron_right_24dp),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(start = 8.dp).size(16.dp),
    )
  }
}

@Composable
private fun SetupFooter(
  state: SetupPageState,
  onGpsChipClick: () -> Unit,
  onStartRunClick: () -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
      if (state.gpsChipVisible) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable(onClick = onGpsChipClick),
        ) {
          Icon(
            painter = painterResource(state.gpsIndicatorRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.padding(end = 6.dp).size(16.dp),
          )
          Text(
            text = stringResource(state.gpsMessageRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (state.gpsPopupVisible) {
        GpsPopup(state)
      }
      Button(
        onClick = onStartRunClick,
        enabled = state.startRunEnabled,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      ) {
        Text(text = stringResource(R.string.Start_run), fontSize = 18.sp)
      }
    }
  }
}

@Composable
private fun GpsPopup(state: SetupPageState) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Text(
        text = stringResource(R.string.GPS_signal_info),
        style = XmlText.body,
        color = themeAttrColor(android.R.attr.textColorPrimary),
        fontWeight = FontWeight.Bold,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
      ) {
        Icon(
          painter = painterResource(state.gpsPopupIndicatorRes),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.padding(end = 6.dp).size(18.dp),
        )
        Text(
          text = state.gpsPopupMessage,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      state.gpsPopupSatellites?.let { satellites ->
        Text(
          text = satellites,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}
