# Interval Stats Box Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain white text box at interval/lap markers on the osmdroid map with a styled card: rounded corners, semi-transparent theme-aware background, colored lap badge, downward arrow.

**Architecture:** Single new method `createIntervalIcon()` in `MapWrapper.java` that draws a styled `Bitmap` via Android `Canvas`. Replaces the existing `marker.setTextIcon(info)` call with `marker.setIcon(createIntervalIcon(...))`. No new files, no XML layouts.

**Tech Stack:** Android Canvas API (Bitmap, Paint, Canvas, Path), osmdroid Marker.setIcon()

## Global Constraints

- osmdroid path only (`app/src/osmdroid/org/runnerup/util/MapWrapper.java`)
- No code comments unless brief includes them
- No XML layouts — all drawing via Canvas
- Stats format unchanged: `"#1 3.2km 45:23"`
- Follow existing code conventions (no new imports beyond what's needed)

---

### Task 1: Create interval icon drawing method and wire it in

**Files:**
- Modify: `app/src/osmdroid/org/runnerup/util/MapWrapper.java`
- Test: `app/test/java/org/runnerup/util/IntervalIconTest.java`

**Interfaces:**
- Consumes: `MapTheme.routeColor(isNight)`, `MapTheme.ROUTE_DAY`, `MapTheme.ROUTE_NIGHT`, `Formatter.formatDistance()`, `Formatter.formatElapsedTime()`
- Produces: `Bitmap createIntervalIcon(int lap, String distance, String elapsed, int routeColor, boolean isNight)` — returns a Bitmap ready for `marker.setIcon()`

**Constants to add to MapWrapper.java (lines ~59-63, after existing constants):**

```java
private static final float INTERVAL_BOX_CORNER_DP = 8f;
private static final float INTERVAL_BOX_PADDING_H_DP = 8f;
private static final float INTERVAL_BOX_PADDING_V_DP = 6f;
private static final float INTERVAL_BADGE_DIAM_DP = 16f;
private static final float INTERVAL_BADGE_GAP_DP = 6f;
private static final float INTERVAL_DIVIDER_WIDTH_DP = 1f;
private static final float INTERVAL_DIVIDER_GAP_DP = 6f;
private static final float INTERVAL_TEXT_SIZE_SP = 12f;
private static final float INTERVAL_BADGE_TEXT_SIZE_SP = 10f;
private static final float INTERVAL_ARROW_WIDTH_DP = 8f;
private static final float INTERVAL_ARROW_HEIGHT_DP = 4f;
private static final int INTERVAL_BOX_ALPHA_DAY = 0xE6; // 90% white
private static final int INTERVAL_BOX_ALPHA_NIGHT = 0xE6; // 90% dark
```

- [ ] **Step 1: Add interval icon constants**

Add the constants block above after the existing `MARKER_CIRCLE_VIEWPORT` constant (line 63) in `MapWrapper.java`.

- [ ] **Step 2: Write the `createIntervalIcon` method**

Add this method to `MapWrapper.java` after the `scaleMarkerIcon` method (after line 199):

```java
private Bitmap createIntervalIcon(
    int lap, String distance, String elapsed, int routeColor, boolean isNight) {
  float density = mapView.getContext().getResources().getDisplayMetrics().density;

  // Paints
  Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  textPaint.setTextSize(INTERVAL_TEXT_SIZE_SP * density);
  textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
  textPaint.setColor(
      isNight ? 0xFFFFFFFF : android.graphics.Color.argb(0xFF, 0x1C, 0x1C, 0x1C));

  Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  badgePaint.setColor(routeColor);

  Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  badgeTextPaint.setTextSize(INTERVAL_BADGE_TEXT_SIZE_SP * density);
  badgeTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
  badgeTextPaint.setColor(0xFFFFFFFF);
  badgeTextPaint.setTextAlign(Paint.Align.CENTER);

  Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  int outlineAlpha = isNight ? 0x4D : 0x4D; // 30%
  dividerPaint.setColor(android.graphics.Color.argb(outlineAlpha, 0x80, 0x80, 0x80));
  dividerPaint.setStrokeWidth(INTERVAL_DIVIDER_WIDTH_DP * density);

  // Measure text
  float distWidth = textPaint.measureText(distance);
  float elapsedWidth = textPaint.measureText(elapsed);
  float textHeight = textPaint.getTextMetrics().ascent + textPaint.getTextMetrics().descent;

  // Dimensions
  float badgeDiam = INTERVAL_BADGE_DIAM_DP * density;
  float badgeGap = INTERVAL_BADGE_GAP_DP * density;
  float dividerGap = INTERVAL_DIVIDER_GAP_DP * density;
  float dividerWidth = INTERVAL_DIVIDER_WIDTH_DP * density;
  float paddingH = INTERVAL_BOX_PADDING_H_DP * density;
  float paddingV = INTERVAL_BOX_PADDING_V_DP * density;
  float cornerRadius = INTERVAL_BOX_CORNER_DP * density;

  float contentWidth =
      badgeDiam + badgeGap + distWidth + dividerGap + dividerWidth + dividerGap + elapsedWidth;
  float boxWidth = paddingH + contentWidth + paddingH;
  float boxHeight = paddingV + Math.max(badgeDiam, textHeight) + paddingV;

  // Arrow dimensions
  float arrowWidth = INTERVAL_ARROW_WIDTH_DP * density;
  float arrowHeight = INTERVAL_ARROW_HEIGHT_DP * density;

  // Total bitmap height includes arrow
  int bitmapWidth = Math.round(boxWidth);
  int bitmapHeight = Math.round(boxHeight + arrowHeight);

  Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
  Canvas canvas = new Canvas(bitmap);

  // Background color
  int bgColor =
      isNight
          ? android.graphics.Color.argb(INTERVAL_BOX_ALPHA_NIGHT, 0x1C, 0x1C, 0x1C)
          : android.graphics.Color.argb(INTERVAL_BOX_ALPHA_DAY, 0xFF, 0xFF, 0xFF);
  Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  bgPaint.setColor(bgColor);

  // Draw rounded rect background
  android.graphics.RectF rect = new android.graphics.RectF(0, 0, boxWidth, boxHeight);
  canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

  // Draw downward arrow triangle at bottom center
  Path arrow = new Path();
  float arrowCenterX = boxWidth / 2f;
  float arrowTop = boxHeight - arrowHeight;
  arrow.moveTo(arrowCenterX - arrowWidth / 2f, arrowTop);
  arrow.lineTo(arrowCenterX + arrowWidth / 2f, arrowTop);
  arrow.lineTo(arrowCenterX, boxHeight + arrowHeight);
  arrow.close();
  canvas.drawPath(arrow, bgPaint);

  // Draw badge circle
  float badgeCenterX = paddingH + badgeDiam / 2f;
  float badgeCenterY = paddingV + Math.max(badgeDiam, textHeight) / 2f;
  canvas.drawCircle(badgeCenterX, badgeCenterY, badgeDiam / 2f, badgePaint);

  // Draw lap number in badge
  Paint.FontMetrics badgeFm = badgeTextPaint.getFontMetrics();
  float badgeTextY = badgeCenterY - (badgeFm.ascent + badgeFm.descent) / 2f;
  canvas.drawText(String.valueOf(lap), badgeCenterX, badgeTextY, badgeTextPaint);

  // Draw stats text (distance and elapsed on same line)
  float textX = paddingH + badgeDiam + badgeGap;
  float textBaseline = paddingV + Math.max(badgeDiam, textHeight) / 2f - textHeight / 2f
      - textPaint.getTextMetrics().ascent;
  canvas.drawText(distance, textX, textBaseline, textPaint);

  // Draw divider
  float dividerX = textX + distWidth + dividerGap;
  float dividerTop = badgeCenterY - (textHeight / 2f);
  float dividerBottom = badgeCenterY + (textHeight / 2f);
  canvas.drawLine(dividerX, dividerTop, dividerX, dividerBottom, dividerPaint);

  // Draw elapsed text
  float elapsedX = dividerX + dividerWidth + dividerGap;
  canvas.drawText(elapsed, elapsedX, textBaseline, textPaint);

  return bitmap;
}
```

- [ ] **Step 3: Replace `setTextIcon` call in `loadRouteData`**

In `MapWrapper.java`, replace lines 149-160 (the interval marker creation block inside the `if (lastLap != lap)` branch, the `else` block):

**Current code (lines 149-160):**
```java
Marker marker = new Marker(mapView);
marker.setPosition(point);
java.lang.String info =
    "#"
        + loc.getLap()
        + " "
        + formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue())
        + " "
        + formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
marker.setTextIcon(info);
marker.setInfoWindow(null);
marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
```

**New code:**
```java
Marker marker = new Marker(mapView);
marker.setPosition(point);
String dist = formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue());
String elapsed = formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
marker.setIcon(
    new android.graphics.drawable.BitmapDrawable(
        mapView.getContext().getResources(),
        createIntervalIcon(loc.getLap(), dist, elapsed, MapTheme.routeColor(isNight), isNight)));
marker.setInfoWindow(null);
marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
```

- [ ] **Step 4: Add import for Path**

Add to the imports section (after line 26):
```java
import android.graphics.Path;
```

- [ ] **Step 5: Run spotlessApply and verify build**

Run: `./gradlew spotlessApply && ./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run unit tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 7: Install on device and verify visually**

Run: `adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`
Open a recorded activity with intervals → Map tab.
Verify: rounded box, colored badge, arrow, text readable, day/night mode both work.

- [ ] **Step 8: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/MapWrapper.java
git commit -m "feat: redesign interval stats box on osmdroid map"
```
