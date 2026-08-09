package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.export.SyncManager;

public class WorkoutSelectionTest {
  private static final SyncManager.WorkoutRef A = new SyncManager.WorkoutRef("My phone", null, "A");
  private static final SyncManager.WorkoutRef B = new SyncManager.WorkoutRef("My phone", null, "B");

  @Test
  public void selectingAThenBLeavesOnlyBSelected() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(B, true);
    assertEquals(B, selection.getSelected());
  }

  @Test
  public void reselectingSelectedItemDeselects() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(A, false);
    assertNull(selection.getSelected());
  }

  @Test
  public void uncheckingUnselectedItemDoesNotChangeSelection() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.onChecked(B, false);
    assertEquals(A, selection.getSelected());
  }

  @Test
  public void clearEmptiesSelection() {
    WorkoutSelection selection = new WorkoutSelection();
    selection.onChecked(A, true);
    selection.clear();
    assertNull(selection.getSelected());
  }
}
