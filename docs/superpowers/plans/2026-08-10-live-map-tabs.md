# Workout/Map Tabs + Live Map on Recording Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain workout list on the recording screen (`RunActivity`) with a Workout/Map tab container, where the Map tab shows a live track and current position during recording.

**Architecture:** `run.xml` gains a Material `TabLayout` + content container holding the existing `workout_list`, a `MapViewWrapper` map view, and a recenter FAB. A new per-source-set `LiveMap` class (osmdroid / mapbox / nomap, mirroring the `MapWrapper` pattern) owns the live map. `RunActivity` adds the tabs (Map only when maps are enabled), feeds `getLastKnownLocation()` to `LiveMap` each tick, and triggers a one-time DB backfill the first time the Map tab is shown so the whole run appears.

**Tech Stack:** AndroidX / Material 3, osmdroid 6.1.20 (osmdroid variant), Mapbox Maps SDK (mapbox variant), Gradle source-set variants, GPLv3.

## Global Constraints

- No new dependencies. Reuse the osmdroid/mapbox libs already wired in the build.
- `RunActivity` (main source set) may only call `LiveMap` methods that exist in ALL three variants: `LiveMap(MapViewWrapper, View)`, `onCreate(Bundle)`, `onFirstShow(SQLiteDatabase, long)`, `onLocationChanged(Location)`, `onResume()`, `onPause()`, `onDestroy()`.
- Map tab is added only when `BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED` (same gate as `DetailActivity`).
- The nomap build (`./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`) must compile and the layout must inflate.
- No comments added to code unless asked. Google Java Format (spotless) enforced.
- No new lint issues beyond the 25-item `app/lint-baseline.xml`. `InlinedApi`/`InconsistentArrays` are fatal.
- Verification order per AGENTS.md: `./gradlew test` → `./gradlew :app:lintLatestDebug` → `./gradlew spotlessApply` + `spotlessCheck`.
- Spec: `docs/superpowers/specs/2026-08-10-live-map-tabs-design.md`.
- Strings `Workout` and `Map` already exist in `org.runnerup.common.R`.

## File Map

- Create `app/res/drawable/ic_recenter.xml` — "my location" icon for the recenter FAB.
- Create `app/res/drawable/ic_map_marker_current.xml` — blue current-position dot.
- Modify `common/src/main/res/values/strings.xml` — add `Recenter`.
- Modify `app/res/layout/run.xml` — tab container (Task 2).
- Create `app/src/nomap/org/runnerup/util/LiveMap.java` — no-op stubs (Task 3).
- Create `app/src/osmdroid/org/runnerup/util/LiveMap.java` — primary implementation (Task 4).
- Create `app/src/mapbox/org/runnerup/util/LiveMap.java` — mapbox port (Task 5).
- Modify `app/src/main/org/runnerup/view/RunActivity.java` — wiring (Task 6).

---

### Task 1: Recenter and current-position marker resources

**Files:**
- Create: `app/res/drawable/ic_recenter.xml`
- Create: `app/res/drawable/ic_map_marker_current.xml`
- Modify: `common/src/main/res/values/strings.xml` (add `Recenter` before the final `</resources>`)

**Interfaces:**
- Produces: `R.drawable.ic_recenter`, `R.drawable.ic_map_marker_current` (`org.runnerup.R`), `org.runnerup.common.R.string.Recenter`. Used by Task 4, Task 5, Task 6.

- [ ] **Step 1: Create `ic_recenter.xml`**

Material "my_location" icon; tinted by the FAB at runtime.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM20.94,11c-0.46,-4.17 -3.77,-7.48 -7.94,-7.94L13,1h-2v2.06C6.83,3.52 3.52,6.83 3.06,11L1,11v2h2.06c0.46,4.17 3.77,7.48 7.94,7.94L11,23h2v-2.06c4.17,-0.46 7.48,-3.77 7.94,-7.94L23,13v-2h-2.06zM12,19c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z" />
</vector>
```

- [ ] **Step 2: Create `ic_map_marker_current.xml`**

Blue filled circle with white stroke, same shape as `ic_map_marker_start` but distinct color.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#2196F3"
        android:pathData="M12,2a10,10 0 1,1 0,20a10,10 0 1,1 0,-20z"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3" />
</vector>
```

