package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.R;
import org.runnerup.db.Statistics;

public class CalendarHeatmapView extends View {

  public interface OnDayTapListener {
    void onDayTap(int dayOfMonth);
  }

  public interface DayLabelFormatter {
    String formatDistance(double meters);
  }

  private static final int DAYS_PER_WEEK = Statistics.CALENDAR_COLUMNS;
  private static final int WEEKS = Statistics.CALENDAR_ROWS;
  private static final int INTENSITY_STEPS = 4;
  private static final int[] ALPHA_STEPS = {42, 92, 150, 214};
  private static final char[] WEEKDAY_LABELS = {'M', 'T', 'W', 'T', 'F', 'S', 'S'};
  private static final float HEADER_HEIGHT_DP = 20;
  private static final float CELL_HEIGHT_DP = 44;
  private static final float CORNER_RADIUS_DP = 6;
  private static final float GAP_DP = 3;

  private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint distancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final GestureDetector gestureDetector;

  private OnDayTapListener onDayTapListener;
  private DayLabelFormatter dayLabelFormatter = value -> String.format("%.1f", value);
  private Statistics.CalendarDay[] cells = new Statistics.CalendarDay[0];
  private double monthMax = 0;
  private int fillColor = 0xFFD68C27;
  private int onSurfaceColor = 0xFF1A1A1A;
  private int onSurfaceVariantColor = 0xFF595959;

  public CalendarHeatmapView(Context context) {
    this(context, null);
  }

  public CalendarHeatmapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    weekdayPaint.setTextSize(dp(11));
    dayPaint.setTextSize(dp(13));
    dayPaint.setFakeBoldText(true);
    distancePaint.setTextSize(dp(9));
    cellPaint.setStyle(Paint.Style.FILL);
    gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                handleTap((int) e.getX(), (int) e.getY());
                return true;
              }
            });
    resolveColors();
  }

  public void setData(Statistics.CalendarDay[] cells) {
    this.cells = cells == null ? new Statistics.CalendarDay[0] : cells;
    monthMax = 0;
    for (Statistics.CalendarDay cell : this.cells) {
      if (cell != null) {
        monthMax = Math.max(monthMax, cell.distance);
      }
    }
    invalidate();
  }

  public void setDayLabelFormatter(DayLabelFormatter formatter) {
    dayLabelFormatter = formatter == null ? dayLabelFormatter : formatter;
    invalidate();
  }

  public void setOnDayTapListener(OnDayTapListener listener) {
    onDayTapListener = listener;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = Math.round(dp(HEADER_HEIGHT_DP) + WEEKS * dp(CELL_HEIGHT_DP));
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cellWidth = getWidth() / (float) DAYS_PER_WEEK;
    float cellHeight = dp(CELL_HEIGHT_DP);
    float gap = dp(GAP_DP);
    float headerHeight = dp(HEADER_HEIGHT_DP);
    float radius = dp(CORNER_RADIUS_DP);

    for (int col = 0; col < DAYS_PER_WEEK; col++) {
      float cx = cellWidth * col + cellWidth / 2;
      String label = String.valueOf(WEEKDAY_LABELS[col]);
      canvas.drawText(
          label, cx - weekdayPaint.measureText(label) / 2, headerHeight - dp(4), weekdayPaint);
    }

    for (int i = 0; i < cells.length && i < DAYS_PER_WEEK * WEEKS; i++) {
      Statistics.CalendarDay cell = cells[i];
      if (cell == null) {
        continue;
      }
      int col = i % DAYS_PER_WEEK;
      int row = i / DAYS_PER_WEEK;
      float left = col * cellWidth + gap;
      float right = (col + 1) * cellWidth - gap;
      float top = headerHeight + row * cellHeight + gap;
      float bottom = headerHeight + (row + 1) * cellHeight - gap;
      rect.set(left, top, right, bottom);

      boolean active = cell.day != 0 && cell.distance > 0;
      if (cell.day != 0 && !active) {
        cellPaint.setColor(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 26));
        canvas.drawRoundRect(rect, radius, radius, cellPaint);
      } else if (active) {
        int bucket = Statistics.distanceBucket(cell.distance, monthMax, INTENSITY_STEPS);
        cellPaint.setColor(ColorUtils.setAlphaComponent(fillColor, ALPHA_STEPS[bucket - 1]));
        canvas.drawRoundRect(rect, radius, radius, cellPaint);
      }

      if (cell.day != 0) {
        float cx = (left + right) / 2;
        String dayLabel = String.valueOf(cell.day);
        dayPaint.setColor(
            active ? onSurfaceColor : ColorUtils.setAlphaComponent(onSurfaceColor, 96));
        canvas.drawText(dayLabel, cx - dayPaint.measureText(dayLabel) / 2, top + dp(16), dayPaint);
        if (active) {
          String distanceLabel = dayLabelFormatter.formatDistance(cell.distance);
          distancePaint.setColor(onSurfaceVariantColor);
          canvas.drawText(
              distanceLabel,
              cx - distancePaint.measureText(distanceLabel) / 2,
              bottom - dp(7),
              distancePaint);
        }
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }

  private void handleTap(int x, int y) {
    if (onDayTapListener == null || cells.length == 0) {
      return;
    }
    float headerHeight = dp(HEADER_HEIGHT_DP);
    float cellWidth = getWidth() / (float) DAYS_PER_WEEK;
    float cellHeight = dp(CELL_HEIGHT_DP);
    if (y < headerHeight) {
      return;
    }
    int col = (int) (x / cellWidth);
    int row = (int) ((y - headerHeight) / cellHeight);
    int index = row * DAYS_PER_WEEK + col;
    if (index < 0 || index >= cells.length) {
      return;
    }
    Statistics.CalendarDay cell = cells[index];
    if (cell != null && cell.day != 0) {
      onDayTapListener.onDayTap(cell.day);
    }
  }

  private void resolveColors() {
    fillColor = resolveColor(R.attr.colorTertiary, fillColor);
    onSurfaceColor = resolveColor(R.attr.colorOnSurface, onSurfaceColor);
    onSurfaceVariantColor = resolveColor(R.attr.colorOnSurfaceVariant, onSurfaceVariantColor);
    weekdayPaint.setColor(onSurfaceVariantColor);
    dayPaint.setColor(onSurfaceColor);
    distancePaint.setColor(onSurfaceVariantColor);
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
}
