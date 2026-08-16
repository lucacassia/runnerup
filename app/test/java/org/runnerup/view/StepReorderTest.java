package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.runnerup.workout.Step;

public class StepReorderTest {
  private static List<Step> threeSteps() {
    List<Step> list = new ArrayList<>();
    list.add(new Step());
    list.add(new Step());
    list.add(new Step());
    return list;
  }

  @Test
  public void swapMovesElementsWithinList() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    Step second = list.get(1);
    assertTrue(StepReorder.swapIndex(list, 0, 1));
    assertSame(second, list.get(0));
    assertSame(first, list.get(1));
  }

  @Test
  public void swapIsReversible() {
    List<Step> list = threeSteps();
    Step a = list.get(0);
    Step b = list.get(2);
    StepReorder.swapIndex(list, 0, 2);
    StepReorder.swapIndex(list, 0, 2);
    assertSame(a, list.get(0));
    assertSame(b, list.get(2));
  }

  @Test
  public void outOfRangeIsNoOp() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    assertFalse(StepReorder.swapIndex(list, 0, 5));
    assertFalse(StepReorder.swapIndex(list, -1, 1));
    assertSame(first, list.get(0));
    assertEquals(3, list.size());
  }

  @Test
  public void nullListIsNoOp() {
    assertFalse(StepReorder.swapIndex(null, 0, 1));
  }

  @Test
  public void equalIndicesReturnTrueWithoutChange() {
    List<Step> list = threeSteps();
    Step first = list.get(0);
    assertTrue(StepReorder.swapIndex(list, 0, 0));
    assertSame(first, list.get(0));
    assertEquals(3, list.size());
  }
}
