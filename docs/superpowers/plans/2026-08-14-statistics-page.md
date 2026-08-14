# Statistics Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Statistics" sub-page to the History tab showing 7/30/365-day distance totals and a Day/Week/Month distance bar chart, sourced from individual (non-deleted) activities.

**Architecture:** HistoryFragment gets a `TabLayout` ("History" | "Statistics") switching two content views via `VISIBLE/GONE` (StartFragment pattern). A new pure-Java `Statistics` helper queries the `activity` table and aggregates in Java with `java.time` (deterministic, timezone-explicit, unit-testable). A new custom `DistanceChartView` draws the bars on a Canvas with Material 3 theme attrs. Background work uses `ExecutorService + Handler` (GraphWrapper pattern).

**Tech Stack:** Android (minSdk 28, Java 17), Material 3 (material 1.14.0), `java.time` (API 26+), JUnit4 (tests in `app/test/java`), Gradle 9.6.1 wrapper.

## Global Constraints

- Data source: `activity` table; all queries filter `deleted = 0 AND distance IS NOT NULL AND start_time >= ?`. `start_time` is seconds since epoch; `distance` is meters (real).
- Stats count ALL sports (not just running). In-progress activities (NULL distance) are excluded automatically.
- Totals are rolling windows of the last 7 / 30 / 365 days (timezone-independent, `<=` includes an activity exactly N days old).
- Chart periods: DAY = 14 calendar days (local time), WEEK = 8 calendar weeks (Monday-start), MONTH = 12 calendar months. Oldest first in the returned arrays.
- All strings go in the `common` module and are referenced in Java as `org.runnerup.common.R.string.X`. Layouts may use `@string/X` directly (merged resource table).
- No comments in code. No new dependencies. No ViewModel/coroutines. Follow existing inline-query convention.
- Gates in order before finishing each task: `./gradlew test`, `:app:lintLatestDebug` (25 baseline-filtered allowed, no new issues; `warningsAsErrors=true` — keep XML text sizes ≥ 11sp, `SmallSp` is fatal), `spotlessApply` then `spotlessCheck`, `:app:assembleLatestDebug`, then `:app:assembleLatestDebug -Porg.runnerup.nomap` (nomap build LAST or it overwrites the map APK).
- Conventional commits (`feat:`, `test:`, `style:`). Do NOT stage user-local files: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.

---

### Task 1: Statistics data helper + unit tests

**Files:**
- Create: `app/src/main/org/runnerup/db/Statistics.java`
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java`

**Interfaces:**
- Produces (consumed by Task 4):
  - `enum Statistics.BucketPeriod { DAY, WEEK, MONTH }`
  - `class Statistics.ActivityRow { public final long startTime; public final double distance; public ActivityRow(long, double) }`
  - `static int bucketCount(BucketPeriod)` → 14 / 8 / 12
  - `static double[] totals(List<ActivityRow>, long nowSeconds)` → `{7d, 30d, 365d}` sums
  - `static double[] bucketize(List<ActivityRow>, BucketPeriod, long nowSeconds, ZoneId)` → period buckets, oldest first
  - `static long[] bucketStarts(BucketPeriod, long nowSeconds, ZoneId)` → bucket start epoch-seconds (local midnight / week-Monday / month-1st), oldest first
  - `static List<ActivityRow> queryActivities(SQLiteDatabase, long fromSeconds)`

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/db/StatisticsTest.java`:

```java
package org.runnerup.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.runnerup.db.Statistics.ActivityRow;
import org.runnerup.db.Statistics.BucketPeriod;

public class StatisticsTest {

  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final long DAY = 86400L;

  private static long at(String date) {
    return LocalDate.parse(date).atStartOfDay(UTC).toEpochSecond();
  }

  private static List<ActivityRow> rows(Object... pairs) {
    List<ActivityRow> rows = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      rows.add(new ActivityRow((Long) pairs[i], (Double) pairs[i + 1]));
    }
    return rows;
  }

  @Test
  public void totalsRollingWindowsExcludeOutOfRange() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            now - 7 * DAY, 1000.0,
            now - 7 * DAY - 1, 2000.0,
            now - 30 * DAY, 4000.0,
            now - 31 * DAY, 8000.0,
            now - 365 * DAY, 16000.0,
            now - 366 * DAY, 32000.0);
    double[] totals = Statistics.totals(rows, now);
    assertEquals(3000.0, totals[0], 0.0);
    assertEquals(15000.0, totals[1], 0.0);
    assertEquals(31000.0, totals[2], 0.0);
  }

  @Test
  public void bucketizeDayGroupsByCalendarDay() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-14"), 1000.0,
            at("2026-08-13"), 2000.0,
            at("2026-08-01"), 500.0,
            at("2026-07-31"), 9999.0);
    double[] buckets = Statistics.bucketize(rows, BucketPeriod.DAY, now, UTC);
    assertEquals(14, buckets.length);
    assertEquals(500.0, buckets[0], 0.0);
    assertEquals(0.0, buckets[11], 0.0);
    assertEquals(2000.0, buckets[12], 0.0);
    assertEquals(1000.0, buckets[13], 0.0);
  }

  @Test
  public void bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary() {
    long now = at("2026-01-07") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2025-12-29"), 1000.0,
            at("2025-12-22"), 2000.0,
            at("2025-11-17"), 3000.0);
    double[] buckets = Statistics.bucketize(rows, BucketPeriod.WEEK, now, UTC);
    assertEquals(8, buckets.length);
    assertEquals(3000.0, buckets[0], 0.0);
    assertEquals(0.0, buckets[1], 0.0);
    assertEquals(0.0, buckets[4], 0.0);
    assertEquals(2000.0, buckets[5], 0.0);
    assertEquals(1000.0, buckets[6], 0.0);
    assertEquals(0.0, buckets[7], 0.0);
  }

  @Test
  public void bucketizeMonthGroupsByCalendarMonth() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-03"), 1000.0,
            at("2026-07-20"), 2000.0,
            at("2026-01-10"), 3000.0,
            at("2025-09-05"), 4000.0,
            at("2025-08-15"), 9999.0);
    double[] buckets = Statistics.bucketize(rows, BucketPeriod.MONTH, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(4000.0, buckets[0], 0.0);
    assertEquals(3000.0, buckets[4], 0.0);
    assertEquals(2000.0, buckets[10], 0.0);
    assertEquals(1000.0, buckets[11], 0.0);
  }

  @Test
  public void bucketStartsAlignToDayWeekMonthStarts() {
    long now = at("2026-08-14") + 12 * 3600;
    long[] days = Statistics.bucketStarts(BucketPeriod.DAY, now, UTC);
    assertEquals(14, days.length);
    assertEquals(at("2026-08-14"), days[13]);
    assertEquals(at("2026-08-01"), days[0]);
    long[] weeks = Statistics.bucketStarts(BucketPeriod.WEEK, now, UTC);
    assertEquals(8, weeks.length);
    assertEquals(at("2026-08-10"), weeks[7]);
    long[] months = Statistics.bucketStarts(BucketPeriod.MONTH, now, UTC);
    assertEquals(12, months.length);
    assertEquals(at("2026-08-01"), months[11]);
    for (int i = 0; i < days.length - 1; i++) {
      assertTrue(days[i] < days[i + 1]);
    }
    for (int i = 0; i < months.length - 1; i++) {
      assertTrue(months[i] < months[i + 1]);
    }
  }

  @Test
  public void bucketCountMatchesPeriods() {
    assertEquals(14, Statistics.bucketCount(BucketPeriod.DAY));
    assertEquals(8, Statistics.bucketCount(BucketPeriod.WEEK));
    assertEquals(12, Statistics.bucketCount(BucketPeriod.MONTH));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test`
Expected: FAIL to compile — `Statistics` class does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/db/Statistics.java`:

```java
package org.runnerup.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants.DB.ACTIVITY;

public final class Statistics {

  public enum BucketPeriod {
    DAY,
    WEEK,
    MONTH
  }

  public static final class ActivityRow {
    public final long startTime;
    public final double distance;

    public ActivityRow(long startTime, double distance) {
      this.startTime = startTime;
      this.distance = distance;
    }
  }

  private static final int[] TOTALS_DAYS = {7, 30, 365};
  private static final long DAY_SECONDS = 86400L;

  private Statistics() {}

  public static int bucketCount(BucketPeriod period) {
    switch (period) {
      case WEEK:
        return 8;
      case MONTH:
        return 12;
      case DAY:
      default:
        return 14;
    }
  }

  public static double[] totals(List<ActivityRow> rows, long nowSeconds) {
    double[] totals = new double[TOTALS_DAYS.length];
    for (ActivityRow row : rows) {
      long age = nowSeconds - row.startTime;
      for (int i = 0; i < TOTALS_DAYS.length; i++) {
        if (age <= TOTALS_DAYS[i] * DAY_SECONDS) {
          totals[i] += row.distance;
        }
      }
    }
    return totals;
  }

