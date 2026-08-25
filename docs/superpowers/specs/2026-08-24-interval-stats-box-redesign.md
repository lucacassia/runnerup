# Interval Stats Box Redesign (osmdroid)

## Problem

The interval/lap stats box on the osmdroid map view is rendered via osmdroid's `Marker.setTextIcon()` — a flat white rectangle with black bold text, sharp corners, zero padding. It is always white regardless of night mode, has no visual hierarchy, and looks dated.

## Goal

Replace the plain text box with a styled card/chip that has rounded corners, a semi-transparent theme-aware background, a colored lap badge, and a small downward-pointing arrow. The same stats are shown (lap number, distance, elapsed time) in a visually modern container.

## Scope

- **In scope:** osmdroid path only (`app/src/osmdroid/org/runnerup/util/MapWrapper.java`). Custom Bitmap rendering via Canvas API replacing `setTextIcon()`. Theme-aware colors (day/night).
- **Out of scope:** Mapbox path (uses stock AlertDialog + plain text annotations). Start/end markers (unchanged).

## Design

### Visual layout

```
        ┌──────────────────────┐
        │ ①  3.2km   45:23    │
        └───────────┬──────────┘
                    ▼
                  (track point)
```

### Container

- Rounded rectangle: 8dp corner radius
- Background: semi-transparent surface color — `?attr/colorSurface` at 90% opacity
- Padding: 8dp horizontal, 6dp vertical
- Arrow: small downward-pointing triangle (8dp wide, 4dp tall) drawn at bottom center, same fill color as box background
- No XML layout — entire box drawn onto a `Bitmap` via Android `Canvas`

### Lap badge

- 16dp diameter circle
- Fill color: track route color from `MapTheme` (day: `#3B7DD8`, night: `#FAB283`)
- Lap number in white, centered, 10sp bold
- Positioned at left edge of box, 8dp from left padding

### Stats text

- Distance and elapsed time: `textAppearanceLabelMedium` (12sp), `?attr/colorOnSurface`
- Separated by a thin vertical divider: 1dp wide, `?attr/colorOutline` at 30% opacity, 12dp tall
- Positioned to the right of the lap badge with 6dp gap

### Positioning

- Marker anchored at `ANCHOR_BOTTOM` (unchanged)
- Box floats above the track point
- Arrow points down to the exact interval location

### Colors

| Element | Day | Night |
|---|---|---|
| Box background | `#E6FFFFFF` (90% white) | `#E61C1C1C` (90% dark) |
| Lap badge fill | `#3B7DD8` (route day color) | `#FAB283` (route night color) |
| Lap badge text | White | White |
| Stats text | `?attr/colorOnSurface` | `?attr/colorOnSurface` |
| Divider | `?attr/colorOutline` at 30% | `?attr/colorOutline` at 30% |
| Arrow | Same as box bg | Same as box bg |

### Text format (unchanged)

`"#1 3.2km 45:23"` — lap number, short distance, short elapsed time.

## Implementation

### Files to modify

1. **`app/src/osmdroid/org/runnerup/util/MapWrapper.java`**
   - New method: `private Bitmap createIntervalIcon(int lap, String distance, String elapsed, int routeColor, boolean isNight)`
   - Draws the styled box onto a `Bitmap` using `Canvas`: rounded rect background, triangle arrow, colored circle badge, divider, text
   - Replace `marker.setTextIcon(info)` call (line 158) with `marker.setIcon(createIntervalIcon(...))`
   - Pass route color from `MapTheme.ROUTE_DAY` / `MapTheme.ROUTE_NIGHT`
   - Detect night mode via `context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK`

2. **`app/src/osmdroid/org/runnerup/util/MapTheme.java`**
   - Ensure `ROUTE_DAY` and `ROUTE_NIGHT` color constants are accessible from `MapWrapper` (they already are — both are `static final int`)

### Drawing details

All measurements in dp-scaled pixels (multiply by `density`):

1. **Measure text:** `Paint.measureText()` for distance and elapsed strings at 12sp
2. **Compute box width:** `8dp + badgeDiameter(16dp) + gap(6dp) + distWidth + dividerGap(6dp) + dividerWidth(1dp) + dividerGap(6dp) + elapsedWidth + 8dp` — all on same horizontal line
3. **Compute box height:** `6dp + max(badgeDiameter, textHeight) + 6dp`
4. **Draw background:** `canvas.drawRoundRect(rect, 8dp, 8dp, bgPaint)` — semi-transparent surface color
5. **Draw arrow:** `canvas.drawPath(triangle, bgPaint)` — triangle from bottom center
6. **Draw badge:** `canvas.drawCircle(cx, cy, 8dp, badgePaint)` — route-colored circle
7. **Draw lap text:** `canvas.drawText(String.valueOf(lap), cx, cy + ascent/2, whitePaint)` — centered in badge
8. **Draw divider:** `canvas.drawLine(x, y1, x, y2, dividerPaint)` — vertical line
9. **Draw stats:** `canvas.drawText(distance, x, baseline, textPaint)` then `canvas.drawText(elapsed, x, baseline, textPaint)`
10. **Set anchor:** `marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)` — unchanged

### Bitmap scaling

- All px values multiplied by display `density` (device is 320dpi = 2x)
- Bitmap created at measured width × height, ARGB_8888, with `setAntiAlias(true)` on all Paints
- Text paint uses `SubpixelTextListener` not needed — standard `Paint` with `AntiAlias` is sufficient

### Night mode detection

```java
boolean isNight = (context.getResources().getConfiguration().uiMode
    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_MASK;
```

Pass `isNight` into `createIntervalIcon()` to select correct background and route colors.

### Test approach

- Unit test: verify `createIntervalIcon()` returns a non-null Bitmap of expected dimensions
- Visual: open a recorded activity with intervals, confirm rounded card, colored badge, arrow, day/night theming
