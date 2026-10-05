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

import android.widget.EditText
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.function.Consumer
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** A row of the account table: a label with an optional control. */
class AccountTableRowItem(
  val label: String,
  val control: AccountControl? = null,
)

/** The control of a table row, identified by the tag the platform rows carry. */
sealed class AccountControl {
  class Check(val tag: Any, checked: Boolean, val onChange: AccountCheckedChange) :
    AccountControl() {
    /** The platform re-checks the last file format, so the page owns the state. */
    var checked by mutableStateOf(checked)
  }

  /** The single line edit text of the RunnerUp LIVE service. */
  class Text(val text: String, val onChange: Consumer<String>) : AccountControl()
}

fun interface AccountCheckedChange {
  fun onCheckedChange(item: AccountControl.Check, checked: Boolean)
}

class AccountPageState {
  var title by mutableStateOf("")
  var headerText by mutableStateOf<String?>(null)
  var headerIcon by mutableStateOf(0)
  var headerUrl by mutableStateOf<String?>(null)
  var rows by mutableStateOf<List<AccountTableRowItem>>(emptyList())
  var uploadVisible by mutableStateOf(false)
  var downloadVisible by mutableStateOf(false)

  /** The window bounds of the overflow button, which anchor the platform popup menu. */
  var overflowBounds by mutableStateOf(Rect.Zero)
}

fun interface AccountOverflowMenu {
  fun onOverflowMenu(left: Int, top: Int, right: Int, bottom: Int)
}

class AccountBindings(
  val onNavigateUp: Runnable,
  val onHeaderClick: Runnable,
  val onOverflowMenu: AccountOverflowMenu,
  val onDisconnect: Runnable,
  val onUpload: Runnable,
  val onDownload: Runnable,
  val onOk: Runnable,
)

object AccountScreenHost {
  fun attach(composeView: ComposeView, state: AccountPageState, bindings: AccountBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { AccountPage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun AccountPage(state: AccountPageState, bindings: AccountBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    AccountToolbar(state = state, bindings = bindings)
    AccountHeader(state = state, onClick = bindings.onHeaderClick::run)
    Spacer(Modifier.height(32.dp))
    Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
      // The platform layout starts the table with an empty row.
      Spacer(Modifier.height(48.dp))
      val labelStyle = XmlText.body
      val measurer = rememberTextMeasurer()
      val density = LocalDensity.current
      // The platform table gives its first column the width of the widest label.
      val labelWidth =
        with(density) {
          state.rows.map { measurer.measure(it.label, labelStyle).size.width }.maxOrNull()?.toDp()
        }
      state.rows.forEach { AccountRow(item = it, labelWidth = labelWidth) }
    }
    AccountButton(
      label = stringResource(CommonR.string.Disconnect),
      containerColor = MaterialTheme.colorScheme.error,
      onClick = bindings.onDisconnect::run,
    )
    if (state.uploadVisible) {
      AccountButton(
        label = stringResource(CommonR.string.Upload),
        containerColor = MaterialTheme.colorScheme.primary,
        onClick = bindings.onUpload::run,
      )
    }
    if (state.downloadVisible) {
      AccountButton(
        label = stringResource(CommonR.string.Download),
        containerColor = MaterialTheme.colorScheme.primary,
        onClick = bindings.onDownload::run,
      )
    }
    AccountButton(
      label = stringResource(CommonR.string.OK),
      containerColor = MaterialTheme.colorScheme.primary,
      onClick = bindings.onOk::run,
    )
  }
}

/** The platform app bar, whose overflow menu stays a platform popup menu. */
@Composable
private fun AccountToolbar(state: AccountPageState, bindings: AccountBindings) {
  val upLabel = stringResource(AppCompatR.string.abc_action_bar_up_description)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(64.dp),
  ) {
    Box(
      modifier =
        Modifier.padding(start = 4.dp)
          .size(48.dp)
          .clickable(onClick = bindings.onNavigateUp::run)
          .semantics { contentDescription = upLabel },
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
      text = state.title,
      style = XmlText.title,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
    Spacer(Modifier.width(8.dp))
    Box(
      modifier =
        Modifier.size(width = 40.dp, height = 48.dp)
          .onGloballyPositioned { state.overflowBounds = it.boundsInWindow() }
          .clickable {
            val bounds = state.overflowBounds
            bindings.onOverflowMenu.onOverflowMenu(
              bounds.left.toInt(),
              bounds.top.toInt(),
              bounds.right.toInt(),
              bounds.bottom.toInt(),
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_more_vert),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(24.dp).offset(x = (-2).dp),
      )
    }
  }
}

/** The platform shows either the name or the icon of the service, centered above the table. */
@Composable
private fun AccountHeader(state: AccountPageState, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        // 16dp above and below the header, plus the 8dp margin of the platform name.
        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (state.headerText != null) {
      Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = state.headerText!!,
          // `AccountListText` only sets the text size, everything else is the default.
          style = XmlText.body.copy(fontSize = 18.sp),
          color = themeAttrColor(android.R.attr.textColorPrimary),
          modifier = Modifier.offset(y = (-1.5).dp),
        )
      }
    } else if (state.headerIcon != 0) {
      val painter = painterResource(state.headerIcon)
      val density = LocalDensity.current
      Box(
        modifier =
          Modifier.size(48.dp)
            .clickable(onClick = onClick)
            .then(
              with(density) {
                Modifier.size(
                  painter.intrinsicSize.width.toDp().coerceAtMost(48.dp),
                  painter.intrinsicSize.height.toDp().coerceAtMost(48.dp),
                )
              }
            )
      ) {
        Icon(painter = painter, contentDescription = null, tint = Color.Unspecified)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRow(item: AccountTableRowItem, labelWidth: Dp?) {
  // A platform row is as high as its control, or 24dp when it only carries a label.
  // The platform table row is 24dp without a control and 32dp with one.
  val control = item.control
  Row(
    // A row without a control keeps its label at the top, the table centers the rest.
    verticalAlignment = if (control == null) Alignment.Top else Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(if (control == null) 24.dp else 32.dp),
  ) {
    Text(
      text = item.label,
      style = XmlText.body,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      modifier = if (labelWidth == null) Modifier else Modifier.width(labelWidth),
    )
    when (control) {
      is AccountControl.Check ->
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
          Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Checkbox(
              checked = control.checked,
              onCheckedChange = { control.onChange.onCheckedChange(control, it) },
              colors =
                CheckboxDefaults.colors(
                  checkedColor = MaterialTheme.colorScheme.primary,
                  uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              // The platform checkbox draws an 18dp box, the compose one is 20dp.
              modifier = Modifier.scale(0.9f),
            )
          }
        }

      is AccountControl.Text ->
        AndroidView(
          factory = { ctx ->
            EditText(ctx).apply {
              isSingleLine = true
              setText(control.text)
              minWidth = 48.dp.value.toInt()
              minHeight = 48.dp.value.toInt()
            }
          },
          update = { view ->
            if (view.text.toString() != control.text) {
              view.setText(control.text)
            }
          },
          modifier = Modifier.width(220.dp),
        )

      null -> Spacer(Modifier.width(32.dp))
    }
  }
}

/** The platform buttons are `ButtonText`: 48dp high with a 3dp margin around them. */
@Composable
private fun AccountButton(label: String, containerColor: Color, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    shape = MaterialTheme.shapes.extraLarge,
    colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    contentPadding = PaddingValues(0.dp),
    modifier = Modifier.fillMaxWidth().padding(3.dp).height(48.dp),
  ) {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.offset(y = 1.dp),
    )
  }
}
