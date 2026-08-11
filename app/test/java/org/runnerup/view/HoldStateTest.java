package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.view.HoldState.Result;

public class HoldStateTest {
  private static final long DURATION = 1500L;
  private static final long T0 = 10000L;

  private final HoldState state = new HoldState(DURATION);

  @Test
  public void idleState() {
    assertFalse(state.isPressing());
    assertEquals(0f, state.progress(T0), 0f);
    assertEquals(Result.NONE, state.onTick(T0));
    assertEquals(Result.NONE, state.onUp(T0));
  }

  @Test
  public void onDownStartsHold() {
    assertTrue(state.onDown(T0));
    assertTrue(state.isPressing());
  }

  @Test
  public void doubleDownIsRejected() {
    assertTrue(state.onDown(T0));
    assertFalse(state.onDown(T0 + 1L));
  }

  @Test
  public void progressIsElapsedOverDuration() {
    state.onDown(T0);
    assertEquals(0f, state.progress(T0), 0f);
    assertEquals(0.5f, state.progress(T0 + 750L), 0.0001f);
    assertEquals(1f, state.progress(T0 + DURATION), 0f);
  }

  @Test
  public void progressIsClampedToUnitRange() {
    state.onDown(T0);
    assertEquals(0f, state.progress(T0 - 100L), 0f);
    assertEquals(1f, state.progress(T0 + DURATION * 3L), 0f);
  }

  @Test
  public void tickCompletesExactlyAtDuration() {
    state.onDown(T0);
    assertEquals(Result.NONE, state.onTick(T0 + DURATION - 1L));
    assertEquals(Result.COMPLETE, state.onTick(T0 + DURATION));
    assertFalse(state.isPressing());
  }

  @Test
  public void tickAfterCompleteReturnsNone() {
    state.onDown(T0);
    state.onTick(T0 + DURATION);
    assertEquals(Result.NONE, state.onTick(T0 + DURATION + 1L));
  }

  @Test
  public void earlyReleaseIsEarly() {
    state.onDown(T0);
    assertEquals(Result.EARLY_RELEASE, state.onUp(T0 + 500L));
    assertFalse(state.isPressing());
  }

  @Test
  public void releaseAfterCompleteIsNotEarly() {
    state.onDown(T0);
    state.onTick(T0 + DURATION);
    assertEquals(Result.NONE, state.onUp(T0 + DURATION));
  }

  @Test
  public void cancelResetsWithoutHintEvent() {
    state.onDown(T0);
    state.onCancel();
    assertEquals(Result.NONE, state.onUp(T0 + 10L));
    assertTrue(state.onDown(T0 + 20L));
  }
}
