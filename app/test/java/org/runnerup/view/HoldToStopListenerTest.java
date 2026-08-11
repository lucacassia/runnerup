package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.view.MotionEvent;
import android.view.View;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class HoldToStopListenerTest {
  private static final long DURATION = 1500L;
  private static final long T0 = 10000L;

  private final View view = mock(View.class);
  private final StopProgressDrawable drawable = mock(StopProgressDrawable.class);
  private final AtomicInteger completeCalls = new AtomicInteger();
  private final AtomicInteger hintCalls = new AtomicInteger();

  private HoldToStopListener listener(boolean stopMode) {
    return new HoldToStopListener(
        view,
        drawable,
        DURATION,
        () -> stopMode,
        completeCalls::incrementAndGet,
        hintCalls::incrementAndGet);
  }

  @Test
  public void runningModeFallsThroughToClick() {
    HoldToStopListener l = listener(false);
    assertFalse(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertFalse(l.onEvent(MotionEvent.ACTION_UP, T0 + 10L));
  }

  @Test
  public void stopModeConsumesEvents() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 100L));
  }

  @Test
  public void holdToCompletionFiresOnCompleteOnceAndResets() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
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
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertFalse(l.tick(T0 + 750L));
    verify(drawable).setProgress(0.5f);
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 900L));
    assertEquals(1, hintCalls.get());
    assertEquals(0, completeCalls.get());
  }

  @Test
  public void upAfterCompleteDoesNotHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.tick(T0 + DURATION));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }

  @Test
  public void cancelResetsWithoutHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_CANCEL, T0 + 100L));
    assertEquals(0, hintCalls.get());
    assertEquals(0, completeCalls.get());
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 200L));
  }

  @Test
  public void duplicateDownIsIgnoredWithoutSideEffects() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 10L));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION));
    assertEquals(0, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }
}
