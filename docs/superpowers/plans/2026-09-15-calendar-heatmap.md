# Calendar Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a month-grid distance heatmap inside the Progress tab (History screen) where each day cell shows the day number + total distance colored by relative intensity, with prev/next month paging and tap-to-open that day's run(s).

**Architecture:** Pure calendar computation lives in `Statistics` (testable, no Android); a new `CalendarHeatmapView` (custom `View`, `DistanceChartView` pattern) draws the 7×6 grid and resolves taps; `HistoryFragment` owns the month state, lazy-loads the full activity table per refresh, and opens runs directly or via a `BottomSheetDialog`. All off-main work runs on the existing `statisticsExecutor`.

**Tech Stack:** Java 17, JUnit4 + mockito (unit tests under `app/test/java`), Material 3. No new dependencies.

## Global Constraints

- Branch: `subproject-a-wip` (has the spec commit `450779e8` and the CI fix). Never push to `fork` unless asked.
- Gates (each task commits only when green): `./gradlew test`, `spotlessApply`, `./gradlew spotlessCheck`, `:app:assembleLatestDebug`, `:app:lintLatestDebug` (ignore the 28 pre-existing baseline errors — only new issues matter). Low-RAM machine: run Gradle **sequentially**; the default `-Xmx2048m` already set in `gradle.properties` is fine, do not raise.
- Targeted test command used during tasks: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`.
- Lint: `StatisticsTest.java` uses `@Test`; no `@SuppressLint` needed except where existing file style requires it.
- No new dependencies, no DB schema changes, no new prefs.
- Google Java Format via `spotlessApply`; run it before committing each task.
- GPL-3 project: new files follow the repo's latest new-file convention (no license header — see `SportCountBadge.java`, merged 2026-09-14).
- No explanatory comments in code unless the surrounding file uses them.
- Do NOT stage user-local files: `.opencode/`, `.superpowers/`, `AGENTS.md`, `imported/`, `job-logs.txt`, `opencode.json`, `gradle/gradle-daemon-jvm.properties`, and any untracked `docs/` content not part of this task.
- Design decisions fixed by the spec (`docs/superpowers/specs/2026-09-15-calendar-heatmap-design.md`): placement between the summary cards and the metric toggle in `statistics.xml`; distance-only coloring relative to the shown month's busiest day; Monday-first grid; prev/next month arrows; whole-history paging; single-run day opens the run directly, multi-run day shows a bottom sheet; sport chips filter the heatmap.

---

### Task 0: Pure calendar computation in `Statistics` + unit tests

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java` (insert after `queryActivities`, ends line 215; add `import java.util.HashMap;` and `import java.util.Map;` to the import block, lines 9-10)
- Modify: `app/test/java/org/runnerup/db/StatisticsTest.java` (add `import java.util.Arrays;`, `import java.util.HashMap;`, `import java.util.Map;`, `import org.runnerup.db.Statistics.CalendarDay;`)

**Interfaces:**
- Produces (used by Tasks 1-3):
  - `public static final int CALENDAR_COLUMNS = 7;`
  - `public static final int CALENDAR_ROWS = 6;`
  - `public static final class CalendarDay { public static final CalendarDay BLANK; public final int day; public final double distance; public final long[] activityIds; public CalendarDay(int day, double distance, long[] activityIds); }`
  - `public static Map<LocalDate, List<ActivityRow>> groupActivitiesByDay(List<ActivityRow> rows, ZoneId zone)`
  - `public static CalendarDay[] calendarDays(LocalDate month, Map<LocalDate, List<ActivityRow>> byDay)` — 42 cells, Monday-first, `day == 0` = blank (outside month), `distance` = summed meters for that day, `activityIds` = that day's ids.
  - `public static int distanceBucket(double distanceMeters, double monthMaxMeters, int steps)` — 0 if no/zero distance, else `clamp(ceil(steps*d/max), 1, steps)`.

- [ ] **Step 1: Write the failing tests**

Append to `StatisticsTest.java`:

```java
  @Test
  public void groupActivitiesByDayGroupsLocalDates() {
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-06-01"), 1000.0));
    rows.add(new ActivityRow(2, at("2026-06-01"), 2000.0));
    rows.add(new ActivityRow(3, at("2026-06-02"), 500.0));
    Map<LocalDate, List<ActivityRow>> byDay = Statistics.groupActivitiesByDay(rows, UTC);
    assertEquals(2, byDay.size());
    assertEquals(2, byDay.get(LocalDate.parse("2026-06-01")).size());
    assertEquals(1, byDay.get(LocalDate.parse("2026-06-02")).size());
  }

  @Test
  public void calendarDaysLaysOutMonth() {
    LocalDate month = LocalDate.of(2026, 7, 1); // 1 July 2026 is a Wednesday
    CalendarDay[] cells = Statistics.calendarDays(month, new HashMap<>());
    assertEquals(7 * 6, cells.length);
    int leading = month.getDayOfWeek().getValue() - 1;
    assertEquals(0, cells[0].day);
    assertEquals(0, cells[leading - 1].day);
    assertEquals(1, cells[leading].day);
    assertEquals(
        month.lengthOfMonth(), cells[leading + month.lengthOfMonth() - 1].day);
    assertEquals(0, cells[leading + month.lengthOfMonth()].day);
  }

  @Test
  public void calendarDaysSumDistanceAndIds() {
    LocalDate month = LocalDate.of(2026, 6, 1);
    int leading = month.getDayOfWeek().getValue() - 1;
    Map<LocalDate, List<ActivityRow>> byDay = new HashMap<>();
    byDay.put(
        LocalDate.parse("2026-06-10"),
        Arrays.asList(
            new ActivityRow(1, at("2026-06-10"), 1000.0),
            new ActivityRow(2, at("2026-06-10"), 3000.0)));
    CalendarDay[] cells = Statistics.calendarDays(month, byDay);
    CalendarDay cell = cells[leading + 9];
    assertEquals(10, cell.day);
    assertEquals(4000.0, cell.distance, 1e-9);
    assertArrayEquals(new long[] {1, 2}, cell.activityIds);
  }

  @Test
  public void distanceBucketScalesAndClamps() {
    assertEquals(0, Statistics.distanceBucket(0, 10, 4));
    assertEquals(0, Statistics.distanceBucket(5, 0, 4));
    assertEquals(1, Statistics.distanceBucket(1, 10, 4));
    assertEquals(2, Statistics.distanceBucket(5, 10, 4));
    assertEquals(4, Statistics.distanceBucket(10, 10, 4));
    assertEquals(4, Statistics.distanceBucket(50, 10, 4));
  }
```

