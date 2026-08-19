package org.runnerup.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DistanceChartViewTest {

  @Test
  public void niceMaxHugsTheData() {
    assertEquals(30.0, DistanceChartView.niceMax(24.0), 0.0);
    assertEquals(20.0, DistanceChartView.niceMax(18.0), 0.0);
    assertEquals(40.0, DistanceChartView.niceMax(40.0), 0.0);
    assertEquals(50.0, DistanceChartView.niceMax(47.0), 0.0);
    assertEquals(6.0, DistanceChartView.niceMax(6.0), 0.0);
    assertEquals(100.0, DistanceChartView.niceMax(85.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(1.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(0.0), 0.0);
    assertEquals(1.0, DistanceChartView.niceMax(-5.0), 0.0);
  }

  @Test
  public void plotPointsMapsValuesToPixels() {
    float[][] points =
        DistanceChartView.plotPoints(new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
    assertEquals(3, points.length);
    assertArrayEquals(new float[] {50f, 100f}, points[0], 0.01f);
    assertArrayEquals(new float[] {150f, 60f}, points[1], 0.01f);
    assertArrayEquals(new float[] {250f, 20f}, points[2], 0.01f);
  }

  @Test
  public void plotPointsHandlesEmptyInput() {
    float[][] points = DistanceChartView.plotPoints(new double[0], 10.0, 0f, 100f, 100f, 100f);
    assertEquals(0, points.length);
  }
}
