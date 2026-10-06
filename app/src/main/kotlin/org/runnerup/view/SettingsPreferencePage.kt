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

import android.widget.CheckBox
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import java.util.function.Consumer
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/**
 * `?android:attr/textAppearanceListItem`, which the material theme resolves to
 * `textAppearanceTitleMedium`. The preference layout uses it for the row title.
 */
private val preferenceTitleStyle =
  TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.15.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
  )

/** `?android:attr/textAppearanceSmall`, which the preference summary uses. */
private val preferenceSummaryStyle = XmlText.bodyMedium

/** The row height `?android:attr/listPreferredItemHeightSmall` resolves to. */
private val rowMinHeight = 48.dp

/** The thickness of the divider the library draws above every category but the first. */
private val dividerThickness = 1.dp

/** The divider colour, measured from what the library draws. */
private val dividerColor = Color(0xFFE0E0E0)

object PreferenceScreenHost {
  @JvmStatic
  fun attach(
    composeView: ComposeView,
    preferences: List<Preference>,
    onClick: Consumer<Preference>,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { PreferenceList(preferences = preferences, onClick = onClick::accept) }
    }
  }
}

/** One entry of a settings screen: either a category header or a preference. */
private sealed interface Entry {
  val preference: Preference

  data class Divider(override val preference: Preference) : Entry

  data class Category(override val preference: Preference, val showDivider: Boolean) : Entry

  data class Row(override val preference: Preference) : Entry
}

/**
 * The preference screens, drawn the way `PreferenceFragmentCompat` draws them: a category header in
 * the accent colour, then a row per preference with a title, an optional summary and, for the two
 * state preferences, a checkbox. A divider goes above every category except the first.
 */
@Composable
fun PreferenceList(preferences: List<Preference>, onClick: (Preference) -> Unit) {
  val entries = remember(preferences) { flatten(preferences) }
  val onSurface = themeAttrColor(com.google.android.material.R.attr.colorOnSurface)
  val summaryColor = themeAttrColor(android.R.attr.textColorSecondary)
  val accent = themeAttrColor(AppCompatR.attr.colorPrimary)
  LazyColumn(modifier = Modifier.fillMaxWidth()) {
    entries.forEachIndexed { index, entry ->
      when (entry) {
        is Entry.Divider ->
          item(key = "divider:$index") {
            Box(
              modifier = Modifier.fillMaxWidth().height(dividerThickness).background(dividerColor)
            )
          }
        is Entry.Category ->
          item(key = "category:${entry.preference.key}") {
            Text(
              text = entry.preference.title?.toString().orEmpty(),
              style = XmlText.labelLarge,
              color = accent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            )
          }
        is Entry.Row ->
          item(key = "item:${entry.preference.key}") {
            PreferenceRow(
              preference = entry.preference,
              onClick = onClick,
              titleColor = onSurface,
              summaryColor = summaryColor,
            )
          }
      }
    }
  }
}

private fun flatten(preferences: List<Preference>): List<Entry> {
  val entries = mutableListOf<Entry>()
  var seenCategory = false
  for (preference in preferences) {
    if (preference is PreferenceCategory) {
      if (seenCategory) {
        entries.add(Entry.Divider(preference))
      }
      seenCategory = true
      entries.add(Entry.Category(preference, showDivider = !seenCategory))
    } else {
      entries.add(Entry.Row(preference))
    }
  }
  return entries
}

@Composable
private fun PreferenceRow(
  preference: Preference,
  onClick: (Preference) -> Unit,
  titleColor: Color,
  summaryColor: Color,
) {
  val enabled = preference.isEnabled
  val summary = preference.summary?.toString().orEmpty()
  val twoState = preference as? TwoStatePreference
  // A disabled preference has its title and its summary drawn in one and the same grey, which is
  // the surface colour at the alpha the library uses rather than each text dimmed separately.
  val disabled = titleColor.copy(alpha = 0.38f)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = rowMinHeight)
        .clickable(
          enabled = enabled,
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = { onClick(preference) },
        )
        .padding(start = 16.dp, end = 16.dp),
  ) {
    Column(modifier = Modifier.weight(1f).padding(top = 16.dp, bottom = 16.dp)) {
      Text(
        text = preference.title?.toString().orEmpty(),
        style = preferenceTitleStyle,
        color = if (enabled) titleColor else disabled,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (summary.isNotEmpty()) {
        Text(
          text = summary,
          style = preferenceSummaryStyle,
          color = if (enabled) summaryColor else disabled,
          maxLines = 10,
        )
      }
    }
    if (twoState != null) {
      Spacer(Modifier.width(16.dp))
      PreferenceCheckBox(preference = twoState, enabled = enabled)
    }
  }
}

/**
 * The `CheckBox` the library inflates for a two state preference. It stays a platform widget
 * because it is the platform that draws the box, and the row around it is compose.
 */
@Composable
private fun PreferenceCheckBox(preference: TwoStatePreference, enabled: Boolean) {
  val context = LocalContext.current
  val box = remember(context) { CheckBox(context) }
  AndroidView(
    factory = {
      box.apply {
        background = null
        isFocusable = false
        isClickable = false
      }
    },
    update = { view ->
      view.isChecked = preference.isChecked
      view.isEnabled = enabled
    },
    // The library lays the checkbox out in a frame that does not take part in the height of the
    // row, so it must not make the row taller either.
    modifier = Modifier.heightIn(max = 24.dp),
  )
}
