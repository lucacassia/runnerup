package org.runnerup.view;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.BooleanSupplier;

public class HoldToStopListener implements View.OnTouchListener {
  private final View view;
  private final StopProgressDrawable drawable;
  private final BooleanSupplier stopMode;
  private final Runnable onComplete;
  private final Runnable onHint;
  private final HoldState state;
  private final Runnable frame = this::onFrame;

  public HoldToStopListener(
      View view,
      StopProgressDrawable drawable,
      long holdDurationMillis,
      BooleanSupplier stopMode,
      Runnable onComplete,
      Runnable onHint) {
    this.view = view;
    this.drawable = drawable;
    this.stopMode = stopMode;
    this.onComplete = onComplete;
    this.onHint = onHint;
    this.state = new HoldState(holdDurationMillis);
  }

  private void onFrame() {
    if (!tick(SystemClock.uptimeMillis()) && state.isPressing()) {
      view.postOnAnimation(frame);
    }
  }

  @Override
  @SuppressLint("ClickableViewAccessibility")
  public boolean onTouch(View v, MotionEvent event) {
    return onEvent(event.getActionMasked(), SystemClock.uptimeMillis());
  }

  public void cancel() {
    stopAnimation();
    state.onCancel();
    drawable.setProgress(0f);
    view.invalidate();
  }

  boolean onEvent(int action, long nowMillis) {
    if (!stopMode.getAsBoolean()) {
      return false;
    }
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (state.onDown(nowMillis)) {
          drawable.setProgress(0f);
          view.invalidate();
          startAnimation();
        }
        return true;
      case MotionEvent.ACTION_UP:
        finishUp(nowMillis);
        return true;
      case MotionEvent.ACTION_CANCEL:
        cancel();
        return true;
      default:
        return true;
    }
  }

  boolean tick(long nowMillis) {
    if (!state.isPressing()) {
      return false;
    }
    drawable.setProgress(state.progress(nowMillis));
    view.invalidate();
    if (state.onTick(nowMillis) == HoldState.Result.COMPLETE) {
      stopAnimation();
      drawable.setProgress(0f);
      onComplete.run();
      return true;
    }
    return false;
  }

  private void startAnimation() {
    if (state.isPressing()) {
      view.postOnAnimation(frame);
    }
  }

  private void stopAnimation() {
    view.removeCallbacks(frame);
  }

  private void finishUp(long nowMillis) {
    stopAnimation();
    if (state.onUp(nowMillis) == HoldState.Result.EARLY_RELEASE) {
      onHint.run();
    }
    drawable.setProgress(0f);
    view.invalidate();
  }
}
