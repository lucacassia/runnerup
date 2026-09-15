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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.BestEffort;
import org.runnerup.db.DBHelper;
import org.runnerup.db.RecordUtils;
import org.runnerup.db.Statistics;
import org.runnerup.db.Statistics.BucketPeriod;
import org.runnerup.db.Statistics.Metric;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.workout.Sport;

public class HistoryFragment extends Fragment implements Constants, LoaderCallbacks<Cursor> {

  private static final int TYPE_HEADER = 0;
  private static final int TYPE_ROW = 1;

  private static final double[] RECORD_DISTANCES = {
    1000.0, 1609.344, 3218.688, 5000.0, 8046.72, 10000.0, 21097.5, 42195.0
  };
  private static final int[] RECORD_DISTANCE_STRINGS = {
    org.runnerup.R.string.records_1k,
    org.runnerup.R.string.records_1mi,
    org.runnerup.R.string.records_2mi,
    org.runnerup.R.string.records_5k,
    org.runnerup.R.string.records_5mi,
    org.runnerup.R.string.records_10k,
    org.runnerup.R.string.records_half,
    org.runnerup.R.string.records_full
  };
  private static final int[] RECORD_DISTANCE_COLORS = {
    org.runnerup.R.color.recordsRing1k,
    org.runnerup.R.color.recordsRing1mi,
    org.runnerup.R.color.recordsRing2mi,
    org.runnerup.R.color.recordsRing5k,
    org.runnerup.R.color.recordsRing5mi,
    org.runnerup.R.color.recordsRing10k,
    org.runnerup.R.color.recordsRingHalf,
    org.runnerup.R.color.recordsRingFull
  };
  private static final int RECORD_LONGEST_COLOR = org.runnerup.R.color.recordsRingLongest;

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
  private Metric currentMetric = Metric.DISTANCE;
  private boolean chartBarMode = false;
  private List<Statistics.ActivityRow> statisticsRows = null;
  private View statisticsContent;
  private View statisticsEmpty;
  private DistanceChartView statisticsChart;
  private TextView statisticsChartTitle;
  private TextView statistics7Value;
  private TextView statistics30Value;
  private TextView statistics365Value;
  private Integer currentSport = null; // null = all sports
  private final List<Chip> sportChips = new ArrayList<>();
  private final List<String> sportChipLabels = new ArrayList<>();

  private View recordsSection;
  private ViewGroup recordsGrid;
  private final Set<Long> recordHolderActivityIds = new HashSet<>();
  private long recordsFingerprint = -1L;
  private List<RecordInfo> recordsCache = null;

  private LocalDate shownMonth = null;
  private Map<LocalDate, List<Statistics.ActivityRow>> calendarByDay = new HashMap<>();
  private TextView calendarMonthLabel;
  private CalendarHeatmapView calendarHeatmap;

