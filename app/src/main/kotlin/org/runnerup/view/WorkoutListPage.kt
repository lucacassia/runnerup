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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** One row of the workout list: either a provider header or a workout of that provider. */
sealed class WorkoutRow {
  abstract val key: String

  class Header(override val key: String, val expanded: Boolean) : WorkoutRow()

  class Item(override val key: String, val workoutName: String) : WorkoutRow()
}

class WorkoutListPageState {
  var title by mutableStateOf("")
  var rows by mutableStateOf<List<WorkoutRow>>(emptyList())

  /** The index of the row being dragged, or -1 while no drag is in progress. */
  var draggedIndex by mutableStateOf(-1)

  /** How far the dragged row has travelled since the last reorder. */
  var dragOffset by mutableStateOf(0f)
}

fun interface ToggleGroup {
  fun onToggleGroup(name: String)
}

fun interface OpenWorkout {
  fun onOpenWorkout(workoutName: String)
}

fun interface StartDrag {
  fun onDragStart(index: Int)
}

fun interface ContinueDrag {
  fun onDrag(dy: Float)
}

class WorkoutListBindings(
  val onNavigateUp: Runnable,
  val onToggleGroup: ToggleGroup,
  val onOpenWorkout: OpenWorkout,
  val onDragStart: StartDrag,
  val onDrag: ContinueDrag,
  val onDragEnd: Runnable,
  val onCreateWorkout: Runnable,
)

object WorkoutListScreenHost {
  fun attach(
    composeView: ComposeView,
    state: WorkoutListPageState,
    bindings: WorkoutListBindings,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { WorkoutListPage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun WorkoutListPage(state: WorkoutListPageState, bindings: WorkoutListBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    AppBar(title = state.title, onNavigateUp = bindings.onNavigateUp::run)
    val createLabel = stringResource(CommonR.string.Create_new_workout)
    Box(modifier = Modifier.weight(1f)) {
      WorkoutRows(state = state, bindings = bindings)
      Box(
        modifier =
          Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics {
            contentDescription = createLabel
          }
      ) {
        FloatingActionButton(
          onClick = bindings.onCreateWorkout::run,
          shape = RoundedCornerShape(16.dp),
          containerColor = themeAttrColor(com.google.android.material.R.attr.colorPrimaryContainer),
          contentColor = themeAttrColor(com.google.android.material.R.attr.colorOnPrimaryContainer),
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_add_white_24dp),
            contentDescription = null,
            tint = themeAttrColor(com.google.android.material.R.attr.colorOnPrimaryContainer),
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun WorkoutRows(state: WorkoutListPageState, bindings: WorkoutListBindings) {
  // The platform list leaves 88dp below the last row so the fab never covers one.
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 88.dp),
  ) {
    itemsIndexed(items = state.rows, key = { _, row -> row.key }) { index, row ->
      when (row) {
        is WorkoutRow.Header -> WorkoutHeader(row = row, bindings = bindings)
        is WorkoutRow.Item ->
          WorkoutCard(row = row, index = index, state = state, bindings = bindings)
      }
    }
  }
}

@Composable
private fun WorkoutHeader(row: WorkoutRow.Header, bindings: WorkoutListBindings) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth().height(48.dp).clickable {
        bindings.onToggleGroup.onToggleGroup(row.key)
      },
  ) {
    Text(
      text = row.key,
      style = XmlText.bodyLarge,
      color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
      // The platform row keeps its label below the 12dp top padding.
      modifier = Modifier.weight(1f).align(Alignment.Top).padding(start = 16.dp, top = 12.dp),
    )
    // The platform drawable is a white vector on a white background, so it stays invisible.
    Icon(
      painter =
        painterResource(
          if (row.expanded) R.drawable.ic_expand_up_white_24dp
          else R.drawable.ic_expand_down_white_24dp
        ),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(24.dp).padding(end = 16.dp),
    )
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
  row: WorkoutRow.Item,
  index: Int,
  state: WorkoutListPageState,
  bindings: WorkoutListBindings,
) {
  val dragged = index == state.draggedIndex
  val dragLabel = stringResource(CommonR.string.Drag_to_reorder)
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .zIndex(if (dragged) 1f else 0f)
        .graphicsLayer { translationY = if (dragged) state.dragOffset else 0f }
        .clip(RoundedCornerShape(12.dp))
        .background(themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
        .clickable { bindings.onOpenWorkout.onOpenWorkout(row.workoutName) }
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .semantics { contentDescription = dragLabel }
            .pointerInput(row.key) {
              detectDragGestures(
                onDragStart = { bindings.onDragStart.onDragStart(index) },
                onDrag = { change, amount ->
                  bindings.onDrag.onDrag(amount.y)
                  change.consume()
                },
                onDragEnd = bindings.onDragEnd::run,
                onDragCancel = bindings.onDragEnd::run,
              )
            },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_drag_handle),
          contentDescription = null,
          tint = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
          modifier = Modifier.size(24.dp),
        )
      }
      WorkoutName(name = row.workoutName)
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WorkoutName(name: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
  ) {
    Text(
      text = name,
      style = XmlText.titleMedium,
      color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(12.dp))
    Icon(
      painter = painterResource(R.drawable.ic_chevron_right_24dp),
      contentDescription = null,
      tint = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
      modifier = Modifier.size(24.dp),
    )
  }
}
