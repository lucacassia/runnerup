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

import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** The tabs of the detail screen, in the order they are shown. */
enum class DetailTab {
  NOTES,
  LAPS,
  MAP,
  GRAPH,
  UPLOAD,
}

/** One row of the lap list, with the columns of `laplist_row` already formatted. */
class LapRow(
  val type: String,
  val time: String,
  val distance: String,
  val pace: String,
  val heartRate: String?,
  val heartRateReserved: Boolean,
)

/** One account row of the upload tab. */
class ReportRow(
  val name: String,
  val checked: Boolean,
  val labelRes: Int,
  val clickable: Boolean,
)

fun interface DetailMenuItem {
  fun onMenuItem(id: Int)
}

/**
 * Opens the platform overflow menu. The bounds are the window bounds of the toolbar button, which
 * the activity uses as the anchor of the popup.
 */
fun interface OverflowMenu {
  fun onOverflowMenu(left: Int, top: Int, right: Int, bottom: Int)
}

fun interface ReportChecked {
  fun onReportChecked(name: String, checked: Boolean)
}

fun interface ReportLongClicked {
  fun onReportLongClicked(name: String)
}

fun interface ReportNameClicked {
  fun onReportNameClicked(name: String)
}

fun interface MapLapToggle {
  fun onMapLapToggle()
}

/**
 * The platform widgets of the detail screen. They keep their platform implementation and are only
 * placed by Compose, which owns everything around them.
 */
@Suppress("LongParameterList")
class DetailBindings(
  val sportView: View,
  val distanceView: View,
  val notesView: View,
  val hrZonesView: View,
  val mapView: View?,
  val graphView: View,
  val onNavigateUp: Runnable,
  val onMenuItem: DetailMenuItem,
  val onOverflowMenu: OverflowMenu,
  val onDiscard: Runnable,
  val onSave: Runnable,
  val onUpload: Runnable,
  val onMapLapToggle: MapLapToggle,
  val onReportChecked: ReportChecked,
  val onReportLongClicked: ReportLongClicked,
  val onReportNameClicked: ReportNameClicked,
  val onConfigureAccounts: Runnable,
)

class DetailPageState {
  var title by mutableStateOf("")
  var distanceText by mutableStateOf("")
  var timeText by mutableStateOf("")
  var paceText by mutableStateOf("")
  var paceVisible by mutableStateOf(false)
  var selectedTab by mutableStateOf(DetailTab.NOTES)
  var mapTabVisible by mutableStateOf(false)
  var manualDistanceVisible by mutableStateOf(false)
  var saveVisible by mutableStateOf(false)
  var discardVisible by mutableStateOf(false)
  var uploadVisible by mutableStateOf(false)
  var mapLapMarkersVisible by mutableStateOf(false)
  var laps by mutableStateOf<List<LapRow>>(emptyList())
  var reports by mutableStateOf<List<ReportRow>>(emptyList())
}

object DetailScreenHost {
  @Suppress("LongParameterList")
  fun attach(composeView: ComposeView, state: DetailPageState, bindings: DetailBindings) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme {
        DetailPage(state = state, bindings = bindings)
      }
    }
  }
}

