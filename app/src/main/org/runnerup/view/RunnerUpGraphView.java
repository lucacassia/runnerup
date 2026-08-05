/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.annotation.NonNull;
import com.google.android.material.R;
import java.util.Locale;

public class RunnerUpGraphView extends View {

  public interface LabelFormatter {
    String formatValue(double value, boolean isValueX);
  }

  public interface OnPointTapListener {
    void onPointTap(double x, double y);
  }

  private static final int DEFAULT_LABELS = 4;

  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private int lineColor = Color.parseColor("#6750A4");
  private int labelColor = Color.parseColor("#49454F");
  private int gridColor = Color.parseColor("#CAC4D0");
  private int titleColor = Color.parseColor("#1D1B20");

  private String title = "";
  private String verticalAxisTitle = "";
  private String horizontalAxisTitle = "";
  private LabelFormatter labelFormatter = null;
  private OnPointTapListener pointTapListener = null;

  private double[] xs = new double[0];
  private double[] ys = new double[0];

  private boolean interactive = false;
  private boolean yBoundsManual = false;
  private double yMinManual = 0;
  private double yMaxManual = 1;
  private double xWinMin = 0;
  private double xWinMax = 1;
  private double xDataMin = 0;
  private double xDataMax = 1;

  // Ring buffer for live data
  private double[] ringX = null;
  private double[] ringY = null;
  private int ringCount = 0;
  private int ringHead = 0;

  private final ScaleGestureDetector scaleDetector;
  private final GestureDetector gestureDetector;

  public RunnerUpGraphView(Context context) {
    this(context, null);
  }

  public RunnerUpGraphView(Context context, AttributeSet attrs) {
    super(context, attrs);

    labelPaint.setTextSize(dp(11));
    titlePaint.setTextSize(dp(14));
    titlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    gridPaint.setStrokeWidth(dp(1));

    resolveColors();

    scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    gestureDetector = new GestureDetector(context, new GestureListener());
  }

