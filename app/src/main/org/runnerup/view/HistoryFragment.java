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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.BestEffort;
import org.runnerup.db.DBHelper;
import org.runnerup.db.RecordUtils;
import org.runnerup.db.Statistics;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

public class HistoryFragment extends Fragment implements Constants {

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

  private HistoryViewModel historyViewModel;
  private ProgressViewModel progressViewModel;
  private boolean historyListEmpty = true;
  View fab = null;
  View emptyView = null;

  private static final int TAB_HISTORY_INDEX = 0;
  private static final int TAB_STATISTICS_INDEX = 1;

  private final ExecutorService statisticsExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private int currentTab = TAB_HISTORY_INDEX;
  private ComposeView statisticsContent;
  private Integer currentSport = null; // null = all sports
  private final List<Chip> sportChips = new ArrayList<>();
  private final List<String> sportChipLabels = new ArrayList<>();

  private long recordsFingerprint = -1L;
  private List<ProgressRecord> recordsCache = null;

  private final ActivityResultLauncher<Intent> reloadLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            recordsFingerprint = -1L;
            if (historyViewModel != null) {
              historyViewModel.load(currentSport);
            }
          });

  public HistoryFragment() {
    super(R.layout.history);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ComposeView listView = view.findViewById(R.id.history_list);
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

    historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
    HistoryScreenHost.INSTANCE.attach(
        listView, historyViewModel, this::openActivity, this::onHistoryStateChanged);

    statisticsContent = view.findViewById(R.id.statistics_content);
    progressViewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
    ProgressScreenHost.INSTANCE.attach(
        statisticsContent, progressViewModel, this::openActivity, this::openDay);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    new ActivityCleaner().conditionalRecompute(mDB);

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
          historyViewModel.load(currentSport);
          if (currentTab == TAB_STATISTICS_INDEX) {
            progressViewModel.load(currentSport);
          }
          loadRecords();
          refreshSportBadges();
        });
    refreshSportBadges();
    loadRecords();
    progressViewModel.load(currentSport);
    historyViewModel.load(currentSport);

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
              progressViewModel.load(currentSport);
            }
            loadRecords();
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    historyViewModel.load(currentSport);
    if (currentTab == TAB_STATISTICS_INDEX) {
      progressViewModel.load(currentSport);
    }
    loadRecords();
  }

  @Override
  public void onDestroy() {
    statisticsExecutor.shutdownNow();
    super.onDestroy();
    DBHelper.closeDB(mDB);
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
    fab.setVisibility(index == TAB_HISTORY_INDEX && !historyListEmpty ? View.VISIBLE : View.GONE);
    if (index == TAB_STATISTICS_INDEX) {
      progressViewModel.load(currentSport);
    }
    loadRecords();
  }

  private void onHistoryStateChanged(HistoryUiState state) {
    historyListEmpty = state.getRows().isEmpty();
    if (emptyView != null) {
      emptyView.setVisibility(state.getLoaded() && historyListEmpty ? View.VISIBLE : View.GONE);
    }
    if (fab != null) {
      fab.setVisibility(
          !historyListEmpty && currentTab == TAB_HISTORY_INDEX ? View.VISIBLE : View.GONE);
    }
    refreshSportBadges();
  }

  private void openDay(int dayOfMonth) {
    List<Statistics.ActivityRow> dayRows = progressViewModel.activitiesOn(dayOfMonth);
    if (dayRows.isEmpty()) {
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
      String distanceLabel =
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(row.distance));
      if (timeLabel.isEmpty()) {
        item.setText(distanceLabel);
      } else {
        item.setText(getString(org.runnerup.R.string.calendar_run_item, distanceLabel, timeLabel));
      }
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

  private int dp(int value) {
    return Math.round(getResources().getDisplayMetrics().density * value);
  }

  private void loadRecords() {
    if (mDB == null) {
      return;
    }
    statisticsExecutor.execute(
        () -> {
          long fingerprint = computeRecordsFingerprint(mDB);
          List<ProgressRecord> records;
          if (fingerprint == recordsFingerprint && recordsCache != null) {
            records = recordsCache;
          } else {
            records = computeRecords(mDB);
            recordsFingerprint = fingerprint;
            recordsCache = records;
          }
          mainHandler.post(
              () -> {
                if (progressViewModel == null) {
                  return;
                }
                progressViewModel.setRecords(records, currentSport);
                if (historyViewModel != null) {
                  historyViewModel.setRecordHolders(progressViewModel.recordHolderIds());
                }
              });
        });
  }

  private static List<ProgressRecord> computeRecords(SQLiteDatabase db) {
    List<ProgressRecord> records = new ArrayList<>();
    for (int sport = DB.ACTIVITY.SPORT_RUNNING; sport <= DB.ACTIVITY.SPORT_MAX; sport++) {
      if (sport == DB.ACTIVITY.SPORT_RUNNING) {
        double longestDistance = queryLongestDistance(db, sport);
        List<RunTrack> tracks = loadRunTracks(db, sport);
        for (int i = 0; i < RECORD_DISTANCES.length; i++) {
          if (longestDistance < RecordUtils.bandLower(RECORD_DISTANCES[i])) {
            break;
          }
          ProgressRecord record = queryBestRunningRecord(db, sport, RECORD_DISTANCES[i], tracks);
          if (record != null) {
            record.recordLabelRes = RECORD_DISTANCE_STRINGS[i];
            record.ringColorRes = RECORD_DISTANCE_COLORS[i];
            records.add(record);
          }
        }
      }
      boolean byDistance = !Sport.isWithoutGps(sport);
      ProgressRecord longest = queryLongestRecord(db, sport, byDistance);
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

  private static ProgressRecord queryLongestRecord(
      SQLiteDatabase db, int sport, boolean byDistance) {
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
      ProgressRecord info = new ProgressRecord();
      info.activityId = cursor.getLong(0);
      info.time = cursor.isNull(1) ? 0 : cursor.getLong(1);
      info.distance = cursor.isNull(2) ? 0 : cursor.getDouble(2);
      info.startTime = cursor.getLong(3);
      info.sport = sport;
      return info;
    }
  }

  private static ProgressRecord queryWholeRunBandRecord(
      SQLiteDatabase db, int sport, double distance) {
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
      ProgressRecord info = new ProgressRecord();
      info.activityId = cursor.getLong(0);
      info.time = cursor.getLong(1);
      info.distance = cursor.getDouble(2);
      info.startTime = cursor.getLong(3);
      info.sport = sport;
      return info;
    }
  }

  private static ProgressRecord queryBestRunningRecord(
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
    ProgressRecord wholeRun = queryWholeRunBandRecord(db, sport, distance);
    if (wholeRun != null && (bestTimeMs < 0 || wholeRun.time * 1000L < bestTimeMs)) {
      bestTimeMs = wholeRun.time * 1000L;
      bestActivity = wholeRun.activityId;
      bestStart = wholeRun.startTime;
    }
    if (bestTimeMs < 0) {
      return null;
    }
    ProgressRecord info = new ProgressRecord();
    info.activityId = bestActivity;
    info.time = Math.round(bestTimeMs / 1000.0);
    info.distance = distance;
    info.startTime = bestStart;
    info.sport = sport;
    return info;
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

  private void openActivity(long id) {
    Intent intent = new Intent(requireContext(), DetailActivity.class);
    intent.putExtra("ID", id);
    intent.putExtra("mode", "details");
    reloadLauncher.launch(intent);
  }
}