- [ ] **Step 3: Add `Recenter` string**

In `common/src/main/res/values/strings.xml`, immediately before the closing `</resources>`:

```xml
    <string name="Recenter">Recenter</string>
```

- [ ] **Step 4: Verify the build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/res/drawable/ic_recenter.xml app/res/drawable/ic_map_marker_current.xml common/src/main/res/values/strings.xml
git commit -m "feat: add recenter and current-position map marker resources"
```

---

### Task 2: run.xml tab container layout

**Files:**
- Modify: `app/res/layout/run.xml`

**Interfaces:**
- Produces layout ids used by Task 6: `@id/run_tab_layout`, `@id/run_mapview`, `@id/recenter_button`. `workout_list` and `hr_debug` ids are preserved.
- Consumes: `org.runnerup.util.MapViewWrapper` (all variants), `@drawable/ic_recenter`, `@string/Recenter`.

- [ ] **Step 1: Replace the workout list + hr_debug blocks**

In `app/res/layout/run.xml`, replace the whole `workout_list` `RecyclerView` block (currently `layout_above="@id/run_table_row1"`, `layout_below="@id/table_layout1"`) and the `hr_debug` `TextView` block (currently aligned to `workout_list`) with:

```xml
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/run_tab_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_below="@id/table_layout1"
        app:tabMode="fixed" />

    <RelativeLayout
        android:id="@+id/run_tab_content"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_above="@id/run_table_row1"
        android:layout_below="@id/run_tab_layout">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/workout_list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingStart="16dp"
            android:paddingTop="4dp"
            android:paddingEnd="16dp"
            android:paddingBottom="8dp" />

        <org.runnerup.util.MapViewWrapper
            android:id="@+id/run_mapview"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <TextView
            android:id="@+id/hr_debug"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scrollbars="vertical"
            android:visibility="gone" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/recenter_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_alignParentBottom="true"
            android:layout_centerHorizontal="true"
            android:layout_marginBottom="16dp"
            android:contentDescription="@string/Recenter"
            android:visibility="gone"
            app:srcCompat="@drawable/ic_recenter"
            app:tint="?attr/colorOnPrimaryContainer" />
    </RelativeLayout>
```

Keep the rest of the file unchanged (stats card `table_layout1` above, buttons `run_table_row1` below).

> NOTE (post-review fix, Task 7 smoke test): the TabLayout must NOT have `layout_above` set. In a `RelativeLayout`, a child with BOTH `layout_above` and `layout_below` is measured EXACTLY to fill the whole anchored span (its `layout_height` is ignored), so the tab bar stretched over the full screen and `run_tab_content` collapsed to 0. With only `layout_below`, the strip keeps its natural height and `run_tab_content` fills the space below it.

- [ ] **Step 2: Verify osmdroid build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. `RunActivity` still compiles because `workout_list` and `hr_debug` ids are unchanged.

- [ ] **Step 3: Verify nomap build inflates the layout**

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL (the nomap `MapViewWrapper` is a plain `View` subclass, so inflation is fine).

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/run.xml
git commit -m "feat: add workout/map tab container to recording screen layout"
```

---

### Task 3: nomap LiveMap stub

**Files:**
- Create: `app/src/nomap/org/runnerup/util/LiveMap.java`

**Interfaces:**
- Produces the full variant API contract used by Task 6. Note: in nomap builds `LiveMap` is never constructed (the Map tab is not added), but the class must still compile because `RunActivity` references it.

- [ ] **Step 1: Create the stub**

```java
package org.runnerup.util;

import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

public class LiveMap {

  public LiveMap(MapViewWrapper mapView, View recenterButton) {}

  public void onCreate(Bundle savedInstanceState) {}

  public void onFirstShow(SQLiteDatabase mDB, long activityId) {}

  public void onLocationChanged(Location location) {}

  public void onResume() {}

  public void onPause() {}

  public void onDestroy() {}
}
```

