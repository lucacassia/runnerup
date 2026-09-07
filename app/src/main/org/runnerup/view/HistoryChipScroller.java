package org.runnerup.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class HistoryChipScroller extends HorizontalScrollView {

  public HistoryChipScroller(Context context) {
    super(context);
  }

  public HistoryChipScroller(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public HistoryChipScroller(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    return super.onInterceptTouchEvent(ev);
  }
}
