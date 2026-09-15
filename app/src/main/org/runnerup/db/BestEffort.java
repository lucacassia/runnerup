package org.runnerup.db;

public final class BestEffort {

  /** Maximum wall-clock gap (ms) between consecutive points a window may span. */
  public static final long GAP_MS = 60_000L;

  public static final class Points {
    public final double[] distanceM;
    public final long[] timeMs;
    public final long[] elapsedMs;

    public Points(double[] distanceM, long[] timeMs, long[] elapsedMs) {
      this.distanceM = distanceM;
      this.timeMs = timeMs;
      this.elapsedMs = elapsedMs;
    }
  }

  private BestEffort() {}

  public static long bestEffort(Points points, double targetM) {
    double[] dist = points.distanceM;
    if (dist.length < 2 || dist[dist.length - 1] < targetM) {
      return -1L;
    }
    long best = -1L;
    int start = 0;
    for (int end = 0; end < dist.length; end++) {
      if (dist[end] < targetM) {
        continue;
      }
      double threshold = dist[end] - targetM;
      while (start + 1 < dist.length && dist[start + 1] <= threshold) {
        start++;
      }
      if (start + 1 >= dist.length) {
        break;
      }
      double span = dist[start + 1] - dist[start];
      if (span <= 0) {
        continue;
      }
      double frac = (threshold - dist[start]) / span;
      long startElapsed =
          points.elapsedMs[start]
              + Math.round(frac * (points.elapsedMs[start + 1] - points.elapsedMs[start]));
      boolean overGap = false;
      for (int k = start; k < end; k++) {
        long wallGap = points.timeMs[k + 1] - points.timeMs[k];
        long runGap = points.elapsedMs[k + 1] - points.elapsedMs[k];
        if (wallGap - runGap > GAP_MS) {
          overGap = true;
          break;
        }
      }
      if (overGap) {
        continue;
      }
      long elapsed = points.elapsedMs[end] - startElapsed;
      if (best < 0 || elapsed < best) {
        best = elapsed;
      }
    }
    return best;
  }
}