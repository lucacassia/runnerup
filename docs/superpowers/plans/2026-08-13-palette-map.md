# Palette-Themed Recording Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the osmdroid recording-screen live map (Dark Matter tiles at night, Positron by day, palette route/edge/marker colors, attribution label) so it follows the app's day/night palette.

**Architecture:** Swap osmdroid's `MAPNIK` tile source for a custom `CartoTileSource` (CARTO raster tiles with retina `@2x` URLs), selected at map creation by system `uiMode`. Route/edge colors come from a pure `MapTheme` helper (unit-testable). Marker drawables get `drawable-night` variants referencing palette `@color` resources. A small attribution TextView is added to `run.xml` and shown by `LiveMap.onCreate`.

**Tech Stack:** osmdroid 6.1.20 (`XYTileSource`, `MapTileIndex`), AndroidX AppCompat, JUnit 4 + Mockito, Gradle `latest` flavor, java 17.

## Global Constraints

- Palette values: day `#3b7dd8` blue route / `#ffffff` edge; night `#fab283` peach route / `#0a0a0a` edge (from `opencode.json` darkStep9/lightStep9, consistent with existing `colorPrimary`).
- CARTO base URLs: dark `https://basemaps.cartocdn.com/dark_all`, light `https://basemaps.cartocdn.com/light_all`. Free, no key. Attribution string exactly `© OpenStreetMap contributors © CARTO`.
- `minSdk 28` → `Context.getColor(int)` is available.
- Do NOT touch `MapWrapper.java` (DetailActivity stays `MAPNIK`), the mapbox tile style, or the mapbox route color (`Color.RED`) — out of scope.
- Keep existing LiveMap behavior (backfill, follow/recenter, zoom, epsilon dedup) unchanged.
- Verify after every task with `./gradlew :app:testLatestDebugUnitTest` and `./gradlew :app:assembleLatestDebug` (default build includes `src/osmdroid`). Final gates: `./gradlew test`, `./gradlew :app:lintLatestDebug` (no NEW issues beyond the 25 in `lint-baseline.xml`), `./gradlew spotlessApply` then `spotlessCheck`, `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`.
- Do NOT stage user-local files (`gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`). The `docs/superpowers/plans/` file IS committed.

---

### Task 1: `MapTheme` palette helper + unit tests

**Files:**
- Create: `app/src/osmdroid/org/runnerup/util/MapTheme.java`
- Test: `app/test/java/org/runnerup/util/MapThemeTest.java`

**Interfaces:**
- Produces: `MapTheme.routeColor(boolean isNight)` → int ARGB; `MapTheme.edgeColor(boolean isNight)` → int ARGB; `MapTheme.tileBaseUrl(boolean isNight)` → String. Constants `ROUTE_DAY=0xFF3B7DD8`, `ROUTE_NIGHT=0xFFFAB283`, `EDGE_DAY=0xFFFFFFFF`, `EDGE_NIGHT=0xFF0A0A0A`, `CARTO_DARK_BASE`, `CARTO_LIGHT_BASE`.

- [ ] **Step 1: Write the failing test**

`app/test/java/org/runnerup/util/MapThemeTest.java`:

```java
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

  @Test
  public void dayUsesPositronTiles() {
    assertEquals(MapTheme.CARTO_LIGHT_BASE, MapTheme.tileBaseUrl(false));
  }

  @Test
  public void nightUsesDarkMatterTiles() {
    assertEquals(MapTheme.CARTO_DARK_BASE, MapTheme.tileBaseUrl(true));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.util.MapThemeTest`
Expected: FAIL — `cannot find symbol: class MapTheme`

- [ ] **Step 3: Write the minimal implementation**

`app/src/osmdroid/org/runnerup/util/MapTheme.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.util.MapThemeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/MapTheme.java app/test/java/org/runnerup/util/MapThemeTest.java
git commit -m "feat: add palette map theme helper with day/night colors"
```