  public static double[] bucketize(
      List<ActivityRow> rows, BucketPeriod period, long nowSeconds, ZoneId zone) {
    double[] buckets = new double[bucketCount(period)];
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    long todayKey = key(today, period);
    for (ActivityRow row : rows) {
      LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
      long dayDiff = todayKey - key(date, period);
      int offset;
      switch (period) {
        case DAY:
        case MONTH:
          offset = (int) dayDiff;
          break;
        case WEEK:
          offset = (int) (dayDiff / 7);
          break;
        default:
          throw new IllegalArgumentException("unknown period " + period);
      }
      if (offset >= 0 && offset < buckets.length) {
        buckets[buckets.length - 1 - offset] += row.distance;
      }
    }
    return buckets;
  }

  public static long[] bucketStarts(BucketPeriod period, long nowSeconds, ZoneId zone) {
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    int count = bucketCount(period);
    long[] starts = new long[count];
    for (int i = 0; i < count; i++) {
      LocalDate date;
      switch (period) {
        case DAY:
          date = today.minusDays(count - 1 - i);
          break;
        case WEEK:
          date = startOfWeek(today).minusWeeks(count - 1 - i);
          break;
        case MONTH:
          date = today.withDayOfMonth(1).minusMonths(count - 1 - i);
          break;
        default:
          throw new IllegalArgumentException("unknown period " + period);
      }
      starts[i] = date.atStartOfDay(zone).toEpochSecond();
    }
    return starts;
  }

  public static List<ActivityRow> queryActivities(SQLiteDatabase db, long fromSeconds) {
    List<ActivityRow> rows = new ArrayList<>();
    try (Cursor cursor =
        db.query(
            ACTIVITY.TABLE,
            new String[] {ACTIVITY.START_TIME, ACTIVITY.DISTANCE},
            ACTIVITY.DELETED
                + " = 0 AND "
                + ACTIVITY.DISTANCE
                + " IS NOT NULL AND "
                + ACTIVITY.START_TIME
                + " >= ?",
            new String[] {Long.toString(fromSeconds)},
            null,
            null,
            ACTIVITY.START_TIME + " ASC")) {
      while (cursor.moveToNext()) {
        rows.add(new ActivityRow(cursor.getLong(0), cursor.getDouble(1)));
      }
    }
    return rows;
  }

  private static long key(LocalDate date, BucketPeriod period) {
    switch (period) {
      case DAY:
        return date.toEpochDay();
      case WEEK:
        return date.toEpochDay() - (date.getDayOfWeek().getValue() - 1);
      case MONTH:
        return date.getYear() * 12L + (date.getMonthValue() - 1);
      default:
        throw new IllegalArgumentException("unknown period " + period);
    }
  }

