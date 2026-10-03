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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.function.Consumer
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** One activity of the upload list. */
class UploadRowItem(
  val id: Long,
  val startTime: String,
  val sport: String,
  val checked: Boolean,
  val enabled: Boolean,
)

class UploadPageState {
  var title by mutableStateOf("")
  var accountName by mutableStateOf<String?>(null)
  var accountIcon by mutableStateOf(0)
  var rows by mutableStateOf<List<UploadRowItem>>(emptyList())
  var actionLabel by mutableStateOf("")
  var actionEnabled by mutableStateOf(false)
}

class UploadBindings(
  val onNavigateUp: Runnable,
  val onRowClick: Consumer<Long>,
  val onRowCheckedChange: CheckedChange,
  val onSelectAll: Runnable,
  val onClearAll: Runnable,
  val onAction: Runnable,
)

fun interface CheckedChange {
  fun onCheckedChange(id: Long, checked: Boolean)
}

object UploadScreenHost {
  fun attach(composeView: ComposeView, state: UploadPageState, bindings: UploadBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { UploadPage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun UploadPage(state: UploadPageState, bindings: UploadBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    UploadToolbar(title = state.title, onNavigateUp = bindings.onNavigateUp::run)
    UploadAccountHeader(name = state.accountName, icon = state.accountIcon)
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
      items(state.rows, key = { it.id }) { row ->
        UploadRow(
          row = row,
          onClick = { bindings.onRowClick.accept(row.id) },
          onCheckedChange = { bindings.onRowCheckedChange.onCheckedChange(row.id, it) },
        )
      }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      UploadButton(
        label = stringResource(CommonR.string.Select_all),
        onClick = bindings.onSelectAll::run,
        modifier = Modifier.weight(1f),
      )
      UploadButton(
        label = stringResource(CommonR.string.Clear_all),
        onClick = bindings.onClearAll::run,
        modifier = Modifier.weight(1f),
      )
    }
    UploadButton(
      label = state.actionLabel,
      onClick = bindings.onAction::run,
      modifier = Modifier.fillMaxWidth(),
      enabled = state.actionEnabled,
    )
  }
}

@Composable
private fun UploadToolbar(title: String, onNavigateUp: () -> Unit) {
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
      text = title,
      style = XmlText.title,
      // The toolbar title appearance takes its color from `android:textColorPrimary`.
      color = themeAttrColor(android.R.attr.textColorPrimary),
      // The platform centers the title half a pixel higher than Compose does.
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
  }
}

/** The platform layout centers either the name of the account or its icon above the list. */
@Composable
private fun UploadAccountHeader(name: String?, icon: Int) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (name != null) {
      Text(
        text = name,
        // `AccountListText` only sets the text size, everything else is the default.
        style = XmlText.body.copy(fontSize = 18.sp),
        color = themeAttrColor(android.R.attr.textColorPrimary),
      )
    } else if (icon != 0) {
      Icon(painter = painterResource(icon), contentDescription = null, tint = Color.Unspecified)
    }
  }
}

@Composable
private fun UploadRow(row: UploadRowItem, onClick: () -> Unit, onCheckedChange: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().height(64.dp).padding(8.dp).clickable(onClick = onClick),
  ) {
    Text(
      text = row.startTime,
      style = XmlText.body,
      color = themeAttrColor(android.R.attr.textColorPrimary),
    )
    Text(
      text = row.sport,
      style = XmlText.body,
      color = themeAttrColor(android.R.attr.textColorPrimary),
      textAlign = TextAlign.Start,
      modifier = Modifier.weight(1f).padding(start = 8.dp),
    )
    Checkbox(
      checked = row.checked,
      onCheckedChange = onCheckedChange,
      enabled = row.enabled,
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

/**
 * The platform buttons are `ButtonText`: 48dp high with a 3dp margin around them. The tint has no
 * disabled state, so a disabled button keeps its primary background and dims the label with
 * `btn_text_color`.
 */
@Composable
private fun UploadButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.extraLarge,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        // The platform tint has no disabled state, the button keeps its background.
        disabledContainerColor = MaterialTheme.colorScheme.primary,
      ),
    contentPadding = PaddingValues(0.dp),
    modifier = modifier.padding(3.dp).height(48.dp),
  ) {
    Text(
      text = label,
      color =
        if (enabled) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
      modifier = Modifier.offset(y = 1.dp),
    )
  }
}
