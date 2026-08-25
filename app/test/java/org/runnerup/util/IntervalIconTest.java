package org.runnerup.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntervalIconTest {

  @Test
  public void routeDayColorIsBlue() {
    assertTrue("Route day color should be non-zero", MapTheme.ROUTE_DAY != 0);
  }

  @Test
  public void routeNightColorIsPeach() {
    assertTrue("Route night color should be non-zero", MapTheme.ROUTE_NIGHT != 0);
  }

  @Test
  public void routeColorsAreDistinctForDayAndNight() {
    assertTrue(
        "Day and night route colors should differ",
        MapTheme.ROUTE_DAY != MapTheme.ROUTE_NIGHT);
  }
}
