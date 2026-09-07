package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.view.SportCountBadge.Badge;

public class SportCountBadgeTest {
  @Test
  public void allSportsShowsGrandTotal() {
    Badge badge = SportCountBadge.forSport(null, new int[] {30, 1});
    assertEquals(31, badge.count);
  }

  @Test
  public void sportShowsItsCount() {
    Badge badge = SportCountBadge.forSport(0, new int[] {30, 1});
    assertEquals(30, badge.count);
  }

  @Test
  public void zeroCountSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(1, new int[] {30, 0}));
  }

  @Test
  public void negativeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(-1, new int[] {30}));
  }

  @Test
  public void outOfRangeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(8, new int[] {30}));
  }

  @Test
  public void grandTotalSumsAllEntries() {
    assertEquals(31, SportCountBadge.grandTotal(new int[] {30, 1, 0}));
  }

  @Test
  public void grandTotalOfEmptyIsZero() {
    assertEquals(0, SportCountBadge.grandTotal(new int[0]));
  }
}
