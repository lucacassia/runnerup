package org.runnerup.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.ACTIVITY;

public final class Statistics {

  public enum BucketPeriod {
    DAY,
    WEEK,
    MONTH
  }

  public enum Metric {
    DISTANCE,
    TIME,
    ELEVATION_GAIN
  }

  public static final class ActivityRow {
    public final long id;
    public final long startTime;
    public final double distance;
    public final Double time;
    public final Double elevationGain;

    public ActivityRow(long id, long startTime, double distance) {
      this(id, startTime, distance, null, null);
    }

    public ActivityRow(long id, long startTime, double distance, Double time) {
      this(id, startTime, distance, time, null);
    }

    public ActivityRow(
        long id, long startTime, double distance, Double time, Double elevationGain) {
      this.id = id;
      this.startTime = startTime;
      this.distance = distance;
      this.time = time;
      this.elevationGain = elevationGain;
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

  public static double[] totals(
      List<ActivityRow> rows, Metric metric, long nowSeconds, ZoneId zone) {
    double[] totals = new double[3];
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    long todayWeekKey = key(today, BucketPeriod.WEEK);
    long todayMonthKey = key(today, BucketPeriod.MONTH);
    int todayYear = today.getYear();
    for (ActivityRow row : rows) {
      if (row.startTime > nowSeconds) {
        continue;
      }
      double value = metricValue(row, metric);
      if (Double.isNaN(value)) {
        continue;
      }
      LocalDate date = Instant.ofEpochSecond(row.startTime).atZone(zone).toLocalDate();
      if (key(date, BucketPeriod.WEEK) == todayWeekKey) {
        totals[0] += value;
      }
      if (key(date, BucketPeriod.MONTH) == todayMonthKey) {
        totals[1] += value;
      }
      if (date.getYear() == todayYear) {
        totals[2] += value;
      }
    }
    return totals;
  }

  private static double metricValue(ActivityRow row, Metric metric) {
    switch (metric) {
      case DISTANCE:
        return row.distance;
      case TIME:
        return row.time != null ? row.time : Double.NaN;
      case ELEVATION_GAIN:
        return row.elevationGain != null ? row.elevationGain : Double.NaN;
      default:
        throw new IllegalArgumentException("unknown metric " + metric);
    }
  }

  public static double[] bucketize(
      List<ActivityRow> rows, Metric metric, BucketPeriod period, long nowSeconds, ZoneId zone) {
    double[] buckets = new double[bucketCount(period)];
    LocalDate today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate();
    long todayKey = key(today, period);
    for (ActivityRow row : rows) {
      if (row.startTime > nowSeconds) {
        continue;
      }
      double value = metricValue(row, metric);
      if (Double.isNaN(value)) {
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
        buckets[buckets.length - 1 - offset] += value;
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
            new String[] {
              DB.PRIMARY_KEY,
              ACTIVITY.START_TIME,
              ACTIVITY.DISTANCE,
              ACTIVITY.TIME,
              ACTIVITY.ELEVATION_GAIN
            },
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
        long id = cursor.getLong(0);
        Double time = cursor.isNull(3) ? null : cursor.getDouble(3);
        Double elevationGain = cursor.isNull(4) ? null : cursor.getDouble(4);
        rows.add(new ActivityRow(id, cursor.getLong(1), cursor.getDouble(2), time, elevationGain));
      }
    }
    return rows;
  }

  public static void computeMissingElevation(SQLiteDatabase db, List<ActivityRow> rows) {
    for (int i = 0; i < rows.size(); i++) {
      ActivityRow row = rows.get(i);
      if (row.elevationGain != null) {
        continue;
      }
      double gain = computeElevationGainForActivity(db, row.id);
      rows.set(i, new ActivityRow(row.id, row.startTime, row.distance, row.time, gain));
      ContentValues cv = new ContentValues();
      cv.put(ACTIVITY.ELEVATION_GAIN, gain);
      db.update(ACTIVITY.TABLE, cv, DB.PRIMARY_KEY + " = ?", new String[] {Long.toString(row.id)});
    }
  }

  private static double computeElevationGainForActivity(SQLiteDatabase db, long activityId) {
    double gain = 0;
    Double prevAlt = null;
    try (Cursor cursor =
        db.query(
            "location",
            new String[] {DB.LOCATION.ALTITUDE},
            DB.LOCATION.ACTIVITY
                + " = ? AND "
                + DB.LOCATION.ALTITUDE
                + " IS NOT NULL ORDER BY "
                + DB.LOCATION.TIME
                + " ASC",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null)) {
      while (cursor.moveToNext()) {
        double alt = cursor.getDouble(0);
        if (prevAlt != null) {
          double delta = alt - prevAlt;
          if (delta > 0) {
            gain += delta;
          }
        }
        prevAlt = alt;
      }
    }
    return gain;
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
