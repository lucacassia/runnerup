package org.runnerup.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class ProgressChartTest {

  @Test
  public void niceMaxHugsTheData() {
    assertEquals(30.0, ChartMath.niceMax(24.0), 0.0);
    assertEquals(20.0, ChartMath.niceMax(18.0), 0.0);
    assertEquals(40.0, ChartMath.niceMax(40.0), 0.0);
    assertEquals(50.0, ChartMath.niceMax(47.0), 0.0);
    assertEquals(6.0, ChartMath.niceMax(6.0), 0.0);
    assertEquals(100.0, ChartMath.niceMax(85.0), 0.0);
    assertEquals(1.0, ChartMath.niceMax(1.0), 0.0);
    assertEquals(1.0, ChartMath.niceMax(0.0), 0.0);
    assertEquals(1.0, ChartMath.niceMax(-5.0), 0.0);
  }

  @Test
  public void plotPointsMapsValuesToPixels() {
    float[] points = ChartMath.plotPoints(new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
    assertEquals(6, points.length);
    assertArrayEquals(new float[] {50f, 100f}, pair(points, 0), 0.01f);
    assertArrayEquals(new float[] {150f, 60f}, pair(points, 2), 0.01f);
    assertArrayEquals(new float[] {250f, 20f}, pair(points, 4), 0.01f);
  }

  @Test
  public void plotPointsHandlesEmptyInput() {
    float[] points = ChartMath.plotPoints(new double[0], 10.0, 0f, 100f, 100f, 100f);
    assertEquals(0, points.length);
  }

  @Test
  public void plotBarRectsMapsValuesToRectangles() {
    float[] rects =
        ChartMath.plotBarRects(new double[] {0.0, 4.0, 8.0}, 10.0, 0f, 100f, 100f, 100f);
    assertEquals(12, rects.length);
    // i=0: center 50, half-width 30 => [20,100,80,100]
    assertArrayEquals(new float[] {20f, 100f, 80f, 100f}, rect(rects, 0), 0.01f);
    // i=1: center 150 => [120,60,180,100]
    assertArrayEquals(new float[] {120f, 60f, 180f, 100f}, rect(rects, 4), 0.01f);
    // i=2: center 250 => [220,20,280,100]
    assertArrayEquals(new float[] {220f, 20f, 280f, 100f}, rect(rects, 8), 0.01f);
  }

  @Test
  public void plotBarRectsHandlesEmptyInput() {
    float[] rects = ChartMath.plotBarRects(new double[0], 10.0, 0f, 100f, 100f, 100f);
    assertEquals(0, rects.length);
  }

  private static float[] pair(float[] points, int from) {
    return Arrays.copyOfRange(points, from, from + 2);
  }

  private static float[] rect(float[] rects, int from) {
    return Arrays.copyOfRange(rects, from, from + 4);
  }
}
