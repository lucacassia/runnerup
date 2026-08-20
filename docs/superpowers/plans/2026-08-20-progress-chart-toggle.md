# Progress Tab: 12-Year Period and Line/Bar Chart Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Year" bucket period (last 12 years) to the Progress tab and a top-right icon button that toggles the chart between line-circle and bar rendering.

**Architecture:** Extend the existing `Statistics` (bucketization) → `DistanceChartView` (rendering) → `HistoryFragment` (wiring) pipeline. No new libraries. `Statistics.BucketPeriod.YEAR` buckets by calendar year using the same indexing as MONTH; `DistanceChartView` gains a `barMode` branch that draws rounded bars; `HistoryFragment` persists the mode under `pref_statistics_chart` and drives a single `ImageButton` that shows the *target* view icon.

**Tech Stack:** Java 17, AndroidX/Material 3, Android Gradle Plugin 9.3.1. Tests are JUnit 4 in `app/test/java` (non-standard path; `sourceSets` root is `test`). No comments in code.

## Global Constraints

- No code comments unless asked.
- Run gates before finishing any task that touches Java: `./gradlew test`, `./gradlew :app:lintLatestDebug` (only pre-existing okhttp error at `app/build.gradle:174` allowed — never fix it), `./gradlew spotlessApply && spotlessCheck`, `./gradlew :app:assembleLatestDebug`.
- Strings live in `common` module → reference via `org.runnerup.common.R.string.<name>`. Drawables live in `app` → `R.drawable.<name>`.
- `statistics.xml` and drawables use `app/res/` (not `app/src/main/res/`).
- Conventional commits (`feat:`, `test:`, etc.).

---

### Task 1: Add YEAR bucket period to Statistics

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java`
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java`

**Interfaces:**
- Consumes: existing `Statistics.ActivityRow`, `Statistics.Metric`, `Statistics.BucketPeriod` (DAY/WEEK/MONTH), `Statistics.queryActivities`, `Statistics.computeMissingElevation`.
- Produces: `BucketPeriod.YEAR` in the enum; `bucketCount(YEAR)` returns 12; `bucketStarts(YEAR, nowSeconds, zone)` returns 12 epoch seconds, Jan 1 of each of the last 12 years; `bucketize(rows, metric, YEAR, nowSeconds, zone)` buckets by calendar year; `key(date, YEAR)` returns `date.getYear()`.

- [ ] **Step 1: Add failing tests for YEAR period**

Append to `StatisticsTest.java` (after the existing `bucketCountMatchesPeriods` test):

```java
  @Test
  public void bucketizeYearGroupsByCalendarYear() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-03"), 1000.0,
            at("2025-06-20"), 2000.0,
            at("2021-01-10"), 3000.0,
            at("2015-01-01"), 4000.0,
            at("2014-12-31"), 9999.0);
    double[] buckets = Statistics.bucketize(rows, Metric.DISTANCE, BucketPeriod.YEAR, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(4000.0, buckets[0], 0.0);  // 2015
    assertEquals(3000.0, buckets[6], 0.0);  // 2021
    assertEquals(2000.0, buckets[10], 0.0); // 2025
    assertEquals(1000.0, buckets[11], 0.0); // 2026
  }

  @Test
  public void bucketStartsYearAlignToJanFirst() {
    long now = at("2026-08-14") + 12 * 3600;
    long[] years = Statistics.bucketStarts(BucketPeriod.YEAR, now, UTC);
    assertEquals(12, years.length);
    assertEquals(at("2015-01-01"), years[0]);
    assertEquals(at("2026-01-01"), years[11]);
    for (int i = 0; i < years.length - 1; i++) {
      assertTrue(years[i] < years[i + 1]);
    }
  }

  @Test
  public void bucketCountYearIsTwelve() {
    assertEquals(12, Statistics.bucketCount(BucketPeriod.YEAR));
  }
```

Note: `at("2014-12-31")` is excluded from the buckets because 2014 < 2015 (the earliest of the 12 buckets for `now` = 2026). The 2026 bucket sums only the 2026 row (1000.0).

- [ ] **Step 2: Run the new tests, verify they fail**

Run: `./gradlew test --tests "org.runnerup.db.StatisticsTest"`
Expected: compile FAIL (enum constant `YEAR` does not exist).

