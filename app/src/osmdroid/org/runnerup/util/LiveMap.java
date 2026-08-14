package org.runnerup.util;

import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.content.res.AppCompatResources;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.R;
import org.runnerup.db.entities.LocationEntity;

public class LiveMap {

  private static final String OSMDROID_USER_AGENT = "org.runnerup.free";
  private static final float TRACK_WIDTH_PX = 10.f;
  private static final float TRACK_EDGE_WIDTH_PX = 20.f;
  private static final float MARKER_DIAMETER_PX = 3 * TRACK_WIDTH_PX;
  private static final float MARKER_ICON_VIEWPORT = 24f;
  private static final float MARKER_CIRCLE_VIEWPORT = 18f;
  private static final double SAME_POINT_EPSILON = 1e-7;
  private static final double INITIAL_ZOOM = 16.;

  private final MapView mapView;
  private final View recenterButton;
  private final View attribution;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final List<GeoPoint> points = new ArrayList<>();
  private final Polyline edge = newPolyline(Color.BLACK, TRACK_EDGE_WIDTH_PX);
  private final Polyline track = newPolyline(Color.BLACK, TRACK_WIDTH_PX);
  private Marker currentMarker = null;
  private boolean following = true;
  private boolean backfilled = false;
  private boolean suppressScroll = false;
  private boolean pinching = false;
  private boolean overlaysAdded = false;
  private double lastLat = Double.NaN;
  private double lastLng = Double.NaN;

  private static final class RouteData {
    final List<GeoPoint> points;
    final List<Marker> markers;

    RouteData(List<GeoPoint> points, List<Marker> markers) {
      this.points = points;
      this.markers = markers;
    }
  }

  public LiveMap(MapViewWrapper mapView, View recenterButton, View attribution) {
    this.mapView = mapView;
    this.recenterButton = recenterButton;
    this.attribution = attribution;
    org.osmdroid.config.Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);
    recenterButton.setOnClickListener(v -> recenter());
  }

  public void onCreate(Bundle savedInstanceState) {
    boolean isNight = isNightMode();
    mapView.setTileSource(CartoTileSource.forNight(isNight));
    mapView.setBackgroundColor(mapView.getContext().getColor(R.color.mapBackground));
    mapView
        .getOverlayManager()
        .getTilesOverlay()
        .setColorFilter(
            new ColorMatrixColorFilter(
                new ColorMatrix(isNight ? MapTheme.NIGHT_TILE_MATRIX : MapTheme.DAY_TILE_MATRIX)));
    track.getOutlinePaint().setColor(MapTheme.routeColor(isNight));
    edge.getOutlinePaint().setColor(MapTheme.edgeColor(isNight));
    attribution.setVisibility(View.VISIBLE);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
    mapView.setMultiTouchControls(true);
    mapView.getController().setZoom(INITIAL_ZOOM);
    mapView.setOnTouchListener(
        (v, event) -> {
          int action = event.getActionMasked();
          pinching =
              action != MotionEvent.ACTION_UP
                  && action != MotionEvent.ACTION_CANCEL
                  && event.getPointerCount() >= 2;
          return false;
        });
    mapView.addMapListener(
        new MapListener() {
          @Override
          public boolean onScroll(ScrollEvent event) {
            if (!suppressScroll && !pinching) {
              stopFollowing();
            }
            return false;
          }

          @Override
          public boolean onZoom(ZoomEvent event) {
            return false;
          }
        });
  }

  private boolean isNightMode() {
    return (mapView.getContext().getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
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
                boolean hadLivePoints = !points.isEmpty();
                if (hadLivePoints) {
                  points.addAll(0, route.points);
                } else {
                  points.addAll(route.points);
                }
                ensureOverlaysAdded();
                for (Marker marker : route.markers) {
                  mapView.getOverlays().add(marker);
                }
                if (hadLivePoints) {
                  mapView.getOverlays().remove(currentMarker);
                  mapView.getOverlays().add(currentMarker);
                }
                track.setPoints(points);
                edge.setPoints(points);
                if (!hadLivePoints) {
                  mapView.getController().setZoom(INITIAL_ZOOM);
                }
                if (!points.isEmpty()) {
                  ensureCurrentMarker();
                  GeoPoint last = points.get(points.size() - 1);
                  currentMarker.setPosition(last);
                  lastLat = last.getLatitude();
                  lastLng = last.getLongitude();
                  centerOn(last);
                }
                mapView.invalidate();
              });
        });
  }

  public void onLocationChanged(Location location) {
    if (location == null) {
      return;
    }
    double lat = location.getLatitude();
    double lng = location.getLongitude();
    if (Math.abs(lat - lastLat) < SAME_POINT_EPSILON
        && Math.abs(lng - lastLng) < SAME_POINT_EPSILON) {
      return;
    }
    lastLat = lat;
    lastLng = lng;
    ensureOverlaysAdded();
    ensureCurrentMarker();
    GeoPoint point = new GeoPoint(lat, lng);
    points.add(point);
    track.setPoints(points);
    edge.setPoints(points);
    currentMarker.setPosition(point);
    if (following) {
      centerOn(point);
    }
    mapView.invalidate();
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onPause() {
    mapView.onPause();
  }

  public void onDestroy() {
    executor.shutdown();
  }

  public void onMapVisibilityChanged(boolean visible) {
    recenterButton.setVisibility(visible && !following ? View.VISIBLE : View.GONE);
  }

  private RouteData loadRoute(SQLiteDatabase mDB, long activityId) {
    List<GeoPoint> routePoints = new ArrayList<>();
    List<Marker> routeMarkers = new ArrayList<>();
    LocationEntity.LocationList<LocationEntity> ll =
        new LocationEntity.LocationList<>(mDB, activityId);
    int lastLap = -1;
    for (LocationEntity loc : ll) {
      GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
      routePoints.add(point);
      int lap = loc.getLap();
      if (lastLap != lap) {
        lastLap = lap;
        if (routeMarkers.isEmpty()) {
          routeMarkers.add(newIconMarker(R.drawable.ic_map_marker_start, point));
        } else {
          routeMarkers.add(newIconMarker(R.drawable.ic_map_marker_lap, point));
        }
      }
    }
    ll.close();
    return new RouteData(routePoints, routeMarkers);
  }

  private void recenter() {
    if (points.isEmpty()) {
      return;
    }
    following = true;
    recenterButton.setVisibility(View.GONE);
    centerOn(points.get(points.size() - 1));
  }

  private void stopFollowing() {
    following = false;
    recenterButton.setVisibility(View.VISIBLE);
  }

  private void centerOn(GeoPoint point) {
    suppressScroll = true;
    try {
      mapView.getController().setCenter(point);
    } finally {
      suppressScroll = false;
    }
  }

  private void ensureOverlaysAdded() {
    if (overlaysAdded) {
      return;
    }
    overlaysAdded = true;
    mapView.getOverlays().add(edge);
    mapView.getOverlays().add(track);
  }

  private void ensureCurrentMarker() {
    if (currentMarker != null) {
      return;
    }
    currentMarker = newIconMarker(R.drawable.ic_map_marker_current, new GeoPoint(0., 0.));
    mapView.getOverlays().add(currentMarker);
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
}
