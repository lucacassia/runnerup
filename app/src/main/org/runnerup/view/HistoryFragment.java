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
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.Statistics;
import org.runnerup.db.Statistics.BucketPeriod;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.workout.Sport;

public class HistoryFragment extends Fragment implements Constants, LoaderCallbacks<Cursor> {

  private static final int TYPE_HEADER = 0;
  private static final int TYPE_ROW = 1;

  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;

  HistoryListAdapter adapter = null;
  View fab = null;
  View emptyView = null;

  private static final int TAB_HISTORY_INDEX = 0;
  private static final int TAB_STATISTICS_INDEX = 1;

  private final ExecutorService statisticsExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private int currentTab = TAB_HISTORY_INDEX;
  private BucketPeriod currentPeriod = BucketPeriod.DAY;
  private List<Statistics.ActivityRow> statisticsRows = null;
  private View statisticsContent;
  private View statisticsEmpty;
  private DistanceChartView statisticsChart;
  private TextView statisticsChartTitle;
  private TextView statistics7Value;
  private TextView statistics30Value;
  private TextView statistics365Value;

  private final ActivityResultLauncher<Intent> reloadLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> LoaderManager.getInstance(this).restartLoader(0, null, this));

  public HistoryFragment() {
    super(R.layout.history);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RecyclerView listView = view.findViewById(R.id.history_list);
    fab = view.findViewById(R.id.history_add);
    emptyView = view.findViewById(R.id.history_empty);

    Context context = requireContext();
    fab.setOnClickListener(
        v -> {
          Intent i = new Intent(context, ManualActivity.class);
          reloadLauncher.launch(i);
        });

    mDB = DBHelper.getReadableDatabase(context);
    formatter = new Formatter(context);
    listView.setLayoutManager(new LinearLayoutManager(context));
    adapter = new HistoryListAdapter(context);
    listView.setAdapter(adapter);

    LoaderManager.getInstance(this).initLoader(0, null, this);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    new ActivityCleaner().conditionalRecompute(mDB);

    statisticsContent = view.findViewById(R.id.statistics_content);
    statisticsEmpty = view.findViewById(R.id.statistics_empty);
    statisticsChart = view.findViewById(R.id.statistics_chart);
    statisticsChartTitle = view.findViewById(R.id.statistics_chart_title);
    statistics7Value = view.findViewById(R.id.statistics_7_value);
    statistics30Value = view.findViewById(R.id.statistics_30_value);
    statistics365Value = view.findViewById(R.id.statistics_365_value);
    statisticsChart.setLabelFormatter(
        value -> formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value)));

    TabLayout historyTabs = view.findViewById(R.id.history_tabs);
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.History));
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Statistics));
    historyTabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            selectTab(tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            if (tab.getPosition() == TAB_STATISTICS_INDEX) {
              loadStatistics();
            }
          }
        });

    MaterialButtonToggleGroup statisticsToggle = view.findViewById(R.id.statistics_toggle);
    statisticsToggle.addOnButtonCheckedListener(
        (group, checkedId, isChecked) -> {
          if (!isChecked) {
            return;
          }
          BucketPeriod period =
              checkedId == R.id.statistics_toggle_week
                  ? BucketPeriod.WEEK
                  : checkedId == R.id.statistics_toggle_month
                      ? BucketPeriod.MONTH
                      : BucketPeriod.DAY;
          currentPeriod = period;
          renderChart();
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    LoaderManager.getInstance(this).restartLoader(0, null, this);
    if (currentTab == TAB_STATISTICS_INDEX) {
      loadStatistics();
    }
  }

  @Override
  public void onDestroy() {
    statisticsExecutor.shutdownNow();
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    String[] from =
        new String[] {
          "_id",
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.SPORT,
          DB.ACTIVITY.AVG_HR
        };

    return new SimpleCursorLoader(
        requireContext(),
        mDB,
        DB.ACTIVITY.TABLE,
        from,
        "deleted == 0",
        null,
        DB.ACTIVITY.START_TIME + " desc");
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
    if (emptyView != null) {
      emptyView.setVisibility(arg1 == null || arg1.getCount() == 0 ? View.VISIBLE : View.GONE);
    }
    adapter.setData(arg1);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    adapter.setData(null);
  }

  private void selectTab(int index) {
    currentTab = index;
    View view = getView();
    if (view == null) {
      return;
    }
    view.findViewById(R.id.history_list_content)
        .setVisibility(index == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    statisticsContent.setVisibility(index == TAB_STATISTICS_INDEX ? View.VISIBLE : View.GONE);
    fab.setVisibility(index == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    if (index == TAB_STATISTICS_INDEX) {
      loadStatistics();
    }
  }

  private void loadStatistics() {
    if (mDB == null || statisticsContent == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          long now = System.currentTimeMillis() / 1000;
          long from =
              Statistics.bucketStarts(Statistics.BucketPeriod.MONTH, now, ZoneId.systemDefault())[
                  0];
          List<Statistics.ActivityRow> rows = Statistics.queryActivities(mDB, from);
          double[] totals = Statistics.totals(rows, now);
          mainHandler.post(
              () -> {
                statisticsRows = rows;
                statistics7Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
                statistics30Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
                statistics365Value.setText(
                    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
                renderChart();
              });
        });
  }

  private void renderChart() {
    if (statisticsRows == null) {
      return;
    }
    long now = System.currentTimeMillis() / 1000;
    double[] buckets =
        Statistics.bucketize(statisticsRows, currentPeriod, now, ZoneId.systemDefault());
    long[] starts = Statistics.bucketStarts(currentPeriod, now, ZoneId.systemDefault());
    statisticsChartTitle.setText(chartTitleFor(currentPeriod));
    statisticsChart.setData(buckets, buildXLabels(currentPeriod, starts));
    boolean empty = true;
    for (double value : buckets) {
      if (value > 0) {
        empty = false;
        break;
      }
    }
    statisticsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    statisticsChart.setVisibility(empty ? View.GONE : View.VISIBLE);
  }

  private String[] buildXLabels(BucketPeriod period, long[] starts) {
    String[] labels = new String[starts.length];
    for (int i = 0; i < starts.length; i++) {
      Date date = new Date(starts[i] * 1000);
      labels[i] =
          period == BucketPeriod.MONTH
              ? formatter.formatMonth(date)
              : formatter.formatDayOfMonth(date);
    }
    return labels;
  }

  private int chartTitleFor(BucketPeriod period) {
    switch (period) {
      case WEEK:
        return org.runnerup.common.R.string.Statistics_last_8_weeks;
      case MONTH:
        return org.runnerup.common.R.string.Statistics_last_12_months;
      case DAY:
      default:
        return org.runnerup.common.R.string.Statistics_last_14_days;
    }
  }

  private void openActivity(long id) {
    Intent intent = new Intent(requireContext(), DetailActivity.class);
    intent.putExtra("ID", id);
    intent.putExtra("mode", "details");
    reloadLauncher.launch(intent);
  }

  private static final class HistoryItem {
    final long id;
    final boolean isHeader;
    final String monthText;
    final long startTime;
    final Double distance;
    final Long time;
    final Integer sport;
    final Integer avgHr;

    private HistoryItem(
        long id,
        boolean isHeader,
        String monthText,
        long startTime,
        Double distance,
        Long time,
        Integer sport,
        Integer avgHr) {
      this.id = id;
      this.isHeader = isHeader;
      this.monthText = monthText;
      this.startTime = startTime;
      this.distance = distance;
      this.time = time;
      this.sport = sport == null ? Sport.OTHER.getDbValue() : sport;
      this.avgHr = avgHr;
    }

    static HistoryItem header(String monthText, int year, int month) {
      return new HistoryItem(-(year * 100L + month), true, monthText, 0, null, null, null, null);
    }

    static HistoryItem row(ActivityEntity ae) {
      long startTime = ae.getStartTime() == null ? 0 : ae.getStartTime();
      return new HistoryItem(
          ae.getId(),
          false,
          null,
          startTime,
          ae.getDistance(),
          ae.getTime(),
          ae.getSport(),
          ae.getAvgHr());
    }

    boolean sameHeader(HistoryItem other) {
      return isHeader == other.isHeader
          && (monthText == null ? other.monthText == null : monthText.equals(other.monthText));
    }

    boolean sameRow(HistoryItem other) {
      return startTime == other.startTime
          && (distance == null ? other.distance == null : distance.equals(other.distance))
          && (time == null ? other.time == null : time.equals(other.time))
          && sport.equals(other.sport)
          && (avgHr == null ? other.avgHr == null : avgHr.equals(other.avgHr));
    }
  }

  class HistoryListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final LayoutInflater inflater;
    private final List<HistoryItem> items = new ArrayList<>();

    HistoryListAdapter(Context context) {
      inflater = LayoutInflater.from(context);
      setHasStableIds(true);
    }

    void setData(Cursor cursor) {
      List<HistoryItem> newItems = new ArrayList<>();
      if (cursor != null && cursor.moveToFirst()) {
        Calendar prevCal = null;
        do {
          ActivityEntity ae = new ActivityEntity(cursor);
          long startTime = ae.getStartTime() == null ? 0 : ae.getStartTime();
          Date date = new Date(startTime * 1000);
          Calendar cal = Calendar.getInstance();
          cal.setTime(date);
          if (prevCal == null
              || prevCal.get(Calendar.YEAR) != cal.get(Calendar.YEAR)
              || prevCal.get(Calendar.MONTH) != cal.get(Calendar.MONTH)) {
            newItems.add(
                HistoryItem.header(
                    formatter.formatMonth(date), cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)));
            prevCal = cal;
          }
          newItems.add(HistoryItem.row(ae));
        } while (cursor.moveToNext());
      }
      DiffUtil.DiffResult result = DiffUtil.calculateDiff(new HistoryDiffCallback(items, newItems));
      items.clear();
      items.addAll(newItems);
      result.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
      return items.get(position).isHeader ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      if (viewType == TYPE_HEADER) {
        return new HistoryHeaderViewHolder(
            inflater.inflate(R.layout.history_section_header, parent, false));
      }
      return new HistoryRowViewHolder(inflater.inflate(R.layout.history_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      if (holder instanceof HistoryHeaderViewHolder) {
        ((HistoryHeaderViewHolder) holder).sectionTitle.setText(items.get(position).monthText);
      } else {
        bindRow((HistoryRowViewHolder) holder, items.get(position));
      }
    }

    private void bindRow(HistoryRowViewHolder holder, HistoryItem item) {
      Context context = holder.itemView.getContext();
      holder.dateText.setText(formatter.formatDateTime(item.startTime));

      Double d = item.distance;
      if (d != null) {
        holder.distanceText.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, d.longValue()));
      } else {
        holder.distanceText.setText("");
      }

      int sportColor = ContextCompat.getColor(context, Sport.colorOf(item.sport));
      Drawable sportDrawable =
          AppCompatResources.getDrawable(context, Sport.drawableColored16Of(item.sport));
      holder.emblem.setImageDrawable(sportDrawable);
      holder.emblem.setColorFilter(sportColor);
      holder.distanceText.setTextColor(sportColor);

      if (item.avgHr != null) {
        holder.additionalText.setText(
            formatter.formatHeartRate(Formatter.Format.TXT_SHORT, item.avgHr));
        holder.additionalText.setTextColor(sportColor);
      } else {
        holder.additionalText.setText(null);
      }

      Long dur = item.time;
      if (dur != null) {
        holder.durationText.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, dur));
      } else {
        holder.durationText.setText("");
      }

      String paceTextContents = "";
      if (d != null && dur != null && dur != 0) {
        paceTextContents =
            formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / dur);
      }
      holder.paceText.setText(paceTextContents);
    }

    @Override
    public long getItemId(int position) {
      return items.get(position).id;
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    class HistoryHeaderViewHolder extends RecyclerView.ViewHolder {
      final TextView sectionTitle;

      HistoryHeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        sectionTitle = itemView.findViewById(R.id.history_section_title);
      }
    }

    class HistoryRowViewHolder extends RecyclerView.ViewHolder {
      final ImageView emblem;
      final TextView distanceText;
      final TextView dateText;
      final TextView durationText;
      final TextView paceText;
      final TextView additionalText;

      HistoryRowViewHolder(@NonNull View itemView) {
        super(itemView);
        emblem = itemView.findViewById(R.id.history_list_emblem);
        distanceText = itemView.findViewById(R.id.history_list_distance);
        dateText = itemView.findViewById(R.id.history_list_date);
        durationText = itemView.findViewById(R.id.history_list_duration);
        paceText = itemView.findViewById(R.id.history_list_pace);
        additionalText = itemView.findViewById(R.id.history_list_additional);
        itemView.setOnClickListener(
            v -> {
              int position = getBindingAdapterPosition();
              if (position == RecyclerView.NO_POSITION) {
                return;
              }
              HistoryItem item = items.get(position);
              if (!item.isHeader) {
                openActivity(item.id);
              }
            });
      }
    }
  }

  class HistoryDiffCallback extends DiffUtil.Callback {
    private final List<HistoryItem> oldList;
    private final List<HistoryItem> newList;

    HistoryDiffCallback(List<HistoryItem> oldList, List<HistoryItem> newList) {
      this.oldList = oldList;
      this.newList = newList;
    }

    @Override
    public int getOldListSize() {
      return oldList.size();
    }

    @Override
    public int getNewListSize() {
      return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
      return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
      HistoryItem oldItem = oldList.get(oldItemPosition);
      HistoryItem newItem = newList.get(newItemPosition);
      return oldItem.isHeader
          ? oldItem.sameHeader(newItem)
          : !newItem.isHeader && oldItem.sameRow(newItem);
    }
  }
}