- [ ] **Step 3: Implement YEAR in Statistics.java**

Edit `BucketPeriod` enum:

```java
  public enum BucketPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR
  }
```

Edit `bucketCount` — add `YEAR` to the switch (all return 12):

```java
  public static int bucketCount(BucketPeriod period) {
    switch (period) {
      case DAY:
      case WEEK:
      case MONTH:
      case YEAR:
      default:
        return 12;
    }
  }
```

Edit `bucketize` offset switch — add `YEAR` to the `DAY`/`MONTH` group (both use `offset = (int) dayDiff`):

```java
      switch (period) {
        case DAY:
        case MONTH:
        case YEAR:
          offset = (int) dayDiff;
          break;
        case WEEK:
          offset = (int) (dayDiff / 7);
          break;
        default:
          throw new IllegalArgumentException("unknown period " + period);
      }
```

Edit `bucketStarts` switch — add `YEAR` case producing Jan 1 of each of the last 12 years:

```java
        case MONTH:
          date = today.withDayOfMonth(1).minusMonths(count - 1 - i);
          break;
        case YEAR:
          date = LocalDate.of(today.getYear() - count + 1 + i, 1, 1);
          break;
```

Edit `key` switch — add `YEAR` case:

```java
      case MONTH:
        return date.getYear() * 12L + (date.getMonthValue() - 1);
      case YEAR:
        return date.getYear();
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew test --tests "org.runnerup.db.StatisticsTest"`
Expected: PASS (all tests including the three new ones).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: add YEAR bucket period to statistics"
```

---

### Task 2: Add formatYear to Formatter

**Files:**
- Modify: `app/src/main/org/runnerup/util/Formatter.java`

**Interfaces:**
- Consumes: `Formatter(Context)` constructor; existing `formatMonthShort(Date)`, `formatDayOfMonth(Date)` patterns.
- Produces: `public String formatYear(Date date)` returning a 4-digit year string (e.g. "2026").

No dedicated unit test: constructing `Formatter` requires mocking the Android `Context` chain plus static `PreferenceManager.getDefaultSharedPreferences` and `android.text.format.DateFormat` calls — the repo has no existing `Formatter` test and its one-liner format methods (`formatMonthShort`, `formatDayOfMonth`) are likewise untested. The year label output is verified by the device smoke test in Task 7 and by `assembleLatestDebug`.

- [ ] **Step 1: Implement formatYear in Formatter.java**

Add a field next to the other date formats:

```java
  private final java.text.DateFormat yearFormat;
```

Initialize it in the constructor next to `dayOfMonthFormat`:

```java
    dayOfMonthFormat = new SimpleDateFormat("E d", cueResources.defaultLocale);
    yearFormat = new SimpleDateFormat("yyyy", cueResources.defaultLocale);
```

Add the method next to `formatDayOfMonth` (around line 635):

```java
  /**
   * @param date date to format
   * @return year as a string (e.g. "2026")
   */
  public String formatYear(Date date) {
    return yearFormat.format(date);
  }
```

(Keep the existing Javadoc style used by the other format methods.)

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/util/Formatter.java
git commit -m "feat: add year formatter"
```

---

### Task 3: Add vector icons and strings

**Files:**
- Create: `app/res/drawable/ic_chart_line.xml`
- Create: `app/res/drawable/ic_chart_bar.xml`
- Modify: `common/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: existing vector drawable pattern (`ic_recenter.xml`).
- Produces: `R.drawable.ic_chart_line`, `R.drawable.ic_chart_bar`, and `org.runnerup.common.R.string.Statistics_year`, `Statistics_last_12_years`, `Statistics_switch_to_bars`, `Statistics_switch_to_line`, `pref_statistics_chart`.

- [ ] **Step 1: Create ic_chart_line.xml** (`show_chart` Material icon)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99z" />
</vector>
```