@Composable
fun DetailPage(state: DetailPageState, bindings: DetailBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        // The window runs edge to edge behind the (transparent) navigation bar, the platform
        // layout did not inset the content for it either. The status bar (and a landscape cutout)
        // is handled here.
        .windowInsetsPadding(
          WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        )
        .background(MaterialTheme.colorScheme.surface)
  ) {
    DetailToolbar(state.title, bindings)
    StatisticsRow(state)
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Column(modifier = Modifier.fillMaxSize()) {
        DetailTabBar(state)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          when (state.selectedTab) {
            DetailTab.NOTES -> NotesPane(state, bindings)
            DetailTab.LAPS -> LapsPane(state.laps)
            DetailTab.MAP -> Unit
            DetailTab.GRAPH -> GraphPane(state, bindings)
            DetailTab.UPLOAD -> UploadPane(state, bindings)
          }
          MapPane(state, bindings, visible = state.selectedTab == DetailTab.MAP)
        }
      }
      // The platform layout pins the buttons to the bottom of the window, so they are drawn on
      // top of the tab content instead of shrinking it.
      DetailButtons(
        state = state,
        bindings = bindings,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

@Composable
private fun DetailToolbar(title: String, bindings: DetailBindings) {
  val upLabel = stringResource(AppCompatR.string.abc_action_bar_up_description)
  var bounds by remember { mutableStateOf(Rect.Zero) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Box(
      modifier =
        Modifier.padding(start = 4.dp)
          .size(48.dp)
          .clickable(onClick = bindings.onNavigateUp::run)
          .semantics {
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
      // The toolbar title appearance takes its color from `android:textColorPrimary`, not from
      // `colorOnSurface`.
      color = themeAttrColor(android.R.attr.textColorPrimary),
      // The platform centers the title half a pixel higher than Compose does.
      modifier = Modifier.weight(1f).padding(start = 8.dp).offset(y = (-0.5).dp),
    )
    Box(
      modifier =
        Modifier.size(48.dp)
          .onGloballyPositioned { bounds = it.boundsInWindow() }
          .clickable {
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
        modifier = Modifier.size(24.dp).offset(x = 2.dp),
      )
    }
  }
}

@Composable
private fun StatisticsRow(state: DetailPageState) {
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceContainer)
  ) {
    val density = LocalDensity.current
    val dividerWidth = with(density) { 1.dp.roundToPx() }
    val textWidth = with(density) { maxWidth.roundToPx() - 2 * 16.dp.roundToPx() }
    val texts = buildList {
      add(state.distanceText)
      add(state.timeText)
      if (state.paceVisible) {
        add(state.paceText)
      }
    }
    // The platform row is a weighted `LinearLayout`, which hands out the space left over by the
    // dividers as truncated shares, so the cells are not all equally wide.
    val widths = weightedWidths(textWidth - dividerWidth * (texts.size - 1), texts.size)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
    ) {
      texts.forEachIndexed { index, text ->
        Statistic(text, Modifier.width(with(density) { widths[index].toDp() }))
        if (index != texts.lastIndex) {
          StatisticDivider()
        }
      }
    }
  }
}

/** Splits [total] pixels over [count] cells the way a weighted `LinearLayout` does. */
private fun weightedWidths(total: Int, count: Int): IntArray {
  var delta = total
  return IntArray(count) { index ->
    val share = (delta.toFloat() / (count - index)).toInt()
    delta -= share
    share
  }
}

@Composable
private fun Statistic(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = XmlText.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
    // The platform centers the statistics half a pixel higher than Compose does.
    modifier = modifier.offset(y = (-0.5).dp),
  )
}

@Composable
private fun StatisticDivider() {
  Box(
    modifier =
      Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colorScheme.outlineVariant)
  )
}