- [ ] **Step 2: Verify nomap build**

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/nomap/org/runnerup/util/LiveMap.java
git commit -m "feat: add nomap LiveMap stub"
```

---

### Task 4: osmdroid LiveMap

**Files:**
- Create: `app/src/osmdroid/org/runnerup/util/LiveMap.java`

**Interfaces:**
- Produces: the LiveMap variant API (constructor + 6 lifecycle methods, per Global Constraints).
- Consumes: `R.drawable.ic_map_marker_start`, `R.drawable.ic_map_marker_lap`, `R.drawable.ic_map_marker_current`, `LocationEntity.LocationList`, `MapViewWrapper` (which is an `org.osmdroid.views.MapView` in this variant).
- Follow behavior: camera follows the last fix while `following == true`; a user pan sets `following = false` and shows `recenterButton`; tapping it re-centers and resumes following. Pinch-zoom does not break following.

- [ ] **Step 1: Create `LiveMap.java`**

Copy the track/marker constants and helpers from `app/src/osmdroid/org/runnerup/util/MapWrapper.java` (track colors, polyline style, marker scaling). Full file:

```java
package org.runnerup.util;

import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.R;
import org.runnerup.db.entities.LocationEntity;

public class LiveMap {

  private static final String OSMDROID_USER_AGENT = "org.runnerup.free";
  private static final int TRACK_COLOR = Color.parseColor("#FF6D00");
  private static final int TRACK_EDGE_COLOR = Color.parseColor("#FFB680");
  private static final float TRACK_WIDTH_PX = 10.f;
  private static final float TRACK_EDGE_WIDTH_PX = 20.f;
  private static final float MARKER_DIAMETER_PX = 3 * TRACK_WIDTH_PX;
  private static final float MARKER_ICON_VIEWPORT = 24f;
  private static final float MARKER_CIRCLE_VIEWPORT = 18f;
  private static final double SAME_POINT_EPSILON = 1e-7;

  private final MapView mapView;
  private final View recenterButton;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final List<GeoPoint> points = new ArrayList<>();
  private final Polyline edge = newPolyline(TRACK_EDGE_COLOR, TRACK_EDGE_WIDTH_PX);
  private final Polyline track = newPolyline(TRACK_COLOR, TRACK_WIDTH_PX);
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

