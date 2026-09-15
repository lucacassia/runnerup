package org.runnerup.db;

public final class RecordUtils {

  public static final double DISTANCE_BAND_TOLERANCE = 0.05;

  private RecordUtils() {}

  public static double bandLower(double standard) {
    return standard * (1.0 - DISTANCE_BAND_TOLERANCE);
  }

  public static double bandUpper(double standard) {
    return standard * (1.0 + DISTANCE_BAND_TOLERANCE);
  }
}
