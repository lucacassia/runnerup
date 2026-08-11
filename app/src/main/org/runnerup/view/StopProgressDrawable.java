package org.runnerup.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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
