package org.runnerup.view;

public final class SportFilter {
  public static final int ALL_SPORTS_POSITION = 0;

  private SportFilter() {}

  public static Integer sportForPosition(int position) {
    return position <= ALL_SPORTS_POSITION ? null : position - 1;
  }

  public static int positionForSport(Integer sport) {
    return sport == null ? ALL_SPORTS_POSITION : sport + 1;
  }
}