  private static LocalDate startOfWeek(LocalDate date) {
    return date.minusDays(date.getDayOfWeek().getValue() - 1);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test`
Expected: PASS (StatisticsTest and all other suites green).

- [ ] **Step 5: Run remaining gates**

```bash
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: all pass; lint no new issues.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: add statistics aggregation helper with tests"
```

---

### Task 2: DistanceChartView custom view

**Files:**
- Create: `app/src/main/org/runnerup/view/DistanceChartView.java`

**Interfaces:**
- Consumes: theme attrs `androidx.appcompat.R.attr.colorPrimary`, `R.attr.colorOnSurfaceVariant`, `R.attr.colorOutlineVariant` (app `R`, same pattern as `RunnerUpGraphView.resolveColors`).
- Produces (consumed by Task 4):
  - `public interface DistanceChartView.LabelFormatter { String formatValue(double value); }`
  - `public void setData(double[] values, String[] xLabels)`
  - `public void setLabelFormatter(LabelFormatter formatter)`

- [ ] **Step 1: Write the view**

Create `app/src/main/org/runnerup/view/DistanceChartView.java`:

```java
package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import java.util.Locale;
import org.runnerup.R;

public class DistanceChartView extends View {

  public interface LabelFormatter {
    String formatValue(double value);
  }

  private static final int MAX_Y_LABELS = 4;
  private static final int X_LABEL_SKIP_THRESHOLD = 8;

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private int barColor = Color.parseColor("#3B7DD8");
  private int labelColor = Color.parseColor("#595959");
  private int gridColor = Color.parseColor("#D4D4D4");

  private double[] values = new double[0];
  private String[] xLabels = new String[0];
  private LabelFormatter labelFormatter = value -> String.format(Locale.US, "%.1f", value);

  public DistanceChartView(Context context) {
    this(context, null);
  }

  public DistanceChartView(Context context, AttributeSet attrs) {
    super(context, attrs);
    labelPaint.setTextSize(dp(11));
    gridPaint.setStrokeWidth(dp(1));
    resolveColors();
  }

  public void setData(double[] values, String[] xLabels) {
    this.values = values == null ? new double[0] : values;
    this.xLabels = xLabels == null ? new String[0] : xLabels;
    invalidate();
  }

  public void setLabelFormatter(LabelFormatter formatter) {
    labelFormatter = formatter == null ? this.labelFormatter : formatter;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float leftPad = dp(48);
    float rightPad = dp(8);
    float topPad = dp(8);
    float bottomPad = dp(20);

    float chartLeft = leftPad;
    float chartRight = getWidth() - rightPad;
    float chartTop = topPad;
    float chartBottom = getHeight() - bottomPad;
    float chartWidth = chartRight - chartLeft;
    float chartHeight = chartBottom - chartTop;
    if (chartWidth <= 0 || chartHeight <= 0) {
      return;
    }

    double maxValue = niceMax(max(values));
    int count = values.length;

    for (int i = 0; i <= MAX_Y_LABELS; i++) {
      float ratio = i / (float) MAX_Y_LABELS;
      float y = chartBottom - ratio * chartHeight;
      canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
      String label = labelFormatter.formatValue(maxValue * ratio);
      canvas.drawText(label, dp(4), y - dp(4), labelPaint);
    }

    if (count > 0) {
      float slot = chartWidth / count;
      float barWidth = slot * 0.7f;
      float radius = dp(3);
      RectF rect = new RectF();
      for (int i = 0; i < count; i++) {
        float barHeight = (float) (values[i] / maxValue * chartHeight);
        rect.left = chartLeft + slot * i + (slot - barWidth) / 2;
        rect.top = chartBottom - barHeight;
        rect.right = rect.left + barWidth;
        rect.bottom = chartBottom;
        canvas.drawRoundRect(rect, radius, radius, barPaint);
      }
    }

    if (count > 0 && xLabels.length == count) {
      boolean skipOdd = count > X_LABEL_SKIP_THRESHOLD;
      float slot = chartWidth / count;
      for (int i = 0; i < count; i++) {
        if (skipOdd && i % 2 == 1) {
          continue;
        }
        float centerX = chartLeft + slot * i + slot / 2;
        String label = xLabels[i];
        canvas.drawText(label, centerX - labelPaint.measureText(label) / 2, chartBottom + dp(14), labelPaint);
      }
    }
  }

  private void resolveColors() {
    barColor = resolveColor(androidx.appcompat.R.attr.colorPrimary, barColor);
    labelColor = resolveColor(R.attr.colorOnSurfaceVariant, labelColor);
    gridColor = resolveColor(R.attr.colorOutlineVariant, gridColor);
    barPaint.setColor(barColor);
    labelPaint.setColor(labelColor);
    gridPaint.setColor(gridColor);
  }

  private int resolveColor(int attr, int fallback) {
    TypedValue tv = new TypedValue();
    if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
      return tv.data;
    }
    return fallback;
  }

  private float dp(float value) {
    return getResources().getDisplayMetrics().density * value;
  }

  private static double max(double[] values) {
    double max = 0;
    for (double value : values) {
      max = Math.max(max, value);
    }
    return max;
  }

  private static double niceMax(double value) {
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
    } else if (fraction <= 5) {
      niceFraction = 5;
    } else {
      niceFraction = 10;
    }
    return niceFraction * base;
  }
}
```

- [ ] **Step 2: Run gates**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: all pass; `DistanceChartView` compiles; lint no new issues.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/DistanceChartView.java
git commit -m "feat: add bar chart view for statistics"
```

---

### Task 3: History sub-tabs and layout restructure

**Files:**
- Modify: `app/res/layout/history.xml`
- Modify: `common/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `org.runnerup.common.R.string.History` (exists), `org.runnerup.common.R.string.Statistics` (added here).
- Produces (consumed by Task 4): view ids `history_tabs`, `history_list_content`, `statistics_content`, existing `history_list`, `history_empty`, `history_add` (now inside the list container).

- [ ] **Step 1: Add the Statistics tab label string**

In `common/src/main/res/values/strings.xml`, next to the existing `History` string, add:

```xml
    <string name="Statistics">Statistics</string>
