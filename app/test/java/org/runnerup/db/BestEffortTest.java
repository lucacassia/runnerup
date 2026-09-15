package org.runnerup.db;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BestEffortTest {

  private static BestEffort.Points points(double[] distM, long[] timesMs) {
    return new BestEffort.Points(distM, timesMs.clone(), timesMs.clone());
  }

  @Test
  public void constantSpeedWholeRunMatchesExpected() {
    double[] dist = new double[11];
    long[] t = new long[11];
    for (int i = 0; i <= 10; i++) {
      dist[i] = i * 500.0;
      t[i] = i * 60_000L;
    }
    assertEquals(600_000L, BestEffort.bestEffort(points(dist, t), 5000.0));
  }

  @Test
  public void fasterInteriorStretchBeatsWholeRun() {
    double[] dist = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};
    long[] elapsedSec = {0, 10, 20, 24, 28, 32, 36, 40, 44, 54, 64};
    long[] t = new long[elapsedSec.length];
    for (int i = 0; i < elapsedSec.length; i++) {
      t[i] = elapsedSec[i] * 1000L;
    }
    // km1-2 slow (10s), km3-8 fast (4s), km9-10 slow: fastest 5K = km2->km7 = 20s
    assertEquals(20_000L, BestEffort.bestEffort(points(dist, t), 5000.0));
  }

  @Test
  public void runShorterThanTargetReturnsMinusOne() {
    double[] dist = {0, 1000, 2000, 3000};
    long[] t = {0, 60_000, 120_000, 180_000};
    assertEquals(-1L, BestEffort.bestEffort(points(dist, t), 5000.0));
  }

  @Test
  public void windowSpanningLargeGapRejected() {
    double[] dist = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};
    long[] elapsedMs = {0, 20000, 40000, 60000, 80000, 100000, 104000, 108000, 112000, 116000, 120000};
    long[] timeMs = {0, 20000, 40000, 60000, 80000, 100000, 104000, 108000, 228000, 232000, 236000};
    // any 5K window crossing the 120s wall gap (k7->k8) is rejected; best valid = k2->k7 = 68s
    assertEquals(68_000L, BestEffort.bestEffort(new BestEffort.Points(dist, timeMs, elapsedMs), 5000.0));
  }

  @Test
  public void interpolationHitsExactDistance() {
    double[] dist = {0, 1000, 2000};
    long[] t = {0, 100_000, 200_000};
    assertEquals(50_000L, BestEffort.bestEffort(points(dist, t), 500.0));
  }
}