  private final ActivityResultLauncher<Intent> reloadLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            recordsFingerprint = -1L;
            LoaderManager.getInstance(this).restartLoader(0, null, this);
          });

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

    View emptyButton = view.findViewById(R.id.history_empty_button);
    emptyButton.setOnClickListener(
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
    statisticsChart.setLabelFormatter(this::formatChartValue);

    recordsSection = view.findViewById(R.id.records_section);
    recordsGrid = view.findViewById(R.id.records_grid);

    calendarMonthLabel = view.findViewById(R.id.calendar_month_label);
    calendarHeatmap = view.findViewById(R.id.calendar_heatmap);
    calendarHeatmap.setDayLabelFormatter(formatter::getDistanceDisplay);
    calendarHeatmap.setOnDayTapListener(this::openDay);
    view.findViewById(R.id.calendar_prev).setOnClickListener(v -> changeMonth(-1));
    view.findViewById(R.id.calendar_next).setOnClickListener(v -> changeMonth(1));

    ChipGroup chipGroup = view.findViewById(R.id.history_sport_chips);
    SharedPreferences sportPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    int savedSport =
        sportPrefs.getInt(getString(org.runnerup.common.R.string.pref_statistics_sport), -1);
    currentSport = savedSport >= 0 ? savedSport : null;
    String[] sportNames = Sport.getStringArray(getResources());
    Chip allChip = new Chip(context);
    allChip.setId(View.generateViewId());
    allChip.setText(getString(org.runnerup.common.R.string.Statistics_all_sports));
    allChip.setCheckable(true);
    allChip.setTag(null);
    chipGroup.addView(allChip);
    sportChips.add(allChip);
    sportChipLabels.add(getString(org.runnerup.common.R.string.Statistics_all_sports));
    for (int dbValue = 0; dbValue < sportNames.length; dbValue++) {
      Chip chip = new Chip(context);
      chip.setId(View.generateViewId());
      chip.setText(sportNames[dbValue]);
      chip.setCheckable(true);
      chip.setTag(dbValue);
      Drawable icon = AppCompatResources.getDrawable(context, Sport.drawableColored16Of(dbValue));
      if (icon != null) {
        icon.mutate();
        icon.setTint(ContextCompat.getColor(context, Sport.colorOf(dbValue)));
        chip.setChipIcon(icon);
      }
      chipGroup.addView(chip);
      sportChips.add(chip);
      sportChipLabels.add(sportNames[dbValue]);
    }
    Chip initial = (Chip) chipGroup.getChildAt(SportFilter.positionForSport(currentSport));
    initial.setChecked(true);
    chipGroup.setOnCheckedStateChangeListener(
        (group, checkedIds) -> {
          if (checkedIds.isEmpty()) {
            return;
          }
          Chip checked = group.findViewById(checkedIds.get(0));
          Integer sport = (Integer) checked.getTag();
          currentSport = sport;
          sportPrefs
              .edit()
              .putInt(
                  getString(org.runnerup.common.R.string.pref_statistics_sport),
                  currentSport == null ? -1 : currentSport)
              .apply();
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          if (currentTab == TAB_STATISTICS_INDEX) {
            loadStatistics();
            loadCalendar();
          }
          loadRecords();
          refreshSportBadges();
        });
    refreshSportBadges();
    loadRecords();
    if (currentSport != null) {
      LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    TabLayout historyTabs = view.findViewById(R.id.history_tabs);
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Activities));
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Progress));
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
              loadCalendar();
            }
            loadRecords();
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
                      : checkedId == R.id.statistics_toggle_year
                          ? BucketPeriod.YEAR
                          : BucketPeriod.DAY;
          currentPeriod = period;
          renderChart();
        });

    MaterialButtonToggleGroup metricToggle = view.findViewById(R.id.statistics_metric_toggle);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    int savedMetric =
        prefs.getInt(getString(org.runnerup.common.R.string.pref_statistics_metric), 0);
    currentMetric = Metric.values()[savedMetric];
    if (savedMetric != 0) {
      int checkedId =
          savedMetric == 1 ? R.id.statistics_metric_time : R.id.statistics_metric_elevation;
      metricToggle.check(checkedId);
    }
    metricToggle.addOnButtonCheckedListener(
        (group, checkedId, isChecked) -> {
          if (!isChecked) {
            return;
          }
          Metric metric;
          if (checkedId == R.id.statistics_metric_time) {
            metric = Metric.TIME;
          } else if (checkedId == R.id.statistics_metric_elevation) {
            metric = Metric.ELEVATION_GAIN;
          } else {
            metric = Metric.DISTANCE;
          }
          currentMetric = metric;
          prefs
              .edit()
              .putInt(
                  getString(org.runnerup.common.R.string.pref_statistics_metric), metric.ordinal())
              .apply();
          if (statisticsRows != null) {
            updateStatisticsCards();
            renderChart();
          }
        });

    ImageButton chartToggle = view.findViewById(R.id.statistics_chart_toggle);
    SharedPreferences chartPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    chartBarMode =
        chartPrefs.getBoolean(getString(org.runnerup.common.R.string.pref_statistics_chart), false);
    chartToggle.setOnClickListener(
        v -> {
          chartBarMode = !chartBarMode;
          chartPrefs
              .edit()
              .putBoolean(
                  getString(org.runnerup.common.R.string.pref_statistics_chart), chartBarMode)
              .apply();
          updateChartToggle(chartToggle);
          renderChart();
        });
    updateChartToggle(chartToggle);
  }

  @Override
  public void onResume() {
    super.onResume();
    LoaderManager.getInstance(this).restartLoader(0, null, this);
    if (currentTab == TAB_STATISTICS_INDEX) {
      loadStatistics();
      loadCalendar();
    }
    loadRecords();
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
    String selection = "deleted == 0";
    String[] args = null;
    if (currentSport != null) {
      selection += " AND " + DB.ACTIVITY.SPORT + " = ?";
      args = new String[] {Integer.toString(currentSport)};
    }
    return new SimpleCursorLoader(
        requireContext(),
        mDB,
        DB.ACTIVITY.TABLE,
        from,
        selection,
        args,
        DB.ACTIVITY.START_TIME + " desc");
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
    boolean empty = arg1 == null || arg1.getCount() == 0;
    if (emptyView != null) {
      emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
    if (fab != null) {
      fab.setVisibility(!empty && currentTab == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    }
    adapter.setData(arg1);
    refreshSportBadges();
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
    fab.setVisibility(
        index == TAB_HISTORY_INDEX && adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
    if (index == TAB_STATISTICS_INDEX) {
      loadStatistics();
      loadCalendar();
    }
    loadRecords();
  }

  private void loadStatistics() {
    if (mDB == null || statisticsContent == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          long now = System.currentTimeMillis() / 1000;
          long from =
              Statistics.bucketStarts(Statistics.BucketPeriod.YEAR, now, ZoneId.systemDefault())[0];
          List<Statistics.ActivityRow> rows = Statistics.queryActivities(mDB, from, currentSport);
          mainHandler.post(
              () -> {
                statisticsRows = rows;
                statisticsExecutor.execute(
                    () -> {
                      Statistics.computeMissingElevation(mDB, rows);
                      mainHandler.post(
                          () -> {
                            updateStatisticsCards();
                            renderChart();
                          });
                    });
              });
        });
  }

  private void loadCalendar() {
    if (mDB == null || calendarHeatmap == null) {
      return;
    }
    if (shownMonth == null) {
      shownMonth = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1);
    }
    statisticsExecutor.execute(
        () -> {
          List<Statistics.ActivityRow> rows = Statistics.queryActivities(mDB, 0L, currentSport);
          Map<LocalDate, List<Statistics.ActivityRow>> byDay =
              Statistics.groupActivitiesByDay(rows, ZoneId.systemDefault());
          mainHandler.post(
              () -> {
                calendarByDay = byDay;
                renderCalendar();
              });
        });
  }

  private void renderCalendar() {
    if (shownMonth == null || calendarHeatmap == null) {
      return;
    }
    calendarHeatmap.setData(Statistics.calendarDays(shownMonth, calendarByDay));
    calendarMonthLabel.setText(
        formatter.formatMonth(
            Date.from(shownMonth.atStartOfDay(ZoneId.systemDefault()).toInstant())));
  }

  private void changeMonth(int delta) {
    shownMonth = shownMonth.plusMonths(delta);
    renderCalendar();
  }

  private void openDay(int dayOfMonth) {
    List<Statistics.ActivityRow> dayRows = calendarByDay.get(shownMonth.withDayOfMonth(dayOfMonth));
    if (dayRows == null || dayRows.isEmpty()) {
      return;
    }
    if (dayRows.size() == 1) {
      openActivity(dayRows.get(0).id);
      return;
    }
    showDaySheet(dayRows);
  }

  private void showDaySheet(List<Statistics.ActivityRow> dayRows) {
    BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
    LinearLayout content = new LinearLayout(requireContext());
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(16);
    content.setPadding(pad, dp(8), pad, dp(8));

    TextView title = new TextView(requireContext());
    title.setText(formatter.formatDate(dayRows.get(0).startTime));
    title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    title.setTextSize(16);
    title.setPadding(0, 0, 0, dp(4));
    content.addView(title);

    for (Statistics.ActivityRow row : dayRows) {
      TextView item = new TextView(requireContext());
      item.setPadding(0, dp(6), 0, dp(6));
      item.setTextSize(14);
      Drawable icon =
          AppCompatResources.getDrawable(requireContext(), Sport.drawableColored16Of(row.sport));
      if (icon != null) {
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
      }
      String timeLabel =
          row.time != null
              ? formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(row.time))
              : "";
      item.setText(
          formatter.getDistanceDisplay(row.distance)
              + (timeLabel.isEmpty() ? "" : "  ·  " + timeLabel));
      item.setOnClickListener(
          v -> {
            sheet.dismiss();
            openActivity(row.id);
          });
      content.addView(item);
    }

    sheet.setContentView(content);
    sheet.show();
  }

  @SuppressLint("NotifyDataSetChanged")
  private void loadRecords() {
    if (mDB == null || recordsGrid == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          long fingerprint = computeRecordsFingerprint(mDB);
          List<RecordInfo> records;
          if (fingerprint == recordsFingerprint && recordsCache != null) {
            records = recordsCache;
          } else {
            records = computeRecords(mDB);
            recordsFingerprint = fingerprint;
            recordsCache = records;
          }
          mainHandler.post(
              () -> {
                if (recordsGrid == null) {
                  return;
                }
                renderRecordBadges(records);
                if (adapter != null) {
                  adapter.notifyDataSetChanged();
                }
              });
        });
  }

  private static List<RecordInfo> computeRecords(SQLiteDatabase db) {
    List<RecordInfo> records = new ArrayList<>();
    for (int sport = DB.ACTIVITY.SPORT_RUNNING; sport <= DB.ACTIVITY.SPORT_MAX; sport++) {
      if (sport == DB.ACTIVITY.SPORT_RUNNING) {
        double longestDistance = queryLongestDistance(db, sport);
        List<RunTrack> tracks = loadRunTracks(db, sport);
        for (int i = 0; i < RECORD_DISTANCES.length; i++) {
          if (longestDistance < RecordUtils.bandLower(RECORD_DISTANCES[i])) {
            break;
          }
          RecordInfo record = queryBestRunningRecord(db, sport, RECORD_DISTANCES[i], tracks);
          if (record != null) {
            record.recordLabelRes = RECORD_DISTANCE_STRINGS[i];
            record.ringColorRes = RECORD_DISTANCE_COLORS[i];
            records.add(record);
          }
        }
      }
      boolean byDistance = !Sport.isWithoutGps(sport);
      RecordInfo longest = queryLongestRecord(db, sport, byDistance);
      if (longest != null) {
        longest.recordLabelRes = org.runnerup.R.string.records_longest;
        longest.ringColorRes = RECORD_LONGEST_COLOR;
        longest.medalShowsDistance = byDistance;
        records.add(longest);
      }
    }
    return records;
  }

  private static long computeRecordsFingerprint(SQLiteDatabase db) {
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            new String[] {DB.PRIMARY_KEY},
            DB.ACTIVITY.DELETED + " == 0",
            null,
            null,
            null,
            null,
            null)) {
      long maxId = 0L;
      while (cursor.moveToNext()) {
        maxId = Math.max(maxId, cursor.getLong(0));
      }
      return ((long) cursor.getCount() << 32) | (maxId & 0xFFFFFFFFL);
    }
  }

  private static double queryLongestDistance(SQLiteDatabase db, int sport) {
    String selection = DB.ACTIVITY.DELETED + " == 0 AND " + DB.ACTIVITY.SPORT + " = ?";
    String[] args = {Integer.toString(sport)};
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            new String[] {DB.ACTIVITY.DISTANCE},
            selection,
            args,
            null,
            null,
            DB.ACTIVITY.DISTANCE + " desc",
            "1")) {
      if (!cursor.moveToFirst() || cursor.isNull(0)) {
        return 0;
      }
      return cursor.getDouble(0);
    }
  }

  private static List<RunTrack> loadRunTracks(SQLiteDatabase db, int sport) {
    List<RunTrack> tracks = new ArrayList<>();
    String[] from = {DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME};
    String selection = DB.ACTIVITY.DELETED + " == 0 AND " + DB.ACTIVITY.SPORT + " = ?";
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            from,
            selection,
            new String[] {Integer.toString(sport)},
            null,
            null,
            null,
            null)) {
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        BestEffort.Points points = loadTrackPoints(db, id);
        if (points != null) {
          tracks.add(new RunTrack(id, cursor.getLong(1), points));
        }
      }
    }
    return tracks;
  }

  private static BestEffort.Points loadTrackPoints(SQLiteDatabase db, long activityId) {
    List<Double> dist = new ArrayList<>();
    List<Long> time = new ArrayList<>();
    List<Long> elapsed = new ArrayList<>();
    LocationEntity.LocationList<LocationEntity> list =
        new LocationEntity.LocationList<>(db, activityId);
    try {
      for (LocationEntity point : list) {
        Double d = point.getDistance();
        Long t = point.getTime();
        Long e = point.getElapsed();
        if (d != null && t != null && e != null) {
          dist.add(d);
          time.add(t);
          elapsed.add(e);
        }
      }
    } finally {
      list.close();
    }
    if (dist.size() < 2) {
      return null;
    }
    double[] distArr = new double[dist.size()];
    long[] timeArr = new long[time.size()];
    long[] elapsedArr = new long[elapsed.size()];
    for (int i = 0; i < dist.size(); i++) {
      distArr[i] = dist.get(i);
      timeArr[i] = time.get(i);
      elapsedArr[i] = elapsed.get(i);
    }
    return new BestEffort.Points(distArr, timeArr, elapsedArr);
  }

  private static RecordInfo queryLongestRecord(SQLiteDatabase db, int sport, boolean byDistance) {
    String selection = DB.ACTIVITY.DELETED + " == 0 AND " + DB.ACTIVITY.SPORT + " = ?";
    if (byDistance) {
      selection +=
          " AND " + DB.ACTIVITY.DISTANCE + " IS NOT NULL AND " + DB.ACTIVITY.TIME + " IS NOT NULL";
    } else {
      selection += " AND " + DB.ACTIVITY.TIME + " IS NOT NULL";
    }
    String orderBy = byDistance ? DB.ACTIVITY.DISTANCE + " desc" : DB.ACTIVITY.TIME + " desc";
    String[] from = {
      DB.PRIMARY_KEY, DB.ACTIVITY.TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.START_TIME
    };
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            from,
            selection,
            new String[] {Integer.toString(sport)},
            null,
            null,
            orderBy,
            "1")) {
      if (!cursor.moveToFirst()) {
        return null;
      }
      RecordInfo info = new RecordInfo();
      info.activityId = cursor.getLong(0);
      info.time = cursor.isNull(1) ? 0 : cursor.getLong(1);
      info.distance = cursor.isNull(2) ? 0 : cursor.getDouble(2);
      info.startTime = cursor.getLong(3);
      info.sport = sport;
      return info;
    }
  }

  private static RecordInfo queryWholeRunBandRecord(SQLiteDatabase db, int sport, double distance) {
    String selection =
        DB.ACTIVITY.DELETED
            + " == 0 AND "
            + DB.ACTIVITY.SPORT
            + " = ? AND "
            + DB.ACTIVITY.DISTANCE
            + " >= ? AND "
            + DB.ACTIVITY.DISTANCE
            + " <= ? AND "
            + DB.ACTIVITY.TIME
            + " IS NOT NULL AND "
            + DB.ACTIVITY.START_TIME
            + " IS NOT NULL";
    String[] args = {
      Integer.toString(sport),
      Double.toString(RecordUtils.bandLower(distance)),
      Double.toString(RecordUtils.bandUpper(distance))
    };
    String[] from = {
      DB.PRIMARY_KEY, DB.ACTIVITY.TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.START_TIME
    };
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE, from, selection, args, null, null, DB.ACTIVITY.TIME + " asc", "1")) {
      if (!cursor.moveToFirst()) {
        return null;
      }
      RecordInfo info = new RecordInfo();
      info.activityId = cursor.getLong(0);
      info.time = cursor.getLong(1);
      info.distance = cursor.getDouble(2);
      info.startTime = cursor.getLong(3);
      info.sport = sport;
      return info;
    }
  }

  private static RecordInfo queryBestRunningRecord(
      SQLiteDatabase db, int sport, double distance, List<RunTrack> tracks) {
    long bestTimeMs = -1L;
    long bestActivity = 0L;
    long bestStart = 0L;
    for (RunTrack track : tracks) {
      BestEffort.Points points = track.points;
      if (points.distanceM[points.distanceM.length - 1] < distance) {
        continue;
      }
      long effortMs = BestEffort.bestEffort(points, distance);
      if (effortMs >= 0 && (bestTimeMs < 0 || effortMs < bestTimeMs)) {
        bestTimeMs = effortMs;
        bestActivity = track.activityId;
        bestStart = track.startTime;
      }
    }
    RecordInfo wholeRun = queryWholeRunBandRecord(db, sport, distance);
    if (wholeRun != null && (bestTimeMs < 0 || wholeRun.time * 1000L < bestTimeMs)) {
      bestTimeMs = wholeRun.time * 1000L;
      bestActivity = wholeRun.activityId;
      bestStart = wholeRun.startTime;
    }
    if (bestTimeMs < 0) {
      return null;
    }
    RecordInfo info = new RecordInfo();
    info.activityId = bestActivity;
    info.time = Math.round(bestTimeMs / 1000.0);
    info.distance = distance;
    info.startTime = bestStart;
    info.sport = sport;
    return info;
  }

  private void renderRecordBadges(List<RecordInfo> records) {
    recordsGrid.removeAllViews();
    recordHolderActivityIds.clear();
    int headerSport = -1;
    List<RecordInfo> group = new ArrayList<>();
    for (RecordInfo record : records) {
      if (currentSport != null && record.sport != currentSport) {
        continue;
      }
      if (currentSport == null && record.sport != headerSport) {
        flushRecordGroup(group);
        group.clear();
        recordsGrid.addView(buildRecordSportHeader(record.sport));
        headerSport = record.sport;
      }
      recordHolderActivityIds.add(record.activityId);
      group.add(record);
    }
    flushRecordGroup(group);
    recordsSection.setVisibility(recordHolderActivityIds.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void flushRecordGroup(List<RecordInfo> group) {
    for (int i = 0; i < group.size(); i += 2) {
      addBadgeRow(group, i);
    }
  }

  private void addBadgeRow(List<RecordInfo> group, int start) {
    LinearLayout row = new LinearLayout(requireContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams rowLp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rowLp.bottomMargin = dp(8);
    recordsGrid.addView(row, rowLp);
    for (int i = start; i < group.size() && i < start + 2; i++) {
      View badge = buildBadgeCard(group.get(i), row);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      if (i % 2 == 0) {
        lp.setMarginEnd(dp(4));
      } else {
        lp.setMarginStart(dp(4));
      }
      row.addView(badge, lp);
    }
  }

  private View buildRecordSportHeader(int sport) {
    View header =
        LayoutInflater.from(requireContext())
            .inflate(R.layout.history_section_header, recordsGrid, false);
    TextView title = header.findViewById(R.id.history_section_title);
    title.setText(Sport.textOf(getResources(), sport));
    return header;
  }

  private View buildBadgeCard(RecordInfo record, ViewGroup parent) {
    View card = LayoutInflater.from(requireContext()).inflate(R.layout.record_badge, parent, false);
    TextView label = card.findViewById(R.id.record_badge_label);
    TextView time = card.findViewById(R.id.record_badge_time);
    TextView meta = card.findViewById(R.id.record_badge_meta);
    View ribbon = card.findViewById(R.id.record_badge_ribbon);
    int ringColor = ContextCompat.getColor(requireContext(), record.ringColorRes);

    label.setText(record.recordLabelRes);
    if (record.medalShowsDistance) {
      time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      time.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(record.distance)));
    } else {
      time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      time.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, record.time));
    }
    time.setTextColor(ringColor);

    StringBuilder metaText = new StringBuilder(formatter.formatDate(record.startTime));
    if (record.medalShowsDistance && record.time > 0) {
      metaText
          .append(" · ")
          .append(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, record.time));
    } else if (!record.medalShowsDistance && record.time > 0 && record.distance > 0) {
      metaText
          .append(" · ")
          .append(
              formatter.formatVelocityByPreferredUnit(
                  Formatter.Format.TXT_LONG, record.distance / record.time));
    }
    meta.setText(metaText);

    GradientDrawable ring = new GradientDrawable();
    ring.setShape(GradientDrawable.OVAL);
    ring.setColor(Color.WHITE);
    ring.setStroke(dp(4), ringColor);
    time.setBackground(ring);

    GradientDrawable ribbonDrawable =
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {ringColor, lighten(ringColor), ringColor});
    ribbonDrawable.setCornerRadius(dp(3));
    ribbon.setBackground(ribbonDrawable);

    card.setOnClickListener(v -> openActivity(record.activityId));
    return card;
  }

  private int dp(int value) {
    return Math.round(getResources().getDisplayMetrics().density * value);
  }

  private static int lighten(int color) {
    return Color.rgb(
        (Color.red(color) + 255) / 2,
        (Color.green(color) + 255) / 2,
        (Color.blue(color) + 255) / 2);
  }

  private static final class RecordInfo {
    long activityId;
    long time;
    double distance;
    long startTime;
    int sport;
    int recordLabelRes;
    int ringColorRes;
    boolean medalShowsDistance;
  }

  private static final class RunTrack {
    final long activityId;
    final long startTime;
    final BestEffort.Points points;

    RunTrack(long activityId, long startTime, BestEffort.Points points) {
      this.activityId = activityId;
      this.startTime = startTime;
      this.points = points;
    }
  }

  private void refreshSportBadges() {
    if (mDB == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          int[] counts = Statistics.sportCounts(mDB);
          mainHandler.post(
              () -> {
                applySportBadges(counts);
              });
        });
  }

  private void applySportBadges(int[] counts) {
    if (!isAdded()) {
      return;
    }
    int allSportsColor =
        ContextCompat.getColor(requireContext(), org.runnerup.R.color.historyBadgeAllSports);
    for (int i = 0; i < sportChips.size(); i++) {
      Chip chip = sportChips.get(i);
      Integer sport = (Integer) chip.getTag();
      SportCountBadge.Badge badge = SportCountBadge.forSport(sport, counts);
      if (badge == null) {
        chip.setText(sportChipLabels.get(i));
        continue;
      }
      int pillColor =
          sport == null
              ? allSportsColor
              : ContextCompat.getColor(requireContext(), Sport.colorOf(sport));
      chip.setText(
          buildChipText(
              sportChipLabels.get(i),
              pillColor,
              badge.count,
              getResources().getDisplayMetrics().density));
    }
  }

  private CharSequence buildChipText(String label, int pillColor, int count, float density) {
    SpannableStringBuilder sb = new SpannableStringBuilder(label);
    sb.append(" ");
    int start = sb.length();
    sb.append(Integer.toString(count));
    int end = sb.length();
    sb.setSpan(new PillSpan(pillColor, density), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return sb;
  }

  private void renderChart() {
    if (statisticsRows == null) {
      return;
    }
    long now = System.currentTimeMillis() / 1000;
    double[] buckets =
        Statistics.bucketize(
            statisticsRows, currentMetric, currentPeriod, now, ZoneId.systemDefault());
    long[] starts = Statistics.bucketStarts(currentPeriod, now, ZoneId.systemDefault());
    statisticsChartTitle.setText(chartTitleFor(currentPeriod));
    statisticsChart.setData(buckets, buildXLabels(currentPeriod, starts));
    statisticsChart.setBarMode(chartBarMode);
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

  private void updateChartToggle(ImageButton button) {
    button.setImageResource(chartBarMode ? R.drawable.ic_chart_line : R.drawable.ic_chart_bar);
    button.setContentDescription(
        getString(
            chartBarMode
                ? org.runnerup.common.R.string.Statistics_switch_to_line
                : org.runnerup.common.R.string.Statistics_switch_to_bars));
  }

  private void updateStatisticsCards() {
    if (statisticsRows == null) {
      return;
    }
    long now = System.currentTimeMillis() / 1000;
    double[] totals = Statistics.totals(statisticsRows, currentMetric, now, ZoneId.systemDefault());
    switch (currentMetric) {
      case TIME:
        long t7 = Math.round(totals[0] / 60.0) * 60;
        long t30 = Math.round(totals[1] / 60.0) * 60;
        long t365 = Math.round(totals[2] / 60.0) * 60;
        statistics7Value.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, t7));
        statistics30Value.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, t30));
        statistics365Value.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, t365));
        break;
      case ELEVATION_GAIN:
        statistics7Value.setText(
            formatter.formatElevation(Formatter.Format.TXT_LONG, Math.round(totals[0])));
        statistics30Value.setText(
            formatter.formatElevation(Formatter.Format.TXT_LONG, Math.round(totals[1])));
        statistics365Value.setText(
            formatter.formatElevation(Formatter.Format.TXT_LONG, Math.round(totals[2])));
        break;
      case DISTANCE:
      default:
        statistics7Value.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[0])));
        statistics30Value.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[1])));
        statistics365Value.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(totals[2])));
        break;
    }
  }

  private String formatChartValue(double value) {
    switch (currentMetric) {
      case TIME:
        return formatter.formatElapsedTime(
            Formatter.Format.TXT_LONG, Math.round(value / 60.0) * 60);
      case ELEVATION_GAIN:
        return formatter.formatElevation(Formatter.Format.TXT_LONG, Math.round(value));
      case DISTANCE:
      default:
        return formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value));
    }
  }

  private String[] buildXLabels(BucketPeriod period, long[] starts) {
    String[] labels = new String[starts.length];
    for (int i = 0; i < starts.length; i++) {
      Date date = new Date(starts[i] * 1000);
      labels[i] =
          period == BucketPeriod.MONTH
              ? formatter.formatMonthShort(date)
              : period == BucketPeriod.YEAR
                  ? formatter.formatYear(date)
                  : formatter.formatDayOfMonth(date);
    }
    return labels;
  }

  private int chartTitleFor(BucketPeriod period) {
    switch (period) {
      case WEEK:
        return org.runnerup.common.R.string.Statistics_last_12_weeks;
      case MONTH:
        return org.runnerup.common.R.string.Statistics_last_12_months;
      case YEAR:
        return org.runnerup.common.R.string.Statistics_last_12_years;
      case DAY:
      default:
        return org.runnerup.common.R.string.Statistics_last_12_days;
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
      TypedValue tv = new TypedValue();
      context
          .getTheme()
          .resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
      int secondaryColor = tv.data;
      Drawable sportDrawable =
          AppCompatResources.getDrawable(context, Sport.drawableColored16Of(item.sport));
      holder.emblem.setImageDrawable(sportDrawable);
      holder.emblem.setColorFilter(sportColor);
      holder.distanceText.setTextColor(sportColor);

      if (item.avgHr != null) {
        holder.additionalText.setText(
            formatter.formatHeartRate(Formatter.Format.TXT_SHORT, item.avgHr));
        holder.additionalText.setTextColor(secondaryColor);
        Drawable hrIcon = AppCompatResources.getDrawable(context, R.drawable.ic_history_hr);
        if (hrIcon != null) {
          hrIcon.setTint(secondaryColor);
        }
        holder.additionalText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            hrIcon, null, null, null);
      } else {
        holder.additionalText.setText(null);
        holder.additionalText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null, null, null, null);
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

      if (recordHolderActivityIds.contains(item.id)) {
        Drawable trophy = AppCompatResources.getDrawable(context, R.drawable.ic_trophy_20dp);
        if (trophy != null) {
          trophy = trophy.mutate();
          TypedValue trophyTint = new TypedValue();
          context
              .getTheme()
              .resolveAttribute(com.google.android.material.R.attr.colorTertiary, trophyTint, true);
          trophy.setTint(trophyTint.data);
          holder.distanceText.setCompoundDrawablesRelativeWithIntrinsicBounds(
              trophy, null, holder.distanceText.getCompoundDrawablesRelative()[2], null);
        }
      } else {
        holder.distanceText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null, null, holder.distanceText.getCompoundDrawablesRelative()[2], null);
      }
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
