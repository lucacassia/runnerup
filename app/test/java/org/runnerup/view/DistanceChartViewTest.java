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

  @Test
  public void plotBarRectsMapsValuesToRectangles() {
    float[][] rects =
        DistanceChartView.plotBarRects(new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
    assertEquals(3, rects.length);
    // i=0: center 50, half-width 30 => [20,100,80,100]
    assertArrayEquals(new float[] {20f, 100f, 80f, 100f}, rects[0], 0.01f);
    // i=1: center 150 => [120,60,180,100]
    assertArrayEquals(new float[] {120f, 60f, 180f, 100f}, rects[1], 0.01f);
    // i=2: center 250 => [220,20,280,100]
    assertArrayEquals(new float[] {220f, 20f, 280f, 100f}, rects[2], 0.01f);
  }

  @Test
  public void plotBarRectsHandlesEmptyInput() {
    float[][] rects = DistanceChartView.plotBarRects(new double[0], 10.0, 0f, 100f, 100f, 100f);
    assertEquals(0, rects.length);
  }
}
