# Design: Match recorded track style to live track

**Date:** 2026-08-14
**Status:** Approved

## Problem

The recorded-activity map (DetailActivity "Map" tab) draws the route track in a
fixed orange: track `#FF6D00`, edge `#FFB680` (`app/src/osmdroid/org/runnerup/util/MapWrapper.java:60-61`).
The live recording map (`app/src/osmdroid/org/runnerup/util/LiveMap.java`) draws
the track theme-aware via `MapTheme`: day `#3B7DD8` blue with white edge, night
`#FAB283` orange with near-black edge (`MapTheme.java:7-10`, applied at
`LiveMap.java:89-90`). The user wants the recorded track to match the live
track's color and style.

## Scope findings

- Widths (track 10px, edge 20px), stroke caps/joins (ROUND) are already
  identical between `MapWrapper` and `LiveMap` — the only difference is color
  and day/night awareness.
- The **mapbox** flavor already matches: both live and recorded draw red at
  3.0f width (`app/src/mapbox/org/runnerup/util/LiveMap.java:231-238`,
  `app/src/mapbox/org/runnerup/util/MapWrapper.java:242-243`). No change.
- The **nomap** flavor is a stub. No change.
- Basemap: the live map swaps CARTO light/dark tiles + a day/night color
  matrix. Per user choice, the recorded map keeps its current MAPNIK basemap —
  only the track colors change.

## Change

**`app/src/osmdroid/org/runnerup/util/MapWrapper.java`:**

1. Compute `isNight` from the device configuration's uiMode, mirroring
   `LiveMap.isNightMode()` (`LiveMap.java:121-125`). Because `MapWrapper`
   imports `org.osmdroid.config.Configuration`, the android
   `Configuration.UI_MODE_NIGHT_MASK/UI_MODE_NIGHT_YES` references are
   fully-qualified:
   `(mapView.getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES`
2. In `onCreate`, compute `isNight` and pass it through `loadRouteAsync()` to
   `loadRouteData()`. Replace the fixed colors:
   - edge: `newPolyline(TRACK_EDGE_COLOR, TRACK_EDGE_WIDTH_PX)` →
     `newPolyline(MapTheme.edgeColor(isNight), TRACK_EDGE_WIDTH_PX)`
   - track: `newPolyline(TRACK_COLOR, TRACK_WIDTH_PX)` →
     `newPolyline(MapTheme.routeColor(isNight), TRACK_WIDTH_PX)`
3. Delete the now-unused constants `TRACK_COLOR` and `TRACK_EDGE_COLOR`
   (`MapWrapper.java:60-61`). Keep `TRACK_WIDTH_PX`, `TRACK_EDGE_WIDTH_PX`,
   `MARKER_DIAMETER_PX`.
4. `MapTheme` is in the same package (`org.runnerup.util`) — no import needed.

Result: recorded track renders day = blue `#3B7DD8` + white edge, night = orange
`#FAB283` + near-black edge — identical colors to the live track. Style
(widths/caps/joins) already matches.

## Not changed

- Basemap tiles of the recorded map (stays MAPNIK).
- Mapbox and nomap flavors.
- Live map.
- Markers.

## Verification

- `./gradlew test` — no logic change; suite stays green.
- `./gradlew :app:lintLatestDebug` — no new issues.
- `./gradlew spotlessApply` / `spotlessCheck`.
- `./gradlew :app:assembleLatestDebug` (+ `-Porg.runnerup.nomap` variant).
- On-device: open a recorded activity's Map tab in day mode and night mode;
  confirm the track colors match the live recording map's colors. Capture
  screenshots for evidence.