```

- [ ] **Step 2: Restructure `history.xml`**

Replace the whole file body (keep the GPL header comment) so the root `ConstraintLayout` (`@id/history_tab1_layout`) contains, in order: the toolbar, a `TabLayout`, a `FrameLayout` holding the list, and an `<include>` of the (not yet existing) statistics layout:

```xml
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/history_tab1_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/history_actionbar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:title="@string/History" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/history_tabs"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/history_actionbar" />

    <FrameLayout
        android:id="@+id/history_list_content"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/history_tabs">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/history_list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingBottom="@dimen/margin_for_fab"
            android:scrollbarStyle="outsideOverlay" />

        <TextView
            android:id="@+id/history_empty"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            app:drawableTopCompat="@drawable/sport_running"
            android:drawablePadding="8dp"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/history_empty"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:visibility="gone"
            android:layout_gravity="center" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/history_add"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="16dp"
            android:contentDescription="@string/Add_manual_entry"
            app:backgroundTint="?attr/colorPrimaryContainer"
            app:fabSize="normal"
            app:srcCompat="@drawable/ic_add_white_24dp"
            app:tint="?attr/colorOnPrimaryContainer" />
    </FrameLayout>

    <include
        android:id="@+id/statistics_content"
        layout="@layout/statistics"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/history_tabs" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

Note: the `<include>` references `@layout/statistics`, which does not exist until Task 4 — the app will NOT assemble until Task 4 completes. For this task's gate, verify with `./gradlew test` + `:app:lintLatestDebug` (lint may report the missing layout reference; if `lint` fails solely on that, defer the failing check and note it) and confirm `spotlessApply`/`spotlessCheck` pass (they don't touch XML). The full assemble gate for this task is therefore deferred to Task 4; document that in the report.

- [ ] **Step 3: Run partial gates**

```bash
./gradlew test
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:lintLatestDebug
```

Expected: `test` and spotless pass. Lint may flag the missing `@layout/statistics` reference (create the file reference in Task 4 resolves it); record the outcome. Do not attempt `assembleLatestDebug` yet (it will fail on the missing layout).

- [ ] **Step 4: Commit**

```bash
git add app/res/layout/history.xml common/src/main/res/values/strings.xml
git commit -m "feat: add history sub-tab layout and statistics label"
```

---

### Task 4: Statistics layout and HistoryFragment wiring

**Files:**
- Create: `app/res/layout/statistics.xml`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java`

**Interfaces:**
- Consumes:
  - `Statistics` from Task 1: `BucketPeriod`, `ActivityRow`, `bucketCount`, `totals`, `bucketize`, `bucketStarts`, `queryActivities`.
  - `DistanceChartView` from Task 2: `setData(double[], String[])`, `setLabelFormatter(LabelFormatter)`, `LabelFormatter.formatValue(double)`.
  - Layout ids from Task 3: `history_tabs`, `history_list_content`, `statistics_content`.
- Produces: view ids `statistics_cards`, `statistics_7_card` / `statistics_30_card` / `statistics_365_card`, `statistics_7_value` / `statistics_30_value` / `statistics_365_value`, `statistics_toggle`, `statistics_toggle_day` / `statistics_toggle_week` / `statistics_toggle_month`, `statistics_chart_title`, `statistics_chart`, `statistics_empty`.

- [ ] **Step 1: Add remaining strings**

In `common/src/main/res/values/strings.xml`, next to the `Statistics` string added in Task 3, add:

```xml
    <string name="Statistics_7_days">Last 7 days</string>
    <string name="Statistics_30_days">Last 30 days</string>
    <string name="Statistics_365_days">Last 365 days</string>
    <string name="Statistics_day">Day</string>
    <string name="Statistics_week">Week</string>
    <string name="Statistics_month">Month</string>
    <string name="Statistics_last_14_days">Last 14 days</string>
    <string name="Statistics_last_8_weeks">Last 8 weeks</string>
    <string name="Statistics_last_12_months">Last 12 months</string>
    <string name="Statistics_no_activities">No activities yet</string>
