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
    assertEquals(1000.0, totals[0], 0.0);
    assertEquals(7000.0, totals[1], 0.0);
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
