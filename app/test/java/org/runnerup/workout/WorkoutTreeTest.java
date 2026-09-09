package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;
import org.junit.Test;

public class WorkoutTreeTest {

  @Test
  public void topLevelEntriesHaveNullParentInInsertionOrder() {
    Workout w = new Workout();
    Step a = new Step();
    Step b = new Step();
    w.addStep(a);
    w.addStep(b);

    List<Workout.StepListEntry> top = w.entriesAtLevel(null);

    assertEquals(2, top.size());
    assertSame(a, top.get(0).step());
    assertSame(b, top.get(1).step());
    assertNull(top.get(0).parent());
  }

  @Test
  public void repeatChildrenHaveRepeatAsParent() {
    Workout w = new Workout();
    RepeatStep rep = new RepeatStep();
    rep.setRepeatCount(3);
    Step s1 = new Step();
    Step s2 = new Step();
    rep.getSteps().add(s1);
    rep.getSteps().add(s2);
    w.addStep(rep);

    List<Workout.StepListEntry> children = w.entriesAtLevel(rep);

    assertEquals(2, children.size());
    assertSame(rep, children.get(0).parent());
    assertSame(s1, children.get(0).step());
    assertSame(s2, children.get(1).step());
  }

  @Test
  public void topLevelDoesNotIncludeRepeatChildren() {
    Workout w = new Workout();
    RepeatStep rep = new RepeatStep();
    Step sub = new Step();
    rep.getSteps().add(sub);
    w.addStep(rep);

    List<Workout.StepListEntry> top = w.entriesAtLevel(null);

    assertEquals(1, top.size());
    assertSame(rep, top.get(0).step());
  }

  @Test
  public void nestedRepeatIsChildOfOuterRepeat() {
    Workout w = new Workout();
    RepeatStep outer = new RepeatStep();
    RepeatStep inner = new RepeatStep();
    Step deep = new Step();
    outer.getSteps().add(inner);
    inner.getSteps().add(deep);
    w.addStep(outer);

    assertSame(inner, w.entriesAtLevel(outer).get(0).step());
    assertSame(deep, w.entriesAtLevel(inner).get(0).step());
    assertEquals(1, w.entriesAtLevel(null).size());
  }
}
