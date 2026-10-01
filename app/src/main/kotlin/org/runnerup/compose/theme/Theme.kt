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
package org.runnerup.compose.theme

import android.content.Context
import androidx.appcompat.R as AppCompatR
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.R

@Composable
fun RunnerUpTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val colorScheme = remember(context) { colorSchemeFromTheme(context) }
  MaterialTheme(colorScheme = colorScheme, content = content)
}

private val THEME_ATTRS =
  intArrayOf(
    AppCompatR.attr.colorPrimary,
    R.attr.colorOnPrimary,
    R.attr.colorPrimaryContainer,
    R.attr.colorOnPrimaryContainer,
    R.attr.colorSecondary,
    R.attr.colorOnSecondary,
    R.attr.colorSecondaryContainer,
    R.attr.colorOnSecondaryContainer,
    R.attr.colorTertiary,
    R.attr.colorOnTertiary,
    R.attr.colorTertiaryContainer,
    R.attr.colorOnTertiaryContainer,
    AppCompatR.attr.colorError,
    R.attr.colorOnError,
    R.attr.colorErrorContainer,
    R.attr.colorOnErrorContainer,
    R.attr.colorSurface,
    R.attr.colorOnSurface,
    R.attr.colorSurfaceVariant,
    R.attr.colorOnSurfaceVariant,
    R.attr.colorSurfaceContainerLowest,
    R.attr.colorSurfaceContainerLow,
    R.attr.colorSurfaceContainer,
    R.attr.colorSurfaceContainerHigh,
    R.attr.colorSurfaceContainerHighest,
    R.attr.colorOutline,
    R.attr.colorOutlineVariant,
    R.attr.colorSurfaceInverse,
    R.attr.colorOnSurfaceInverse,
  )

private fun colorSchemeFromTheme(context: Context): ColorScheme {
  val styled = context.theme.obtainStyledAttributes(THEME_ATTRS)
  fun color(index: Int) = Color(styled.getColor(index, 0))
  val scheme =
    lightColorScheme(
      primary = color(0),
      onPrimary = color(1),
      primaryContainer = color(2),
      onPrimaryContainer = color(3),
      secondary = color(4),
      onSecondary = color(5),
      secondaryContainer = color(6),
      onSecondaryContainer = color(7),
      tertiary = color(8),
      onTertiary = color(9),
      tertiaryContainer = color(10),
      onTertiaryContainer = color(11),
      error = color(12),
      onError = color(13),
      errorContainer = color(14),
      onErrorContainer = color(15),
      surface = color(16),
      onSurface = color(17),
      surfaceVariant = color(18),
      onSurfaceVariant = color(19),
      surfaceContainerLowest = color(20),
      surfaceContainerLow = color(21),
      surfaceContainer = color(22),
      surfaceContainerHigh = color(23),
      surfaceContainerHighest = color(24),
      outline = color(25),
      outlineVariant = color(26),
      inverseSurface = color(27),
      inverseOnSurface = color(28),
      background = color(16),
      onBackground = color(17),
    )
  styled.recycle()
  return scheme
}
