package org.runnerup.workout;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;

public class SportTest {
  @Test
  public void treadmillHasOwnColor() {
    assertEquals(R.color.sportTreadmill, Sport.colorOf(DB.ACTIVITY.SPORT_TREADMILL));
  }

  @Test
  public void gymHasOwnColor() {
    assertEquals(R.color.sportGym, Sport.colorOf(DB.ACTIVITY.SPORT_GYM));
  }

  @Test
  public void stationaryBikeHasOwnColor() {
    assertEquals(R.color.sportStationaryBike, Sport.colorOf(DB.ACTIVITY.SPORT_STATIONARY_BIKE));
  }

  @Test
  public void existingSportColorsUnchanged() {
    assertEquals(R.color.sportRunning, Sport.colorOf(DB.ACTIVITY.SPORT_RUNNING));
    assertEquals(R.color.sportBiking, Sport.colorOf(DB.ACTIVITY.SPORT_BIKING));
    assertEquals(R.color.sportWalking, Sport.colorOf(DB.ACTIVITY.SPORT_WALKING));
    assertEquals(R.color.sportOrienteering, Sport.colorOf(DB.ACTIVITY.SPORT_ORIENTEERING));
    assertEquals(R.color.sportOther, Sport.colorOf(DB.ACTIVITY.SPORT_OTHER));
  }

  @Test
  public void newSportsHaveOwnDrawables() {
    assertEquals(
        R.drawable.sport_treadmill, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_TREADMILL));
    assertEquals(R.drawable.sport_gym, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_GYM));
    assertEquals(
        R.drawable.sport_stationary_bike,
        Sport.drawableColored16Of(DB.ACTIVITY.SPORT_STATIONARY_BIKE));
  }

  @Test
  public void existingSportDrawablesUnchanged() {
    assertEquals(R.drawable.sport_running, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_RUNNING));
    assertEquals(R.drawable.sport_biking, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_BIKING));
    assertEquals(R.drawable.sport_walking, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_WALKING));
    assertEquals(
        R.drawable.sport_orienteering, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_ORIENTEERING));
    assertEquals(R.drawable.sport_other, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_OTHER));
  }
}
