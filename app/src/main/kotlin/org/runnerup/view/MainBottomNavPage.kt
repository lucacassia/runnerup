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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.themeAttrColor

/** The height of the bar itself, which the bottom navigation view reports as 80dp. */
private val barHeight = 80.dp

/** The icon size a navigation bar item draws. */
private val itemIcon = 24.dp

/** The gap between an item's icon and its label, measured from where the platform puts them. */
private val itemIconGap = 7.dp

/** Half a dp up, which is where the platform puts the icon and the label against this item box. */
private val itemBaselineNudge = (-0.5).dp

/** `textAppearanceLabelMedium`, which is what the navigation bar labels use. */
private val itemLabelStyle =
  TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.04166667.em,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** One tab of the navigation bar. */
class BottomNavItem(val titleRes: Int, val iconRes: Int)

/**
 * Holds the state of the navigation bar.
 *
 * <p>The pager is what actually decides the tab, so the activity pushes the selected position in
 * rather than compose reading it, which compose would not see change.
 */
class BottomNavHolder(private val items: List<BottomNavItem>) {
  private val selectedState = mutableIntStateOf(0)
  private val visibleState = mutableStateOf(true)

  fun setSelected(position: Int) {
    selectedState.intValue = position
  }

  fun setVisible(visible: Boolean) {
    visibleState.value = visible
  }

  fun bind(composeView: ComposeView, onSelect: (Int) -> Unit) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        val selected = selectedState.intValue
        val visible = visibleState.value
        if (visible) {
          BottomNavBar(items = items, selected = selected, onSelect = onSelect)
        }
      }
    }
  }
}

/**
 * The bottom navigation bar.
 *
 * <p>This replaces the `BottomNavigationView` that used to draw it. The pager above it stays,
 * because the tabs are still fragments.
 */
@Composable
fun BottomNavBar(
  items: List<BottomNavItem>,
  selected: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedColor = themeAttrColor(AppCompatR.attr.colorPrimary)
  val unselectedColor = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainer))
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().height(barHeight),
    ) {
      items.forEachIndexed { index, item ->
        NavItem(
          item = item,
          selected = index == selected,
          selectedColor = selectedColor,
          unselectedColor = unselectedColor,
          onClick = { onSelect(index) },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun NavItem(
  item: BottomNavItem,
  selected: Boolean,
  selectedColor: Color,
  unselectedColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint = if (selected) selectedColor else unselectedColor
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier =
      modifier
        .offset(y = itemBaselineNudge)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        ),
  ) {
    Box(modifier = Modifier.size(itemIcon), contentAlignment = Alignment.Center) {
      Image(
        painter = painterResource(item.iconRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(itemIcon),
      )
    }
    Spacer(Modifier.height(itemIconGap))
    Text(
      text = stringResource(item.titleRes),
      style = itemLabelStyle,
      color = tint,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
