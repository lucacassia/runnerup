# Statistics Calendar Periods Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Statistics page's three stat cards show calendar-aligned totals (this week Mon–Sun / this month / this year) and the chart show 12 daily bars, 12 weekly bars, and 12 monthly bars.

**Architecture:** Changes stay inside the existing statistics feature: `Statistics.java` (data layer), `HistoryFragment.java` + `statistics.xml` (UI), and `strings.xml` (labels). `bucketize()`/`bucketStarts()` logic is untouched — only `bucketCount()` constants and `totals()` semantics change. The load query already fetches 12 months back, which covers Jan 1 of the current year.

**Tech Stack:** Java 17, AndroidX/Material 3, SQLite (via `DBHelper`), JUnit 4 (tests in non-standard `app/test/java`).

## Global Constraints

- Signatures: `Statistics.totals(List<ActivityRow>, long nowSeconds, ZoneId)` returns `double[3]` where index 0 = this week, 1 = this month, 2 = this year. `Statistics.bucketCount(BucketPeriod)` returns 12 for all periods.
- The `key()` helper in `Statistics.java` must NOT be extended with a YEAR case and `BucketPeriod` must NOT be extended — year totals compare `date.getYear()` inline.
- Week totals count Monday through Sunday (matching the existing Monday-start `key()` logic at `Statistics.java:139`).
- A row counts only if `startTime <= nowSeconds` (future rows excluded).
- Card labels: "This week" / "This month" / "This year". Chart titles: "Last 12 days" / "Last 12 weeks" / "Last 12 months".
- Do NOT modify `DistanceChartView.java`, the month bucket logic, or the period toggle.
- No code comments added unless asked. Google Java Format via `./gradlew spotlessApply`.
- Only `StatisticsTest.java` and `HistoryFragment.java` reference these methods/strings (verified); no translations exist for the changed strings.

---

### Task 1: Calendar-aligned totals and 12-bucket counts in Statistics

**Files:**
- Modify: `app/src/main/org/runnerup/db/Statistics.java:30-58` (remove `TOTALS_DAYS`/`DAY_SECONDS`, rewrite `totals()`, rewrite `bucketCount()`)
- Test: `app/test/java/org/runnerup/db/StatisticsTest.java`

**Interfaces:**
- Consumes: existing `key(LocalDate, BucketPeriod)` helper (`Statistics.java:134`) — unchanged, handles DAY/WEEK/MONTH, weeks are Monday-aligned.
- Produces: `public static double[] totals(List<ActivityRow> rows, long nowSeconds, ZoneId zone)` (replaces `totals(List<ActivityRow>, long)`), `public static int bucketCount(BucketPeriod)` returning 12 for DAY, WEEK, and MONTH.

- [ ] **Step 1: Rewrite the totals test to the new calendar semantics**

In `app/test/java/org/runnerup/db/StatisticsTest.java`, replace the test `totalsRollingWindowsExcludeOutOfRange` (lines 31-46) with:

```java
  @Test
  public void totalsIncludeThisCalendarPeriod() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-10"), 1000.0,
            at("2026-08-14"), 2000.0,
            at("2026-08-01"), 4000.0,
            at("2026-01-01"), 8000.0);
    double[] totals = Statistics.totals(rows, now, UTC);
    assertEquals(3000.0, totals[0], 0.0);
    assertEquals(7000.0, totals[1], 0.0);
    assertEquals(15000.0, totals[2], 0.0);
  }

  @Test
  public void totalsExcludeOutsideCalendarPeriod() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-09"), 1000.0,
            at("2026-07-31"), 2000.0,
            at("2025-12-31"), 4000.0);
    double[] totals = Statistics.totals(rows, now, UTC);
    assertEquals(0.0, totals[0], 0.0);
    assertEquals(0.0, totals[1], 0.0);
    assertEquals(0.0, totals[2], 0.0);
  }

  @Test
  public void totalsExcludeFutureRows() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            now - 3600, 1000.0,
            now + 3600, 5000.0);
    double[] totals = Statistics.totals(rows, now, UTC);
    assertEquals(1000.0, totals[0], 0.0);
    assertEquals(1000.0, totals[1], 0.0);
    assertEquals(1000.0, totals[2], 0.0);
  }
```

- [ ] **Step 2: Run the totals tests to verify they fail (compile error expected)**

