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

  private Statistics() {}

  public static int bucketCount(BucketPeriod period) {
    switch (period) {
      case DAY:
      case WEEK:
      case MONTH:
      default:
        return 12;
    }
  }

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

  public static double[] bucketize(
      List<ActivityRow> rows, BucketPeriod period, long nowSeconds, ZoneId zone) {
    double[] buckets = new double[bucketCount(period)];
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    long todayKey = key(today, period);
    for (ActivityRow row : rows) {
      if (row.startTime > nowSeconds) {
        continue;
      }
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