```

- [ ] **Step 2: Create `app/res/layout/statistics.xml`**

Root is a `ScrollView` (no id — the id comes from the `<include>` in `history.xml`), `fillViewport` true:

```xml
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:id="@+id/statistics_cards"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/statistics_7_card"
                style="?attr/materialCardViewFilledStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                app:cardCornerRadius="@dimen/history_row_corner">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/Statistics_7_days"
                        android:textAllCaps="true"
                        android:textAppearance="?attr/textAppearanceLabelMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/statistics_7_value"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/statistics_30_card"
                style="?attr/materialCardViewFilledStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                app:cardCornerRadius="@dimen/history_row_corner">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/Statistics_30_days"
                        android:textAllCaps="true"
                        android:textAppearance="?attr/textAppearanceLabelMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/statistics_30_value"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/statistics_365_card"
                style="?attr/materialCardViewFilledStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                app:cardCornerRadius="@dimen/history_row_corner">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/Statistics_365_days"
                        android:textAllCaps="true"
                        android:textAppearance="?attr/textAppearanceLabelMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/statistics_365_value"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
        </LinearLayout>

        <com.google.android.material.button.MaterialButtonToggleGroup
            android:id="@+id/statistics_toggle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            app:checkedButton="@id/statistics_toggle_day"
            app:selectionRequired="true"
            app:singleSelection="true">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/statistics_toggle_day"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/Statistics_day" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/statistics_toggle_week"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/Statistics_week" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/statistics_toggle_month"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/Statistics_month" />
        </com.google.android.material.button.MaterialButtonToggleGroup>

        <TextView
            android:id="@+id/statistics_chart_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/Statistics_last_14_days"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:textColor="?attr/colorOnSurface" />

        <org.runnerup.view.DistanceChartView
            android:id="@+id/statistics_chart"
            android:layout_width="match_parent"
            android:layout_height="240dp"
            android:layout_marginTop="8dp" />

        <TextView
            android:id="@+id/statistics_empty"
            android:layout_width="match_parent"
            android:layout_height="240dp"
            android:gravity="center"
            android:text="@string/Statistics_no_activities"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:visibility="gone" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: Wire up HistoryFragment**

In `app/src/main/org/runnerup/view/HistoryFragment.java`:

Add imports (append to the existing import block):

```java
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.common.R;
import org.runnerup.db.Statistics;
import org.runnerup.db.Statistics.BucketPeriod;
```

Note: `org.runnerup.R` and `android.widget.TextView` imports already exist; do not duplicate them. `TextView` is imported at line 30. Verify against the current file.

Add fields after the existing `emptyView` field (line 68):

```java
  private static final int TAB_HISTORY_INDEX = 0;
  private static final int TAB_STATISTICS_INDEX = 1;

  private final ExecutorService statisticsExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private int currentTab = TAB_HISTORY_INDEX;
  private BucketPeriod currentPeriod = BucketPeriod.DAY;
  private List<Statistics.ActivityRow> statisticsRows = null;
  private View statisticsContent;
  private View statisticsEmpty;
  private DistanceChartView statisticsChart;
  private TextView statisticsChartTitle;
  private TextView statistics7Value;
  private TextView statistics30Value;
  private TextView statistics365Value;
```

In `onViewCreated`, after `new ActivityCleaner().conditionalRecompute(mDB);` (line 103), add:

```java
    statisticsContent = view.findViewById(R.id.statistics_content);
    statisticsEmpty = view.findViewById(R.id.statistics_empty);
    statisticsChart = view.findViewById(R.id.statistics_chart);
    statisticsChartTitle = view.findViewById(R.id.statistics_chart_title);
    statistics7Value = view.findViewById(R.id.statistics_7_value);
    statistics30Value = view.findViewById(R.id.statistics_30_value);
    statistics365Value = view.findViewById(R.id.statistics_365_value);
    statisticsChart.setLabelFormatter(
        value -> formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value)));

    TabLayout historyTabs = view.findViewById(R.id.history_tabs);
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.History));
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Statistics));
    historyTabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            selectTab(tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            if (tab.getPosition() == TAB_STATISTICS_INDEX) {
              loadStatistics();
            }
          }
        });

    MaterialButtonToggleGroup statisticsToggle = view.findViewById(R.id.statistics_toggle);
    statisticsToggle.addOnButtonCheckedListener(
        (group, checkedId, isChecked) -> {
          if (!isChecked) {
            return;
          }
          BucketPeriod period =
              checkedId == R.id.statistics_toggle_week
                  ? BucketPeriod.WEEK
                  : checkedId == R.id.statistics_toggle_month ? BucketPeriod.MONTH : BucketPeriod.DAY;
          currentPeriod = period;
          updateChart();
        });
```