---

### Task 2: `CartoTileSource` (CARTO raster tiles, retina)

**Files:**
- Create: `app/src/osmdroid/org/runnerup/util/CartoTileSource.java`

**Interfaces:**
- Consumes: `MapTheme.CARTO_DARK_BASE`, `MapTheme.CARTO_LIGHT_BASE`.
- Produces: `CartoTileSource.forNight(boolean isNight)` → `CartoTileSource`; constants `DARK`, `LIGHT`.

- [ ] **Step 1: Write the implementation**

`app/src/osmdroid/org/runnerup/util/CartoTileSource.java`:

```java
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
```

Note: the 7-arg `XYTileSource` constructor is `(name, minZoom, maxZoom, tileSizePixels, imageFilenameEnding, baseUrls, copyrightNotice)`. Retina: 512px tiles requested as `.../{z}/{x}/{y}@2x.png` (CARTO's retina path), so tiles are sharp on the 2.625-density phone.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (no unit test needed — `XYTileSource` is an Android lib class; the day/night URL selection is already covered by `MapThemeTest.tileBaseUrl`).

- [ ] **Step 3: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/CartoTileSource.java
git commit -m "feat: add CARTO tile source with retina support for recording map"
```

---

### Task 3: Palette color resources + marker drawables (day and night)

**Files:**
- Modify: `app/res/values/colors.xml`
- Modify: `app/res/values-night/colors.xml`
- Modify: `app/res/drawable/ic_map_marker_current.xml`
- Modify: `app/res/drawable/ic_map_marker_start.xml`
- Modify: `app/res/drawable/ic_map_marker_lap.xml`
- Create: `app/res/drawable-night/ic_map_marker_current.xml`
- Create: `app/res/drawable-night/ic_map_marker_start.xml`
- Create: `app/res/drawable-night/ic_map_marker_lap.xml`
- Create: `app/res/drawable/map_attribution_bg.xml`

**Interfaces:**
- Produces: `@color/mapBackground`, `@color/markerCurrent`, `@color/markerCurrentStroke`, `@color/markerStart`, `@color/markerStartStroke`, `@color/markerLap`, `@color/mapAttributionBg` (day + night), and drawables `@drawable/map_attribution_bg`, `ic_map_marker_current|start|lap` (day + night). Task 4 references `@drawable/map_attribution_bg`; Task 5 references `@color/mapBackground`.

- [ ] **Step 1: Add day colors**

Append to `app/res/values/colors.xml` (before `</resources>`):

```xml
    <color name="mapBackground">#f5f5f5</color>
    <color name="mapAttributionBg">#66FFFFFF</color>
    <color name="markerCurrent">#3b7dd8</color>
    <color name="markerCurrentStroke">#2968c3</color>
    <color name="markerStart">#3d9a57</color>
    <color name="markerStartStroke">#2e7d32</color>
    <color name="markerLap">#b0851f</color>
```

- [ ] **Step 2: Add night colors**

Replace `app/res/values-night/colors.xml` content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#FAB283</color>
    <color name="colorText">#EEEEEE</color>

    <color name="mapBackground">#0a0a0a</color>
    <color name="mapAttributionBg">#66000000</color>
    <color name="markerCurrent">#fab283</color>
    <color name="markerCurrentStroke">#ffc09f</color>
    <color name="markerStart">#7fd88f</color>
    <color name="markerStartStroke">#4fae62</color>
    <color name="markerLap">#e5c07b</color>
</resources>
```

- [ ] **Step 3: Recolor day marker drawables to reference palette**

`app/res/drawable/ic_map_marker_current.xml` — change only the two `android:fillColor`/`android:strokeColor` values to `@color/markerCurrent` / `@color/markerCurrentStroke` (keep geometry, stroke width 3):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/markerCurrent"
        android:pathData="M12,2a10,10 0 1,1 0,20a10,10 0 1,1 0,-20z"
        android:strokeColor="@color/markerCurrentStroke"
        android:strokeWidth="3" />
</vector>
```

`app/res/drawable/ic_map_marker_start.xml` — fill `@color/markerStart`, stroke `@color/markerStartStroke` (keep geometry, stroke width 2):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/markerStart"
        android:pathData="M12,2a10,10 0 1,1 0,20a10,10 0 1,1 0,-20z"
        android:strokeColor="@color/markerStartStroke"
        android:strokeWidth="2" />
</vector>
```

`app/res/drawable/ic_map_marker_lap.xml` — outer pin fill `@color/markerLap`, inner circle stays `#FFFFFFFF` (keep geometry):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group
        android:translateY="3">
        <path
            android:fillColor="@color/markerLap"
            android:pathData="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 0 1 0-5 2.5 2.5 0 0 1 0 5z" />
    </group>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12 6.5a3 3 0 0 1 0 6 3 3 0 0 1 0-6z" />
</vector>
```

- [ ] **Step 4: Create night marker drawables**

`app/res/drawable-night/ic_map_marker_current.xml`, `app/res/drawable-night/ic_map_marker_start.xml`, `app/res/drawable-night/ic_map_marker_lap.xml` — byte-for-byte identical to the day versions in Step 3 (same `@color/marker*` references; the `values-night` qualifier supplies the dark palette). Copy the exact content of each day file into the corresponding night file.

- [ ] **Step 5: Create attribution pill background**

`app/res/drawable/map_attribution_bg.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/mapAttributionBg" />
    <corners android:radius="8dp" />
</shape>
```

- [ ] **Step 6: Verify resources compile**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL. Then verify no NEW lint issues:

Run: `./gradlew :app:lintLatestDebug`
Expected: `Lint found no new issues (and 25 errors filtered by baseline ...)`

- [ ] **Step 7: Commit**

```bash
git add app/res/values/colors.xml app/res/values-night/colors.xml app/res/drawable/ic_map_marker_current.xml app/res/drawable/ic_map_marker_start.xml app/res/drawable/ic_map_marker_lap.xml app/res/drawable-night/ic_map_marker_current.xml app/res/drawable-night/ic_map_marker_start.xml app/res/drawable-night/ic_map_marker_lap.xml app/res/drawable/map_attribution_bg.xml
git commit -m "style: theme map markers and background to app palette"
```

---

### Task 4: Attribution string + label in `run.xml`

**Files:**
- Modify: `app/res/values/strings.xml`
- Modify: `app/res/layout/run.xml`
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:194` (call site — done in Task 6; here only layout)

**Interfaces:**
- Produces: `@string/map_attribution`, view `@+id/map_attribution` (TextView, default GONE). Consumed by Task 5 (osmdroid LiveMap) and Task 6 (mapbox LiveMap, RunActivity call site).

- [ ] **Step 1: Add the attribution string**

Append to `app/res/values/strings.xml` (before `</resources>`):

```xml
    <string name="map_attribution">© OpenStreetMap contributors © CARTO</string>
```

- [ ] **Step 2: Add the label to `run.xml`**

Inside the root `FrameLayout` (id `start_view`), immediately AFTER the closing tag of `run_mapview` (line 29, the `/>` of the `MapViewWrapper`), insert:

```xml
    <TextView
        android:id="@+id/map_attribution"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="16dp"
        android:layout_marginBottom="52dp"
        android:background="@drawable/map_attribution_bg"
        android:paddingStart="8dp"
        android:paddingTop="2dp"
        android:paddingEnd="8dp"
        android:paddingBottom="2dp"
        android:text="@string/map_attribution"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorOnSurface"
        android:visibility="gone" />
```

Positioning rationale: `marginBottom 52dp` places it in the visible map strip just above the 48dp bottom-sheet peek; the opaque card/sheet/buttons draw over it where they overlap, and the recenter button (margin bottom 136dp) does not collide.

- [ ] **Step 3: Verify build + lint**

Run: `./gradlew :app:assembleLatestDebug` then `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL; no new lint issues.

- [ ] **Step 4: Commit**

```bash
git add app/res/values/strings.xml app/res/layout/run.xml
git commit -m "layout: add map attribution label to recording screen"
```

---

### Task 5: osmdroid `LiveMap` — themed tiles, colors, attribution

**Files:**
- Modify: `app/src/osmdroid/org/runnerup/util/LiveMap.java`

**Interfaces:**
- Consumes: `CartoTileSource.forNight`, `MapTheme.routeColor/edgeColor`, `@color/mapBackground`, `@string/map_attribution`, new constructor arg `View attribution`.
- Produces: `LiveMap(MapViewWrapper, View recenterButton, View attribution)`. Consumed by Task 6.

- [ ] **Step 1: Update imports**

In `app/src/osmdroid/org/runnerup/util/LiveMap.java`, replace the import `import org.osmdroid.tileprovider.tilesource.TileSourceFactory;` with:

```java
import android.content.res.Configuration;
```

`View`, `Color`, `org.runnerup.R`, and `MapTheme`/`CartoTileSource` (same package) need no change.

- [ ] **Step 2: Update fields and constructor**

Delete the two color constants (keep the width/diameter constants that follow them):

```java
  private static final int TRACK_COLOR = Color.parseColor("#FF6D00");
  private static final int TRACK_EDGE_COLOR = Color.parseColor("#FFB680");
```

Change the two polyline field initializers to use a placeholder color that is overwritten in `onCreate` before the polylines are ever added to the map (`ensureOverlaysAdded` only runs on first location/backfill, so the placeholder never displays):

```java
  private final Polyline edge = newPolyline(Color.BLACK, TRACK_EDGE_WIDTH_PX);
  private final Polyline track = newPolyline(Color.BLACK, TRACK_WIDTH_PX);
```

Change the constructor to accept and store the attribution view:

```java
  public LiveMap(MapViewWrapper mapView, View recenterButton, View attribution) {
    this.mapView = mapView;
    this.recenterButton = recenterButton;
    this.attribution = attribution;
    Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);
    recenterButton.setOnClickListener(v -> recenter());
  }
