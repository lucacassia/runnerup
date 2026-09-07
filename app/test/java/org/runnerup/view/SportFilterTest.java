package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SportFilterTest {
  @Test
  public void allSportsPositionMapsToNull() {
    assertNull(SportFilter.sportForPosition(SportFilter.ALL_SPORTS_POSITION));
  }

  @Test
  public void negativePositionTreatsAsAllSports() {
    assertNull(SportFilter.sportForPosition(-1));
  }

  @Test
  public void sportPositionOffsetsByOne() {
    assertEquals(Integer.valueOf(0), SportFilter.sportForPosition(1));
    assertEquals(Integer.valueOf(7), SportFilter.sportForPosition(8));
  }

  @Test
  public void nullSportMapsToAllPosition() {
    assertEquals(SportFilter.ALL_SPORTS_POSITION, SportFilter.positionForSport(null));
  }

  @Test
  public void nonNullSportMapsToOffsetPosition() {
    assertEquals(1, SportFilter.positionForSport(0));
    assertEquals(8, SportFilter.positionForSport(7));
  }

  @Test
  public void roundTripPreservesSelection() {
    for (int position = 0; position < 9; position++) {
      assertEquals(position, SportFilter.positionForSport(SportFilter.sportForPosition(position)));
    }
  }
}
