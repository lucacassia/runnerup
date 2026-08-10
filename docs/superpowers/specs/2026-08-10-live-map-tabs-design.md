# Design: Workout/Map tabs + live map on the recording screen

## Overview

Replace the plain workout-plan list on the recording screen (`RunActivity`) with a
tabbed container under the stats card. Two tabs: **Workout** (the existing step
list) and **Map** (a live map showing the recorded track and current position
during recording). Tab look and behavior mirrors the Basic/Interval/Advanced tabs
in `StartFragment`. The Map tab only exists when maps are enabled
(`BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED`), mirroring
`DetailActivity`.

## Architecture

Follows the existing source-set variant pattern used by `MapWrapper` and
`MapViewWrapper`:

- `app/src/main` — layouts, `RunActivity` wiring.
- `app/src/osmdroid` — primary `LiveMap` implementation.
- `app/src/mapbox` — parallel `LiveMap` port (same behavior/visuals).
- `app/src/nomap` — `LiveMap` no-op stubs; never constructed because the Map tab
  is not added.

## 1. Layout (`app/res/layout/run.xml`)

The middle region (currently just `workout_list` between `table_layout1` and
`run_table_row1`) becomes:

```
[table_layout1]   stats card (unchanged)
[run_tab_layout]  Material TabLayout, tabMode=fixed, below table_layout1, above run_table_row1
[run_tab_content] RelativeLayout, below run_tab_layout, above run_table_row1
   ├─ workout_list (RecyclerView, fills parent)        [existing]
   ├─ run_mapview   (MapViewWrapper, fills parent, GONE)  [new]
   └─ hr_debug      (aligned to workout_list, unchanged)
[run_table_row1]   pause / next-lap buttons (unchanged)
```

- `run_tab_content` is a `RelativeLayout` so `hr_debug`'s existing
  `layout_alignTop`/`layout_alignBottom` on `workout_list` keeps working.
- `run_mapview` is a `org.runnerup.util.MapViewWrapper` (resolves to a real map
  view in osmdroid/mapbox builds, a plain `View` in nomap builds), inflated the
  same way `detail.xml` already does.
- The `Workout` tab is always present. The `Map` tab is added only in code when
  maps are enabled; `run_mapview` stays GONE otherwise (single-tab TabLayout in
  nomap builds, same as `DetailActivity` always showing its TabLayout).

## 2. `RunActivity` wiring

New fields: `runTabLayout`, `LiveMap liveMap` (null when maps disabled), and a
readable DB handle via `DBHelper.getReadableDatabase(this)` for backfill.

- `onCreate`: add `Workout` tab always; if
  `BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED`, add `Map` tab,
  then construct `liveMap = new LiveMap(this, (MapViewWrapper) findViewById(R.id.run_mapview))`
  and call `liveMap.onCreate(savedInstanceState)`.
- Tab listener toggles content visibility (StartFragment pattern):
  - Workout → `workout_list` VISIBLE, `run_mapview` GONE.
  - Map → `workout_list` GONE, `run_mapview` VISIBLE; first selection triggers
    `liveMap.onFirstShow(mDB, mTracker.getActivityId())` (one-time DB backfill).
- Existing `onTick()` (~1 Hz) additionally calls
  `liveMap.onLocationChanged(mTracker.getLastKnownLocation())`; `LiveMap` dedupes
  internally.
- Lifecycle: `onResume`/`onPause` → `liveMap.onResume()/onPause()`; `onDestroy` →
  `liveMap.onDestroy()`.
- Untouched: `hr_debug`, workout-list auto-scroll to current step, current-step
  highlighting, buttons.

## 3. `LiveMap` (osmdroid variant — primary)

- Constructor `LiveMap(Context, MapViewWrapper)`. Map config mirrors `MapWrapper`:
  MAPNIK tiles, zoom buttons hidden, multi-touch enabled, osmdroid user agent.
- State: `List<GeoPoint> points`, edge + track `Polyline`s (reuse MapWrapper's
  color/width constants), current-position `Marker`, lap `Marker`s,
  `boolean following = true`, `boolean backfilled = false`.
- `onFirstShow(SQLiteDatabase mDB, long activityId)`: on a single-thread executor,
  load `LocationEntity.LocationList(mDB, activityId)`; build edge/track polylines
  and start/lap markers (same logic and style as `MapWrapper`); center on the last
  point, zoom 15, set `backfilled`. Guard when `activityId == -1`.
- `onLocationChanged(Location)`: ignore null and unchanged fixes; append `GeoPoint`
  to both polylines, move the current-position marker; if `following`, recenter
  the camera on the new point; `mapView.invalidate()`.
- Follow + recenter: a map scroll (user pan) sets `following = false` and shows a
  small recenter `ImageButton` (overlaid on the map in `run.xml`, GONE by
  default). Tapping it re-centers on the last fix, hides itself, and resumes
  following. Pinch-zoom does not break following.
- Lifecycle: `onResume`/`onPause` forward to the map view; `onDestroy` shuts down
  the executor.

### mapbox variant

Parallel port using mapbox map + annotations APIs, same behavior and visuals,
mirroring how the mapbox `MapWrapper` parallels the osmdroid one.

### nomap variant

All methods no-op stubs, mirroring the nomap `MapWrapper`.

## 4. Data flow

- Live fixes flow in at ~1 Hz via the existing `onTick()` hook.
- Anything recorded before the map is first opened is backfilled once from the DB,
  so the map always shows the whole run from the start.
- Backfill runs on a background executor (same pattern as `MapWrapper`);
  incremental live appends mutate the polylines on the main thread at 1 Hz (cheap).

## 5. Edge cases

- No GPS fix yet / null location → map stays empty, no crash.
- Map opened mid-run → one-time backfill draws the full track; live fixes continue.
- Rotation/recreation → `RunActivity` re-binds; `LiveMap` rebuilds and backfills
  again; following defaults to true.
- Paused run → no new fixes, map static; no special UI.
- `activityId == -1` → backfill skipped, no crash.
- nomap builds → single Workout tab; `LiveMap` never constructed.
- No new dependencies (osmdroid/mapbox already present).

## 6. Verification

- `./gradlew test` — existing tests must keep passing.
- `./gradlew :app:assembleLatestDebug`.
- `./gradlew :app:lintLatestDebug` — no new issues beyond the 25-item baseline.
- `./gradlew spotlessApply` + `spotlessCheck`.
- Device smoke test (OnePlus Nord CE, serial `5717a66e`): start a Basic run →
  workout list behaves as today; switch to Map tab → backfilled track appears and
  camera follows; pan → recenter button appears; tap → recenters and resumes
  following; next lap → lap marker appears; pause → map static; rotate → no crash
  and track restored.

## Out of scope

- Map offline tile caching (matches current DetailActivity behavior).
- MapBox specifics beyond a functional parallel port.
- Any changes to `DetailActivity` or the saved-activity map.
