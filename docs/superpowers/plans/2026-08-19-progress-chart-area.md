# Progress Tab Area Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bar rendering in `DistanceChartView` with an area chart (line + gradient fill + outline data-point dots) without changing its public API.

**Architecture:** Single-file rendering change in `DistanceChartView.java`. The bucket-value → pixel-coordinate math is extracted into a package-private static helper so it is unit-testable with plain JUnit (matching the existing `DistanceChartViewTest` pattern). `onDraw` consumes that helper to build the line path, gradient fill, and dots. `HistoryFragment` is untouched.

**Tech Stack:** Java, Android Canvas/Paint/Path, LinearGradient shader, JUnit 4.

## Global Constraints

- No comments added to code unless asked.
- Colors must stay theme-resolved (`colorPrimary` line/dots, `colorOnSurfaceVariant` labels, `colorOutlineVariant` grid) for light + dark mode.
- Public API of `DistanceChartView` (`setData`, `setLabelFormatter`) must not change.
- All changes gated by: `./gradlew test`, `./gradlew :app:lintLatestDebug`, `./gradlew spotlessApply && spotlessCheck`, `./gradlew :app:assembleLatestDebug`, `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`.

---

### Task 1: Extract and test point-mapping helper

**Files:**
- Modify: `app/src/main/org/runnerup/view/DistanceChartView.java`
- Test: `app/test/java/org/runnerup/view/DistanceChartViewTest.java`

**Interfaces:**
- Consumes: existing `niceMax(double)`, `dp(float)`.
- Produces: `static float[][] plotPoints(double[] values, double maxValue, float chartLeft, float slot, float chartHeight, float chartBottom)` — returns an N×2 array where `result[i][0]` is the x pixel and `result[i][1]` is the y pixel of data point `i`. x = `chartLeft + slot*i + slot/2`; y = `chartBottom - (float)(values[i] / maxValue * chartHeight)`. Empty input returns an empty array.

- [ ] **Step 1: Write the failing test**

Add to `DistanceChartViewTest.java`:

```java
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@Test
public void plotPointsMapsValuesToPixels() {
  float[][] points =
      DistanceChartView.plotPoints(
          new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
  assertEquals(3, points.length);
  assertArrayEquals(new float[] {50f, 100f}, points[0], 0.01f);
  assertArrayEquals(new float[] {150f, 60f}, points[1], 0.01f);
  assertArrayEquals(new float[] {250f, 20f}, points[2], 0.01f);
}

@Test
public void plotPointsHandlesEmptyInput() {
  float[][] points = DistanceChartView.plotPoints(new double[0], 10.0, 0f, 100f, 100f, 100f);
  assertEquals(0, points.length);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: FAIL — compile error, `plotPoints` undefined.

- [ ] **Step 3: Write minimal implementation**

Add to `DistanceChartView.java`:

```java
static float[][] plotPoints(
    double[] values, double maxValue, float chartLeft, float slot, float chartHeight,
    float chartBottom) {
  float[][] points = new float[values.length][2];
  for (int i = 0; i < values.length; i++) {
    points[i][0] = chartLeft + slot * i + slot / 2;
    points[i][1] =
        chartBottom - (float) (values[i] / maxValue * chartHeight);
  }
  return points;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: PASS (existing `niceMaxHugsTheData` plus the two new tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java app/test/java/org/runnerup/view/DistanceChartViewTest.java
git commit -m "feat: extract plot point mapping helper for progress chart"
```

---

### Task 2: Draw area chart with dots in onDraw

**Files:**
- Modify: `app/src/main/org/runnerup/view/DistanceChartView.java`

**Interfaces:**
- Consumes: `plotPoints(...)` from Task 1, existing `niceMax`, `dp`, `barColor`/`labelColor`/`gridColor` paint fields.
- Produces: (no new API) updated `onDraw` rendering.

- [ ] **Step 1: Add paints and gradient fields**

Add after the existing paint fields:

```java
private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Paint dotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final Path linePath = new Path();
private final Path fillPath = new Path();
```

Remove the now-unused `barPaint` field and `rect` (RectF) field, and their imports if they become unused (`android.graphics.RectF`).

Initialize in the constructor next to the existing paint setup:

```java
linePaint.setStyle(Paint.Style.STROKE);
linePaint.setStrokeWidth(dp(2.5f));
linePaint.setStrokeJoin(Paint.Join.ROUND);
linePaint.setStrokeCap(Paint.Cap.ROUND);
dotFillPaint.setStyle(Paint.Style.FILL);
dotStrokePaint.setStyle(Paint.Style.STROKE);
dotStrokePaint.setStrokeWidth(dp(2));
```

- [ ] **Step 2: Set line/dot colors in resolveColors()**

In `resolveColors()`, replace `barPaint.setColor(barColor);` with:

```java
linePaint.setColor(barColor);
dotFillPaint.setColor(Color.WHITE);
dotStrokePaint.setColor(barColor);
```

- [ ] **Step 3: Rewrite the bar loop into line + fill + dots**

Replace the `if (count > 0) { ... }` block (the current bar drawing loop) in `onDraw` with:

```java
if (count > 0) {
  float slot = chartWidth / count;
  float[][] points = plotPoints(values, maxValue, chartLeft, slot, chartHeight, chartBottom);
  linePath.reset();
  fillPath.reset();
  fillPath.moveTo(points[0][0], chartBottom);
  for (int i = 0; i < count; i++) {
    if (i == 0) {
      linePath.moveTo(points[i][0], points[i][1]);
    } else {
      linePath.lineTo(points[i][0], points[i][1]);
    }
    fillPath.lineTo(points[i][0], points[i][1]);
  }
  fillPath.lineTo(points[count - 1][0], chartBottom);
  fillPath.close();

  fillPaint.setShader(
      new LinearGradient(
          0,
          chartTop,
          0,
          chartBottom,
          ColorUtils.setAlphaComponent(barColor, 64),
          ColorUtils.setAlphaComponent(barColor, 0),
          Shader.TileMode.CLAMP));
  canvas.drawPath(fillPath, fillPaint);
  canvas.drawPath(linePath, linePaint);

  float dotRadius = dp(4);
  for (int i = 0; i < count; i++) {
    canvas.drawCircle(points[i][0], points[i][1], dotRadius, dotFillPaint);
    canvas.drawCircle(points[i][0], points[i][1], dotRadius, dotStrokePaint);
  }
}
```

- [ ] **Step 4: Add required imports**

Add to the import block (keep google-java-format alphabetical order):

```java
import android.graphics.Path;
import android.graphics.Shader;
import androidx.core.graphics.ColorUtils;
```

- [ ] **Step 5: Run tests and verify build**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: PASS

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Verify style gates**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew :app:lintLatestDebug`
Expected: spotless passes, lint reports only the 25 pre-existing baseline issues.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java
git commit -m "feat: render progress chart as area chart with data point dots"
```

---

### Task 3: Final verification

**Files:** none.

- [ ] **Step 1: Run full gate suite**

Run in order:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```
Expected: all pass.

- [ ] **Step 2: Device smoke test**

Install the debug APK, open Progress tab, verify:
- Chart renders as a smooth line with gradient fill and white-outlined dots
- Works for Distance, Time, and Elevation metrics
- Works in light mode and dark mode (toggle system dark theme)
- Empty period still shows the empty-state view
- Push to fork: `git push fork master`