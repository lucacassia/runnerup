# Full-Screen Map Background for RunActivity

## Overview
Add a full-screen map view as the background layer in RunActivity, showing live GPS position and recorded track during runs. The map sits behind all existing UI (stats card, bottom sheet, control buttons) which remain opaque.

## Requirements
- Map covers entire screen as background layer
- Shows live GPS position (current location marker) and recorded track (polyline)
- Loads historical route from database when activity resumes
- Only active when `OSMDROID_ENABLED` or `MAPBOX_ENABLED` (feature flags)
- Stats card, bottom sheet, and buttons remain opaque (no transparency)
- Existing stats card click-to-expand animation unchanged
- Map active whenever RunActivity is visible (not just during active run)

## Architecture

### Layout Hierarchy (run.xml)
```
FrameLayout (root, replaces LinearLayout)
├── MapViewWrapper (id: run_mapview) - full screen, visibility=gone by default
├── LinearLayout (existing UI container)
│   ├── MaterialCardView (stats card, clickable, expandable)
│   ├── CoordinatorLayout (bottom sheet host)
│   │   └── LinearLayout (bottom sheet: handle + "Workout" title + RecyclerView)
│   └── LinearLayout (control buttons: pause, next lap, stop)
```

### Map Integration
- **LiveMap** (existing): Handles live GPS updates, current position marker, track polyline, recenter button logic
- **MapWrapper** (existing): Loads historical route from database on first show
- Both have osmdroid, mapbox, and nomap implementations via source set

### RunActivity.java Changes
1. Add member variables: `MapViewWrapper runMapview`, `LiveMap liveMap`
2. In `onCreate()`: if map enabled, find `run_mapview`, set VISIBLE, create `LiveMap(liveMap, recenterButton)`, call `liveMap.onCreate(savedInstanceState)`
3. In `onTick()`: when tracker has location, call `liveMap.onLocationChanged(location)`
4. Lifecycle delegation:
   - `onResume()` → `liveMap.onResume()`
   - `onPause()` → `liveMap.onPause()`
   - `onDestroy()` → `liveMap.onDestroy()`
5. In `onGpsTrackerBound()`: when tracker has activityId, call `liveMap.onFirstShow(db, activityId)` to backfill historical route
6. Remove old `MapWrapper.start(this)` call

### Feature Flag Handling
```java
if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
    // Initialize map
    runMapview = findViewById(R.id.run_mapview);
    runMapview.setVisibility(View.VISIBLE);
    liveMap = new LiveMap(runMapview, findViewById(R.id.recenter_button));
    liveMap.onCreate(savedInstanceState);
}
```
When disabled: `run_mapview` stays GONE, no LiveMap created.

### Recenter Button
- Keep existing `recenter_button` (FloatingActionButton) in layout
- Pass to LiveMap constructor for recenter functionality
- LiveMap manages its visibility based on `following` state

## Layout Changes (run.xml)
- Root: `LinearLayout` → `FrameLayout`
- Add `MapViewWrapper` as first child with `match_parent` width/height, `visibility="gone"`
- Existing UI structure unchanged, just new parent
- `recenter_button` remains in layout (referenced by LiveMap)

## Data Flow
1. RunActivity starts → map initializes if enabled
2. GPS tracker binds → `onGpsTrackerBound()` → `liveMap.onFirstShow()` loads historical route
3. GPS updates → `onTick()` → `liveMap.onLocationChanged()` adds live points
4. LiveMap renders: historical route (markers + polyline) + live track (polyline) + current position marker
5. User interaction: recenter button toggles `following` mode

## Testing
- Unit tests: existing tests should pass (map code guarded by feature flags)
- Manual: verify map shows behind UI, live tracking works, historical route loads, recenter works
- Feature flag variants: test with osmdroid, mapbox, and nomap builds

## Risks & Mitigations
- **Map initialization timing**: Ensure map ready before first location update (LiveMap handles this)
- **Memory**: LiveMap uses executor for DB loading; shutdown in onDestroy()
- **nomap flavor**: LiveMap is no-op stub; no code changes needed
- **Performance**: Polyline updates on every location change; throttle if needed (LiveMap already has epsilon check)