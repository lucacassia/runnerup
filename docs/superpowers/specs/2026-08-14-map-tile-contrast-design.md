# Map Tile Contrast (osmdroid recording map)

## Overview

The palette-themed recording map (Task 1-7 of the palette-map plan) renders CARTO Positron by day and CARTO Dark Matter at night. On the test phone the day map reads as washed out (near-white background, pale gray roads) and the night map as pitch black (features invisible against `#0a0a0a`). This change makes the base tiles' features (roads, labels, land) more visible by applying a per-mode color filter to the tile layer. Route line, markers, and attribution are untouched (user scope decision).

## Scope

- **In scope:** `app/src/osmdroid/org/runnerup/util/MapTheme.java` (filter matrices as data), `app/src/osmdroid/org/runnerup/util/LiveMap.java` (apply filter), `mapBackground` day/night colors, `MapThemeTest`.
- **Out of scope:** mapbox / nomap `LiveMap` variants, route/marker/attribution colors, tile providers, marker drawables, anything outside the `app` module.
- Tiles stay CARTO Positron (day) / CARTO Dark Matter (night). Approach 1 of the brainstorm: `TilesOverlay.setColorFilter` (a supported osmdroid API; also used by its built-in night invert).

## Requirements

- Day tiles: slightly darker and higher separation so gray roads/labels stand out from the near-white background, with a moderate base-tone shift (background stays light, not pure white).
- Night tiles: lifted out of pitch black with higher separation so roads/labels read against a dark-gray background (dark theme preserved, no longer black-on-black).
- The filter applies to the tile layer only (draw time); route, markers, attribution pill, and map background canvas are unaffected.
- Filter values live in `MapTheme` as pure `float[20]` data so the unit test stays JVM-safe (no `android.graphics.ColorMatrix` construction in tested code).
- All existing LiveMap behavior unchanged.

## Architecture

### Filter matrices — `MapTheme.java`

Add two constants (pure data, no Android imports needed in the class):

```java
public static final float[] DAY_TILE_MATRIX = { ... };   // scale-from-white, c = 1.3
public static final float[] NIGHT_TILE_MATRIX = { ... }; // scale-from-black, c = 1.8
```

Both are identity except the R/G/B diagonal scale coefficient:

- **Day** — scale toward white: `R' = c·R + 255·(1−c)`, `c ≈ 1.3`. Diagonal = 1.3, translation = 255·(1−1.3) = −76.5 per channel. Effect: bg ~`#f2`→~`#ee`, gray roads ~`#c8`→~`#b7`, labels → near-black.
- **Night** — scale from black: `R' = c·R`, `c ≈ 1.8`. Diagonal = 1.8, no translation. Effect: bg ~`#0a`→~`#12`, roads ~`#1e`→~`#36`, labels ~`#77`→~`#d6`.

These are starting points; exact coefficients are tuned against on-device screenshots in the implementation plan.

### Apply the filter — `LiveMap.java` (osmdroid)

In `onCreate`, right after `mapView.setTileSource(...)`:

```java
mapView.getOverlayManager().getTilesOverlay().setColorFilter(
    new ColorMatrixColorFilter(new ColorMatrix(isNight ? NIGHT_TILE_MATRIX : DAY_TILE_MATRIX)));
```

`MapViewWrapper` extends osmdroid `MapView`, so `getOverlayManager()` is directly available. The filter is re-applied on every tile draw (`onTileReadyToDraw` → `Drawable.setColorFilter`), including cached tiles and future loads — no re-download, no per-tile processing.

### Background alignment — `colors.xml` / `values-night/colors.xml`

The filter changes tiles only; `mapBackground` (canvas, drawn unfiltered) peeks through at map edges/while tiles load. Retune to the filtered tone so no seam appears:

- Day `mapBackground`: `#f5f5f5` → `#eeeeee`
- Night `mapBackground`: `#0a0a0a` → `#121212`

## Data Flow

1. `LiveMap.onCreate` resolves `isNight` → sets tile source (unchanged), applies the matching `ColorMatrixColorFilter` to the tiles overlay, sets `mapBackground` (unchanged call, new value).
2. Tiles render through the filter each frame; route/markers/attribution draw unfiltered on top.
3. uiMode change → RunActivity is recreated, filter re-applied with the new mode (no mid-run handling, same as today).

## Testing

- **Unit tests (`MapThemeTest`):** assert both matrices are identity except the R/G/B diagonal scale; diagonal > 1 for both; night translation = 0; day translation negative (`255·(1−c)`); alpha row untouched. No Android objects constructed (raw float data only).
- **On-device:** pixel-probe the rendered map before/after in day and night (bg, road, label tones) against the targets above; capture screenshots. Toggle system day/night. Route cannot be shown live (no GPS fix) — unaffected anyway.
- **Gates:** `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond the 25 baseline), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` and the `-Porg.runnerup.nomap` variant (nomap/mapbox `LiveMap` untouched, but the shared `colors.xml` change must build).

## Risks & Mitigations

- **Over-clamping:** strong scale can clamp channels to 0/255 (e.g. day `#ffffff` stays white). Acceptable — targets keep every key tone inside range; verified by pixel probes.
- **Aesthetic drift:** retuned `mapBackground` and tile tone shift make the map slightly grayer on both modes. Intended, per the approved "moderate shift".
- **osmdroid version differences:** `TilesOverlay.setColorFilter` exists in the pinned 6.1.x; verified against the bundled AAR's bytecode. No dependency change.
