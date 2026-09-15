package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.runnerup.tracker.Tracker;

public class GpsWaitStepTest {

  private final Workout workout = new Workout();
  private final Tracker tracker = mock(Tracker.class);
  private final GpsWaitStep step = new GpsWaitStep();

  private Workout ready() {
    workout.setTracker(tracker);
    step.onInit(workout);
    return workout;
  }

  @Test
  public void gateIsRestedByInit() {
    ready();
    assertEquals(Intensity.RESTING, step.getIntensity());
  }

  @Test
  public void onTickFalseUntilFix() {
    ready();
    when(tracker.isGpsFixed()).thenReturn(false);
    assertFalse(step.onTick(workout));
    when(tracker.isGpsFixed()).thenReturn(true);
    assertTrue(step.onTick(workout));
  }

  @Test
  public void onStartPausesTracker() {
    ready();
    step.onStart(Scope.STEP, workout);
    verify(tracker).pause();
  }

  @Test
  public void stepIsPauseStep() {
    ready();
    assertTrue(step.isPauseStep());
  }
}
