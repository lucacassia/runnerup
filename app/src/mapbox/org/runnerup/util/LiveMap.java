package org.runnerup.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import androidx.preference.PreferenceManager;
import com.mapbox.common.Cancelable;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions;
import com.mapbox.maps.plugin.locationcomponent.utils.BitmapUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.db.entities.LocationEntity;

public class LiveMap {

  private final Context context;
  private final MapView mapView;
  private final View recenterButton;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final List<Point> points = new ArrayList<>();
  private PolylineAnnotationManager polylineAnnotationManager = null;
  private PointAnnotationManager pointAnnotationManager = null;
  private PolylineAnnotation trackAnnotation = null;
  private PointAnnotation currentAnnotation = null;
  private boolean following = true;
  private boolean backfilled = false;
  private boolean suppressCamera = false;
  private boolean styleReady = false;
  private double lastLat = Double.NaN;
  private double lastLng = Double.NaN;
  private double lastZoom = Double.NaN;
  private Cancelable cameraSubscription = null;

  private static final class RouteData {
    final List<Point> path;
    final List<Point> markers;

    RouteData(List<Point> path, List<Point> markers) {
      this.path = path;
      this.markers = markers;
    }
  }

  public LiveMap(MapViewWrapper mapView, View recenterButton) {
    this.context = mapView.getContext();
    this.mapView = mapView;
    this.recenterButton = recenterButton;
    recenterButton.setOnClickListener(v -> recenter());
  }