Note: `selectTab(0)` fires automatically when the first tab is added and selected, so the default state (list visible, stats gone, FAB visible) matches the XML defaults — no extra call needed. If a later fix changes tab auto-selection, add an explicit `selectTab(0)` after wiring.

In `onResume`, after `LoaderManager.getInstance(this).restartLoader(0, null, this);` (line 109), add:

```java
    if (currentTab == TAB_STATISTICS_INDEX) {
      loadStatistics();
    }
```

In `onDestroy`, after `DBHelper.closeDB(mDB);` (line 115), add:

```java
    statisticsExecutor.shutdown();
```

Add these private methods (place them after `onLoaderReset`, before `openActivity`):

```java
  private void selectTab(int index) {
    currentTab = index;
    View view = getView();
    if (view == null) {
      return;
    }
    view.findViewById(R.id.history_list_content)
        .setVisibility(index == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    statisticsContent.setVisibility(index == TAB_STATISTICS_INDEX ? View.VISIBLE : View.GONE);
    fab.setVisibility(index == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    if (index == TAB_STATISTICS_INDEX) {
      loadStatistics();
    }
  }

  private void loadStatistics() {
    if (mDB == null || statisticsContent == null) {
      return;
    }
    BucketPeriod period = currentPeriod;
    statisticsExecutor.execute(
        () -> {
          long now = System.currentTimeMillis() / 1000;
          List<Statistics.ActivityRow> rows = Statistics.queryActivities(mDB, now - 365L * 86400);
          double[] totals = Statistics.totals(rows, now);
          double[] buckets = Statistics.bucketize(rows, period, now, ZoneId.systemDefault());
          long[] starts = Statistics.bucketStarts(period, now, ZoneId.systemDefault());
          mainHandler.post(
              () -> {
                statisticsRows = rows;
                statistics7Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
                statistics30Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
                statistics365Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
                statisticsChartTitle.setText(chartTitleFor(period));
                statisticsChart.setData(buckets, buildXLabels(period, starts));
                boolean empty = true;
                for (double value : buckets) {
                  if (value > 0) {
                    empty = false;
                    break;
                  }
                }
                statisticsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                statisticsChart.setVisibility(empty ? View.GONE : View.VISIBLE);
              });
        });
  }

  private void updateChart() {
    if (statisticsRows == null) {
      return;
    }
    long now = System.currentTimeMillis() / 1000;
    double[] buckets = Statistics.bucketize(statisticsRows, currentPeriod, now, ZoneId.systemDefault());
    long[] starts = Statistics.bucketStarts(currentPeriod, now, ZoneId.systemDefault());
    statisticsChartTitle.setText(chartTitleFor(currentPeriod));
    statisticsChart.setData(buckets, buildXLabels(currentPeriod, starts));
  }

  private String[] buildXLabels(BucketPeriod period, long[] starts) {
    String[] labels = new String[starts.length];
    for (int i = 0; i < starts.length; i++) {
      Date date = new Date(starts[i] * 1000);
      labels[i] =
          period == BucketPeriod.MONTH ? formatter.formatMonth(date) : formatter.formatDayOfMonth(date);
    }
    return labels;
  }

  private int chartTitleFor(BucketPeriod period) {
    switch (period) {
      case WEEK:
        return org.runnerup.common.R.string.Statistics_last_8_weeks;
      case MONTH:
        return org.runnerup.common.R.string.Statistics_last_12_months;
      case DAY:
      default:
        return org.runnerup.common.R.string.Statistics_last_14_days;
    }
  }
```

Note: `R.id.statistics_toggle_week` / `statistics_toggle_month` in the toggle listener refer to the **app** `R` (already imported as `org.runnerup.R` at line 49) — the ids live in the app module's layouts. `org.runnerup.common.R.string.*` is used for all common-module strings. The unused `import org.runnerup.common.R;` in the import block above is for the string constants only if referenced; if the compiler flags it as unused, remove that one line (all strings in this task are referenced via the fully-qualified `org.runnerup.common.R.string.*`).

