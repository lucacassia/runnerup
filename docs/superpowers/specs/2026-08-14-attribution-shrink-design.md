# Shrink + De-emphasize Map Attribution

## Overview

The recording-screen map shows an attribution pill ("© OpenStreetMap contributors © CARTO") at the bottom-left of the map. It renders at 11sp with a 40%-opacity background and reads as visually noisy. Keep the attribution (required by CARTO ToS and OSM ODbL — attribution is binding; this was confirmed with the user, who chose "shrink + de-emphasize" over removal) but make it subtle.

## Scope

- **In scope:** `app/res/layout/run.xml` (pill text size + padding), `app/res/values/colors.xml` + `app/res/values-night/colors.xml` (`mapAttributionBg` alpha).
- **Out of scope:** string `map_attribution`, drawable shape `map_attribution_bg.xml`, LiveMap/RunActivity wiring, pill position/margins, any other screen.

## Requirements

- Text size 11sp → 8sp (overrides `?attr/textAppearanceLabelSmall` via an explicit `android:textSize`; roughly half the visual area).
- Padding 8/2dp → 4dp horizontal, 1dp vertical.
- Pill background alpha `#66` (40%) → `#40` (25%) in both day (`#66FFFFFF` → `#40FFFFFF`) and night (`#66000000` → `#40000000`).
- Pill stays visible (unchanged `setVisibility(VISIBLE)` in osmdroid + mapbox LiveMap), position `bottom|start`, margins 16dp / `@dimen/run_recenter_bottom_margin`, text `?attr/colorOnSurface`, corners radius 8dp.
- Attribution remains legible enough to satisfy the attribution requirement.

## Architecture

No structural change. Two files:

1. `run.xml` pill TextView (~lines 31-47): add `android:textSize="8sp"`, change `paddingStart/End` 8dp → 4dp and `paddingTop/Bottom` 2dp → 1dp. Everything else unchanged.
2. `colors.xml:43` (`mapAttributionBg` `#66FFFFFF` → `#40FFFFFF`) and `values-night/colors.xml:7` (`#66000000` → `#40000000`).

## Testing

- No unit tests needed (layout-only change).
- On-device: launch the run screen in day and night, confirm the pill renders smaller with a faint background and the text is still readable; screenshot for the record.
- Gates: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond 25 baseline), `spotlessApply`/`spotlessCheck`, `:app:assembleLatestDebug` + nomap variant.
