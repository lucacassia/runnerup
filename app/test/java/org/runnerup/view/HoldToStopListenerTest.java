package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.MotionEvent;
import android.view.View;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;

public class HoldToStopListenerTest {
  private static final long DURATION = 1500L;
  private static final long T0 = 10000L;

  private final View view = mock(View.class);
  private final StopProgressDrawable drawable = mock(StopProgressDrawable.class);
  private final AtomicInteger completeCalls = new AtomicInteger();
  private final AtomicInteger hintCalls = new AtomicInteger();

  private HoldToStopListener listener(boolean stopMode) {
    return listener(() -> stopMode);
  }

  private HoldToStopListener listener(BooleanSupplier stopMode) {
    return new HoldToStopListener(
        view,
        drawable,
        DURATION,
        stopMode,
        completeCalls::incrementAndGet,
        hintCalls::incrementAndGet);
  }

  @Test
  public void runningModeFallsThroughToClick() {
    HoldToStopListener l = listener(false);
    assertFalse(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertFalse(l.onEvent(MotionEvent.ACTION_UP, T0 + 10L, 50f, 50f));
  }

  @Test
  public void stopModeConsumesEvents() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 100L, 50f, 50f));
  }

  @Test
  public void holdToCompletionFiresOnCompleteOnceAndResets() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertFalse(l.tick(T0 + DURATION - 1L));
    assertTrue(l.tick(T0 + DURATION));
    assertFalse(l.tick(T0 + DURATION + 1L));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
    verify(drawable).setProgress(1f);
    verify(drawable, times(2)).setProgress(0f);
  }

  @Test
  public void earlyReleaseFiresHintOnceAndResets() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertFalse(l.tick(T0 + 750L));
    verify(drawable).setProgress(0.5f);
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 900L, 50f, 50f));
    assertEquals(1, hintCalls.get());
    assertEquals(0, completeCalls.get());
  }

  @Test
  public void upAfterCompleteDoesNotHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.tick(T0 + DURATION));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION, 50f, 50f));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }

  @Test
  public void cancelResetsWithoutHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_CANCEL, T0 + 100L, 50f, 50f));
    assertEquals(0, hintCalls.get());
    assertEquals(0, completeCalls.get());
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 200L, 50f, 50f));
  }

  @Test
  public void duplicateDownIsIgnoredWithoutSideEffects() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 10L, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION, 50f, 50f));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }

  @Test
  public void resumeMidHoldCancelsWithoutCompleting() {
    AtomicBoolean stop = new AtomicBoolean(true);
    HoldToStopListener l = listener(stop::get);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertFalse(l.tick(T0 + 500L));
    stop.set(false);
    assertTrue(l.tick(T0 + 500L));
    assertEquals(0, completeCalls.get());
    assertEquals(0, hintCalls.get());
    assertFalse(l.tick(T0 + DURATION + 1000L));
    assertEquals(0, completeCalls.get());
    assertEquals(0, hintCalls.get());
    assertFalse(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION + 2000L, 50f, 50f));
    assertEquals(0, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }

  @Test
  public void moveInsideBoundsKeepsHold() {
    when(view.getWidth()).thenReturn(100);
    when(view.getHeight()).thenReturn(100);
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_MOVE, T0 + 500L, 60f, 60f));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 900L, 60f, 60f));
    assertEquals(1, hintCalls.get());
    assertEquals(0, completeCalls.get());
  }

  @Test
  public void moveOutsideBoundsCancelsWithoutHint() {
    when(view.getWidth()).thenReturn(100);
    when(view.getHeight()).thenReturn(100);
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_MOVE, T0 + 500L, 150f, 50f));
    assertEquals(0, hintCalls.get());
    assertEquals(0, completeCalls.get());
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 900L, 150f, 50f));
    assertEquals(0, hintCalls.get());
    assertEquals(0, completeCalls.get());
  }

  @Test
  public void upAtOrAfterDurationWithoutTickCompletes() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0, 50f, 50f));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION, 50f, 50f));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }
}