  public LiveMap(MapViewWrapper mapView, View recenterButton) {
    this.mapView = mapView;
    this.recenterButton = recenterButton;
    Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);
    recenterButton.setOnClickListener(v -> recenter());
  }

  public void onCreate(Bundle savedInstanceState) {
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
    mapView.setMultiTouchControls(true);
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
                  mapView.getController().setZoom(15.);
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
```

- [ ] **Step 2: Verify osmdroid build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. `RunActivity` does not reference `LiveMap` yet, so this only compiles the new class.

- [ ] **Step 3: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/LiveMap.java
git commit -m "feat: add osmdroid LiveMap"
```

---

### Task 5: mapbox LiveMap

**Files:**
- Create: `app/src/mapbox/org/runnerup/util/LiveMap.java`

**Interfaces:**
- Produces: the same LiveMap variant API as Task 4 (constructor + 6 lifecycle methods).
- Consumes: `MapViewWrapper` (a `com.mapbox.maps.MapView` in this variant), the mapbox annotation-plugin APIs used by `app/src/mapbox/org/runnerup/util/MapWrapper.java`.
- IMPORTANT: this variant compiles only when `mapbox.properties` exists (opt-in, non-CI). It CANNOT be compile-verified in this workspace. The gate is: code mirrors the mapbox `MapWrapper`'s known-good API usage and stays behaviorally identical to Task 4. If the mapbox build is ever exercised, fix compile errors there; do not change the Task 6 contract.

- [x] **Step 1: Create `LiveMap.java`**

```java
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
import com.mapbox.maps.CameraChangedCallback;
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
                  PointAnnotationManagerKt.createPointAnnotationManager(annotationApi, configMarker);
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
                        .setCamera(
                            new CameraOptions.Builder()
                                .center(last)
                                .zoom(15.0)
                                .build());
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
        new PolylineAnnotationOptions().withPoints(points).withLineColor(android.graphics.Color.RED)
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
            .withTextOffset(new ArrayList<>() {{ add(0.0); add(0.0); }});
    if (currentAnnotation == null) {
      currentAnnotation = pointAnnotationManager.create(options);
    } else {
      currentAnnotation.setPoint(point);
      currentAnnotation.setIconImage("current");
      currentAnnotation.setIconAnchor(IconAnchor.CENTER);
      currentAnnotation.setTextAnchor(TextAnchor.CENTER);
      currentAnnotation.setTextField("");
      currentAnnotation.setTextOffset(new ArrayList<>() {{ add(0.0); add(0.0); }});
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
              .withTextOffset(new ArrayList<>() {{ add(0.0); add(0.0); }});
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
```

Notes for the implementer:
- The mapbox port is a faithful mirror of Task 4: same backfill (track + start/lap markers), same live behavior, same follow/recenter semantics. The marker text popups that the saved-activity mapbox `MapWrapper` shows are intentionally omitted here (the osmdroid `LiveMap` has none either).
- This file cannot be compiled locally (mapbox SDK requires `mapbox.properties`). Treat the osmdroid variant (Task 4) as the source of truth for behavior.
- The two mapbox API corrections in this code (verified against the mapbox-maps-android v11.17.1 sources): (1) the camera listener is `MapboxMap.subscribeCameraChanged(CameraChangedCallback)` returning `com.mapbox.common.Cancelable` — `OnCameraChangeListener` does not exist in 11.17.1, and the subscription must be cancelled in `onDestroy`; (2) `AnnotationManager` has only `update(annotation)` / `update(annotations)`, so the track and current-point annotations are updated by mutating their properties (`setPoints`, `setPoint`, `setLineColorInt`, `setIconAnchor`, …) and then calling the one-arg `update(annotation)`.
- The Java block above must be transcribed with every line break preserved. Extract the fenced ```java``` block that follows the "Step 1: Create `LiveMap.java`" heading from this file (or from the generated task brief) programmatically — e.g. a small `python3`/`awk`/`sed` snippet that copies the block verbatim to `app/src/mapbox/org/runnerup/util/LiveMap.java` — rather than re-typing it. If any line ends up over 120 characters after `spotlessApply`, the transcription collapsed newlines and must be redone.

- [x] **Step 2: Review-only verification**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: both BUILD SUCCESSFUL (neither build compiles `src/mapbox`; this confirms no accidental breakage). The mapbox file itself is verified by review only — confirm every imported symbol appears in `app/src/mapbox/org/runnerup/util/MapWrapper.java` or the mapbox Maps SDK.
Verified: both builds BUILD SUCCESSFUL; round-2 review approved `842f089a` against the amended brief (token-identical, corrected v11.17.1 APIs). 3 non-blocking Minor notes inherited from the brief (marker z-order in backfill path; zoom-unchanged pan heuristic vs `pinching`; `subscribeCameraChanged` emission-timing assumption to smoke-test when the mapbox build is exercised).

- [x] **Step 3: Commit**

```bash
git add app/src/mapbox/org/runnerup/util/LiveMap.java
git commit -m "feat: add mapbox LiveMap"
```
Committed: `c2816d2b` (round 1), reworked via plan amendment `20390fc4` and re-committed `842f089a` after review.

---

### Task 6: RunActivity tab wiring and live map feed

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java`

**Interfaces:**
- Consumes: `LiveMap(MapViewWrapper, View)`, `onCreate(Bundle)`, `onFirstShow(SQLiteDatabase, long)`, `onLocationChanged(Location)`, `onResume()`, `onPause()`, `onDestroy()`; `MapViewWrapper`; `MapWrapper.start(Context)`; `DBHelper.getReadableDatabase(Context)`; `@id/run_tab_layout`, `@id/run_mapview`, `@id/recenter_button`; `org.runnerup.common.R.string.Workout` / `Map`.
- Produces: tabs (Workout always; Map only when maps enabled), content visibility toggling, one-time backfill on first Map selection.

- [x] **Step 1: Add imports**

Add to the existing import groups (keep googleJavaFormat alphabetical order within each group):

```java
import com.google.android.material.tabs.TabLayout;
```
after `import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;`

```java
import org.runnerup.BuildConfig;
import org.runnerup.db.DBHelper;
import org.runnerup.util.LiveMap;
import org.runnerup.util.MapViewWrapper;
import org.runnerup.util.MapWrapper;
```
in the `org.runnerup.*` group (`BuildConfig` before `R`; `DBHelper` after `org.runnerup.R`; `LiveMap` between `Formatter` and `TickListener`; `MapViewWrapper` and `MapWrapper` after `LiveMap`, before `ViewUtil`).

- [x] **Step 2: Add fields**

After `private TextView hrDebug;` add:

```java
  private TabLayout runTabLayout = null;
  private MapViewWrapper runMapview = null;
  private LiveMap liveMap = null;
  private boolean mapTabActive = false;
```

`mapTabActive` gates live location feeding: points are appended to the map ONLY while the Map tab is selected, so the one-time DB backfill in `onFirstShow` (which covers everything recorded before the tab was opened) never duplicates points already appended live.

- [x] **Step 3: Call `MapWrapper.start` before `setContentView`**

In `onCreate`, change:

```java
    EdgeToEdge.enable(this);
    Window window = getWindow();
    super.onCreate(savedInstanceState);
    if (!isLargeScreen()) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    setContentView(R.layout.run);
```

to:

```java
    EdgeToEdge.enable(this);
    Window window = getWindow();
    super.onCreate(savedInstanceState);
    if (!isLargeScreen()) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
      MapWrapper.start(this);
    }
    setContentView(R.layout.run);
