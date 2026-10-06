/*
 * Copyright (C) 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** The header row sits 8dp below the app bar, which is where the platform put it. */
private val headerTopMargin = 8.dp

/** The height the platform's header row measures at, one line of `textAppearanceBodyLarge`. */
private val headerHeight = 21.5.dp

/** The device block is a flat `colorSurfaceContainer` band with the two values inside it. */
private val deviceBlockHeight = 51.dp

/** The platform buttons are pills: `Widget.Material3.Button` with a fully rounded shape. */
private val buttonHeight = 48.dp
private val buttonShape = RoundedCornerShape(percent = 50)
private val buttonMargin = 3.dp

class HrSettingsPageState {
  private var deviceNameState by mutableStateOf("")
  private var heartRateState by mutableStateOf("")
  private var batteryState by mutableStateOf("")
  private var batteryVisibleState by mutableStateOf(false)
  private var logState by mutableStateOf("")
  private var scanEnabledState by mutableStateOf(true)
  private var connectEnabledState by mutableStateOf(false)

  @StringRes private var connectLabelResState: Int = CommonR.string.Connect

  /** The name of the connected device, empty when there is none. */
  val deviceName: String
    get() = deviceNameState

  /** The heart rate, or a `?` while a connection is being made. */
  val heartRate: String
    get() = heartRateState

  /** The battery level line, which the platform only shows once a device reports one. */
  val battery: String
    get() = batteryState

  /** Whether the battery line should be on screen at all. */
  val batteryVisible: Boolean
    get() = batteryVisibleState

  /** The provider log, newest line first. */
  val log: String
    get() = logState

  val scanEnabled: Boolean
    get() = scanEnabledState

  val connectEnabled: Boolean
    get() = connectEnabledState

  @get:StringRes
  val connectLabelRes: Int
    get() = connectLabelResState

  fun setDeviceName(value: String) {
    deviceNameState = value
  }

  fun setHeartRate(value: String) {
    heartRateState = value
  }

  fun setBattery(value: String) {
    batteryState = value
  }

  fun setBatteryVisible(value: Boolean) {
    batteryVisibleState = value
  }

  fun setLog(value: String) {
    logState = value
  }

  fun setScanEnabled(value: Boolean) {
    scanEnabledState = value
  }

  fun setConnectEnabled(value: Boolean) {
    connectEnabledState = value
  }

  fun setConnectLabelRes(@StringRes value: Int) {
    connectLabelResState = value
  }
}

class HrSettingsBindings(
  /** The graph, which stays a platform view the same way it does on the detail screen. */
  val graphView: View,
  val onScan: Runnable,
  val onConnect: Runnable,
  val onOverflow: OverflowMenuListener,
  val overflowButton: OverflowButtonBounds,
  val onUp: Runnable,
  val rootTitleRes: Int,
)

object HrSettingsHost {
  fun attach(composeView: ComposeView, state: HrSettingsPageState, bindings: HrSettingsBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent { RunnerUpTheme { HrSettingsPage(state, bindings) } }
  }
}

/**
 * The heart rate monitor screen.
 *
 * <p>Everything but the graph is drawn here. The graph stays a platform view, because the detail
 * screen already draws the same view the same way.
 */
@Composable
fun HrSettingsPage(state: HrSettingsPageState, bindings: HrSettingsBindings) {
  Column(modifier = Modifier.fillMaxSize()) {
    SettingsToolbar(
      title = "",
      showBack = true,
      onBack = { bindings.onUp.run() },
      showOverflow = true,
      onOverflow = bindings.onOverflow,
      overflowButton = bindings.overflowButton,
      rootTitle = bindings.rootTitleRes,
    )
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Spacer(Modifier.height(headerTopMargin))
      DeviceHeader()
      DeviceBlock(state)
      Spacer(Modifier.height(headerTopMargin))
      GraphAndLog(state, bindings)
      Spacer(Modifier.height(11.dp))
      HrButton(
        labelRes = CommonR.string.Scan,
        enabled = state.scanEnabled,
        onClick = { bindings.onScan.run() },
      )
      Spacer(Modifier.height(buttonMargin * 2))
      HrButton(
        labelRes = state.connectLabelRes,
        enabled = state.connectEnabled,
        onClick = { bindings.onConnect.run() },
      )
      Spacer(Modifier.height(buttonMargin))
    }
  }
}

/**
 * The `Device` and `Heart rate` captions, which the platform drew as two equally wide italic
 * `textAppearanceBodyLarge` text views, one flushed left and one flushed right.
 */
@Composable
private fun DeviceHeader() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp),
  ) {
    Caption(CommonR.string.Device, Modifier.weight(1f), TextAlign.Start)
    Caption(CommonR.string.Heart_rate, Modifier.weight(1f), TextAlign.End)
  }
}

@Composable
private fun Caption(labelRes: Int, modifier: Modifier, align: TextAlign) {
  Text(
    text = stringResource(labelRes),
    style = XmlText.bodyLarge.copy(fontStyle = FontStyle.Italic),
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = align,
    maxLines = 1,
    modifier = modifier,
  )
}

/** The band holding the device name on the left and the heart rate flushed right. */
@Composable
private fun DeviceBlock(state: HrSettingsPageState) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .height(deviceBlockHeight)
        .padding(horizontal = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.Bottom,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Value(state.deviceName, Modifier.weight(1f), TextAlign.Start)
      Value(state.heartRate, Modifier.weight(1f), TextAlign.End)
    }
  }
}

@Composable
private fun Value(text: String, modifier: Modifier, align: TextAlign) {
  Text(
    text = text,
    style = XmlText.title,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = align,
    maxLines = 1,
    modifier = modifier,
  )
}

/**
 * The graph on top and the provider log below it, split evenly, with the battery line in between
 * when the device has reported one. The platform gave each of the two halves an equal weight and
 * gave the battery line none.
 */
@Composable
private fun ColumnScope.GraphAndLog(state: HrSettingsPageState, bindings: HrSettingsBindings) {
  Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
    AndroidView(
      factory = { bindings.graphView },
      // The graph clips its own drawing, which compose would not do on its own: the last x axis
      // label is wider than the plot and the platform cuts it off.
      modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
    )
    if (state.batteryVisible) {
      Text(
        text = state.battery,
        style = XmlText.titleMedium,
        color = themeAttrColor(android.R.attr.textColorPrimary),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
      )
    }
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Text(
        text = state.log,
        style = XmlText.bodyMedium,
        color = themeAttrColor(android.R.attr.textColorPrimary),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      )
    }
  }
}

/** A pill shaped button filled with `colorPrimary`, labelled `colorOnPrimary` while enabled. */
@Composable
private fun HrButton(labelRes: Int, enabled: Boolean, onClick: () -> Unit) {
  val label = stringResource(labelRes)
  val onPrimary = MaterialTheme.colorScheme.onPrimary
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = buttonMargin)
        .height(buttonHeight)
        .clip(buttonShape)
        .background(MaterialTheme.colorScheme.primary)
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = XmlText.labelLarge,
      color =
        if (enabled) {
          onPrimary
        } else {
          // `@color/btn_text_color` puts `colorOnSurface` at 38% over the button when disabled.
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
      maxLines = 1,
    )
  }
}
