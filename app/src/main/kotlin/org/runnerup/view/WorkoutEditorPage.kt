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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.runnerup.R
import org.runnerup.common.R as CommonR
import org.runnerup.compose.theme.RunnerUpTheme
import org.runnerup.compose.theme.XmlText
import org.runnerup.compose.theme.themeAttrColor

/** One entry of a workout: a plain step or a repeat group with nested entries. */
sealed class WorkoutEntryItem {
  abstract val key: String

  class Step(
    override val key: String,
    val durationText: String,
    val goalText: String?,
    val badgeText: String,
    val badgeTextColor: Int,
    val badgeBgColor: Int,
  ) : WorkoutEntryItem()

  class Repeat(
    override val key: String,
    val title: String,
    val children: List<WorkoutEntryItem>,
  ) : WorkoutEntryItem()
}

class WorkoutEditorPageState {
  /** The measured height of every entry, used to decide when a drag swaps two rows. */
  val itemHeights = mutableMapOf<String, Int>()

  var title by mutableStateOf("")
  var items by mutableStateOf<List<WorkoutEntryItem>>(emptyList())

  /** The key of the entry being dragged, or null while no drag is running. */
  var draggedKey by mutableStateOf<String?>(null)

  /** How far the dragged entry has travelled since the last reorder. */
  var dragOffset by mutableStateOf(0f)

  /** The window bounds of the overflow button, which anchor the platform popup menu. */
  var overflowBounds by mutableStateOf(Rect.Zero)
}

fun interface EditorOverflowMenu {
  fun onOverflowMenu(left: Int, top: Int, right: Int, bottom: Int)
}

fun interface EditorEntryAction {
  fun onEntry(key: String)
}

fun interface EditorDrag {
  fun onDrag(key: String, dy: Float)
}

class WorkoutEditorBindings(
  val onNavigateUp: Runnable,
  val onSave: Runnable,
  val onOverflowMenu: EditorOverflowMenu,
  val onOpenEntry: EditorEntryAction,
  val onDeleteEntry: EditorEntryAction,
  val onEditRepeatCount: EditorEntryAction,
  val onAddStepInsideRepeat: EditorEntryAction,
  val onDragStart: EditorEntryAction,
  val onDrag: EditorDrag,
  val onDragEnd: Runnable,
  val onAddStep: Runnable,
)

object WorkoutEditorScreenHost {
  fun attach(
    composeView: ComposeView,
    state: WorkoutEditorPageState,
    bindings: WorkoutEditorBindings,
  ) {
    composeView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    composeView.setContent {
      RunnerUpTheme { WorkoutEditorPage(state = state, bindings = bindings) }
    }
  }
}

@Composable
fun WorkoutEditorPage(state: WorkoutEditorPageState, bindings: WorkoutEditorBindings) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .background(MaterialTheme.colorScheme.surface)
  ) {
    EditorToolbar(state = state, bindings = bindings)
    Box(modifier = Modifier.weight(1f)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
      ) {
        itemsIndexed(items = state.items, key = { _, item -> item.key }) { _, item ->
          when (item) {
            is WorkoutEntryItem.Step -> StepCard(item = item, state = state, bindings = bindings)
            is WorkoutEntryItem.Repeat ->
              RepeatCard(item = item, state = state, bindings = bindings)
          }
        }
      }
      val addLabel = stringResource(CommonR.string.Add_step)
      Box(
        modifier =
          Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics {
            contentDescription = addLabel
          }
      ) {
        FloatingActionButton(
          onClick = bindings.onAddStep::run,
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
private fun EditorToolbar(state: WorkoutEditorPageState, bindings: WorkoutEditorBindings) {
  val upLabel = stringResource(AppCompatR.string.abc_action_bar_up_description)
  val saveLabel = stringResource(CommonR.string.Save)
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
    Box(
      modifier =
        Modifier.size(48.dp).clickable(onClick = bindings.onSave::run).semantics {
          contentDescription = saveLabel
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_check),
        contentDescription = null,
        tint = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
        modifier = Modifier.size(24.dp),
      )
    }
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

/** A step card: the platform row background is the only card, the step button has none. */
@Composable
private fun StepCard(
  item: WorkoutEntryItem.Step,
  state: WorkoutEditorPageState,
  bindings: WorkoutEditorBindings,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .onGloballyPositioned { state.itemHeights[item.key] = it.size.height }
        .dragModifiers(item = item, state = state, bindings = bindings)
        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .outline(themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
      DragHandle(item = item, state = state, bindings = bindings)
      StepContent(
        item = item,
        modifier =
          Modifier.weight(1f).padding(horizontal = 4.dp).clickable {
            bindings.onOpenEntry.onEntry(item.key)
          },
      )
      DeleteButton(key = item.key, bindings = bindings)
    }
  }
}

@Composable
private fun StepContent(item: WorkoutEntryItem.Step, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Text(
        text = item.durationText,
        style = XmlText.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = themeAttrColor(com.google.android.material.R.attr.colorOnSurface),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      IntensityBadge(item = item)
    }
    if (item.goalText != null) {
      Spacer(Modifier.height(2.dp))
      Text(
        text = item.goalText,
        style = XmlText.bodySmall,
        color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
      )
    }
  }
}

@Composable
private fun IntensityBadge(item: WorkoutEntryItem.Step) {
  Text(
    text = item.badgeText,
    style =
      XmlText.bodySmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = TextUnit.Unspecified,
      ),
    color = Color(item.badgeTextColor),
    maxLines = 1,
    modifier =
      Modifier.offset(y = (-1).dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(item.badgeBgColor))
        .padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 2.dp),
  )
}

