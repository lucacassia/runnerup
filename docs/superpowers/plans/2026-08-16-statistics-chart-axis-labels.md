# Statistics Chart Axis and Label Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Statistics chart's y-axis hug the data (finer max step ladder) and make its x-axis date labels shorter and visually anchored to their bins.

**Architecture:** All changes are local to the chart feature. `DistanceChartView.niceMax()` gains a finer step ladder and becomes package-private static (testable); `DistanceChartView.onDraw` draws a tick under each labeled bar; `Formatter` gains `formatMonthShort("LLL")`; `HistoryFragment` uses it for month chart labels. No new dependencies.

**Tech Stack:** Java 17, Android View/Canvas drawing, JUnit 4 (tests in non-standard `app/test/java`).

## Global Constraints

- Worktree: `/home/megadoro/local/runnerup/.worktrees/statistics-calendar-periods`, branch `feature/statistics-calendar-periods` (head `c00494db`). Run all Gradle commands there. This feature merges together with the already-committed statistics calendar-periods work on the same branch.
- `niceMax` step ladder: {1, 2, 3, 4, 5, 6, 8, 10} (× 10ⁿ). Expected values: 24→30, 18→20, 40→40, 47→50, 6→6, 85→100, 1→1, 0→1.0. `value <= 0` returns 1.0.
- `niceMax` becomes package-private `static` (drop `private`) so `DistanceChartViewTest` (same package) can call it.
- `formatMonthShort` → `new SimpleDateFormat("LLL", cueResources.defaultLocale)`, e.g. "Sep". Only the chart's month labels use it; history-list headers keep `formatMonth` ("LLL yyyy").
- Tick: `gridPaint` line from `chartBottom` down to `chartBottom + dp(4)` at each drawn label's center x; label baseline stays at `chartBottom + dp(14)`. Existing `skipOdd` behavior (count > 8 → every other label) unchanged.
- Day/week chart labels keep `formatDayOfMonth` ("E d"). No string resources change.
- Do NOT change `HistoryFragment.buildXLabels` except the one `formatMonth` → `formatMonthShort` call. Do NOT touch `Statistics.java`, the toggle, or the card UI.
- No code comments added unless asked. Google Java Format via `./gradlew spotlessApply`.

---

### Task 1: Finer y-axis max ladder with unit tests

**Files:**
- Modify: `app/src/main/org/runnerup/view/DistanceChartView.java:146-164` (`niceMax`) and `:77` (`niceMax` call site — signature unchanged)
- Create: `app/test/java/org/runnerup/view/DistanceChartViewTest.java`

