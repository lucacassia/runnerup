/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.util;

import static org.runnerup.util.Formatter.Format.TXT_SHORT;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.appcompat.content.res.AppCompatResources;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final SQLiteDatabase mDB;
  private final long mID;
  private final Formatter formatter;
  private final MapView mapView;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private static final java.lang.String OSMDROID_USER_AGENT = "org.runnerup.free";

  private static final float TRACK_WIDTH_PX = 10.f;
  private static final float TRACK_EDGE_WIDTH_PX = 20.f;
  private static final float MARKER_DIAMETER_PX = 3 * TRACK_WIDTH_PX;
  private static final float MARKER_ICON_VIEWPORT = 24f;
  private static final float MARKER_CIRCLE_VIEWPORT = 18f;

  public MapWrapper(
      Context context,
      SQLiteDatabase mDB,
      long mID,
      Formatter formatter,
      java.lang.Object mapView) {
    this.mDB = mDB;
    this.mID = mID;
    this.formatter = formatter;
    this.mapView = (MapView) mapView;
  }

  public static void start(Context context) {}

  public void onCreate(Bundle savedInstanceState) {
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
    mapView.setMultiTouchControls(true);

    Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);

    loadRouteAsync(isNightMode());
  }

  private boolean isNightMode() {
    return (mapView.getContext().getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
  }

  // The results from the database query
  record Route(List<Polyline> polylines, List<Marker> markers, GeoPoint firstPoint) {}

  /**
   * Loads the route from the database on a background thread and then updates the UI on the main
   * thread.
   */
  private void loadRouteAsync(boolean isNight) {
    executor.execute(
        () -> {
          final Route route = loadRouteData(isNight);

          // UI update
          mapView.post(
              () -> {
                IMapController mapController = mapView.getController();
                mapController.setZoom(15.);

                if (route.firstPoint != null) {
                  mapController.setCenter(route.firstPoint);
                }

                // Add the polyline and markers to the map, polyline first so the markers sit on top
                for (Polyline polyline : route.polylines) {
                  mapView.getOverlays().add(polyline);
                }
                for (Marker marker : route.markers) {
                  mapView.getOverlays().add(marker);
                }
                mapView.invalidate();
              });
        });
  }

  /** The long-running database query and data processing logic. */
  private Route loadRouteData(boolean isNight) {
    Polyline edge = newPolyline(MapTheme.edgeColor(isNight), TRACK_EDGE_WIDTH_PX);
    Polyline track = newPolyline(MapTheme.routeColor(isNight), TRACK_WIDTH_PX);

    java.util.List<Marker> markers = new ArrayList<>();
    java.util.List<GeoPoint> points = new LinkedList<>();

    LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
    int lastLap = -1;
    for (LocationEntity loc : ll) {
      GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
      points.add(point);

      int lap = loc.getLap();
      if (lastLap != lap) {
        lastLap = lap;
        if (markers.isEmpty()) {
          markers.add(newIconMarker(R.drawable.ic_map_marker_start, point));
        } else {
          Marker marker = new Marker(mapView);
          marker.setPosition(point);
          java.lang.String info =
              "#"
                  + loc.getLap()
                  + " "
                  + formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue())
                  + " "
                  + formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
          marker.setTextIcon(info);
          marker.setInfoWindow(null);
          marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
          markers.add(marker);
        }
      }
    }
    ll.close();
    if (!points.isEmpty()) {
      if (!markers.isEmpty()) {
        Marker lastMarker = markers.get(markers.size() - 1);
        if (lastMarker.getPosition().equals(points.get(points.size() - 1))) {
          markers.remove(markers.size() - 1);
        }
      }
      markers.add(newIconMarker(R.drawable.ic_map_marker_end, points.get(points.size() - 1)));
    }
    edge.setPoints(points);
    track.setPoints(points);

    GeoPoint firstPoint = points.isEmpty() ? null : points.get(0);
    return new Route(List.of(edge, track), markers, firstPoint);
  }

  private Marker newIconMarker(int drawableRes, GeoPoint point) {
    Marker marker = new Marker(mapView);
    marker.setPosition(point);
    marker.setIcon(scaleMarkerIcon(drawableRes));
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
    marker.setInfoWindow(null);
    return marker;
  }

  private Drawable scaleMarkerIcon(int drawableRes) {
    Drawable icon = AppCompatResources.getDrawable(mapView.getContext(), drawableRes);
    int size = Math.round(MARKER_DIAMETER_PX * MARKER_ICON_VIEWPORT / MARKER_CIRCLE_VIEWPORT);
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    icon.setBounds(0, 0, size, size);
    icon.draw(canvas);
    return new BitmapDrawable(mapView.getContext().getResources(), bitmap);
  }

  private Polyline newPolyline(int color, float width) {
    Polyline polyline = new Polyline(mapView, true);
    polyline.setInfoWindow(null);
    polyline.getOutlinePaint().setColor(color);
    polyline.getOutlinePaint().setStrokeWidth(width);
    polyline.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
    polyline.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);
    return polyline;
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onStart() {}

  public void onStop() {}

  public void onPause() {
    mapView.onPause();
  }

  public void onSaveInstanceState(Bundle outState) {}

  public void onLowMemory() {}

  public void onDestroy() {
    executor.shutdown();
  }
}
