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

import android.graphics.Paint
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role.Companion.RadioButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.function.Consumer
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText

/** A chip of the sport filter: an icon, a label and the number of activities of that sport. */
class SportChipItem(
  val label: String,
  val iconRes: Int,
  val iconTint: Int,
  val badgeCount: Int,
  val badgeColor: Int,
)

class HistorySportFilterState {
  var chips by mutableStateOf<List<SportChipItem>>(emptyList())
  var selectedPosition by mutableStateOf(0)
}

object HistorySportFilterHost {
  fun attach(
    composeView: ComposeView,
    state: HistorySportFilterState,
    onSelect: Consumer<Int>,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        HistorySportFilter(state = state, onSelect = { onSelect.accept(it) })
      }
    }
  }
}

@Composable
fun HistorySportFilter(state: HistorySportFilterState, onSelect: (Int) -> Unit) {
  LazyRow(
    modifier = Modifier.fillMaxWidth().disallowInterceptByParent(),
    contentPadding = PaddingValues(start = 16.dp, end = 24.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    itemsIndexed(state.chips) { index, chip ->
      SportChip(
        chip = chip,
        checked = index == state.selectedPosition,
        onClick = { onSelect(index) },
      )
    }
  }
}

/**
 * The platform chips are Material 3 assist chips: a 32dp container with an 8dp corner radius inside
 * the 48dp touch target, filled with `colorSecondaryContainer` and drawn without an outline while
 * it is checked, `colorSurface` with a `colorOutline` outline while it is not.
 */
@Composable
private fun SportChip(chip: SportChipItem, checked: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(8.dp)
  val sportLabel = if (chip.badgeCount > 0) "${chip.label} ${chip.badgeCount}" else chip.label
  Box(
    // The platform chips are checkable, a `ChipGroup` with `singleSelection` reports them as radio
    // buttons to accessibility services.
    modifier =
      Modifier.height(48.dp)
        .selectable(selected = checked, role = RadioButton, onClick = onClick)
        .clearAndSetSemantics {
          selected = checked
          this.role = RadioButton
          contentDescription = sportLabel
        },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.height(32.dp)
          .clip(shape)
          .background(
            if (checked) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              MaterialTheme.colorScheme.surface
            }
          )
          .let {
            if (checked) it else it.border(1.dp, MaterialTheme.colorScheme.outline, shape)
          }
          .padding(start = 8.dp, end = 16.dp),
    ) {
      if (chip.iconRes != 0) {
        Icon(
          painter = painterResource(chip.iconRes),
          contentDescription = null,
          tint = Color(chip.iconTint),
          modifier = Modifier.size(18.dp),
        )
      }
      // `textStartPadding` separates the label from the icon, and is kept without an icon.
      Spacer(Modifier.width(8.dp))
      Text(
        text = chip.label,
        style = XmlText.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        // The platform centers the baseline of the label on the middle of the font metrics, which
        // is a little higher than the middle of the line box Compose centers.
        modifier = Modifier.offset(y = (-1).dp),
      )
      if (chip.badgeCount > 0) {
        // The platform appends a space and the count to the label as one span. The label keeps the
        // letter spacing behind its last glyph while the count is drawn without any, which leaves
        // one pixel less than a full space between them.
        Spacer(Modifier.width(3.dp))
        CountBadge(count = chip.badgeCount, color = chip.badgeColor)
      }
    }
  }
}

/** The count is drawn by the platform as a rounded rect that is as tall as the font metrics. */
@Composable
private fun CountBadge(count: Int, color: Int) {
  Box(
    modifier =
      Modifier.height(rememberLabelFontHeight())
        .clip(RoundedCornerShape(percent = 50))
        .background(Color(color))
        .padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = count.toString(),
      // `PillSpan` measures the count with `Paint.measureText`, which knows no letter spacing.
      style = XmlText.labelLarge.copy(letterSpacing = 0.sp),
      color = Color.White,
      // `PillSpan` centers the baseline on the middle of the font metrics, which is a little
      // higher than the middle of the line box Compose centers.
      modifier = Modifier.offset(y = (-1).dp),
    )
  }
}

@Composable
private fun rememberLabelFontHeight(): Dp {
  val density = LocalDensity.current
  return remember(density) {
    val paint =
      Paint().apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = with(density) { XmlText.labelLarge.fontSize.toPx() }
      }
    val fm = paint.fontMetrics
    with(density) { (fm.descent - fm.ascent).toDp() }
  }
}

/** The platform scroller keeps the touch events of the chips away from the view pager. */
@Composable
private fun Modifier.disallowInterceptByParent(): Modifier {
  val view = LocalView.current
  return pointerInput(view) {
    awaitPointerEventScope {
      while (true) {
        if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.changedToDown() }) {
          var parent = view.parent
          while (parent != null) {
            (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
            parent = parent.parent
          }
        }
      }
    }
  }
}