  public void onCreate(Bundle savedInstanceState) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Resources res = context.getResources();
    String val =
        prefs.getString(
            res.getString(R.string.pref_mapbox_default_style),
            res.getString(R.string.mapboxDefaultStyle));
    mapView
        .getMapboxMap()
        .loadStyle(
            val,
            mapStyle -> {
              AnnotationPlugin annotationApi =
                  mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
              if (annotationApi == null) {
                return;
              }
              AnnotationConfig configLine = new AnnotationConfig(null, null, "bottom");
              polylineAnnotationManager =
                  PolylineAnnotationManagerKt.createPolylineAnnotationManager(
                      annotationApi, configLine);
              AnnotationConfig configMarker = new AnnotationConfig(null, null, "top");
              pointAnnotationManager =
                  PointAnnotationManagerKt.createPointAnnotationManager(
                      annotationApi, configMarker);
              mapStyle.addImage(
                  "current",
                  Objects.requireNonNull(
                      BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                          context, R.drawable.ic_map_marker_current)),
                  false);
              mapStyle.addImage(
                  "start",
                  Objects.requireNonNull(
                      BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                          context, R.drawable.ic_map_marker_start)),
                  false);
              mapStyle.addImage(
                  "lap",
                  Objects.requireNonNull(
                      BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                          context, R.drawable.ic_map_marker_lap)),
                  false);
              styleReady = true;
            });
    cameraSubscription =
        mapView
            .getMapboxMap()
            .subscribeCameraChanged(
                cameraChanged -> {
                  if (Double.isNaN(lastZoom)) {
                    lastZoom = cameraChanged.getCameraState().getZoom();
                    return;
                  }
                  double zoom = cameraChanged.getCameraState().getZoom();
                  boolean zoomChanged = Math.abs(zoom - lastZoom) > 0.01;
                  lastZoom = zoom;
                  if (!suppressCamera && !zoomChanged) {
                    stopFollowing();
                  }
                });
  }

  public void onFirstShow(SQLiteDatabase mDB, long activityId) {
    if (backfilled || activityId < 0) {
      return;
    }
    backfilled = true;
    executor.execute(
        () -> {
          final RouteData route = loadRoute(mDB, activityId);
          mapView.post(
              () -> {
                if (!styleReady) {
                  return;
                }
                boolean hadLivePoints = !points.isEmpty();
                if (hadLivePoints) {
                  points.addAll(0, route.path);
                } else {
                  points.addAll(route.path);
                }
                drawRouteMarkers(route.markers);
                redrawTrack();
                if (!points.isEmpty() && !hadLivePoints) {
                  Point last = points.get(points.size() - 1);
                  lastLat = last.latitude();
                  lastLng = last.longitude();
                  ensureCurrentAnnotation(last);
                  suppressCamera = true;
                  try {
                    mapView
                        .getMapboxMap()
                        .setCamera(new CameraOptions.Builder().center(last).zoom(15.0).build());
                  } finally {
                    suppressCamera = false;
                  }
                }
              });
        });
  }

  public void onLocationChanged(Location location) {
    if (location == null) {
      return;
    }
    double lat = location.getLatitude();
    double lng = location.getLongitude();
    if (Math.abs(lat - lastLat) < 1e-7 && Math.abs(lng - lastLng) < 1e-7) {
      return;
    }
    lastLat = lat;
    lastLng = lng;
    Point point = Point.fromLngLat(lng, lat);
    points.add(point);
    redrawTrack();
    ensureCurrentAnnotation(point);
    if (following) {
      suppressCamera = true;
      try {
        mapView.getMapboxMap().setCamera(new CameraOptions.Builder().center(point).build());
      } finally {
        suppressCamera = false;
      }
    }
  }

  public void onResume() {}

  public void onPause() {}

  public void onDestroy() {
    if (cameraSubscription != null) {
      cameraSubscription.cancel();
    }
    executor.shutdown();
  }

  private void redrawTrack() {
    if (polylineAnnotationManager == null || points.size() < 2) {
      return;
    }
    PolylineAnnotationOptions options =
        new PolylineAnnotationOptions()
            .withPoints(points)
            .withLineColor(android.graphics.Color.RED)
            .withLineWidth(3.0f);
    if (trackAnnotation == null) {
      trackAnnotation = polylineAnnotationManager.create(options);
    } else {
      trackAnnotation.setPoints(points);
      trackAnnotation.setLineColorInt(android.graphics.Color.RED);
      trackAnnotation.setLineWidth(3.0);
      polylineAnnotationManager.update(trackAnnotation);
    }
  }

  private void ensureCurrentAnnotation(Point point) {
    if (pointAnnotationManager == null) {
      return;
    }
    PointAnnotationOptions options =
        new PointAnnotationOptions()
            .withPoint(point)
            .withIconImage("current")
            .withIconAnchor(IconAnchor.CENTER)
            .withTextField("")
            .withTextAnchor(TextAnchor.CENTER)
            .withTextOffset(
                new ArrayList<>() {
                  {
                    add(0.0);
                    add(0.0);
                  }
                });
    if (currentAnnotation == null) {
      currentAnnotation = pointAnnotationManager.create(options);
    } else {
      currentAnnotation.setPoint(point);
      currentAnnotation.setIconImage("current");
      currentAnnotation.setIconAnchor(IconAnchor.CENTER);
      currentAnnotation.setTextAnchor(TextAnchor.CENTER);
      currentAnnotation.setTextField("");
      currentAnnotation.setTextOffset(
          new ArrayList<>() {
            {
              add(0.0);
              add(0.0);
            }
          });
      pointAnnotationManager.update(currentAnnotation);
    }
  }

  private void drawRouteMarkers(List<Point> markerPoints) {
    if (pointAnnotationManager == null || markerPoints.isEmpty()) {
      return;
    }
    boolean first = true;
    for (Point point : markerPoints) {
      PointAnnotationOptions options =
          new PointAnnotationOptions()
              .withPoint(point)
              .withIconImage(first ? "start" : "lap")
              .withIconAnchor(IconAnchor.CENTER)
              .withTextField("")
              .withTextAnchor(TextAnchor.CENTER)
              .withTextOffset(
                  new ArrayList<>() {
                    {
                      add(0.0);
                      add(0.0);
                    }
                  });
      pointAnnotationManager.create(options);
      first = false;
    }
  }

  private void recenter() {
    if (points.isEmpty()) {
      return;
    }
    following = true;
    recenterButton.setVisibility(View.GONE);
    Point last = points.get(points.size() - 1);
    suppressCamera = true;
    try {
      mapView.getMapboxMap().setCamera(new CameraOptions.Builder().center(last).build());
    } finally {
      suppressCamera = false;
    }
  }

  private void stopFollowing() {
    following = false;
    recenterButton.setVisibility(View.VISIBLE);
  }

  private RouteData loadRoute(SQLiteDatabase mDB, long activityId) {
    List<Point> routePath = new ArrayList<>();
    List<Point> routeMarkers = new ArrayList<>();
    LocationEntity.LocationList<LocationEntity> ll =
        new LocationEntity.LocationList<>(mDB, activityId);
    int lastLap = -1;
    for (LocationEntity loc : ll) {
      Point point = Point.fromLngLat(loc.getLongitude(), loc.getLatitude());
      routePath.add(point);
      int lap = loc.getLap();
      if (lastLap != lap) {
        lastLap = lap;
        routeMarkers.add(point);
      }
    }
    ll.close();
    return new RouteData(routePath, routeMarkers);
  }
}
