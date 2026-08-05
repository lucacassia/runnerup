/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import androidx.appcompat.content.res.AppCompatResources;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import org.runnerup.R;

public class MaterialSportSpinner extends MaterialAutoCompleteTextView implements SpinnerInterface {
  final SpinnerPresenter mPresenter;
  private AdapterView.OnItemSelectedListener mItemSelectedListener = null;

  public MaterialSportSpinner(Context context, AttributeSet attrs) {
    super(context, attrs);

    setKeyListener(null);
    setFocusable(false);
    setCursorVisible(false);
    setClickable(true);

    setOnItemClickListener(
        (parent, view, position, id) -> {
          ListAdapter adapter = getAdapter();
          if (adapter != null && !adapter.isEnabled(position)) {
            return;
          }
          if (mItemSelectedListener != null) {
            mItemSelectedListener.onItemSelected(parent, view, position, id);
          }
        });

    Drawable arrow = AppCompatResources.getDrawable(context, R.drawable.ic_arrow_drop_down_24dp);
    if (arrow != null) {
      TypedValue tv = new TypedValue();
      if (context.getTheme().resolveAttribute(android.R.attr.colorControlNormal, tv, true)) {
        arrow.setTintList(ColorStateList.valueOf(tv.data));
      }
      setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
    }

    mPresenter = new SpinnerPresenter(context, attrs, this);
  }

  @Override
  public void setViewPrompt(CharSequence charSequence) {
    setHint(charSequence);
  }

  @Override
  public void setViewLabel(CharSequence label) {
    setContentDescription(label);
  }

  @Override
  public void setViewValue(int itemId) {
    ListAdapter adapter = getAdapter();
    if (adapter != null && itemId >= 0 && itemId < adapter.getCount()) {
      setText(adapter.getItem(itemId).toString(), false);
    }
  }

  @Override
  public void setViewText(CharSequence charSequence) {
    setText(charSequence, false);
  }

  @Override
  public CharSequence getViewValueText() {
    return getText();
  }

  @Override
  public void setViewOnClickListener(OnClickListener onClickListener) {
    setOnClickListener(onClickListener);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_UP && !isPopupShowing()) {
      showDropDown();
    }
    return super.onTouchEvent(event);
  }

  @Override
  public void setOnClickSpinnerOpen() {}

  @Override
  public void setViewAdapter(DisabledEntriesAdapter adapter) {
    setAdapter((ListAdapter & Filterable) adapter);
  }

  @Override
  public SpinnerAdapter getViewAdapter() {
    ListAdapter adapter = getAdapter();
    if (adapter instanceof SpinnerAdapter) {
      return (SpinnerAdapter) adapter;
    }
    return null;
  }

  @Override
  public void setViewSelection(int value) {
    ListAdapter adapter = getAdapter();
    if (adapter != null && value >= 0 && value < adapter.getCount()) {
      setText(adapter.getItem(value).toString(), false);
    }
  }

  @Override
  public void viewOnClose(OnCloseDialogListener listener, boolean b) {
    listener.onClose(this, b);
  }

  @Override
  public void setViewOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
    mItemSelectedListener = listener;
  }

  @Override
  public AdapterView.OnItemSelectedListener getViewOnItemSelectedListener() {
    return mItemSelectedListener;
  }
}
