package org.runnerup.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ConflictClassifierTest {

  @Test
  public void nonNullTypeMatchIsDuplicate() {
    Map<String, Long> index = indexOf(1000L, 1);
    assertTrue(DatabaseImporter.isDuplicate(index, 1000L, 1));
  }

  @Test
  public void differentTypeIsNotDuplicate() {
    Map<String, Long> index = indexOf(1000L, 1);
    assertFalse(DatabaseImporter.isDuplicate(index, 1000L, 2));
  }

  @Test
  public void nullLocalTypeVsNonNullImportedIsNotDuplicate() {
    Map<String, Long> index = indexOf(1000L, null);
    assertFalse(DatabaseImporter.isDuplicate(index, 1000L, 1));
  }

  @Test
  public void nullImportedTypeVsNonNullLocalIsNotDuplicate() {
    Map<String, Long> index = indexOf(1000L, 1);
    assertFalse(DatabaseImporter.isDuplicate(index, 1000L, null));
  }

  @Test
  public void bothNullIsDuplicate() {
    Map<String, Long> index = indexOf(1000L, null);
    assertTrue(DatabaseImporter.isDuplicate(index, 1000L, null));
  }

  @Test
  public void noMatchingStartTimeIsNotDuplicate() {
    Map<String, Long> index = indexOf(1000L, 1);
    assertFalse(DatabaseImporter.isDuplicate(index, 2000L, 1));
  }

  @Test
  public void classifyConflictsReturnsOnlyLiveDuplicates() {
    Map<String, Long> index = indexOf(1000L, 1);
    index.put(DatabaseImporter.key(2000L, null), 2L);

    List<DatabaseImporter.ImportedActivity> activities = new ArrayList<>();
    activities.add(activity(10L, 1000L, 1, false));
    activities.add(activity(11L, 2000L, null, false));
    activities.add(activity(12L, 1000L, 1, true));
    activities.add(activity(13L, 3000L, 1, false));
    activities.add(activity(14L, 1000L, 2, false));

    List<DatabaseImporter.ImportedActivity> conflicts =
        DatabaseImporter.classifyConflicts(activities, index);

    assertEquals(2, conflicts.size());
    assertEquals(10L, conflicts.get(0).oldId);
    assertEquals(11L, conflicts.get(1).oldId);
  }

  @Test
  public void classifyConflictsReturnsEmptyWhenNoDuplicates() {
    Map<String, Long> index = indexOf(1000L, 1);
    List<DatabaseImporter.ImportedActivity> activities = new ArrayList<>();
    activities.add(activity(10L, 3000L, 1, false));
    activities.add(activity(11L, 1000L, 1, true));

    List<DatabaseImporter.ImportedActivity> conflicts =
        DatabaseImporter.classifyConflicts(activities, index);

    assertTrue(conflicts.isEmpty());
  }

  private static Map<String, Long> indexOf(long startTime, Integer type) {
    Map<String, Long> index = new HashMap<>();
    index.put(DatabaseImporter.key(startTime, type), 1L);
    return index;
  }

  private static DatabaseImporter.ImportedActivity activity(
      long oldId, long startTime, Integer type, boolean deleted) {
    DatabaseImporter.ImportedActivity activity = new DatabaseImporter.ImportedActivity();
    activity.oldId = oldId;
    activity.startTime = startTime;
    activity.type = type;
    activity.deleted = deleted;
    return activity;
  }
}