@Composable
private fun RepeatCard(
  item: WorkoutEntryItem.Repeat,
  state: WorkoutEditorPageState,
  bindings: WorkoutEditorBindings,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .onGloballyPositioned { state.itemHeights[item.key] = it.size.height }
        .dragModifiers(item = item, state = state, bindings = bindings)
        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .outline(themeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
  ) {
    // The platform header uses negative margins, so it bleeds over the card edges.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
          .background(themeAttrColor(AppCompatR.attr.colorPrimary))
          .padding(start = 12.dp, end = 12.dp),
    ) {
      DragHandle(
        item = item,
        state = state,
        bindings = bindings,
        tint = themeAttrColor(com.google.android.material.R.attr.colorOnPrimary),
      )
      Text(
        text = item.title,
        style = XmlText.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = themeAttrColor(com.google.android.material.R.attr.colorOnPrimary),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier.weight(1f).padding(start = 8.dp, top = 2.dp, bottom = 2.dp).clickable {
            bindings.onEditRepeatCount.onEntry(item.key)
          },
      )
      DeleteButton(
        key = item.key,
        bindings = bindings,
        tint = themeAttrColor(com.google.android.material.R.attr.colorOnPrimary),
      )
    }
    // The host of the children and the add button sit inside the 12dp group padding; the
    // platform header cancelled the top padding with its negative margins.
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
      Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        item.children.forEach { child ->
          when (child) {
            is WorkoutEntryItem.Step -> StepCard(item = child, state = state, bindings = bindings)
            is WorkoutEntryItem.Repeat ->
              RepeatCard(item = child, state = state, bindings = bindings)
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(
          onClick = { bindings.onAddStepInsideRepeat.onEntry(item.key) },
          shape = RoundedCornerShape(20.dp),
          colors =
            ButtonDefaults.outlinedButtonColors(
              contentColor = themeAttrColor(AppCompatR.attr.colorPrimary)
            ),
          border =
            BorderStroke(1.dp, themeAttrColor(com.google.android.material.R.attr.colorOutline)),
          contentPadding = PaddingValues(0.dp),
          modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
          Text(
            text = stringResource(CommonR.string.Add_step_inside_repeat),
            maxLines = 1,
            modifier = Modifier.offset(y = 1.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun DragHandle(
  item: WorkoutEntryItem,
  state: WorkoutEditorPageState,
  bindings: WorkoutEditorBindings,
  modifier: Modifier = Modifier,
  tint: Color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
) {
  val dragLabel = stringResource(CommonR.string.Drag_to_reorder)
  Box(
    modifier =
      modifier
        .size(40.dp)
        .padding(8.dp)
        .semantics { contentDescription = dragLabel }
        .pointerInput(item.key) {
          detectDragGestures(
            onDragStart = { bindings.onDragStart.onEntry(item.key) },
            onDrag = { change, amount ->
              bindings.onDrag.onDrag(item.key, amount.y)
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
      tint = tint,
      modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
private fun DeleteButton(
  key: String,
  bindings: WorkoutEditorBindings,
  modifier: Modifier = Modifier,
  tint: Color = themeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
) {
  val deleteLabel = stringResource(CommonR.string.Delete)
  Box(
    modifier =
      modifier
        .size(40.dp)
        .padding(9.dp)
        .semantics { contentDescription = deleteLabel }
        .clickable { bindings.onDeleteEntry.onEntry(key) },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_close),
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}

/**
 * The 1dp outline every platform card draws. Unlike `Modifier.border` this keeps the content at
 * full size, so a child such as the repeat header can cover the outline just like the platform
 * shape drawable does.
 */
@Composable
private fun Modifier.outline(fill: Color): Modifier {
  val shape = RoundedCornerShape(12.dp)
  val strokeColor = themeAttrColor(com.google.android.material.R.attr.colorOutlineVariant)
  val inset = 0.5.dp
  val width = 1.dp
  return this.clip(shape).background(fill).drawWithContent {
    val edge = inset.toPx()
    val radius = 12.dp.toPx() - edge
    val path =
      Path().apply {
        addRoundRect(
          RoundRect(
            left = edge,
            top = edge,
            right = size.width - edge,
            bottom = size.height - edge,
            cornerRadius = CornerRadius(radius, radius),
          )
        )
      }
    drawPath(path, strokeColor, style = Stroke(width.toPx()))
    // Drawn first so that a child such as the repeat header can cover it.
    drawContent()
  }
}

@Composable
private fun Modifier.dragModifiers(
  item: WorkoutEntryItem,
  state: WorkoutEditorPageState,
  bindings: WorkoutEditorBindings,
): Modifier {
  val dragged = item.key == state.draggedKey
  return this.zIndex(if (dragged) 1f else 0f).graphicsLayer {
    translationY = if (dragged) state.dragOffset else 0f
  }
}