@Composable
private fun DetailTabBar(state: DetailPageState) {
  val labels = linkedMapOf<DetailTab, String>()
  labels[DetailTab.NOTES] = stringResource(CommonR.string.Notes)
  labels[DetailTab.LAPS] = stringResource(CommonR.string.Laps)
  if (state.mapTabVisible) {
    labels[DetailTab.MAP] = stringResource(CommonR.string.Map)
  }
  labels[DetailTab.GRAPH] = stringResource(CommonR.string.Graph)
  labels[DetailTab.UPLOAD] = stringResource(CommonR.string.Upload)

  val labelStyle = XmlText.titleSmall
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
    // `TabLayout` draws a divider along its bottom edge, the indicator paints over it.
    Box(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(1.dp)
          .background(MaterialTheme.colorScheme.outlineVariant)
    )
    Row(modifier = Modifier.fillMaxSize()) {
      labels.forEach { (tab, label) ->
        val selected = state.selectedTab == tab
        Box(
          modifier = Modifier.weight(1f).fillMaxHeight().clickable { state.selectedTab = tab },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style = labelStyle,
            color =
              if (selected) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
          if (selected) {
            // The indicator spans the label, inset by the 2dp the indicator drawable adds.
            val width =
              with(density) { (measurer.measure(label, labelStyle).size.width - 8).toDp() }
            Box(
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .width(width)
                  .height(3.dp)
                  .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                  .background(MaterialTheme.colorScheme.primary)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NotesPane(state: DetailPageState, bindings: DetailBindings) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    AndroidView(
      factory = { bindings.sportView },
      modifier = Modifier.fillMaxWidth().onePixelTopMargin(),
    )
    if (state.manualDistanceVisible) {
      AndroidView(
        factory = { bindings.distanceView },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      )
    }
    AndroidView(
      factory = { bindings.notesView },
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    AndroidView(factory = { bindings.hrZonesView }, modifier = Modifier.fillMaxWidth().weight(1f))
  }
}

@Composable
private fun LapsPane(laps: List<LapRow>) {
  LazyColumn(
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize().padding(16.dp),
  ) {
    items(laps) { lap -> LapRowItem(lap) }
  }
}

@Composable
private fun LapRowItem(lap: LapRow) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
  ) {
    LapCell(lap.type, 0.8f)
    LapCell(lap.time, 1f)
    LapCell(lap.distance, 1f)
    LapCell(lap.pace, 1f)
    if (lap.heartRate != null) {
      LapCell(lap.heartRate, 1f)
    } else if (lap.heartRateReserved) {
      Spacer(modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun RowScope.LapCell(text: String, weight: Float) {
  Text(
    text = text,
    style = XmlText.body,
    // A `TextView` without an explicit textColor renders `android:textColorSecondary` here.
    color = themeAttrColor(android.R.attr.textColorSecondary),
    textAlign = TextAlign.End,
    modifier = Modifier.weight(weight).fillMaxWidth(),
  )
}

@Composable
private fun MapPane(state: DetailPageState, bindings: DetailBindings, visible: Boolean) {
  val mapView = bindings.mapView ?: return
  val fabElevation = if (state.mapLapMarkersVisible) 6.dp else 2.dp
  // The platform pane is a `FrameLayout`, which clips the map view to its bounds.
  Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
    // The platform layout keeps every pane attached to the window and only toggles the
    // visibility. Detaching the map view makes osmdroid shut its tile loader down, so the view
    // stays composed and is hidden instead.
    AndroidView(
      factory = { mapView },
      update = { it.visibility = if (visible) View.VISIBLE else View.GONE },
      modifier = Modifier.fillMaxSize(),
    )
    if (!visible) {
      return@Box
    }
    FloatingActionButton(
      onClick = { bindings.onMapLapToggle.onMapLapToggle() },
      elevation =
        FloatingActionButtonDefaults.elevation(
          defaultElevation = fabElevation,
          pressedElevation = fabElevation,
          focusedElevation = fabElevation,
          hoveredElevation = fabElevation,
        ),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_list_24dp),
        contentDescription = stringResource(CommonR.string.Laps),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
private fun GraphPane(state: DetailPageState, bindings: DetailBindings) {
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    AndroidView(
      factory = { bindings.graphView },
      // The graph clips its own drawing, which compose would not do on its own: the last x axis
      // label is wider than the plot and the platform cuts it off.
      modifier = Modifier.fillMaxWidth().padding(16.dp).clipToBounds(),
    )
  }
}

@Composable
private fun UploadPane(state: DetailPageState, bindings: DetailBindings) {
  LazyColumn(
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize().padding(16.dp),
  ) {
    items(state.reports) { report -> ReportRowItem(report, bindings) }
    item {
      Button(
        onClick = bindings.onConfigureAccounts::run,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().padding(3.dp).height(48.dp),
      ) {
        // The platform centers the label ink, Compose centers the line box.
        Text(
          text = stringResource(CommonR.string.Configure_accounts),
          modifier = Modifier.offset(y = 1.dp),
        )
      }
    }
  }
}

@Composable
private fun ReportRowItem(report: ReportRow, bindings: DetailBindings) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
  ) {
    Text(
      text = report.name,
      style = XmlText.bodyLarge,
      color = if (report.clickable) Color.Blue else MaterialTheme.colorScheme.onSurface,
      modifier =
        Modifier.weight(1f).clickable {
          bindings.onReportNameClicked.onReportNameClicked(report.name)
        },
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.combinedClickable(
          onClick = { bindings.onReportChecked.onReportChecked(report.name, !report.checked) },
          onLongClick = { bindings.onReportLongClicked.onReportLongClicked(report.name) },
        ),
    ) {
      Checkbox(
        checked = report.checked,
        onCheckedChange = { bindings.onReportChecked.onReportChecked(report.name, it) },
      )
      Text(
        text = stringResource(report.labelRes),
        style = XmlText.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun DetailButtons(
  state: DetailPageState,
  bindings: DetailBindings,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    if (state.discardVisible || state.saveVisible) {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
      ) {
        if (state.discardVisible) {
          ExtendedFloatingActionButton(
            onClick = bindings.onDiscard::run,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_delete),
              contentDescription = null,
              tint = Color.Unspecified,
            )
            Text(
              text = stringResource(CommonR.string.Discard),
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
        if (state.saveVisible) {
          ExtendedFloatingActionButton(
            onClick = bindings.onSave::run,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_check),
              contentDescription = null,
              tint = Color.Unspecified,
            )
            Text(
              text = stringResource(CommonR.string.Save),
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
    if (state.uploadVisible) {
      Button(
        onClick = bindings.onUpload::run,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
      ) {
        Text(text = stringResource(CommonR.string.Upload))
      }
    }
  }
}

/**
 * The sport spinner used to carry a `android:layout_marginTop="1px"` in the layout, which Compose
 * cannot express in dp.
 */
private fun Modifier.onePixelTopMargin() = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints)
  layout(placeable.width, placeable.height + 1) { placeable.placeRelative(0, 1) }
}