Note: `assertArrayEquals(new long[] {...}, long[])` requires `import static org.junit.Assert.assertArrayEquals;` — add it.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: FAIL — `calendardayslaysoutmonth` etc. fail/skip with "cannot find symbol" or assertion errors (`calendarDays`, `groupActivitiesByDay`, `distanceBucket` don't exist yet; `CalendarDay` unresolved).

- [ ] **Step 3: Implement the helpers**

In `Statistics.java`, after the end of `queryActivities` (line 215), add:

```java
  public static final int CALENDAR_COLUMNS = 7;
  public static final int CALENDAR_ROWS = 6;

  public static final class CalendarDay {
    public static final CalendarDay BLANK = new CalendarDay(0, 0, new long[0]);

    public final int day;
    public final double distance;
    public final long[] activityIds;

    public CalendarDay(int day, double distance, long[] activityIds) {
      this.day = day;
      this.distance = distance;
      this.activityIds = activityIds;
    }
  }

  public static Map<LocalDate, List<ActivityRow>> groupActivitiesByDay(
      List<ActivityRow> rows, ZoneId zone) {
    Map<LocalDate, List<ActivityRow>> byDay = new HashMap<>();
    for (ActivityRow row : rows) {
      LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
      List<ActivityRow> dayRows = byDay.get(date);
      if (dayRows == null) {
        dayRows = new ArrayList<>();
        byDay.put(date, dayRows);
      }
      dayRows.add(row);
    }
    return byDay;
  }

  public static CalendarDay[] calendarDays(
      LocalDate month, Map<LocalDate, List<ActivityRow>> byDay) {
    CalendarDay[] cells = new CalendarDay[CALENDAR_COLUMNS * CALENDAR_ROWS];
    LocalDate first = month.withDayOfMonth(1);
    int leading = first.getDayOfWeek().getValue() - 1;
    int daysInMonth = month.lengthOfMonth();
    for (int i = 0; i < cells.length; i++) {
      int dayOfMonth = i - leading + 1;
      if (dayOfMonth < 1 || dayOfMonth > daysInMonth) {
        cells[i] = CalendarDay.BLANK;
        continue;
      }
      List<ActivityRow> dayRows = byDay.get(month.withDayOfMonth(dayOfMonth));
      if (dayRows == null || dayRows.isEmpty()) {
        cells[i] = new CalendarDay(dayOfMonth, 0, new long[0]);
        continue;
      }
      double distance = 0;
      long[] ids = new long[dayRows.size()];
      for (int j = 0; j < dayRows.size(); j++) {
        distance += dayRows.get(j).distance;
        ids[j] = dayRows.get(j).id;
      }
      cells[i] = new CalendarDay(dayOfMonth, distance, ids);
    }
    return cells;
  }

  public static int distanceBucket(double distanceMeters, double monthMaxMeters, int steps) {
    if (distanceMeters <= 0 || monthMaxMeters <= 0 || steps <= 0) {
      return 0;
    }
    int bucket = (int) Math.ceil(steps * distanceMeters / monthMaxMeters);
    return Math.max(1, Math.min(steps, bucket));
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: PASS, all green.

- [ ] **Step 5: Spotless + commit**

Run: `./gradlew spotlessApply`
Then verify `git status` shows only `Statistics.java` and `StatisticsTest.java`; stage exactly those two files and:

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: add calendar day grouping and grid math to Statistics"
```

---

### Task 1: `CalendarHeatmapView` + Progress-tab section layout + strings + icons

**Files:**
- Create: `app/src/main/org/runnerup/view/CalendarHeatmapView.java`
- Create: `app/res/drawable/ic_calendar_prev.xml`
- Create: `app/res/drawable/ic_calendar_next.xml`
- Modify: `app/res/layout/statistics.xml` (insert after the closing `</LinearLayout>` of `statistics_cards`, line 119, before the metric toggle on line 121)
- Modify: `app/res/values/strings.xml` (add three strings after line 55, `<string name="records_section_title">`)

**Interfaces:**
- Consumes: `Statistics.CALENDAR_COLUMNS`, `Statistics.CALENDAR_ROWS`, `Statistics.CalendarDay`, `Statistics.distanceBucket(...)` (from Task 0).
- Produces (used by Task 2-3):
  - `public void setData(Statistics.CalendarDay[] cells)`
  - `public void setDayLabelFormatter(CalendarHeatmapView.DayLabelFormatter formatter)`
  - `public void setOnDayTapListener(CalendarHeatmapView.OnDayTapListener listener)`
  - `public interface DayLabelFormatter { String formatDistance(double meters); }`
  - `public interface OnDayTapListener { void onDayTap(int dayOfMonth); }`

- [ ] **Step 1: Create the vector drawables**

`app/res/drawable/ic_calendar_prev.xml` (left chevron; `autoMirrored` so it flips in RTL):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z" />
</vector>
```

`app/res/drawable/ic_calendar_next.xml` (right chevron):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M14,6l1.41,1.41L10.83,12l4.58,4.59L14,18l-6,-6z" />
</vector>
```

- [ ] **Step 2: Add the strings**

In `app/res/values/strings.xml`, right after the `records_section_title` line (55):

```xml
    <string name="calendar_section_title">Calendar</string>
    <string name="calendar_prev">Previous month</string>
    <string name="calendar_next">Next month</string>
```

- [ ] **Step 3: Add the section to `statistics.xml`**

Insert between the end of the `statistics_cards` LinearLayout (line 119) and the `statistics_metric_toggle` (line 121):

```xml
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="vertical">

            <TextView
                android:id="@+id/calendar_section_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/calendar_section_title"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textColor="?attr/colorOnSurface" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageButton
                    android:id="@+id/calendar_prev"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/calendar_prev"
                    android:src="@drawable/ic_calendar_prev" />

                <TextView
                    android:id="@+id/calendar_month_label"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="center"
                    android:textAppearance="?attr/textAppearanceTitleSmall"
                    android:textColor="?attr/colorOnSurface" />

                <ImageButton
                    android:id="@+id/calendar_next"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/calendar_next"
                    android:src="@drawable/ic_calendar_next" />
            </LinearLayout>

            <org.runnerup.view.CalendarHeatmapView
                android:id="@+id/calendar_heatmap"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp" />
        </LinearLayout>
```

- [ ] **Step 4: Create `CalendarHeatmapView.java`**

Full file:

```java
package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.R;
import org.runnerup.db.Statistics;

public class CalendarHeatmapView extends View {

  public interface OnDayTapListener {
    void onDayTap(int dayOfMonth);
  }

  public interface DayLabelFormatter {
    String formatDistance(double meters);
  }

  private static final int DAYS_PER_WEEK = Statistics.CALENDAR_COLUMNS;
  private static final int WEEKS = Statistics.CALENDAR_ROWS;
  private static final int INTENSITY_STEPS = 4;
  private static final int[] ALPHA_STEPS = {42, 92, 150, 214};
  private static final char[] WEEKDAY_LABELS = {'M', 'T', 'W', 'T', 'F', 'S', 'S'};
  private static final float HEADER_HEIGHT_DP = 20;
  private static final float CELL_HEIGHT_DP = 44;
  private static final float CORNER_RADIUS_DP = 6;
  private static final float GAP_DP = 3;

  private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint distancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final GestureDetector gestureDetector;

  private OnDayTapListener onDayTapListener;
  private DayLabelFormatter dayLabelFormatter = value -> String.format("%.1f", value);
  private Statistics.CalendarDay[] cells = new Statistics.CalendarDay[0];
  private double monthMax = 0;
  private int fillColor = 0xFFD68C27;
  private int onSurfaceColor = 0xFF1A1A1A;
  private int onSurfaceVariantColor = 0xFF595959;

  public CalendarHeatmapView(Context context) {
    this(context, null);
  }

  public CalendarHeatmapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    weekdayPaint.setTextSize(dp(11));
    dayPaint.setTextSize(dp(13));
    dayPaint.setFakeBoldText(true);
    distancePaint.setTextSize(dp(9));
    cellPaint.setStyle(Paint.Style.FILL);
    gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                handleTap((int) e.getX(), (int) e.getY());
                return true;
              }
            });
    resolveColors();
  }

  public void setData(Statistics.CalendarDay[] cells) {
    this.cells = cells == null ? new Statistics.CalendarDay[0] : cells;
    monthMax = 0;
    for (Statistics.CalendarDay cell : this.cells) {
      if (cell != null) {
        monthMax = Math.max(monthMax, cell.distance);
      }
    }
    invalidate();
  }

  public void setDayLabelFormatter(DayLabelFormatter formatter) {
    dayLabelFormatter = formatter == null ? dayLabelFormatter : formatter;
    invalidate();
  }

  public void setOnDayTapListener(OnDayTapListener listener) {
    onDayTapListener = listener;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = Math.round(dp(HEADER_HEIGHT_DP) + WEEKS * dp(CELL_HEIGHT_DP));
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cellWidth = getWidth() / (float) DAYS_PER_WEEK;
    float cellHeight = dp(CELL_HEIGHT_DP);
    float gap = dp(GAP_DP);
    float headerHeight = dp(HEADER_HEIGHT_DP);
    float radius = dp(CORNER_RADIUS_DP);

    for (int col = 0; col < DAYS_PER_WEEK; col++) {
      float cx = cellWidth * col + cellWidth / 2;
      String label = String.valueOf(WEEKDAY_LABELS[col]);
      canvas.drawText(
          label,
          cx - weekdayPaint.measureText(label) / 2,
          headerHeight - dp(4),
          weekdayPaint);
    }

    for (int i = 0; i < cells.length && i < DAYS_PER_WEEK * WEEKS; i++) {
      Statistics.CalendarDay cell = cells[i];
      if (cell == null) {
        continue;
      }
      int col = i % DAYS_PER_WEEK;
      int row = i / DAYS_PER_WEEK;
      float left = col * cellWidth + gap;
      float right = (col + 1) * cellWidth - gap;
      float top = headerHeight + row * cellHeight + gap;
      float bottom = headerHeight + (row + 1) * cellHeight - gap;
      rect.set(left, top, right, bottom);

      boolean active = cell.day != 0 && cell.distance > 0;
      if (cell.day != 0 && !active) {
        cellPaint.setColor(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 26));
        canvas.drawRoundRect(rect, radius, radius, cellPaint);
      } else if (active) {
        int bucket = Statistics.distanceBucket(cell.distance, monthMax, INTENSITY_STEPS);
        cellPaint.setColor(ColorUtils.setAlphaComponent(fillColor, ALPHA_STEPS[bucket - 1]));
        canvas.drawRoundRect(rect, radius, radius, cellPaint);
      }

      if (cell.day != 0) {
        float cx = (left + right) / 2;
        String dayLabel = String.valueOf(cell.day);
        dayPaint.setColor(
            active ? onSurfaceColor : ColorUtils.setAlphaComponent(onSurfaceColor, 96));
        canvas.drawText(dayLabel, cx - dayPaint.measureText(dayLabel) / 2, top + dp(16), dayPaint);
        if (active) {
          String distanceLabel = dayLabelFormatter.formatDistance(cell.distance);
          distancePaint.setColor(onSurfaceVariantColor);
          canvas.drawText(
              distanceLabel,
              cx - distancePaint.measureText(distanceLabel) / 2,
              bottom - dp(7),
              distancePaint);
        }
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }

  private void handleTap(int x, int y) {
    if (onDayTapListener == null || cells.length == 0) {
      return;
    }
    float headerHeight = dp(HEADER_HEIGHT_DP);
    float cellWidth = getWidth() / (float) DAYS_PER_WEEK;
    float cellHeight = dp(CELL_HEIGHT_DP);
    if (y < headerHeight) {
      return;
    }
    int col = (int) (x / cellWidth);
    int row = (int) ((y - headerHeight) / cellHeight);
    int index = row * DAYS_PER_WEEK + col;
    if (index < 0 || index >= cells.length) {
      return;
    }
    Statistics.CalendarDay cell = cells[index];
    if (cell != null && cell.day != 0) {
      onDayTapListener.onDayTap(cell.day);
    }
  }

  private void resolveColors() {
    fillColor = resolveColor(R.attr.colorTertiary, fillColor);
    onSurfaceColor = resolveColor(androidx.appcompat.R.attr.colorOnSurface, onSurfaceColor);
    onSurfaceVariantColor = resolveColor(R.attr.colorOnSurfaceVariant, onSurfaceVariantColor);
    weekdayPaint.setColor(onSurfaceVariantColor);
    dayPaint.setColor(onSurfaceColor);
    distancePaint.setColor(onSurfaceVariantColor);
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
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (new view, layout, strings, drawables compile and inflate).

- [ ] **Step 6: Spotless + commit**

Run: `./gradlew spotlessApply`
Stage exactly the five touched files and commit:

```bash
git add app/src/main/org/runnerup/view/CalendarHeatmapView.java \
  app/res/drawable/ic_calendar_prev.xml \
  app/res/drawable/ic_calendar_next.xml \
  app/res/layout/statistics.xml \
  app/res/values/strings.xml
git commit -m "feat: add calendar heatmap view and Progress-tab section"
```

---

### Task 2: Wire the heatmap into `HistoryFragment` (load, month paging, refresh)

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java`

**Interfaces:**
- Consumes: Task 0 helpers (`Statistics.groupActivitiesByDay`, `Statistics.calendarDays`, `Statistics.queryActivities(mDB, 0L, currentSport)`), Task 1 view (`CalendarHeatmapView.setData / setDayLabelFormatter / setOnDayTapListener`) and ids (`R.id.calendar_prev`, `R.id.calendar_next`, `R.id.calendar_month_label`, `R.id.calendar_heatmap`).
- Produces (consumed by Task 3):
  - Field `private LocalDate shownMonth = null;`
  - Field `private Map<LocalDate, List<Statistics.ActivityRow>> calendarByDay = new HashMap<>();`
  - `private void loadCalendar()` — lazy: sets `shownMonth` to the current month's first day when null, queries the whole `activity` table, groups by day on the executor, posts to main, calls `renderCalendar()`.
  - `private void renderCalendar()` — pushes `Statistics.calendarDays(shownMonth, calendarByDay)` into the view and sets the month label.
  - `private void changeMonth(int delta)` — `shownMonth = shownMonth.plusMonths(delta); renderCalendar();`
  - `private void openDay(int dayOfMonth)` — Task 3 fills this in; Task 2 adds the listener calling it (declared now, body minimal returning on null).

- [ ] **Step 1: Add imports and fields**

Add to the `java.time`/`java.util` import block (after line 61 `import java.time.ZoneId;` and in the `java.util` block lines 62-67):

```java
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
```

After the records fields (after line 148 `private List<RecordInfo> recordsCache = null;`), add:

```java
  private LocalDate shownMonth = null;
  private Map<LocalDate, List<Statistics.ActivityRow>> calendarByDay = new HashMap<>();
  private TextView calendarMonthLabel;
  private CalendarHeatmapView calendarHeatmap;
```

- [ ] **Step 2: Wire view + buttons + tap listener in `onViewCreated`**

After the records wiring (`recordsGrid = view.findViewById(R.id.records_grid);`, line 205), add:

```java
    calendarMonthLabel = view.findViewById(R.id.calendar_month_label);
    calendarHeatmap = view.findViewById(R.id.calendar_heatmap);
    calendarHeatmap.setDayLabelFormatter(formatter::getDistanceDisplay);
    calendarHeatmap.setOnDayTapListener(this::openDay);
    view.findViewById(R.id.calendar_prev)
        .setOnClickListener(v -> changeMonth(-1));
    view.findViewById(R.id.calendar_next)
        .setOnClickListener(v -> changeMonth(1));
```

- [ ] **Step 3: Add `loadCalendar`, `renderCalendar`, `changeMonth`, and `openDay`**

Place them right after `loadStatistics()` (ends line 463):

```java
  private void loadCalendar() {
    if (mDB == null || calendarHeatmap == null) {
      return;
    }
    if (shownMonth == null) {
      shownMonth = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1);
    }
    statisticsExecutor.execute(
        () -> {
          List<Statistics.ActivityRow> rows = Statistics.queryActivities(mDB, 0L, currentSport);
          Map<LocalDate, List<Statistics.ActivityRow>> byDay =
              Statistics.groupActivitiesByDay(rows, ZoneId.systemDefault());
          mainHandler.post(
              () -> {
                calendarByDay = byDay;
                renderCalendar();
              });
        });
  }

  private void renderCalendar() {
    if (shownMonth == null || calendarHeatmap == null) {
      return;
    }
    calendarHeatmap.setData(Statistics.calendarDays(shownMonth, calendarByDay));
    calendarMonthLabel.setText(
        formatter.formatMonth(
            Date.from(shownMonth.atStartOfDay(ZoneId.systemDefault()).toInstant())));
  }

  private void changeMonth(int delta) {
    shownMonth = shownMonth.plusMonths(delta);
    renderCalendar();
  }

  private void openDay(int dayOfMonth) {
    List<Statistics.ActivityRow> dayRows =
        calendarByDay.get(shownMonth.withDayOfMonth(dayOfMonth));
    if (dayRows == null || dayRows.isEmpty()) {
      return;
    }
  }
```

- [ ] **Step 4: Trigger `loadCalendar()` on the Progress-tab refresh paths**

Edit four spots, each adding a `loadCalendar()` call guarded like the neighbouring `loadStatistics()` call:

1. Sport chip change handler (around line 254): inside `if (currentTab == TAB_STATISTICS_INDEX) { loadStatistics(); }` → append `loadCalendar();` on the line after `loadStatistics();` (still inside the `if`).
2. Tab reselect handler (around line 281): inside `if (tab.getPosition() == TAB_STATISTICS_INDEX) { loadStatistics(); }` → add `loadCalendar();` after it (inside the `if`).
3. `onResume()` (around line 363): after `loadStatistics();` inside `if (currentTab == TAB_STATISTICS_INDEX)` → add `loadCalendar();`.
4. `selectTab(int index)` (around line 433): inside `if (index == TAB_STATISTICS_INDEX) { loadStatistics(); }` → add `loadCalendar();`.

- [ ] **Step 5: Build + spotless + commit**

Run: `./gradlew :app:assembleLatestDebug && ./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL. Stage only `app/src/main/org/runnerup/view/HistoryFragment.java` and commit:

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: wire calendar heatmap into History Progress tab"
```

---

### Task 3: Day tap — direct open + bottom sheet; add `sport` to `ActivityRow`

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java` (`ActivityRow`, `queryActivities`, `computeMissingElevation`)
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java` (finish `openDay`, add `showDaySheet`)
- Modify: `app/test/java/org/runnerup/db/StatisticsTest.java` (assert sport is read in the existing `queryActivitiesAppliesSportFilter` test)

**Interfaces:**
- Consumes: `Statistics.ActivityRow.sport` (new), `Formatter.getDistanceDisplay(double)`, `Formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, long)`, `Formatter.formatDate(long)`, `Sport.drawableColored16Of(int)`, `Sport.colorOf(int)`, existing `openActivity(long)` (HistoryFragment.java ~line 1031), existing `dp(int)` helper (HistoryFragment.java line 838), `com.google.android.material.bottomsheet.BottomSheetDialog`.

- [ ] **Step 1: Add `sport` to `ActivityRow` and plumb it through `queryActivities`**

In `Statistics.java` `ActivityRow` (lines 29-52): add the field and constructors so existing callers keep working:

```java
  public static final class ActivityRow {
    public final long id;
    public final long startTime;
    public final double distance;
    public final Double time;
    public final Double elevationGain;
    public final int sport;

    public ActivityRow(long id, long startTime, double distance) {
      this(id, startTime, distance, null, null, -1);
    }

    public ActivityRow(long id, long startTime, double distance, Double time) {
      this(id, startTime, distance, time, null, -1);
    }

    public ActivityRow(long id, long startTime, double distance, Double time, Double elevationGain) {
      this(id, startTime, distance, time, elevationGain, -1);
    }

    public ActivityRow(
        long id, long startTime, double distance, Double time, Double elevationGain, int sport) {
      this.id = id;
      this.startTime = startTime;
      this.distance = distance;
      this.time = time;
      this.elevationGain = elevationGain;
      this.sport = sport;
    }
  }
```

In `queryActivities` (lines 192-206): extend the projection and read the sport column:

```java
    try (Cursor cursor =
        db.query(
            ACTIVITY.TABLE,
            new String[] {
              DB.PRIMARY_KEY,
              ACTIVITY.START_TIME,
              ACTIVITY.DISTANCE,
              ACTIVITY.TIME,
              ACTIVITY.ELEVATION_GAIN,
              ACTIVITY.SPORT
            },
            selection,
            args,
            null,
            null,
            ACTIVITY.START_TIME + " ASC")) {
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        Double time = cursor.isNull(3) ? null : cursor.getDouble(3);
        Double elevationGain = cursor.isNull(4) ? null : cursor.getDouble(4);
        rows.add(
            new ActivityRow(
                id, cursor.getLong(1), cursor.getDouble(2), time, elevationGain, cursor.getInt(5)));
      }
    }
```

In `computeMissingElevation` (line 245), preserve the sport across the row rebuild:

```java
      rows.set(i, new ActivityRow(row.id, row.startTime, row.distance, row.time, gain, row.sport));
```

- [ ] **Step 2: Update the mock test to cover sport**

In `StatisticsTest.java` `queryActivitiesAppliesSportFilter` (lines 268-303), add the sport column stub and assertion:

```java
    when(cursor.getInt(5)).thenReturn(0);
```

After `assertEquals(1000.0, rows.get(0).distance, 0.0);` add:

```java
    assertEquals(0, rows.get(0).sport);
```

- [ ] **Step 3: Finish `openDay` and add `showDaySheet` in `HistoryFragment`**

Replace the `openDay` stub from Task 2 (the empty-bodied method) with:

```java
  private void openDay(int dayOfMonth) {
    List<Statistics.ActivityRow> dayRows =
        calendarByDay.get(shownMonth.withDayOfMonth(dayOfMonth));
    if (dayRows == null || dayRows.isEmpty()) {
      return;
    }
    if (dayRows.size() == 1) {
      openActivity(dayRows.get(0).id);
      return;
    }
    showDaySheet(dayRows);
  }
```

Add `showDaySheet` right after `openDay`:

```java
  private void showDaySheet(List<Statistics.ActivityRow> dayRows) {
    BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
    LinearLayout content = new LinearLayout(requireContext());
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(16);
    content.setPadding(pad, dp(8), pad, dp(8));

    TextView title = new TextView(requireContext());
    title.setText(formatter.formatDate(dayRows.get(0).startTime));
    title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    title.setTextSize(16);
    title.setPadding(0, 0, 0, dp(4));
    content.addView(title);

    for (Statistics.ActivityRow row : dayRows) {
      TextView item = new TextView(requireContext());
      item.setPadding(0, dp(6), 0, dp(6));
      item.setTextSize(14);
      Drawable icon =
          AppCompatResources.getDrawable(requireContext(), Sport.drawableColored16Of(row.sport));
      if (icon != null) {
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
      }
      String timeLabel =
          row.time != null
              ? formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(row.time))
              : "";
      item.setText(
          formatter.getDistanceDisplay(row.distance)
              + (timeLabel.isEmpty() ? "" : "  ·  " + timeLabel));
      item.setOnClickListener(
          v -> {
            sheet.dismiss();
            openActivity(row.id);
          });
      content.addView(item);
    }

    sheet.setContentView(content);
    sheet.show();
  }
```

Add the import (with the other Material imports, near line 60):

```java
import com.google.android.material.bottomsheet.BottomSheetDialog;
```

Note: `openActivity(long)` launches DetailActivity via `reloadLauncher` — already wired (HistoryFragment.java:1031).

- [ ] **Step 4: Run the unit tests**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.db.StatisticsTest"`
Expected: PASS (also confirms the `sport` plumbing didn't break existing query tests).

- [ ] **Step 5: Build + spotless + commit**

Run: `./gradlew :app:assembleLatestDebug && ./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL. Stage the three files and commit:

```bash
git add app/src/main/org/runnerup/db/Statistics.java \
  app/src/main/org/runnerup/view/HistoryFragment.java \
  app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: open day runs from calendar heatmap, track sport in statistics"
```

---

### Task 4: Full verification gate

**Files:** none (verification only).

- [ ] **Step 1: Full test suite**

Run: `./gradlew test`
Expected: EXIT 0, all tests pass (includes `StatisticsTest` plus the full app/common/wear/hrdevice suites).

- [ ] **Step 2: Lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL, "Lint found no new issues (and 28 errors filtered by baseline lint-baseline.xml)" — no new issues beyond the pre-existing baseline.

- [ ] **Step 3: Spotless (idempotent)**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: `spotlessCheck` SUCCESS (no diff).

- [ ] **Step 4: Final assemble**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/latest/debug/app-latest-debug.apk`.

- [ ] **Step 5: Device smoke test (if a device is attached)**

Install the debug APK, open History → Progress, and verify:
1. A "Calendar" section renders below the week/month/year cards with the current month label and a 7-column grid with weekday header.
2. Days with runs show coloured cells with day number + distance in the preferred unit; the month's busiest day is the most intense.
3. `◀`/`▶` pages months without error; an empty month shows uncoloured day-number cells.
4. Tapping a single-run day opens DetailActivity; tapping a multi-run day opens a bottom sheet listing each run (icon + distance + time), and each row opens its activity.
5. Changing the sport chip re-colors the calendar (only that sport's days).

(If no device, skip to Step 6 and note it.)

- [ ] **Step 6: Log verification**

Record in `.superpowers/sdd/2026-09-15-calendar-heatmap/progress.md`: each task's commit hash, gate outputs, and device findings per the ledger format used by the records feature.