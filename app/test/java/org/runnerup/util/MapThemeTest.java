package org.runnerup.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  @Test
  public void dayUsesPositronTiles() {
    assertEquals(MapTheme.CARTO_LIGHT_BASE, MapTheme.tileBaseUrl(false));
  }

  @Test
  public void nightUsesDarkMatterTiles() {
    assertEquals(MapTheme.CARTO_DARK_BASE, MapTheme.tileBaseUrl(true));
  }

  private static void assertIdentityExceptScale(float[] m, float scale) {
    assertEquals(20, m.length);
    for (int i = 0; i < 20; i++) {
      switch (i) {
        case 0:
        case 6:
        case 12:
          assertEquals(scale, m[i], 0f);
          break;
        case 18:
          assertEquals(1f, m[i], 0f);
          break;
        case 4:
        case 9:
        case 14:
          break;
        default:
          assertEquals(0f, m[i], 0f);
      }
    }
  }

  @Test
  public void dayMatrixScalesFromWhite() {
    float[] m = MapTheme.DAY_TILE_MATRIX;
    assertIdentityExceptScale(m, 1.3f);
    assertEquals(255f * (1f - 1.3f), m[4], 0.001f);
    assertEquals(255f * (1f - 1.3f), m[9], 0.001f);
    assertEquals(255f * (1f - 1.3f), m[14], 0.001f);
  }

  @Test
  public void nightMatrixScalesFromBlack() {
    float[] m = MapTheme.NIGHT_TILE_MATRIX;
    assertIdentityExceptScale(m, 1.8f);
    assertEquals(0f, m[4], 0f);
    assertEquals(0f, m[9], 0f);
    assertEquals(0f, m[14], 0f);
  }

  @Test
  public void matricesIncreaseContrast() {
    assertTrue(MapTheme.DAY_TILE_MATRIX[0] > 1f);
    assertTrue(MapTheme.NIGHT_TILE_MATRIX[0] > 1f);
  }
}