```

- [x] **Step 4: Set up tabs and LiveMap in `onCreate`**

After `workoutList.setAdapter(adapter);` add:

```java
    runTabLayout = findViewById(R.id.run_tab_layout);
    runMapview = findViewById(R.id.run_mapview);
    runTabLayout.addTab(
        runTabLayout
            .newTab()
            .setText(getString(org.runnerup.common.R.string.Workout))
            .setTag("workout"));
    if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
      runTabLayout.addTab(
          runTabLayout
              .newTab()
              .setText(getString(org.runnerup.common.R.string.Map))
              .setTag("map"));
      liveMap = new LiveMap(runMapview, findViewById(R.id.recenter_button));
      liveMap.onCreate(savedInstanceState);
    }
    runTabLayout.addOnTabSelectedListener(onRunTabSelectedListener);
    workoutList.setVisibility(View.VISIBLE);
    runMapview.setVisibility(View.GONE);
```

- [x] **Step 5: Add the tab listener and selection handler**

Add these members (near the `onRunTabSelectedListener`-style code; place after `onCreate` or among the click handlers):

```java
  private final TabLayout.OnTabSelectedListener onRunTabSelectedListener =
      new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
          selectRunTab((String) tab.getTag());
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {}

        @Override
        public void onTabReselected(TabLayout.Tab tab) {}
      };

  private void selectRunTab(String tag) {
    mapTabActive = "map".contentEquals(tag);
    workoutList.setVisibility(mapTabActive ? View.GONE : View.VISIBLE);
    runMapview.setVisibility(mapTabActive ? View.VISIBLE : View.GONE);
    if (mapTabActive && liveMap != null) {
      liveMap.onFirstShow(
          DBHelper.getReadableDatabase(this), mTracker != null ? mTracker.getActivityId() : -1);
    }
  }
```

- [x] **Step 6: Feed locations in `onTick`**

Change the `if (mTracker != null)` block in `onTick`:

```java
      if (mTracker != null) {
        Location l2 = mTracker.getLastKnownLocation();
        if (l2 != null && !l2.equals(l)) {
          l = l2;
        }
        if (liveMap != null && mapTabActive) {
          liveMap.onLocationChanged(l2);
        }
      }
