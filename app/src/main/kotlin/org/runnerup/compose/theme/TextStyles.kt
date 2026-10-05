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

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Text styles that match how the platform renders the text appearances of the XML theme.
 *
 * Compose leaves the font padding out of its text layout, which makes every text a few dp shorter
 * than the `TextView` it replaces. The styles below ask for the platform measurement again, so the
 * ported layouts stay as close to pixel identical to the layouts they replace as possible.
 */
object XmlText {
  /** Default `TextView` text: 14sp without letter spacing, with the platform's font padding. */
  val body =
    TextStyle(
      fontSize = 14.sp,
      letterSpacing = 0.sp,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

  /** `textAppearanceTitleLarge`, used for the toolbar titles. */
  val title =
    TextStyle(fontSize = 22.sp, platformStyle = PlatformTextStyle(includeFontPadding = true))

  /** `textAppearanceTitleMedium`, used for the statistics below a toolbar. */
  val titleMedium =
    TextStyle(
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

  /** `textAppearanceTitleSmall`, used for the tab labels. */
  val titleSmall =
    TextStyle(
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

  /** `textAppearanceBodyLarge`, used for list item titles. */
  val bodyLarge =
    TextStyle(
      fontSize = 16.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.035714286.em,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

  /** `textAppearanceLabelLarge`, used for the labels of the sport filter chips. */
  val labelLarge =
    TextStyle(
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 0.00714286.em,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

  /** `textAppearanceBodyMedium`, used for the description of the history empty state. */
  val bodyMedium =
    TextStyle(
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.017857142.em,
      platformStyle = PlatformTextStyle(includeFontPadding = true),
    )
}
