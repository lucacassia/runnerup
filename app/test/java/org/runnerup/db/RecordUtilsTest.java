package org.runnerup.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecordUtilsTest {

  @Test
  public void bandIncludesRunsJustUnderTheStandard() {
    assertEquals(950.0, RecordUtils.bandLower(1000.0), 0.0);
    assertEquals(1050.0, RecordUtils.bandUpper(1000.0), 0.0);
    assertEquals(9500.0, RecordUtils.bandLower(10000.0), 0.0);
    assertEquals(10500.0, RecordUtils.bandUpper(10000.0), 0.0);
  }

  @Test
  public void bandLowerStaysBelowUpper() {
    for (double standard : new double[] {1000.0, 1609.344, 3218.688, 5000.0, 8046.72, 10000.0}) {
      assertTrue(RecordUtils.bandLower(standard) < standard);
      assertTrue(standard < RecordUtils.bandUpper(standard));
    }
  }
}
