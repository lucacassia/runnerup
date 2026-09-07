package org.runnerup.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapThemeTest {
  @Test
  public void dayRouteIsBlue() {
    assertEquals(0xFF3B7DD8, MapTheme.routeColor(false));
  }

  @Test
  public void nightRouteIsPeach() {
    assertEquals(0xFFFAB283, MapTheme.routeColor(true));
  }

  @Test
  public void dayEdgeIsWhite() {
    assertEquals(0xFFFFFFFF, MapTheme.edgeColor(false));
  }

  @Test
  public void nightEdgeIsNearBlack() {
    assertEquals(0xFF0A0A0A, MapTheme.edgeColor(true));
  }
}
