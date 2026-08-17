# Recording Screen Stats Morph Design

**Date:** 2026-08-17
**Status:** Approved

## Goal

In the recording activity screen (`RunActivity`), when the stats container card is tapped to expand, the three primary stats — distance, time, pace — and their labels should smoothly morph from their horizontal row positions into their vertically stacked positions, growing in size as they travel. Collapse reverses the animation.

## Current Behavior

- `run.xml` `stats_3_area` is a `FrameLayout` holding two overlapping layouts:
  - `stats_3_horizontal` — 3 columns (Distance | Time | Pace), value + label per column (`textAppearanceHeadlineLarge`, ~32sp max).
  - `stats_3_vertical` — 3 stacked rows, value + label per row (`textAppearanceDisplayMedium`, up to 96sp max), `visibility=gone` by default.
- `RunActivity.toggleStatsExpanded()` (RunActivity.java:441) animates the card/area height over 250ms, cross-fades the two layouts (150ms alpha each way), and rotates the chevron 180°.
- Both value sets are fed identical text every ~1s tick (RunActivity.java:674-680).

## New Behavior

Replace the cross-fade with a 250ms shared-element morph: each value and label animates `translationX/Y` + `scaleX/Y` + `alpha` from its horizontal slot to its vertical slot. Collapse runs the same animation in reverse.

### Animated Pairs (6)

For each metric — distance, time, pace — the value and its label move together:

| Metric | Compact value | Compact label | Expanded value | Expanded label |
|---|---|---|---|---|
| Distance | `run_activity_distance` | `distance_label` (new ID) | `run_activity_distance_expanded` | `distance_expanded_label` |
| Time | `run_activity_time` | `time_label` (new ID) | `run_activity_time_expanded` | `time_expanded_label` |
| Pace | `run_activity_pace` | `pace_label` (new ID) | `run_activity_pace_expanded` | `pace_expanded_label` |

The compact labels currently have no IDs in `run.xml` (lines 116, 144, 172); IDs are added so they can be animated. Expanded labels already have IDs.

### Animation Mechanics

- Single 250ms animator drives both the existing card/area height expansion AND the morph, keeping them in sync.
- **Targets:** before the animator starts, defer target computation to after the layout pass (via `View.post()`), so expanded views have non-zero dimensions. The targets are the compact views' translated/scaled end state (equivalently, where the expanded views sit).
- **Per frame:** for each of the 6 pairs, set `translationX/Y` (pivot at top-left (0,0), so values grow toward their expanded row), `scaleX/Y` (1.0 → ~2-3x, matching compact→expanded size ratio), and `alpha` (compact 1→0; expanded 0→1).
- **Expand direction:** distance top-left → top full-width row; time → middle row; pace → bottom row; labels follow their values.
- **Collapse direction:** exact reverse — values shrink and slide back to their horizontal slots.
- Interpolator: default (decelerate), matching existing feel.

### Clipping & Rendering

- Set `setClipChildren(false)` on `stats_3_area` (and the card `table_layout1` if needed) so growing text is not clipped during scale-up.
- Expanded values already use `LAYER_TYPE_SOFTWARE` (RunActivity.java:233-235). The compact values get a software layer only during the animation to keep scaled text crisp, then the layer is removed.

### Live Updates During Recording

- Values refresh ~every 1s; both layouts are kept in sync (RunActivity.java:674-680).
- During the ~250ms morph, suppress that sync so a mid-flight refresh cannot re-measure and jump the animated views. Values are captured at animation start; at most one tick of staleness.

### Edge Cases

- `statsDelta <= 0` or natural height <= 0 → existing guard returns early (no room to expand).
- Rapid taps → existing `statsAnimating` guard.
- No change to value formatting, units, or layout structure.

## Files Touched

- `app/res/layout/run.xml` — add IDs to the 3 compact label TextViews.
- `app/src/main/org/runnerup/view/RunActivity.java` — replace the cross-fade block in `toggleStatsExpanded()` with the morph; add measure-once target computation and per-frame updates; suppress value sync while animating.
- New unit test for any extracted target-math helper (if one is extracted); device smoke test (expand and collapse both directions, verify smooth growth and reversal, verify no clipping).