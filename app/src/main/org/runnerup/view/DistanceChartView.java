package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import com.google.android.material.R;
import java.util.Locale;

public class DistanceChartView extends View {

  public interface LabelFormatter {
    String formatValue(double value);
  }

  private static final int MAX_Y_LABELS = 4;
  private static final int X_LABEL_SKIP_THRESHOLD = 8;

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  private int barColor = Color.parseColor("#3B7DD8");
  private int labelColor = Color.parseColor("#595959");
  private int gridColor = Color.parseColor("#D4D4D4");

  private double[] values = new double[0];
  private String[] xLabels = new String[0];
  private LabelFormatter labelFormatter = value -> String.format(Locale.US, "%.1f", value);

  public DistanceChartView(Context context) {
    this(context, null);
  }

  public DistanceChartView(Context context, AttributeSet attrs) {
    super(context, attrs);
    labelPaint.setTextSize(dp(11));
    gridPaint.setStrokeWidth(dp(1));
    resolveColors();
  }

  public void setData(double[] values, String[] xLabels) {
    this.values = values == null ? new double[0] : values;
    this.xLabels = xLabels == null ? new String[0] : xLabels;
    invalidate();
  }

  public void setLabelFormatter(LabelFormatter formatter) {
    labelFormatter = formatter == null ? this.labelFormatter : formatter;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float leftPad = dp(48);
    float rightPad = dp(8);
    float topPad = dp(16);
    float bottomPad = dp(20);

    float chartLeft = leftPad;
    float chartRight = getWidth() - rightPad;
    float chartTop = topPad;
    float chartBottom = getHeight() - bottomPad;
    float chartWidth = chartRight - chartLeft;
    float chartHeight = chartBottom - chartTop;
    if (chartWidth <= 0 || chartHeight <= 0) {
      return;
    }

    double maxValue = niceMax(max(values));
    int count = values.length;

    for (int i = 0; i <= MAX_Y_LABELS; i++) {
      float ratio = i / (float) MAX_Y_LABELS;
      float y = chartBottom - ratio * chartHeight;
      canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
      String label = labelFormatter.formatValue(maxValue * ratio);
      canvas.drawText(label, dp(4), y - dp(4), labelPaint);
    }

    if (count > 0) {
      float slot = chartWidth / count;
      float barWidth = slot * 0.7f;
      float radius = dp(3);
      for (int i = 0; i < count; i++) {
        float barHeight = (float) (values[i] / maxValue * chartHeight);
        rect.left = chartLeft + slot * i + (slot - barWidth) / 2;
        rect.top = chartBottom - barHeight;
        rect.right = rect.left + barWidth;
        rect.bottom = chartBottom;
        canvas.drawRoundRect(rect, radius, radius, barPaint);
      }
    }

    if (count > 0 && xLabels.length == count) {
      boolean skipOdd = count > X_LABEL_SKIP_THRESHOLD;
      float slot = chartWidth / count;
      for (int i = 0; i < count; i++) {
        if (skipOdd && i % 2 == 1) {
          continue;
        }
        float centerX = chartLeft + slot * i + slot / 2;
        canvas.drawLine(centerX, chartBottom, centerX, chartBottom + dp(4), gridPaint);
        String label = xLabels[i];
        canvas.drawText(
            label, centerX - labelPaint.measureText(label) / 2, chartBottom + dp(14), labelPaint);
      }
    }
  }

  private void resolveColors() {
    barColor = resolveColor(androidx.appcompat.R.attr.colorPrimary, barColor);
    labelColor = resolveColor(R.attr.colorOnSurfaceVariant, labelColor);
    gridColor = resolveColor(R.attr.colorOutlineVariant, gridColor);
    barPaint.setColor(barColor);
    labelPaint.setColor(labelColor);
    gridPaint.setColor(gridColor);
  }

  private int resolveColor(int attr, int fallback) {
    TypedValue tv = new TypedValue();
    if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
      return tv.data;
    }
    return fallback;
  }

  private float dp(float value) {
    return getResources().getDisplayMetrics().density * value;
  }

  private static double max(double[] values) {
    double max = 0;
    for (double value : values) {
      max = Math.max(max, value);
    }
    return max;
  }

  static double niceMax(double value) {
    if (value <= 0) {
      return 1.0;
    }
    double exp = Math.floor(Math.log10(value));
    double base = Math.pow(10, exp);
    double fraction = value / base;
    double niceFraction;
    if (fraction <= 1) {
      niceFraction = 1;
    } else if (fraction <= 2) {
      niceFraction = 2;
    } else if (fraction <= 3) {
      niceFraction = 3;
    } else if (fraction <= 4) {
      niceFraction = 4;
    } else if (fraction <= 5) {
      niceFraction = 5;
    } else if (fraction <= 6) {
      niceFraction = 6;
    } else if (fraction <= 8) {
      niceFraction = 8;
    } else {
      niceFraction = 10;
    }
    return niceFraction * base;
  }
}
