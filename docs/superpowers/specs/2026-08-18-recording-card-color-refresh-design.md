# Recording Screen Stats Card Color Refresh

**Date:** 2026-08-18
**Status:** Draft

## Summary

Refresh the recording screen stats card with a 3px themed border and elevation shadow to solve three visual issues: card blends into the bottom sheet, too much white/flat feel, and the stats card feels plain.

## What Changes

### Stats card (`table_layout1` in `run.xml`)

- **Border:** 3px solid `?attr/colorPrimary` — blue (#3B7DD8) in light mode, peach (#FAB283) in dark mode
- **Shadow:** 4dp elevation — standard Material black shadow, pairs with the colored border
- **Background:** white in light mode, `?attr/colorSurface` in dark mode (via MaterialCardView default)
- **Corner radius:** 12dp (unchanged)
- **Stat values:** `?attr/colorOnSurface` — dark in light mode, light in dark mode (unchanged)
- **Stat labels:** `?attr/colorOnSurfaceVariant` (unchanged)
- **Expand indicator:** `?attr/colorOnSurfaceVariant` (unchanged)

### Dark mode specifics

The app's dark theme uses a different primary color:

| Role | Light | Dark |
|------|-------|------|
| `colorPrimary` | `#3B7DD8` (blue) | `#FAB283` (peach) |
| `colorSurface` | `#FFFFFF` | `#0A0A0A` |
| `colorOnSurface` | `#1A1A1A` | `#EEEEEE` |

Using `?attr/colorPrimary` for the stroke means the border adapts: blue card border in light mode, peach card border in dark mode. The card background follows the theme surface automatically via MaterialCardView.

### What stays the same

- Bottom sheet: follows theme surface (white in light, dark in dark)
- Buttons (Next Lap / Pause): use `?attr/colorPrimary`, already theme-aware
- Map: no changes
- Expand/collapse animation: no changes
- All stat text content and formatting: no changes

## Implementation

### `app/res/layout/run.xml`

Replace the `MaterialCardView` attributes for `table_layout1`:

```xml
<!-- Before -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewFilledStyle"
    ...
    app:cardElevation="0dp">

<!-- After -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewOutlinedStyle"
    app:strokeColor="?attr/colorPrimary"
    app:strokeWidth="3dp"
    app:cardElevation="4dp"
    ...>
```

Note: `materialCardViewOutlinedStyle` gives us the stroke system. The card background inherits from the theme surface automatically — no need to hardcode white.

### Shadow

Standard Material elevation shadow (black-based). The colored border provides the accent; the shadow provides lift. No custom shadow code needed.

## Files to Modify

- `app/res/layout/run.xml` — card style attributes only

## Verification

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug`
3. `./gradlew spotlessApply && spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. Light mode: verify blue border, shadow lift, clear separation from white bottom sheet
6. Dark mode: verify peach border, shadow lift, card visible against dark bottom sheet
