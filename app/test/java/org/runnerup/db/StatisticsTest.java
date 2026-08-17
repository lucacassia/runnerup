package org.runnerup.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.runnerup.db.Statistics.ActivityRow;
import org.runnerup.db.Statistics.BucketPeriod;
import org.runnerup.db.Statistics.Metric;

public class StatisticsTest {

  private static final ZoneId UTC = ZoneId.of("UTC");

  private static long at(String date) {
    return LocalDate.parse(date).atStartOfDay(UTC).toEpochSecond();
  }

  private static long nextId = 1;

  private static List<ActivityRow> rows(Object... pairs) {
    List<ActivityRow> rows = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      rows.add(new ActivityRow(nextId++, (Long) pairs[i], (Double) pairs[i + 1]));
    }
    return rows;
  }

  @Test
  public void totalsIncludeThisCalendarPeriod() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-10"), 1000.0,
            at("2026-08-14"), 2000.0,
            at("2026-08-01"), 4000.0,
            at("2026-01-01"), 8000.0);
    double[] totals = Statistics.totals(rows, Metric.DISTANCE, now, UTC);
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
    double[] totals = Statistics.totals(rows, Metric.DISTANCE, now, UTC);
    assertEquals(0.0, totals[0], 0.0);
    assertEquals(1000.0, totals[1], 0.0);
    assertEquals(3000.0, totals[2], 0.0);
  }

  @Test
  public void totalsExcludeFutureRows() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = rows(now - 3600, 1000.0, now + 3600, 5000.0);
    double[] totals = Statistics.totals(rows, Metric.DISTANCE, now, UTC);
    assertEquals(1000.0, totals[0], 0.0);
    assertEquals(1000.0, totals[1], 0.0);
    assertEquals(1000.0, totals[2], 0.0);
  }

  @Test
  public void bucketizeDayGroupsByCalendarDay() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2026-08-14"), 1000.0,
            at("2026-08-13"), 2000.0,
            at("2026-08-03"), 500.0,
            at("2026-08-02"), 9999.0);
    double[] buckets = Statistics.bucketize(rows, Metric.DISTANCE, BucketPeriod.DAY, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(500.0, buckets[0], 0.0);
    assertEquals(0.0, buckets[9], 0.0);
    assertEquals(2000.0, buckets[10], 0.0);
    assertEquals(1000.0, buckets[11], 0.0);
  }

  @Test
  public void bucketizeWeekGroupsByCalendarWeekAcrossYearBoundary() {
    long now = at("2026-01-07") + 12 * 3600;
    List<ActivityRow> rows =
        rows(
            at("2025-12-29"), 1000.0,
            at("2025-12-22"), 2000.0,
            at("2025-11-17"), 3000.0);
    double[] buckets = Statistics.bucketize(rows, Metric.DISTANCE, BucketPeriod.WEEK, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(0.0, buckets[0], 0.0);
    assertEquals(3000.0, buckets[4], 0.0);
    assertEquals(0.0, buckets[6], 0.0);
    assertEquals(2000.0, buckets[9], 0.0);
    assertEquals(1000.0, buckets[10], 0.0);
    assertEquals(0.0, buckets[11], 0.0);
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
    double[] buckets = Statistics.bucketize(rows, Metric.DISTANCE, BucketPeriod.MONTH, now, UTC);
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

  @Test
  public void bucketCountMatchesPeriods() {
    assertEquals(12, Statistics.bucketCount(BucketPeriod.DAY));
    assertEquals(12, Statistics.bucketCount(BucketPeriod.WEEK));
    assertEquals(12, Statistics.bucketCount(BucketPeriod.MONTH));
  }

  @Test
  public void totalsTimeSumsActivityDuration() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 1800.0)); // 30 min
    rows.add(new ActivityRow(2, at("2026-08-03"), 2000.0, 3600.0)); // 60 min
    double[] totals = Statistics.totals(rows, Metric.TIME, now, UTC);
    assertEquals(1800.0, totals[0], 0.0);
    assertEquals(5400.0, totals[1], 0.0);
    assertEquals(5400.0, totals[2], 0.0);
  }

  @Test
  public void totalsElevationSumsGain() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, null, 50.0));
    rows.add(new ActivityRow(2, at("2026-08-10"), 2000.0, null, 120.0));
    double[] totals = Statistics.totals(rows, Metric.ELEVATION_GAIN, now, UTC);
    assertEquals(170.0, totals[0], 0.0);
    assertEquals(170.0, totals[1], 0.0);
    assertEquals(170.0, totals[2], 0.0);
  }

  @Test
  public void totalsElevationSkipsNull() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, null, (Double) null));
    rows.add(new ActivityRow(2, at("2026-08-03"), 2000.0, null, 100.0));
    double[] totals = Statistics.totals(rows, Metric.ELEVATION_GAIN, now, UTC);
    assertEquals(0.0, totals[0], 0.0);
    assertEquals(100.0, totals[1], 0.0);
  }

  @Test
  public void bucketizeTimeGroupsByMetricValue() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, 1800.0));
    rows.add(new ActivityRow(2, at("2026-08-13"), 2000.0, 3600.0));
    double[] buckets = Statistics.bucketize(rows, Metric.TIME, BucketPeriod.DAY, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(1800.0, buckets[11], 0.0);
    assertEquals(3600.0, buckets[10], 0.0);
  }

  @Test
  public void bucketizeElevationGroupsByMetricValue() {
    long now = at("2026-08-14") + 12 * 3600;
    List<ActivityRow> rows = new ArrayList<>();
    rows.add(new ActivityRow(1, at("2026-08-14"), 1000.0, null, 50.0));
    rows.add(new ActivityRow(2, at("2026-08-13"), 2000.0, null, 120.0));
    double[] buckets =
        Statistics.bucketize(rows, Metric.ELEVATION_GAIN, BucketPeriod.DAY, now, UTC);
    assertEquals(12, buckets.length);
    assertEquals(50.0, buckets[11], 0.0);
    assertEquals(120.0, buckets[10], 0.0);
  }
}
