package org.runnerup.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

public class PillSpan extends ReplacementSpan {
  private static final int COLOR_WHITE = 0xFFFFFFFF;
  private static final float PADDING_DP = 6f;

  private final int color;
  private final float density;

  public PillSpan(int color, float density) {
    this.color = color;
    this.density = density;
  }

  private float padPx() {
    return PADDING_DP * density;
  }

  @Override
  public int getSize(
      @NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
    String value = text.subSequence(start, end).toString();
    return Math.round(paint.measureText(value) + 2 * padPx());
  }

  @Override
  public void draw(
      @NonNull Canvas canvas,
      CharSequence text,
      int start,
      int end,
      float x,
      int top,
      int y,
      int bottom,
      @NonNull Paint paint) {
    String value = text.subSequence(start, end).toString();
    float width = paint.measureText(value) + 2 * padPx();
    float centerY = (top + bottom) / 2f;
    float radius = (bottom - top) / 2f;
    int saveColor = paint.getColor();

    paint.setColor(color);
    canvas.drawRoundRect(x, centerY - radius, x + width, centerY + radius, radius, radius, paint);
    paint.setColor(COLOR_WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics fm = paint.getFontMetrics();
    float baseline = centerY - (fm.ascent + fm.descent) / 2f;
    canvas.drawText(value, x + width / 2f, baseline, paint);

    paint.setTextAlign(Paint.Align.LEFT);
    paint.setColor(saveColor);
  }
}