  private void resolveColors() {
    lineColor = resolveColor(androidx.appcompat.R.attr.colorPrimary, lineColor);
    labelColor = resolveColor(R.attr.colorOnSurfaceVariant, labelColor);
    gridColor = resolveColor(R.attr.colorOutlineVariant, gridColor);
    titleColor = resolveColor(R.attr.colorOnSurface, titleColor);
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

  public void setTitle(String value) {
    title = value == null ? "" : value;
    invalidate();
  }

  public void setVerticalAxisTitle(String value) {
    verticalAxisTitle = value == null ? "" : value;
    invalidate();
  }

  public void setHorizontalAxisTitle(String value) {
    horizontalAxisTitle = value == null ? "" : value;
    invalidate();
  }

  public void setLabelFormatter(LabelFormatter formatter) {
    labelFormatter = formatter;
    invalidate();
  }

  public void setOnPointTapListener(OnPointTapListener listener) {
    pointTapListener = listener;
  }

  public void setInteractive(boolean value) {
    interactive = value;
  }

  public void setPoints(double[] xValues, double[] yValues) {
    xs = xValues;
    ys = yValues;
    if (xs.length > 0) {
      xDataMin = xs[0];
      xDataMax = xs[0];
      for (double v : xs) {
        xDataMin = Math.min(xDataMin, v);
        xDataMax = Math.max(xDataMax, v);
      }
      if (xDataMax > xDataMin) {
        xWinMin = xDataMin;
        xWinMax = xDataMax;
      } else {
        xWinMin = xDataMin - 1;
        xWinMax = xDataMax + 1;
      }
    }
    invalidate();
  }

  public void clearPoints() {
    xs = new double[0];
    ys = new double[0];
    invalidate();
  }

  public void setManualYBounds(double min, double max) {
    yBoundsManual = true;
    yMinManual = min;
    yMaxManual = max;
    invalidate();
  }

  /** Re-fit the manual Y bounds to the current data range; no-op when empty. */
  public void fitYBoundsToData() {
    if (ys.length == 0) return;
    double min = ys[0];
    double max = ys[0];
    for (double v : ys) {
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    if (max == min) {
      min -= 1;
      max += 1;
    }
    yBoundsManual = true;
    yMinManual = min;
    yMaxManual = max;
    invalidate();
  }

  public void setXWindow(double min, double max) {
    xWinMin = min;
    xWinMax = max;
    invalidate();
  }

  /** Append a point to the live-data ring buffer, keeping at most maxPoints points. */
  public void appendPoint(double x, double y, int maxPoints) {
    if (ringX == null || ringX.length != maxPoints) {
      ringX = new double[maxPoints];
      ringY = new double[maxPoints];
      ringCount = 0;
      ringHead = 0;
    }
    int index = (ringHead + ringCount) % maxPoints;
    ringX[index] = x;
    ringY[index] = y;
    if (ringCount < maxPoints) {
      ringCount++;
    } else {
      ringHead = (ringHead + 1) % maxPoints;
    }

    xs = new double[ringCount];
    ys = new double[ringCount];
    for (int i = 0; i < ringCount; i++) {
      int idx = (ringHead + i) % maxPoints;
      xs[i] = ringX[idx];
      ys[i] = ringY[idx];
    }
    if (xDataMax < x) {
      xDataMax = x;
    }
    double span = xWinMax - xWinMin;
    if (x > xWinMax) {
      xWinMin = x - span;
      xWinMax = x;
    }
    invalidate();
  }

  public void clearLiveData() {
    ringCount = 0;
    ringHead = 0;
    xs = new double[0];
    ys = new double[0];
    invalidate();
  }

  private final class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      if (!interactive || xs.length < 2 || getWidth() <= 0) return false;
      double span = xWinMax - xWinMin;
      double newSpan = span / detector.getScaleFactor();
      double dataWidth = Math.max(xDataMax - xDataMin, 1e-9);
      newSpan = Math.max(newSpan, dataWidth / 100.0);
      newSpan = Math.min(newSpan, dataWidth * 10.0);
      double focusRatio = Math.max(0, Math.min(1, detector.getFocusX() / getWidth()));
      double anchor = xWinMin + span * focusRatio;
      double newMin = anchor - newSpan * focusRatio;
      double newMax = newMin + newSpan;
      if (newMin < xDataMin) {
        newMin = xDataMin;
        newMax = newMin + newSpan;
      }
      if (newMax > xDataMax) {
        newMax = xDataMax;
        newMin = Math.max(xDataMin, newMax - newSpan);
      }
      xWinMin = newMin;
      xWinMax = newMax;
      invalidate();
      return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      return interactive;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {}
  }

  private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
      if (!interactive || xs.length < 2) return false;
      double span = xWinMax - xWinMin;
      double pan = span * (distanceX / getWidth());
      xWinMin += pan;
      xWinMax += pan;
      if (xWinMin < xDataMin) {
        xWinMin = xDataMin;
        xWinMax = xDataMin + span;
      } else if (xWinMax > xDataMax) {
        xWinMax = xDataMax;
        xWinMin = xDataMax - span;
      }
      invalidate();
      return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      if (pointTapListener != null && xs.length > 0) {
        int index = findNearestPoint(e.getX());
        if (index >= 0) {
          pointTapListener.onPointTap(xs[index], ys[index]);
        }
      }
      return true;
    }
  }

  private int findNearestPoint(float screenX) {
    double[] plot = computePlotArea();
    int left = (int) plot[0];
    int right = (int) plot[2];
    if (getWidth() <= 0) return -1;
    int best = -1;
    double bestDist = dp(40);
    for (int i = 0; i < xs.length; i++) {
      if (xs[i] < xWinMin || xs[i] > xWinMax) continue;
      float px = (float) (left + (right - left) * (xs[i] - xWinMin) / (xWinMax - xWinMin));
      float dist = Math.abs(px - screenX);
      if (dist < bestDist) {
        bestDist = dist;
        best = i;
      }
    }
    return best;
  }

  /** Returns {left, top, right, bottom} of the plot area in pixels. */
  private double[] computePlotArea() {
    float density = getResources().getDisplayMetrics().density;
    float titleHeight = title.isEmpty() ? dp(8) : dp(28);
    float xLabelSpace =
        labelPaint.getFontMetrics().bottom - labelPaint.getFontMetrics().top + dp(6);
    float yLabelSpace = dp(44);
    float axisTitleSpace = (verticalAxisTitle.isEmpty() ? 0 : dp(18));
    float hAxisTitleSpace = horizontalAxisTitle.isEmpty() ? 0 : dp(16);
    float left = yLabelSpace + axisTitleSpace;
    float top = titleHeight;
    float right = getWidth() - density * 8;
    float bottom = getHeight() - xLabelSpace - hAxisTitleSpace - density * 8;
    return new double[] {left, top, right, bottom};
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    double[] plot = computePlotArea();
    int left = (int) plot[0];
    int top = (int) plot[1];
    int right = (int) plot[2];
    int bottom = (int) plot[3];
    if (right <= left || bottom <= top) return;

    drawTitle(canvas, left, top, right);
    drawAxesTitles(canvas, left, top, right, bottom);

    double[] yRange = computeYRange(left, right);
    if (yRange[1] - yRange[0] < 1e-9) {
      yRange[1] = yRange[0] + 1;
    }

    drawGridAndLabels(canvas, left, top, right, bottom, yRange);
    drawSeries(canvas, left, top, right, bottom, yRange);
  }

  private void drawTitle(Canvas canvas, int left, int top, int right) {
    if (title.isEmpty()) return;
    titlePaint.setColor(titleColor);
    titlePaint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(title, (left + right) / 2f, dp(14), titlePaint);
  }

  private void drawAxesTitles(Canvas canvas, int left, int top, int right, int bottom) {
    labelPaint.setTextAlign(Paint.Align.CENTER);
    if (!horizontalAxisTitle.isEmpty()) {
      labelPaint.setColor(labelColor);
      canvas.drawText(horizontalAxisTitle, (left + right) / 2f, getHeight() - dp(4), labelPaint);
    }
    if (!verticalAxisTitle.isEmpty()) {
      labelPaint.setColor(labelColor);
      canvas.save();
      canvas.rotate(-90);
      canvas.drawText(verticalAxisTitle, -(top + bottom) / 2f, dp(12), labelPaint);
      canvas.restore();
    }
  }

  private double[] computeYRange(int left, int right) {
    if (yBoundsManual) {
      return new double[] {yMinManual, yMaxManual};
    }
    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    boolean found = false;
    for (int i = 0; i < xs.length; i++) {
      if (xs[i] < xWinMin || xs[i] > xWinMax) continue;
      min = Math.min(min, ys[i]);
      max = Math.max(max, ys[i]);
      found = true;
    }
    if (!found) {
      return new double[] {0, 1};
    }
    double pad = (max - min) * 0.1;
    if (pad == 0) {
      pad = Math.max(Math.abs(max) * 0.1, 1);
    }
    return new double[] {min - pad, max + pad};
  }

  private void drawGridAndLabels(
      Canvas canvas, int left, int top, int right, int bottom, double[] yRange) {
    gridPaint.setColor(gridColor);
    labelPaint.setColor(labelColor);

    for (int i = 0; i <= DEFAULT_LABELS; i++) {
      float ratio = i / (float) DEFAULT_LABELS;
      int gy = (int) (bottom - (bottom - top) * ratio);
      double gv = yRange[0] + (yRange[1] - yRange[0]) * ratio;
      canvas.drawLine(left, gy, right, gy, gridPaint);
      String label =
          labelFormatter == null ? formatPlain(gv) : labelFormatter.formatValue(gv, false);
      labelPaint.setTextAlign(Paint.Align.RIGHT);
      canvas.drawText(label, left - dp(4), gy + labelPaint.getTextSize() / 3, labelPaint);
    }

    for (int i = 0; i <= DEFAULT_LABELS; i++) {
      float ratio = i / (float) DEFAULT_LABELS;
      int gx = (int) (left + (right - left) * ratio);
      double gv = xWinMin + (xWinMax - xWinMin) * ratio;
      canvas.drawLine(gx, top, gx, bottom, gridPaint);
      String label =
          labelFormatter == null ? formatPlain(gv) : labelFormatter.formatValue(gv, true);
      labelPaint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText(label, gx, bottom + dp(14), labelPaint);
    }
  }

  private static String formatPlain(double value) {
    if (value == Math.rint(value) && Math.abs(value) < 1e9) {
      return String.valueOf((long) value);
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private void drawSeries(
      Canvas canvas, int left, int top, int right, int bottom, double[] yRange) {
    if (xs.length == 0) return;

    Path linePath = new Path();
    boolean started = false;
    float lastPlotX = Float.NaN;
    for (int i = 0; i < xs.length; i++) {
      if (xs[i] < xWinMin || xs[i] > xWinMax) continue;
      float px = (float) (left + (right - left) * (xs[i] - xWinMin) / (xWinMax - xWinMin));
      float py = (float) (bottom - (bottom - top) * (ys[i] - yRange[0]) / (yRange[1] - yRange[0]));
      if (!started) {
        linePath.moveTo(px, py);
        started = true;
      } else {
        linePath.lineTo(px, py);
      }
      lastPlotX = px;
    }
    if (!started) return;

    Path areaPath = new Path(linePath);
    areaPath.lineTo(lastPlotX, bottom);
    areaPath.lineTo(left, bottom);
    areaPath.close();
    areaPaint.setShader(
        new LinearGradient(
            0,
            top,
            0,
            bottom,
            (lineColor & 0x00FFFFFF) | 0x2E000000,
            (lineColor & 0x00FFFFFF) | 0x02000000,
            Shader.TileMode.CLAMP));
    canvas.drawPath(areaPath, areaPaint);

    linePaint.setColor(lineColor);
    linePaint.setStrokeWidth(dp(2));
    linePaint.setStyle(Paint.Style.STROKE);
    canvas.drawPath(linePath, linePaint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!interactive) {
      return super.onTouchEvent(event);
    }
    scaleDetector.onTouchEvent(event);
    gestureDetector.onTouchEvent(event);
    return true;
  }
}
