package org.runnerup.util;

public final class MapTheme {

  private MapTheme() {}

  public static final int ROUTE_DAY = 0xFF3B7DD8;
  public static final int ROUTE_NIGHT = 0xFFFAB283;
  public static final int EDGE_DAY = 0xFFFFFFFF;
  public static final int EDGE_NIGHT = 0xFF0A0A0A;
  public static final String CARTO_DARK_BASE = "https://basemaps.cartocdn.com/dark_all";
  public static final String CARTO_LIGHT_BASE = "https://basemaps.cartocdn.com/light_all";

  public static int routeColor(boolean isNight) {
    return isNight ? ROUTE_NIGHT : ROUTE_DAY;
  }

  public static int edgeColor(boolean isNight) {
    return isNight ? EDGE_NIGHT : EDGE_DAY;
  }

  public static String tileBaseUrl(boolean isNight) {
    return isNight ? CARTO_DARK_BASE : CARTO_LIGHT_BASE;
  }
}
