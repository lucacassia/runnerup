package org.runnerup.view;

import org.runnerup.export.SyncManager;

public class WorkoutSelection {
  private SyncManager.WorkoutRef selected = null;

  public void onChecked(SyncManager.WorkoutRef workout, boolean isChecked) {
    if (isChecked) {
      selected = workout;
    } else if (selected == workout) {
      selected = null;
    }
  }

  public SyncManager.WorkoutRef getSelected() {
    return selected;
  }

  public void clear() {
    selected = null;
  }
}
