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

import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** The height of the toolbar, which is what the platform `MaterialToolbar` measures itself as. */
private val toolbarHeight = 64.dp

/** The margin the platform puts before the navigation icon, and before the title when alone. */
private val toolbarStartInset = 16.dp

/** The gap between the navigation icon and the title, measured from the platform layout. */
private val toolbarIconGap = 20.dp

/** The size the platform draws the navigation icon at. */
private val toolbarIconSize = 24.dp

/**
 * Holds the state of the settings toolbar.
 *
 * <p>The fragment decides which settings level is showing, so it pushes the title and whether the
 * up affordance is there in rather than compose working it out.
 */
class SettingsToolbarHolder {
  private val titleState = mutableStateOf("")
  private val showBackState = mutableStateOf(false)
  private var backClick: Runnable = Runnable {}

  /** The title of the settings level currently showing, or empty when nothing is showing yet. */
  val title: String
    get() = titleState.value

  fun setRoot() {
    titleState.value = ""
    showBackState.value = false
  }

  fun setSubScreen(title: CharSequence) {
    titleState.value = title.toString()
    showBackState.value = true
  }

  fun setOnBackClickListener(listener: Runnable) {
    backClick = listener
  }

  fun bind(composeView: ComposeView) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        SettingsToolbar(
          title = titleState.value.ifEmpty { stringResource(CommonR.string.Settings) },
          showBack = showBackState.value,
          onBack = { backClick.run() },
        )
      }
    }
  }
}

/**
 * The settings app bar.
 *
 * <p>This replaces the `MaterialToolbar` that used to draw it. The fragment container below it
 * stays, because the preference screens are still fragments.
 */
@Composable
fun SettingsToolbar(title: String, showBack: Boolean, onBack: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .height(toolbarHeight)
        .background(MaterialTheme.colorScheme.surface)
        .padding(start = toolbarStartInset),
  ) {
    if (showBack) {
      Icon(
        painter = painterResource(AppCompatR.drawable.abc_ic_ab_back_material),
        contentDescription = null,
        // The up indicator vector tints itself with `colorControlNormal`, and the toolbar does not
        // override it, so that is the attribute the platform arrow actually resolves.
        tint = themeAttrColor(android.R.attr.colorControlNormal),
        modifier = Modifier.size(toolbarIconSize).clickable(onClick = onBack),
      )
      Spacer(Modifier.width(toolbarIconGap))
    }
    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.CenterStart,
    ) {
      Text(
        text = title,
        style = XmlText.title,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // The toolbar centers the baseline on the middle of the font metrics, a little higher
        // than the middle of the line box compose centers.
        modifier = Modifier.offset(y = (-0.5).dp),
      )
    }
  }
}
