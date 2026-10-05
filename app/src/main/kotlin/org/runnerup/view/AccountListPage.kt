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

import android.graphics.PorterDuff
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.function.Consumer
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** One account of the list. */
class AccountRowItem(
  val name: String,
  val sectionTitle: String?,
  val available: Boolean,
  val iconRes: Int,
  val iconColor: Int,
  val showSwitch: Boolean,
  val uploadEnabled: Boolean,
)

class AccountListState {
  var rows by mutableStateOf<List<AccountRowItem>>(emptyList())
  var showDisabled by mutableStateOf(false)
}

class AccountListBindings(
  val onNavigateUp: Runnable,
  val onRowClick: Consumer<String>,
  val onUploadChange: UploadChange,
  val onShowDisabledClick: Runnable,
)

fun interface UploadChange {
  fun onUploadChange(name: String, checked: Boolean)
}

object AccountListScreenHost {
  fun attach(composeView: ComposeView, state: AccountListState, bindings: AccountListBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { AccountListPage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun AccountListPage(state: AccountListState, bindings: AccountListBindings) {
  val showDisabledLabel =
    stringResource(
      if (state.showDisabled) CommonR.string.Hide_disabled_accounts
      else CommonR.string.Show_disabled_accounts
    )
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    AppBar(
      title = stringResource(CommonR.string.Configure_accounts),
      onNavigateUp = bindings.onNavigateUp::run,
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
      items(state.rows, key = { it.name }) { row ->
        AccountRow(
          row = row,
          onClick = { bindings.onRowClick.accept(row.name) },
          onUploadChange = { bindings.onUploadChange.onUploadChange(row.name, it) },
        )
      }
    }
    TextButton(
      onClick = bindings.onShowDisabledClick::run,
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      // The platform centers the label ink, Compose centers the line box.
      Text(text = showDisabledLabel, modifier = Modifier.offset(y = 1.dp))
    }
  }
}

/**
 * The platform app bar is a 64dp toolbar with a 16dp start margin for the title, the navigation
 * button is 48dp and starts 4dp from the edge.
 */
@Composable
internal fun AppBar(title: String, onNavigateUp: () -> Unit) {
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
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
  }
}

@Composable
private fun AccountRow(
  row: AccountRowItem,
  onClick: () -> Unit,
  onUploadChange: (Boolean) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    if (row.sectionTitle != null) {
      Text(
        text = row.sectionTitle,
        style = XmlText.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
    ) {
      Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (row.iconRes != 0) {
          val painter = painterResource(row.iconRes)
          val density = LocalDensity.current
          Icon(
            painter = painter,
            contentDescription = null,
            tint = Color.Unspecified,
            // The platform draws the icon at its intrinsic size, capped by the 40dp frame around
            // it.
            modifier =
              with(density) {
                val width = painter.intrinsicSize.width.toDp().coerceAtMost(40.dp)
                val height = painter.intrinsicSize.height.toDp().coerceAtMost(40.dp)
                Modifier.requiredSize(width, height)
              },
          )
        } else {
          Box(
            modifier = Modifier.size(40.dp).background(Color(row.iconColor), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = row.name.take(1),
              color = Color.White,
              style = XmlText.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
          }
        }
      }
      // The platform name has a minimum height of 48dp and keeps the text at its top.
      Box(
        modifier = Modifier.weight(1f).padding(start = 16.dp).height(48.dp),
        contentAlignment = Alignment.TopStart,
      ) {
        Text(
          text = row.name,
          // `AccountListText` only sets the text size, everything else is the default.
          style = XmlText.body.copy(fontSize = 18.sp),
          color =
            if (row.available) {
              themeAttrColor(android.R.attr.textColorPrimary)
            } else {
              // The platform strikes through the accounts of unavailable services.
              themeAttrColor(android.R.attr.textColorPrimary).copy(alpha = 0.38f)
            },
          textDecoration = if (row.available) null else TextDecoration.LineThrough,
        )
      }
      if (row.showSwitch) {
        UploadSwitch(
          checked = row.uploadEnabled,
          onCheckedChange = onUploadChange,
          modifier = Modifier.padding(start = 16.dp),
        )
      }
    }
  }
}

/**
 * The platform switches keep a `SwitchCompat` with a custom thumb, which has no compose counterpart
 * that draws the same.
 */
@Composable
private fun UploadSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier) {
  val density = LocalDensity.current
  val minSize = with(density) { 48.dp.roundToPx() }
  AndroidView(
    factory = { ctx ->
      SwitchCompat(ctx).apply {
        thumbDrawable = ContextCompat.getDrawable(ctx, R.drawable.switch_upload)
        thumbTintList = ContextCompat.getColorStateList(ctx, R.color.switch_thumb)
        thumbTintMode = PorterDuff.Mode.MULTIPLY
        minimumWidth = minSize
        minimumHeight = minSize
        contentDescription = ctx.getString(CommonR.string.Upload)
        layoutParams =
          android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    },
    update = { view ->
      view.isChecked = checked
      view.setOnCheckedChangeListener(null)
      view.setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
    },
    modifier = modifier,
  )
}