Run: `./gradlew test --tests "org.runnerup.db.StatisticsTest"` from repo root.
Expected: compile error — `totals(List, long)` no longer exists, so the new 3-arg call fails. (This confirms the signature change is needed; if it compiles, the old method is still present.)

- [ ] **Step 3: Rewrite `totals()` and remove dead constants**

In `app/src/main/org/runnerup/db/Statistics.java`, replace lines 30-31:

```java
  private static final int[] TOTALS_DAYS = {7, 30, 365};
  private static final long DAY_SECONDS = 86400L;
```

with nothing (delete both fields). Then replace the whole `totals` method (lines 47-58):

```java
  public static double[] totals(List<ActivityRow> rows, long nowSeconds, ZoneId zone) {
    double[] totals = new double[3];
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    long todayWeekKey = key(today, BucketPeriod.WEEK);
    long todayMonthKey = key(today, BucketPeriod.MONTH);
    int todayYear = today.getYear();
    for (ActivityRow row : rows) {
      if (row.startTime > nowSeconds) {
        continue;
      }
      LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
      if (key(date, BucketPeriod.WEEK) == todayWeekKey) {
        totals[0] += row.distance;
      }
      if (key(date, BucketPeriod.MONTH) == todayMonthKey) {
        totals[1] += row.distance;
      }
      if (date.getYear() == todayYear) {
        totals[2] += row.distance;
      }
    }
    return totals;
  }
```

- [ ] **Step 4: Change `bucketCount()` to return 12 for every period**

In `app/src/main/org/runnerup/db/Statistics.java`, replace lines 35-45:

```java
  public static int bucketCount(BucketPeriod period) {
    switch (period) {
      case DAY:
      case WEEK:
      case MONTH:
      default:
        return 12;
    }
  }
```

- [ ] **Step 5: Update the bucketize tests to 12 buckets**

In `app/test/java/org/runnerup/db/StatisticsTest.java`, replace the body of `bucketizeDayGroupsByCalendarDay` (lines 49-63) so the 4th row falls inside the now-12-day window:

```java
  @Test
  public void bucketizeDayGroupsByCalendarDay() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-14"), 1000.0,
            at("2026-08-13"), 2000.0,
            at("2026-08-03"), 500.0,
            at("2026-08-02"), 9999.0);
    double[] buckets = Statistics.bucketize(rows, BucketPeriod.DAY, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(500.0, buckets[0], 0.0);
    assertEquals(0.0, buckets[9], 0.0);
    assertEquals(2000.0, buckets[10], 0.0);
    assertEquals(1000.0, buckets[11], 0.0);
  }
```

Replace the body of `bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary` (lines 65-81):

```java
  @Test
  public void bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary() {
    long now = at("2026-01-07") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2025-12-29"), 1000.0,
            at("2025-12-22"), 2000.0,
            at("2025-11-17"), 3000.0);
    double[] buckets = Statistics.bucketize(rows, BucketPeriod.WEEK, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(0.0, buckets[0], 0.0);
    assertEquals(3000.0, buckets[4], 0.0);
    assertEquals(0.0, buckets[6], 0.0);
    assertEquals(2000.0, buckets[9], 0.0);
    assertEquals(1000.0, buckets[10], 0.0);
    assertEquals(0.0, buckets[11], 0.0);
  }
```

Replace the body of `bucketStartsAlignToDayWeekMonthStarts` (lines 101-120) with 12-day/12-week counts:

```java
  @Test
  public void bucketStartsAlignToDayWeekMonthStarts() {
    long now = at("2026-08-14") + 12 * 3600;
    long[] days = Statistics.bucketStarts(BucketPeriod.DAY, now, UTC);
    assertEquals(12, days.length);
    assertEquals(at("2026-08-14"), days[11]);
    assertEquals(at("2026-08-03"), days[0]);
    long[] weeks = Statistics.bucketStarts(BucketPeriod.WEEK, now, UTC);
    assertEquals(12, weeks.length);
    assertEquals(at("2026-08-10"), weeks[11]);
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
```

Replace the body of `bucketCountMatchesPeriods` (lines 122-127):

```java
  @Test
  public void bucketCountMatchesPeriods() {
    assertEquals(12, Statistics.bucketCount(BucketPeriod.DAY));
    assertEquals(12, Statistics.bucketCount(BucketPeriod.WEEK));
    assertEquals(12, Statistics.bucketCount(BucketPeriod.MONTH));
  }
```