- [ ] **Step 4: Run gates**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: all pass (this resolves Task 3's pending missing-layout reference). Lint no new issues.

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/statistics.xml common/src/main/res/values/strings.xml app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: add statistics page to history tab"
```

---

### Task 5: On-device verification

**Files:** none (verification only).

**Interfaces:**
- Consumes: the finished feature from Tasks 1-4 (built map APK from Task 4's last `assembleLatestDebug`).

- [ ] **Step 1: Ensure map APK is current**

Task 4's gate ran `assembleLatestDebug -Porg.runnerup.nomap` LAST, which overwrites the shared APK path. Rebuild the map variant:

```bash
./gradlew :app:assembleLatestDebug
```

APK: `app/build/outputs/apk/latest/debug/app-latest-debug.apk`.

- [ ] **Step 2: Install and seed known data**

```bash
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 025b46e24edcbca6 shell am force-stop org.runnerup.debug
adb -s 025b46e24edcbca6 shell cmd uimode night no
```

Check existing activities (the app DB has no `sqlite3` on-device):

```bash
adb -s 025b46e24edcbca6 exec-out run-as org.runnerup.debug cat databases/runnerup.db > /tmp/opencode/runnerup-stats.db
sqlite3 /tmp/opencode/runnerup-stats.db "SELECT _id, start_time, distance, deleted FROM activity;"
```

If there are no non-deleted activities with distance, seed a deterministic set (3 known rows; adjust the epoch values to the current date) by editing the pulled DB and pushing it back per AGENTS.md (host `sqlite3`, push to `/data/local/tmp`, chmod 666, `run-as` copy, force-stop app). Use today's date with `start_time` in seconds: e.g. today 5000 m, 1 day ago 3000 m, 6 days ago 10000 m (for the day chart), and 30/60/300 days ago smaller values (for week/month charts and the 7/30/365 totals).

- [ ] **Step 3: Verify Statistics tab in day mode**

```bash
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Tap the History tab (middle of the 3 bottom-nav items), then the "Statistics" sub-tab at the top of the screen. Confirm via `adb shell dumpsys activity top` and screenshots (tap coordinates may be confirmed from a `screencap`):
- Three cards show correct totals for the seeded data (7-day total = today + 1 day ago + 6 days ago = 18000 m → "18.00 km" in metric).
- Chart shows 14 day-bars; today's bar is the rightmost and 6-days-ago bar is visible; title "Last 14 days".
- Toggle to Week: 8 week-bars, title "Last 8 weeks". Toggle to Month: 12 month-bars, title "Last 12 months".

Screenshot: `adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/statistics_day.png`.

- [ ] **Step 4: Verify in night mode**

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night yes
adb -s 025b46e24edcbca6 shell am force-stop org.runnerup.debug
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Repeat Step 3 for History → Statistics and at least the Day toggle. Confirm bars/labels use the night palette (primary-blue bars, light axis text) and are legible. Screenshot: `/tmp/opencode/statistics_night.png`.

- [ ] **Step 5: Verify History tab still works**

Switch back to the History sub-tab: the list, FAB, and empty state behave as before; the FAB is hidden while on Statistics and returns when back on History.

- [ ] **Step 6: Run full gates**

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```

Expected: all pass.

- [ ] **Step 7: Report**

Record the on-device results (day + night, all three toggles, FAB behavior, card totals matching seeded data) and screenshot paths in the task report.

No commit for this task.

---

## Self-Review Notes

- **Spec coverage:** Navigation sub-tabs (Task 3 layout + Task 4 fragment) ✓; 7/30/365 totals (Task 1 `totals` + Task 4 cards) ✓; DAY/WEEK/MONTH buckets (Task 1 `bucketize`) ✓; custom bar chart (Task 2) ✓; toggle + titles + empty state (Task 4) ✓; strings in common (Tasks 3-4) ✓; ExecutorService+Handler async (Task 4) ✓; on-device day/night verification (Task 5) ✓; unit tests for pure logic (Task 1) ✓.
- **Placeholder scan:** no TBDs; every code step contains full code.
- **Type consistency:** `Statistics.BucketPeriod/ActivityRow/bucketCount/totals/bucketize/bucketStarts/queryActivities` defined in Task 1 and consumed identically in Task 4. `DistanceChartView.setData/setLabelFormatter/LabelFormatter` defined in Task 2, consumed in Task 4. `Formatter.Format.TXT_SHORT` verified against Formatter.java:782-790. Theme-attr resolution pattern (`resolveColor`) copied verbatim from RunnerUpGraphView.java:105-118.
- **Known deviation from spec:** Task 3's `<include>` references a layout created in Task 4, so Task 3's assemble gate is deferred (documented in the task). DAY buckets use calendar days (local time) to match their day-of-month labels, per the spec's design section.