**Interfaces:**
- Consumes: nothing (standalone static).
- Produces: `static double niceMax(double value)` (package-private) — returns the smallest value from {1,2,3,4,5,6,8,10}×10ⁿ that is `>= value`, or 1.0 when `value <= 0`.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/view/DistanceChartViewTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DistanceChartViewTest {

  @Test
  public void niceMaxHugsTheData() {
    assertEquals(30.0, DistanceChartView.niceMax(24.0), 0.0);
    assertEquals(20.0, DistanceChartView.niceMax(18.0), 0.0);
    assertEquals(40.0, DistanceChartView.niceMax(40.0), 0.0);
    assertEquals(50.0, DistanceChartView.niceMax(47.0), 0.0);
    assertEquals(6.0, DistanceChartView.niceMax(6.0), 0.0);
    assertEquals(100.0, DistanceChartView.niceMax(85.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(1.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(0.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(-5.0), 0.0);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails (compile error expected)**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: compile error — `niceMax(double)` in class `DistanceChartView` cannot be applied / is not accessible (it's `private`). This confirms the visibility change is needed.

- [ ] **Step 3: Change `niceMax` visibility and the step ladder**

In `app/src/main/org/runnerup/view/DistanceChartView.java`, replace lines 146-164:

```java
  static double niceMax(double value) {
    if (value <= 0) {
      return 1.0;
    }
    double exp = Math.floor(Math.log10(value));
    double base = Math.pow(10, exp);
    double fraction = value / base;
    double niceFraction;
    if (fraction <= 1) {
      niceFraction = 1;
    } else if (fraction <= 2) {
      niceFraction = 2;
    } else if (fraction <= 3) {
      niceFraction = 3;
    } else if (fraction <= 4) {
      niceFraction = 4;
    } else if (fraction <= 5) {
      niceFraction = 5;
    } else if (fraction <= 6) {
      niceFraction = 6;
    } else if (fraction <= 8) {
      niceFraction = 8;
    } else {
      niceFraction = 10;
    }
    return niceFraction * base;
  }
```

The call site `double maxValue = niceMax(max(values));` at line 77 stays unchanged.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: BUILD SUCCESSFUL, 1 test, 9 assertions pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java app/test/java/org/runnerup/view/DistanceChartViewTest.java
git commit -m "feat: tighter y-axis max for the statistics chart"
```

---

### Task 2: Short month labels for the chart

**Files:**
- Modify: `app/src/main/org/runnerup/util/Formatter.java:51,65-66` (field + init) and after `formatMonth` (~line 617)
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java:299`

**Interfaces:**
- Consumes: `Formatter` instance already held as `formatter` in `HistoryFragment`.
- Produces: `public String formatMonthShort(Date date)` — short month name ("Sep").

- [ ] **Step 1: Add the field and initialization**

In `app/src/main/org/runnerup/util/Formatter.java`:
- After line 50 (`private final java.text.DateFormat monthFormat;`), add:

```java
  private final java.text.DateFormat monthShortFormat;
```

- After line 65 (`monthFormat = new SimpleDateFormat("LLL yyyy", cueResources.defaultLocale);`), add:

```java
    monthShortFormat = new SimpleDateFormat("LLL", cueResources.defaultLocale);
```

- [ ] **Step 2: Add the method**

After the `formatMonth` method (ends ~line 617), add:

```java
  public String formatMonthShort(Date date) {
    return monthShortFormat.format(date);
  }
```

- [ ] **Step 3: Use it for the chart's month labels**

In `app/src/main/org/runnerup/view/HistoryFragment.java`, line 299, change `formatter.formatMonth(date)` → `formatter.formatMonthShort(date)`:

```java
          period == BucketPeriod.MONTH
              ? formatter.formatMonthShort(date)
              : formatter.formatDayOfMonth(date);
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/util/Formatter.java app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: short month labels on the statistics chart"
```

---

### Task 3: Tick anchors under x-axis labels

**Files:**
- Modify: `app/src/main/org/runnerup/view/DistanceChartView.java:102-114` (x-label drawing block)

**Interfaces:**
- Consumes: existing `gridPaint`, `chartBottom`, `dp(...)`, `skipOdd` logic, `xLabels`, `count`, `chartWidth`, `chartLeft`.
- Produces: (drawing only) a short tick under each drawn label.

- [ ] **Step 1: Add the tick draw**

In `app/src/main/org/runnerup/view/DistanceChartView.java`, replace lines 102-114:

```java
    if (count > 0 && xLabels.length == count) {
      boolean skipOdd = count > X_LABEL_SKIP_THRESHOLD;
      float slot = chartWidth / count;
      for (int i = 0; i < count; i++) {
        if (skipOdd && i % 2 == 1) {
          continue;
        }
        float centerX = chartLeft + slot * i + slot / 2;
        canvas.drawLine(centerX, chartBottom, centerX, chartBottom + dp(4), gridPaint);
        String label = xLabels[i];
        canvas.drawText(
            label, centerX - labelPaint.measureText(label) / 2, chartBottom + dp(14), labelPaint);
      }
    }
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java
git commit -m "feat: anchor statistics chart x-labels with tick marks"
```

---

### Task 4: Full verification and device smoke test

**Files:**
- No source changes. Runs the AGENTS.md gates and confirms on device.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew test`
Expected: all pass (new `DistanceChartViewTest` included).

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: no NEW issues. The 25 pre-existing baseline issues and the known `AppBundleLocaleChanges` at `app/src/main/org/runnerup/util/Formatter.java:817` are acceptable; do not fix them.

- [ ] **Step 3: Run spotless**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Build both map variants**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Install and smoke test on device**

Device: `adb devices` to find the connected serial (last used `6a6743fd`), package `org.runnerup.debug`.

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell am force-stop org.runnerup.debug
adb shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Then in the app: open History → Progress tab. Verify:
- Week view: y-axis top hugs the data (no wasted band to 50 km when all bins < 50 km).
- All three views: x-labels readable, each label has a tick under its bar, month view shows "Sep"-style labels (no year).
- `uiautomator dump` can confirm label text strings; tick/axis rendering is visual — screenshot if needed (`adb exec-out screencap -p > /tmp/opencode/chart_fixes.png`).