```

- [x] **Step 7: Forward lifecycle methods**

In `onPause()`:

```java
  @Override
  public void onPause() {
    super.onPause();
    if (liveMap != null) {
      liveMap.onPause();
    }
  }
```

In `onResume()`, after `showOnLockScreen(showOnLockScreen);` add:

```java
    if (liveMap != null) {
      liveMap.onResume();
    }
```

In `onDestroy()`, after `stopTimer();` add:

```java
    if (liveMap != null) {
      liveMap.onDestroy();
    }
```

- [x] **Step 8: Verify osmdroid and nomap builds**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (compiles osmdroid `LiveMap`).

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL (compiles nomap `LiveMap` stub; RunActivity references only the shared API).

- [x] **Step 9: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "feat: wire workout/map tabs and live map into RunActivity"
```

---

### Task 7: Full verification

No code changes. Runs the AGENTS.md verification sequence and the device smoke test.

- [x] **Step 1: Unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no failing tests.
Verified: BUILD SUCCESSFUL (19 executed, 72 up-to-date).

- [x] **Step 2: Lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: no NEW lint issues beyond the 25-item `app/lint-baseline.xml`. `InlinedApi`/`InconsistentArrays` (fatal) must be clean.
Verified: "Lint found no new issues (and 25 errors filtered by baseline)".

- [x] **Step 3: Spotless**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: both pass. If `spotlessApply` reformats any edited file, re-run the affected builds and amend the last commit.
Verified: both pass; no reformatting needed after the Task 7 run.

- [x] **Step 4: Build both variants**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: both BUILD SUCCESSFUL.
Verified: both BUILD SUCCESSFUL.

- [x] **Step 5: Device smoke test (osmdroid debug build)**

With the OnePlus Nord CE (serial `5717a66e`) unlocked and `adb shell svc power stayon true`:

1. `adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`
2. Launch the app, start a Basic run.
3. Confirm the workout plan still renders under the stats card with a `Workout` tab and a `Map` tab above it.
4. Start moving (or wait for GPS fixes). Tap `Map`. Confirm the track polyline, a start marker, and the blue current-position marker appear; camera follows the current position (verify with `adb shell dumpsys activity top` / `uiautomator dump` if needed).
5. Pan the map with a drag. Confirm the recenter FAB appears and the camera stops following. Tap the FAB. Confirm it re-centers on the current position, hides itself, and following resumes.
6. Pause the run. Confirm the map is static (no camera movement, marker stays put).
7. Tap `Next Lap`. Return to the Workout tab and back to Map. Confirm lap markers appear on the backfilled track.
8. Stop the run and save. Confirm no crash and that `DetailActivity` still shows its Map tab normally.

Verified on a Nexus 5X (serial `025b46e24edcbca6`, the Nord CE was not connected). This test FOUND A REAL BUG: the run screen content (workout list AND map) was 0-height because the Task 2 `TabLayout` had both `layout_above` and `layout_below` — RelativeLayout then stretches it over the full span and `run_tab_content` collapses to 0 (see the Task 2 note). Fixed in commit `a898db52` and re-verified. Confirmed after the fix: tabs render at natural height (126px) with Workout + Map; workout row visible; tab switching toggles map/list visibility; osmdroid initializes and loads tiles; pan shows the recenter FAB; `recenter()` is a no-op while no points exist (by design); pause/next-lap/save work; run saved to `DetailActivity` with its Map tab rendering. GPS-dependent checks (polyline/start/current markers, camera follow, FAB hiding on recenter, lap-marker backfill) could NOT be confirmed: no fresh GPS fix indoors and no mock-location injection on API 30 — deferred to a manual smoke test with a live GPS signal.

- [x] **Step 6: Confirm no regressions**

- HR debug overlay (`pref_bt_debug`) still toggles over the tab content.
- Workout list auto-scroll to the current step still works.
- Pause/Next Lap buttons unchanged.

Pause, Next Lap, and Save worked (run saved to `DetailActivity`, no crash; both `MainLayout` and `DetailActivity` render). HR debug overlay and workout auto-scroll not exercised in this pass.

---



