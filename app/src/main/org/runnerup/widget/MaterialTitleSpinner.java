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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import org.runnerup.R;

public class MaterialTitleSpinner extends LinearLayout implements SpinnerInterface {
  final SpinnerPresenter mPresenter;
  final TextInputLayout mInput;
  final MaterialAutoCompleteTextView mText;
  private SpinnerAdapter mAdapter = null;
  private AdapterView.OnItemSelectedListener mItemSelectedListener = null;

  public MaterialTitleSpinner(Context context, AttributeSet attrs) {
    super(context, attrs);

    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.material_title_spinner, this);

    mInput = findViewById(R.id.mts_layout);
    mText = findViewById(R.id.mts_text);
    mText.setSaveEnabled(false);
    mText.setOnItemClickListener(
        (parent, view, position, id) -> {
          if (mAdapter instanceof ListAdapter && !((ListAdapter) mAdapter).isEnabled(position)) {
            return;
          }
          if (mItemSelectedListener != null) {
            mItemSelectedListener.onItemSelected(parent, view, position, id);
          }
        });

    mPresenter = new SpinnerPresenter(context, attrs, this);
  }

  @Override
  public void setOnClickSpinnerOpen() {
    mInput.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
    mInput.setOnClickListener(v -> mText.showDropDown());
    mText.setOnClickListener(v -> mText.showDropDown());
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    mInput.setEnabled(enabled);
    mText.setEnabled(enabled);
  }

  @Override
  public void setViewPrompt(CharSequence charSequence) {
    mInput.setHint(charSequence);
  }

  @Override
  public void setViewLabel(CharSequence label) {
    mInput.setHint(label);
  }

  @Override
  public void setViewValue(int itemId) {
    if (mAdapter != null && itemId >= 0 && itemId < mAdapter.getCount()) {
      mText.setText(mAdapter.getItem(itemId).toString(), false);
    }
  }

  @Override
  public void setViewText(CharSequence charSequence) {
    mText.setText(charSequence, false);
  }

  @Override
  public CharSequence getViewValueText() {
    return mText.getText();
  }

  @Override
  public void setViewOnClickListener(OnClickListener onClickListener) {
    mInput.setEndIconMode(TextInputLayout.END_ICON_NONE);
    setOnClickListener(onClickListener);
    mInput.setOnClickListener(onClickListener);
    mText.setOnClickListener(onClickListener);
  }

  @Override
  public void setViewAdapter(DisabledEntriesAdapter adapter) {
    mAdapter = adapter;
    mText.setAdapter((ListAdapter & Filterable) adapter);
  }

  @Override
  public SpinnerAdapter getViewAdapter() {
    return mAdapter;
  }

  @Override
  public void setViewSelection(int value) {
    if (mAdapter != null && value >= 0 && value < mAdapter.getCount()) {
      mText.setText(mAdapter.getItem(value).toString(), false);
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

  public void setAdapter(SpinnerAdapter adapter) {
    mAdapter = adapter;
    mText.setAdapter(filterable(adapter));
    mPresenter.loadValue(null);
  }

  /** MaterialAutoCompleteTextView requires a Filterable adapter; wrap non-filterable ones. */
  @SuppressWarnings("unchecked")
  private static <T extends ListAdapter & Filterable> T filterable(SpinnerAdapter adapter) {
    if (adapter instanceof Filterable) {
      return (T) adapter;
    }
    return (T) new FilterableListAdapterWrapper(adapter);
  }

  private static final class FilterableListAdapterWrapper extends BaseAdapter
      implements Filterable {
    private final SpinnerAdapter delegate;

    FilterableListAdapterWrapper(SpinnerAdapter adapter) {
      delegate = adapter;
    }

    @Override
    public int getCount() {
      return delegate.getCount();
    }

    @Override
    public Object getItem(int position) {
      return delegate.getItem(position);
    }

    @Override
    public long getItemId(int position) {
      return delegate.getItemId(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      return delegate.getView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
      return delegate.getDropDownView(position, convertView, parent);
    }

    @Override
    public boolean isEnabled(int position) {
      if (delegate instanceof ListAdapter) {
        return ((ListAdapter) delegate).isEnabled(position);
      }
      return true;
    }

    @Override
    public Filter getFilter() {
      return new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
          FilterResults results = new FilterResults();
          results.values = delegate;
          results.count = delegate.getCount();
          return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
          notifyDataSetChanged();
        }
      };
    }
  }

  public void setValue(int value) {
    mPresenter.setValue(value);
  }

  public void setValue(String value) {
    mPresenter.setValue(value);
  }

  public CharSequence getValue() {
    return mPresenter.getValue();
  }

  public int getValueInt() {
    return mPresenter.getValueInt();
  }

  public void addDisabledValue(int value) {
    int selection = mPresenter.getSelectionValue(value);
    ((DisabledEntriesAdapter) mAdapter).addDisabled(selection);
  }

  public void clearDisabled() {
    ((DisabledEntriesAdapter) mAdapter).clearDisabled();
  }

  public void clear() {
    mPresenter.clear();
  }

  public void setOnSetValueListener(SpinnerInterface.OnSetValueListener listener) {
    mPresenter.setOnSetValueListener(listener);
  }

  public void setOnCloseDialogListener(SpinnerInterface.OnCloseDialogListener listener) {
    mPresenter.setOnCloseDialogListener(listener);
  }

  // Instead of android:entries="@array/anArray"
  public void setArrayEntries(String[] entries) {
    var adapter =
        new ArrayAdapter<CharSequence>(getContext(), android.R.layout.simple_spinner_item, entries);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mAdapter = adapter;
    mText.setAdapter(adapter);
  }
}
