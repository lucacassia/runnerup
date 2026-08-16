package org.runnerup.view;

import java.util.List;
import org.runnerup.workout.Step;

public final class StepReorder {

  private StepReorder() {}

  public static boolean swapIndex(List<Step> list, int i, int j) {
    if (list == null || i < 0 || j < 0 || i >= list.size() || j >= list.size()) {
      return false;
    }
    if (i == j) {
      return true;
    }
    Step tmp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, tmp);
    return true;
  }
}
