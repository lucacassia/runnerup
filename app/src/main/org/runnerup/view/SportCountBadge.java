package org.runnerup.view;

public final class SportCountBadge {
  private SportCountBadge() {}

  public static final class Badge {
    public final int count;

    public Badge(int count) {
      this.count = count;
    }
  }

  public static int grandTotal(int[] counts) {
    int total = 0;
    for (int count : counts) {
      total += count;
    }
    return total;
  }

  public static Badge forSport(Integer sport, int[] counts) {
    if (sport == null) {
      return new Badge(grandTotal(counts));
    }
    if (sport < 0 || sport >= counts.length) {
      return null;
    }
    int count = counts[sport];
    return count > 0 ? new Badge(count) : null;
  }
}
