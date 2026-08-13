# Palette-Themed Recording Map (osmdroid)

## Overview

Change the recording-screen live map (osmdroid `LiveMap`) so its base tiles and app-drawn overlays follow the app's day/night color palette. The map currently uses standard OpenStreetMap `MAPNIK` tiles (beige/gray) with an orange route and hardcoded marker colors; the goal is a map that looks like it belongs to the app's theme.

Palette source: the opencode theme palette at `/home/megadoro/Downloads/opencode.json` (dark + light variants). It is consistent with the app's existing day/night `colorPrimary` (`#3B7DD8` day / `#FAB283` night) and text colors, so the design reuses those values.

## Scope

- **In scope:** `app/src/osmdroid/org/runnerup/util/LiveMap.java` (recording screen live map), marker drawables, run layout attribution label, palette color resources.
- **Out of scope:** mapbox `LiveMap` (no token present; Play-only), `MapWrapper` (DetailActivity "Map" tab stays `MAPNIK`), anything outside `app` module.
- Base tiles **and** overlays change.
- Map follows system day/night mode.

## Requirements

- Base tiles: CARTO Dark Matter at night, CARTO Positron (light) by day. Free, no API key, standard osmdroid raster tiles.
- Route line + edge follow `colorPrimary` per mode: day blue `#3b7dd8` (edge white), night peach `#fab283` (edge near-black).
- Markers (current/start/lap) recolored to the palette with `drawable-night` variants.
- Map background color matches the palette so no white flash while tiles load.
- Attribution "© OpenStreetMap contributors © CARTO" shown on the map (osmdroid does not render it automatically).
- All existing LiveMap behavior unchanged: route backfill, current-position marker, follow/recenter, zoom.

## Architecture

### Tile sources — new file `app/src/osmdroid/org/runnerup/util/CartoTileSource.java`

Small `XYTileSource` subclass that emits retina `@2x` URLs (512px tiles) so the map is sharp on the 2.625-density test phone.

```java
public class CartoTileSource extends XYTileSource {
  public CartoTileSource(String name, String baseUrl, String copyright) {
    super(name, 0, 19, 512, ".png", new String[] {baseUrl}, copyright, TileSourcePolicy.WHITELISTED);
  }
  @Override
  public String getTileURLString(long mapTileIndex) {
    // base/{z}/{x}/{y}@2x.png
  }
}
```

Two instances defined as constants in `LiveMap` (or in `CartoTileSource`):
- Night: name `"carto-dark"`, base `https://basemaps.cartocdn.com/dark_all`
- Day: name `"carto-positron"`, base `https://basemaps.cartocdn.com/light_all`
- Copyright: `"© OpenStreetMap contributors © CARTO"`

Distinct names keep the osmdroid tile cache separate per mode.

### Color resources

New entries in `app/res/values/colors.xml` (day) and `app/res/values-night/colors.xml` (night):

| Resource | Day | Night |
|---|---|---|
| `mapBackground` | `#f5f5f5` | `#0a0a0a` |
| `mapRoute` | `#3b7dd8` | `#fab283` |
| `mapRouteEdge` | `#ffffff` | `#0a0a0a` |
| `markerCurrent` | `#3b7dd8` | `#fab283` |
| `markerCurrentStroke` | `#2968c3` | `#ffc09f` |
| `markerStart` | `#3d9a57` | `#7fd88f` |
| `markerStartStroke` | `#2e7d32` | `#4fae62` |
| `markerLap` | `#b0851f` | `#e5c07b` |
| `markerLapStroke` | `#7a5c14` | `#a9873f` |

The day values re-use the existing `colorPrimary` blue and palette colors already in `colors.xml` where they match; night values come from the opencode dark palette.

### Marker drawables

- Add `app/res/drawable-night/ic_map_marker_current.xml`, `ic_map_marker_start.xml`, `ic_map_marker_lap.xml` — identical geometry to the day versions, fill/stroke colors from `@color/markerCurrent`, `@color/markerStart`, `@color/markerLap`.
- Recolor the day `app/res/drawable/ic_map_marker_{current,start,lap}.xml` to reference the same `@color/marker*` resources instead of hardcoded hex.

### LiveMap.java changes (osmdroid)

In `onCreate`:
1. Resolve current mode once: `(context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES`.
2. `mapView.setTileSource(isNight ? CARTODARK : CARTOLIGHT)`.
3. `mapView.setBackgroundColor(context.getColor(R.color.mapBackground))` — single resource, resolves per mode via `values-night`.
4. Replace the `static final int TRACK_COLOR` / `TRACK_EDGE_COLOR` constants with mode-resolved values: `TRACK_COLOR = R.color.mapRoute`, `TRACK_EDGE_COLOR = R.color.mapRouteEdge`, resolved via `context.getColor(...)`. The polylines keep their current widths/caps/joins.
5. Set the attribution label text.

Mid-run theme changes are not handled: RunActivity is recreated on `uiMode` change, so the map restarts with the new mode. No extra code.

### Attribution label

- Add `TextView` `@+id/map_attribution` to `run.xml`, inside the root `FrameLayout` after `run_mapview`, positioned `layout_gravity="bottom|end"`, `layout_marginBottom="52dp"` (just above the 48dp bottom-sheet peek so it stays visible in the map strip), small text size (`11sp`), translucent background, `colorOnSurfaceVariant` tint.
- New string `map_attribution` = `© OpenStreetMap contributors © CARTO`.
- Extend the `LiveMap` constructor to accept the attribution view as a third parameter (mirrors the existing `recenterButton` pattern) and set its text in `onCreate`.
- Update `RunActivity` call site to pass the view.

## Data Flow

1. RunActivity starts → `LiveMap.onCreate` resolves uiMode → sets tile source, background color, route/edge colors, attribution text.
2. Tiles load from CARTO (dark or light). Route/edge/markers draw with palette colors.
3. All existing flows (backfill, live updates, follow/recenter) unchanged.

## Testing

- **Unit tests:** extract the day/night color mapping into a small pure helper (e.g., a `MapTheme` enum or a static method returning the color resources/ints for a given `isNight`), and unit-test that night yields the dark palette and day the light palette. Existing tests must keep passing.
- **On-device:** verify Dark Matter renders in night mode and Positron in light mode (toggle system day/night). Route line cannot be shown live (no GPS fix on the Nexus 5X); route/edge/marker colors are covered by the unit tests and code review.
- Gates: `./gradlew test`, `:app:lintLatestDebug` (no new issues), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` and the `-Porg.runnerup.nomap` variant.

## Risks & Mitigations

- **CARTO availability:** free tile provider; an outage yields empty tiles exactly like today's MAPNIK. No special handling.
- **Retina URL correctness:** verify the `@2x` URL format renders on-device; fallback is dropping to 256px tiles (plain `XYTileSource`).
- **Nexus 5X compositing flakiness:** the pre-existing rendering corruption may make visual verification unreliable; rely on unit tests plus the Nord CE for visual confirmation.
- **Network/attribution policy:** attribution label required by CARTO terms; included in this design.