- [ ] **Step 2: Create ic_chart_bar.xml** (`bar_chart` Material icon)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z" />
</vector>
```

- [ ] **Step 3: Add strings to common strings.xml**

In `common/src/main/res/values/strings.xml`, after the `Statistics_elevation` line (line 274), add:

```xml
    <string name="Statistics_year">Year</string>
    <string name="Statistics_last_12_years">Last 12 years</string>
    <string name="Statistics_switch_to_bars">Switch to bar chart</string>
    <string name="Statistics_switch_to_line">Switch to line chart</string>
    <string name="pref_statistics_chart">pref_statistics_chart</string>
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/res/drawable/ic_chart_line.xml app/res/drawable/ic_chart_bar.xml common/src/main/res/values/strings.xml
git commit -m "feat: add chart style toggle icons and strings"
```

---

### Task 4: Add bar mode to DistanceChartView

**Files:**
- Modify: `app/src/main/org/runnerup/view/DistanceChartView.java`
- Test: `app/test/java/org/runnerup/view/DistanceChartViewTest.java`

**Interfaces:**
- Consumes: existing `setData(double[], String[])`, `setLabelFormatter(LabelFormatter)`, `plotPoints`, `niceMax`, theme colors.
- Produces: `setBarMode(boolean)` that flips between line rendering (default) and rounded-bar rendering; static `plotBarRects(double[], double, float, float, float, float)` helper returning bar rectangles for unit testing.

- [ ] **Step 1: Write the failing test**

Append to `DistanceChartViewTest.java`:

```java
  @Test
  public void plotBarRectsMapsValuesToRectangles() {
    float[][] rects =
        DistanceChartView.plotBarRects(new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
    assertEquals(3, rects.length);
    // i=0: center 50, half-width 30 => [20,100,80,100]
    assertArrayEquals(new float[] {20f, 100f, 80f, 100f}, rects[0], 0.01f);
    // i=1: center 150 => [120,60,180,100]
    assertArrayEquals(new float[] {120f, 60f, 180f, 100f}, rects[1], 0.01f);
    // i=2: center 250 => [220,20,280,100]
    assertArrayEquals(new float[] {220f, 20f, 280f, 100f}, rects[2], 0.01f);
  }

  @Test
  public void plotBarRectsHandlesEmptyInput() {
    float[][] rects =
        DistanceChartView.plotBarRects(new double[0], 10.0, 0f, 100f, 100f, 100f);
    assertEquals(0, rects.length);
  }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew test --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: FAIL (method `plotBarRects` does not exist).

- [ ] **Step 3: Implement bar mode in DistanceChartView.java**

Add constants and fields near the top of the class (after the existing paint fields):

```java
  private static final float BAR_WIDTH_FRACTION = 0.6f;
  private static final float BAR_CORNER_RADIUS_DP = 2;

  private boolean barMode = false;
  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
```

Initialize `barPaint` in the constructor (after `dotStrokePaint` setup):

```java
    barPaint.setStyle(Paint.Style.FILL);
```

Set its color in `resolveColors()` (after `linePaint.setColor(barColor)`):

```java
    barPaint.setColor(barColor);
```

Add the public setter after `setLabelFormatter`:

```java
  public void setBarMode(boolean barMode) {
    this.barMode = barMode;
    invalidate();
  }
```

Add the static helper near `plotPoints`:

```java
  static float[][] plotBarRects(
      double[] values,
      double maxValue,
      float chartLeft,
      float slot,
      float chartHeight,
      float chartBottom) {
    float[][] rects = new float[values.length][4];
    float barWidth = slot * BAR_WIDTH_FRACTION;
    for (int i = 0; i < values.length; i++) {
      float centerX = chartLeft + slot * i + slot / 2;
      float left = centerX - barWidth / 2;
      float right = centerX + barWidth / 2;
      float top = chartBottom - (float) (values[i] / maxValue * chartHeight);
      rects[i] = new float[] {left, top, right, chartBottom};
    }
    return rects;
  }
```

In `onDraw`, branch the data-drawing block. Replace the `if (count > 0) { float slot = chartWidth / count; float[][] points = plotPoints(...); ... }` block with:

```java
    if (count > 0) {
      float slot = chartWidth / count;
      if (barMode) {
        float[][] rects =
            plotBarRects(values, maxValue, chartLeft, slot, chartHeight, chartBottom);
        float radius = dp(BAR_CORNER_RADIUS_DP);
        for (float[] rect : rects) {
          if (rect[3] - rect[1] <= 0) {
            continue;
          }
          canvas.drawRoundRect(rect[0], rect[1], rect[2], rect[3], radius, radius, barPaint);
        }
      } else {
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

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        float dotRadius = dp(4);
        for (int i = 0; i < count; i++) {
          canvas.drawCircle(points[i][0], points[i][1], dotRadius, dotFillPaint);
          canvas.drawCircle(points[i][0], points[i][1], dotRadius, dotStrokePaint);
        }
      }
    }
```

X labels and grid drawing stay unchanged.

- [ ] **Step 4: Run the tests, verify pass**

Run: `./gradlew test --tests "org.runnerup.view.DistanceChartViewTest"`
Expected: PASS (existing 3 tests + 2 new).

- [ ] **Step 5: Run full gates**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug && ./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: tests PASS; lint only the pre-existing okhttp error; spotless clean.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java app/test/java/org/runnerup/view/DistanceChartViewTest.java
git commit -m "feat: add bar rendering mode to distance chart"
```

---

### Task 5: Add Year button and chart toggle to statistics layout

**Files:**
- Modify: `app/res/layout/statistics.xml`

**Interfaces:**
- Consumes: `@string/Statistics_year`, `@string/Statistics_switch_to_bars`, `@drawable/ic_chart_bar` from Task 3.
- Produces: `R.id.statistics_toggle_year` (4th MaterialButton in the bottom toggle group); `R.id.statistics_chart_toggle` (ImageButton in the title row); `R.id.statistics_chart_title` unchanged.

- [ ] **Step 1: Add the 4th "Year" button**

In `app/res/layout/statistics.xml`, inside the `statistics_toggle` `MaterialButtonToggleGroup` (lines 174-206), after the `statistics_toggle_month` button and before `</com.google.android.material.button.MaterialButtonToggleGroup>`, add:

```xml
            <com.google.android.material.button.MaterialButton
                android:id="@+id/statistics_toggle_year"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/Statistics_year" />
```

- [ ] **Step 2: Wrap the chart title in a row with the toggle button**

Replace the `statistics_chart_title` `TextView` block (lines 149-156) with a horizontal `LinearLayout` containing the title and the toggle `ImageButton`:

```xml
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/statistics_chart_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/Statistics_last_12_days"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textColor="?attr/colorOnSurface" />

            <ImageButton
                android:id="@+id/statistics_chart_toggle"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/Statistics_switch_to_bars"
                android:src="@drawable/ic_chart_bar"
                app:tint="?attr/colorOnSurfaceVariant" />
        </LinearLayout>
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/statistics.xml
git commit -m "feat: add year period button and chart style toggle to statistics layout"
```

---

### Task 6: Wire Year period and chart toggle in HistoryFragment

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java`

**Interfaces:**
- Consumes: `BucketPeriod.YEAR` from Task 1; `Formatter.formatYear` from Task 2; `R.id.statistics_toggle_year`, `R.id.statistics_chart_toggle` from Task 5; `R.drawable.ic_chart_line/ic_chart_bar`, strings from Task 3; `DistanceChartView.setBarMode` from Task 4.
- Produces: `currentPeriod` supports YEAR; `chartBarMode` boolean persisted under `pref_statistics_chart`; `renderChart()` applies bar mode.

- [ ] **Step 1: Add imports and field**

In `HistoryFragment.java`, add `import android.widget.ImageButton;` to the imports (alphabetical, after `android.widget.ImageView` at line 32). Add the field after `private boolean ... currentMetric` area (line 89-90):

```java
  private boolean chartBarMode = false;
```

- [ ] **Step 2: Wire the period toggle for YEAR**

In the `statisticsToggle.addOnButtonCheckedListener` (lines 166-179), extend the ternary to handle `statistics_toggle_year`:

```java
          BucketPeriod period =
              checkedId == R.id.statistics_toggle_week
                  ? BucketPeriod.WEEK
                  : checkedId == R.id.statistics_toggle_month
                      ? BucketPeriod.MONTH
                      : checkedId == R.id.statistics_toggle_year
                          ? BucketPeriod.YEAR
                          : BucketPeriod.DAY;
```

- [ ] **Step 3: Wire the chart style toggle button**

After the metricToggle listener block (after line 214), add:

```java
    ImageButton chartToggle = view.findViewById(R.id.statistics_chart_toggle);
    SharedPreferences chartPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    chartBarMode = chartPrefs.getBoolean(getString(org.runnerup.common.R.string.pref_statistics_chart), false);
    chartToggle.setOnClickListener(
        v -> {
          chartBarMode = !chartBarMode;
          chartPrefs.edit()
              .putBoolean(
                  getString(org.runnerup.common.R.string.pref_statistics_chart), chartBarMode)
              .apply();
          updateChartToggle(chartToggle);
          renderChart();
        });
    updateChartToggle(chartToggle);
```

Note: the metric-toggle block already declares a method-scope `prefs` variable (line 182) that is still in scope at this insertion point, so the new block MUST use a distinct name — `chartPrefs`. Do not re-declare `prefs`.

Add the helper method after `renderChart` (after line 331):

```java
  private void updateChartToggle(ImageButton button) {
    button.setImageResource(chartBarMode ? R.drawable.ic_chart_line : R.drawable.ic_chart_bar);
    button.setContentDescription(
        getString(
            chartBarMode
                ? org.runnerup.common.R.string.Statistics_switch_to_line
                : org.runnerup.common.R.string.Statistics_switch_to_bars));
  }
```

- [ ] **Step 4: Apply bar mode in renderChart**

In `renderChart()` (after `statisticsChart.setData(buckets, buildXLabels(currentPeriod, starts));` at line 321), add:

```java
    statisticsChart.setBarMode(chartBarMode);
```

- [ ] **Step 5: Change statistics query start to cover 12 years**

In `loadStatistics()` (lines 290-293), change the `from` computation from MONTH to YEAR:

```java
          long from =
              Statistics.bucketStarts(Statistics.BucketPeriod.YEAR, now, ZoneId.systemDefault())[
                  0];
```

- [ ] **Step 6: Handle YEAR in chartTitleFor and buildXLabels**

In `chartTitleFor` (lines 393-403), add a `YEAR` case:

```java
      case YEAR:
        return org.runnerup.common.R.string.Statistics_last_12_years;
```

In `buildXLabels` (lines 381-391), add the YEAR branch:

```java
      labels[i] =
          period == BucketPeriod.MONTH
              ? formatter.formatMonthShort(date)
              : period == BucketPeriod.YEAR
                  ? formatter.formatYear(date)
                  : formatter.formatDayOfMonth(date);
```

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run full gates**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug && ./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: all pass (lint only pre-existing okhttp error).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: wire year period and chart style toggle into progress tab"
```

---

### Task 7: Final verification and device smoke test

**Files:** none (verification only)

**Interfaces:**
- Consumes: the full feature from Tasks 1-6.

- [ ] **Step 1: Full gate run**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug && ./gradlew spotlessCheck && ./gradlew :app:assembleLatestDebug`
Expected: all pass; lint shows only the pre-existing okhttp error at `app/build.gradle:174`.

- [ ] **Step 2: Install on device**

Install the debug APK:
```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
```

- [ ] **Step 3: Manual smoke test**

- Open History → Progress tab.
- Tap "Year" in the bottom selector: title reads "Last 12 years", chart shows 12 yearly buckets (x labels are years).
- Tap the toggle icon top-right: chart switches line ↔ bars for Year, then verify it also toggles for Day/Week/Month.
- Force-stop and relaunch the app; confirm the chosen bar/line mode is restored (persisted via `pref_statistics_chart`).
- Confirm content description announces "Switch to line chart" when in bar mode and "Switch to bar chart" when in line mode.

- [ ] **Step 4: Push final commit (if any fixes were needed)**

If the smoke test revealed issues, fix them, rerun gates, and commit. Otherwise no new commit.

---

## Self-Review Notes

- **Spec coverage:** YEAR bucket period (Task 1), formatYear (Task 2), icons/strings (Task 3), bar mode (Task 4), layout Year button + toggle button (Task 5), wiring + persistence + titles/labels + query range (Task 6), verification (Task 7). All spec sections covered.
- **Placeholder scan:** no TBD/TODO; every step has concrete code or commands.
- **Type consistency:** `setBarMode(boolean)` defined in Task 4 and consumed in Task 6; `plotBarRects(double[], double, float, float, float, float)` consistent between test and implementation; `formatYear(Date)` consistent; `BucketPeriod.YEAR` consistent across Tasks 1 and 6; `chartBarMode` field name consistent; string names consistent (`Statistics_year`, `Statistics_last_12_years`, `Statistics_switch_to_bars`, `Statistics_switch_to_line`, `pref_statistics_chart`).