```

Add the field alongside `recenterButton`:

```java
  private final View attribution;
```

- [ ] **Step 3: Update `onCreate`**

Replace:

```java
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
```

with:

```java
    boolean isNight = isNightMode();
    mapView.setTileSource(CartoTileSource.forNight(isNight));
    mapView.setBackgroundColor(mapView.getContext().getColor(R.color.mapBackground));
    track.getOutlinePaint().setColor(MapTheme.routeColor(isNight));
    edge.getOutlinePaint().setColor(MapTheme.edgeColor(isNight));
    attribution.setVisibility(View.VISIBLE);
    mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
```

The label text is already set in `run.xml` (`android:text="@string/map_attribution"`, Task 4); `onCreate` only shows it.

- [ ] **Step 4: Add the `isNightMode` helper**

Add a private method to `LiveMap`, placed after `onCreate`:

```java
  private boolean isNightMode() {
    return (mapView.getContext().getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
  }
```

- [ ] **Step 5: Verify build + tests + lint**

Run: `./gradlew :app:assembleLatestDebug :app:testLatestDebugUnitTest` then `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, tests pass, no new lint issues.

- [ ] **Step 6: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/LiveMap.java
git commit -m "feat: theme recording map tiles and route colors by day/night"
```

---

### Task 6: `RunActivity` call site + mapbox + nomap constructors

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:194`
- Modify: `app/src/mapbox/org/runnerup/util/LiveMap.java`
- Modify: `app/src/nomap/org/runnerup/util/LiveMap.java`

**Interfaces:**
- Consumes: 3-arg `LiveMap` constructors from Task 5.
- Produces: compilable `latest`, `latest -Porg.runnerup.nomap`, and (compile-blind, no token) mapbox variants.

- [ ] **Step 1: Update the `RunActivity` call site**

In `app/src/main/org/runnerup/view/RunActivity.java`, line 194, change:

```java
      liveMap = new LiveMap(runMapview, findViewById(R.id.recenter_button));
```

to:

```java
      liveMap =
          new LiveMap(
              runMapview,
              findViewById(R.id.recenter_button),
              findViewById(R.id.map_attribution));
```

- [ ] **Step 2: Update the mapbox `LiveMap` constructor + attribution**

In `app/src/mapbox/org/runnerup/util/LiveMap.java`, change the constructor signature to accept `View attribution` and store it:

```java
  private final View attribution;

  public LiveMap(MapViewWrapper mapView, View recenterButton, View attribution) {
    this.context = mapView.getContext();
    this.mapView = mapView;
    this.recenterButton = recenterButton;
    this.attribution = attribution;
    recenterButton.setOnClickListener(v -> recenter());
  }
```

At the end of `onCreate` (after the camera subscription setup), show the label (text already set in `run.xml`):

```java
    attribution.setVisibility(View.VISIBLE);
```

Tiles and route color are unchanged (out of scope).

- [ ] **Step 3: Update the nomap stub constructor**

In `app/src/nomap/org/runnerup/util/LiveMap.java`, change:

```java
  public LiveMap(MapViewWrapper mapView, View recenterButton) {}
```

to:

```java
  public LiveMap(MapViewWrapper mapView, View recenterButton, View attribution) {}
```

- [ ] **Step 4: Verify both buildable variants**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL for both. (The mapbox source set is only compiled when `mapbox.properties` exists; it cannot be built here — verify by review. This variant is Play-only.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java app/src/mapbox/org/runnerup/util/LiveMap.java app/src/nomap/org/runnerup/util/LiveMap.java
git commit -m "refactor: pass map attribution view through LiveMap constructors"
```

---

### Task 7: Full gates + on-device day/night verification

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full gate suite**

Run, in order:
1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug`
3. `./gradlew spotlessApply` then `./gradlew spotlessCheck`
4. `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`

Expected: all green; lint reports `Lint found no new issues (and 25 errors filtered by baseline ...)`.

- [ ] **Step 2: On-device night verification**

Device: Nexus 5X (serial `025b46e24edcbca6`). Install the debug build, force system dark mode, start a run via the reliable flow (MainLayout → spinner (180,145) → Treadmill (180,596) → gps_enable (944,1519) → Start (540,1340)), then screenshot:

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night yes
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
# start run via reliable flow, then:
adb -s 025b46e24edcbca6 shell screencap -p /sdcard/night.png
adb -s 025b46e24edcbca6 pull /sdcard/night.png /tmp/opencode/night_palette.png
```

Expected: the visible map strip (between the stats card and the bottom sheet) shows **dark** CARTO tiles, not the old beige OSM style. Verify the attribution label is visible at bottom-end.

- [ ] **Step 3: On-device day verification**

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night no
# force-stop and re-start the run, then:
adb -s 025b46e24edcbca6 shell screencap -p /sdcard/day.png
adb -s 025b46e24edcbca6 pull /sdcard/day.png /tmp/opencode/day_palette.png
```

Expected: **light** CARTO tiles (Positron). If tiles do not load, verify the tile URL by checking `dumpsys` for cache dir creation or inspect the screenshot for blank tiles.

- [ ] **Step 4: Confirm the diff is clean**

```bash
git status --short
```

Expected: only `docs/superpowers/plans/2026-08-13-palette-map.md` and the source changes from Tasks 1-6 (already committed). No user-local files staged.

- [ ] **Step 5: Report**

Report to the user: tile swap verified on-device in both modes, gates green, and the known caveat that the route-line color and markers can't be shown live on the Nexus 5X (no GPS fix) — those are covered by `MapThemeTest` + code review.