- [ ] **Step 6: Run the full Statistics test class**

Run: `./gradlew test --tests "org.runnerup.db.StatisticsTest"`
Expected: 8 tests PASS — `totalsIncludeThisCalendarPeriod`, `totalsExcludeOutsideCalendarPeriod`, `totalsExcludeFutureRows`, `bucketizeDayGroupsByCalendarDay`, `bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary`, `bucketizeMonthGroupsByCalendarMonth` (unchanged), `bucketStartsAlignToDayWeekMonthStarts`, `bucketCountMatchesPeriods`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/org/runnerup/db/Statistics.java app/test/java/org/runnerup/db/StatisticsTest.java
git commit -m "feat: calendar-aligned statistics totals and 12-bucket chart"
```

---

### Task 2: UI labels, chart titles, and the totals call site

**Files:**
- Modify: `common/src/main/res/values/strings.xml:262-270`
- Modify: `app/res/layout/statistics.xml:37,69,100,154`
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java:257,308,313`

**Interfaces:**
- Consumes: `Statistics.totals(List<ActivityRow>, long, ZoneId)` from Task 1.
- Produces: card labels "This week"/"This month"/"This year", chart titles "Last 12 days"/"Last 12 weeks"/"Last 12 months".

- [ ] **Step 1: Rename the string resources**

In `common/src/main/res/values/strings.xml`, replace the block at lines 262-270:

```xml
    <string name="Statistics_this_week">This week</string>
    <string name="Statistics_this_month">This month</string>
    <string name="Statistics_this_year">This year</string>
    <string name="Statistics_day">Day</string>
    <string name="Statistics_week">Week</string>
    <string name="Statistics_month">Month</string>
    <string name="Statistics_last_12_days">Last 12 days</string>
    <string name="Statistics_last_12_weeks">Last 12 weeks</string>
    <string name="Statistics_last_12_months">Last 12 months</string>
```

- [ ] **Step 2: Update the layout string references**

In `app/res/layout/statistics.xml`:
- line 37: `android:text="@string/Statistics_7_days"` → `android:text="@string/Statistics_this_week"`
- line 69: `android:text="@string/Statistics_30_days"` → `android:text="@string/Statistics_this_month"`
- line 100: `android:text="@string/Statistics_365_days"` → `android:text="@string/Statistics_this_year"`
- line 154: `android:text="@string/Statistics_last_14_days"` → `android:text="@string/Statistics_last_12_days"`

- [ ] **Step 3: Update HistoryFragment — totals call and chart titles**

In `app/src/main/org/runnerup/view/HistoryFragment.java`:
- line 257: `double[] totals = Statistics.totals(rows, now);` → `double[] totals = Statistics.totals(rows, now, ZoneId.systemDefault());` (`ZoneId` is already imported and used at line 254.)
- line 308: `R.string.Statistics_last_8_weeks` → `R.string.Statistics_last_12_weeks`
- line 313: `R.string.Statistics_last_14_days` → `R.string.Statistics_last_12_days`

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/res/values/strings.xml app/res/layout/statistics.xml app/src/main/org/runnerup/view/HistoryFragment.java
git commit -m "feat: this week/month/year labels and last-12-days/weeks chart titles"
```

---

### Task 3: Full verification and device smoke test

**Files:**
- No source changes. Runs the AGENTS.md gates and confirms on device.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew test`
Expected: all pass (93+ tests; the new totals/bucket tests included).

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: no NEW issues. The 25 pre-existing baseline issues in `app/lint-baseline.xml` and the known `AppBundleLocaleChanges` at `app/src/main/org/runnerup/view/Formatter.java:817` are acceptable; do not fix them.

- [ ] **Step 3: Run spotless**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: PASS.

- [ ] **Step 4: Build both map variants**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Install and smoke test on device**

Device: serial `025b46e24edcbca6`, package `org.runnerup.debug`.

```bash
adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb shell am force-stop org.runnerup.debug
adb shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Then in the app: open History → Statistics tab. Verify:
- Card labels read "This week" / "This month" / "This year" and show non-zero values if activities exist in those periods.
- Chart title reads "Last 12 days"; toggle to Week → "Last 12 weeks", 12 bars; toggle to Month → "Last 12 months", 12 bars.
- Day view shows exactly 12 bars.

