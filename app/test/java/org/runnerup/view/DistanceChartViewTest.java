package org.runnerup.view;

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
}
