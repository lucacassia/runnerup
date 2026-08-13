package org.runnerup.util;

import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;

public class CartoTileSource extends XYTileSource {

  private static final String COPYRIGHT = "© OpenStreetMap contributors © CARTO";
  private static final int MIN_ZOOM = 0;
  private static final int MAX_ZOOM = 19;

  public static final CartoTileSource DARK =
      new CartoTileSource("carto-dark", MapTheme.CARTO_DARK_BASE);
  public static final CartoTileSource LIGHT =
      new CartoTileSource("carto-positron", MapTheme.CARTO_LIGHT_BASE);

  private final String baseUrl;

  public CartoTileSource(String name, String baseUrl) {
    super(name, MIN_ZOOM, MAX_ZOOM, 512, ".png", new String[] {baseUrl}, COPYRIGHT);
    this.baseUrl = baseUrl;
  }

  public static CartoTileSource forNight(boolean isNight) {
    return isNight ? DARK : LIGHT;
  }

  @Override
  public String getTileURLString(long mapTileIndex) {
    return baseUrl
        + "/"
        + MapTileIndex.getZoom(mapTileIndex)
        + "/"
        + MapTileIndex.getX(mapTileIndex)
        + "/"
        + MapTileIndex.getY(mapTileIndex)
        + "@2x.png";
  }
}
