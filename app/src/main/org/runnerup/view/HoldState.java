package org.runnerup.view;

public final class HoldState {
  public enum Result {
    NONE,
    COMPLETE,
    EARLY_RELEASE
  }

  private final long holdDurationMillis;
  private long downMillis = -1L;

  public HoldState(long holdDurationMillis) {
    this.holdDurationMillis = holdDurationMillis;
  }

  public boolean onDown(long nowMillis) {
    if (downMillis >= 0L) {
      return false;
    }
    downMillis = nowMillis;
    return true;
  }

  public boolean isPressing() {
    return downMillis >= 0L;
  }

  public float progress(long nowMillis) {
    if (downMillis < 0L) {
      return 0f;
    }
    float p = (float) (nowMillis - downMillis) / (float) holdDurationMillis;
    if (p < 0f) {
      return 0f;
    }
    return p > 1f ? 1f : p;
  }

  public Result onTick(long nowMillis) {
    if (downMillis < 0L) {
      return Result.NONE;
    }
    if (nowMillis - downMillis >= holdDurationMillis) {
      downMillis = -1L;
      return Result.COMPLETE;
    }
    return Result.NONE;
  }

  public Result onUp(long nowMillis) {
    if (downMillis < 0L) {
      return Result.NONE;
    }
    boolean early = nowMillis - downMillis < holdDurationMillis;
    downMillis = -1L;
    return early ? Result.EARLY_RELEASE : Result.NONE;
  }

  public void onCancel() {
    downMillis = -1L;
  }
}
