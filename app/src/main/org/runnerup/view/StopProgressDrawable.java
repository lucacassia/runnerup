package org.runnerup.view;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class StopProgressDrawable extends Drawable {
  private static final float SQUARE_LEFT = 6f;
  private static final float SQUARE_TOP = 6f;
  private static final float SQUARE_RIGHT = 18f;
  private static final float SQUARE_BOTTOM = 18f;
  private static final float RING_RADIUS = 10f;
  private static final float RING_STROKE_WIDTH = 2.5f;

  private final Paint squarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF ringBounds =
      new RectF(
          12f - RING_RADIUS, 12f - RING_RADIUS, 12f + RING_RADIUS, 12f + RING_RADIUS);
  private float progress = 0f;
  private ColorStateList tint = null;
  private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
  private ColorFilter tintFilter = null;

  public StopProgressDrawable() {
    squarePaint.setColor(0xffffffff);
    squarePaint.setStyle(Paint.Style.FILL);
    ringPaint.setColor(0xffffffff);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(RING_STROKE_WIDTH);
    ringPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  public void setProgress(float p) {
    progress = p;
    invalidateSelf();
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    squarePaint.setColorFilter(tintFilter);
    ringPaint.setColorFilter(tintFilter);
    canvas.drawRect(SQUARE_LEFT, SQUARE_TOP, SQUARE_RIGHT, SQUARE_BOTTOM, squarePaint);
    if (progress > 0f) {
      canvas.drawArc(ringBounds, -90f, 360f * progress, false, ringPaint);
    }
  }

  @Override
  public void setAlpha(int alpha) {
    squarePaint.setAlpha(alpha);
    ringPaint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {}

  @Override
  public void setTint(int color) {
    setTintList(ColorStateList.valueOf(color));
  }

  @Override
  public void setTintList(@Nullable ColorStateList tint) {
    this.tint = tint;
    rebuildTintFilter();
    invalidateSelf();
  }

  @Override
  public void setTintMode(@NonNull PorterDuff.Mode mode) {
    tintMode = mode;
    rebuildTintFilter();
    invalidateSelf();
  }

  private void rebuildTintFilter() {
    tintFilter = tint == null ? null : new PorterDuffColorFilter(tint.getDefaultColor(), tintMode);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth() {
    return 24;
  }

  @Override
  public int getIntrinsicHeight() {
    return 24;
  }
}
