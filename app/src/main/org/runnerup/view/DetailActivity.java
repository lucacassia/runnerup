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

import static org.runnerup.content.ActivityProvider.GPX_MIME;
import static org.runnerup.content.ActivityProvider.TCX_MIME;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.ActivityProvider;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Feature;
import org.runnerup.util.Bitfield;
import org.runnerup.util.FileNameHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.GraphWrapper;
import org.runnerup.util.MapViewWrapper;
import org.runnerup.util.MapWrapper;
import org.runnerup.util.SafeParse;
import org.runnerup.widget.MaterialTitleSpinner;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Sport;

public class DetailActivity extends AppCompatActivity implements Constants {
  private long mID;
  private SQLiteDatabase mDB = null;
  private final HashSet<String> pendingSynchronizers = new HashSet<>();
  private final HashSet<String> alreadySynched = new HashSet<>();
  private final Map<String, String> synchedExternalId = new HashMap<>();

  private boolean lapHrPresent = false;
  private ContentValues[] laps = null;
  private final ArrayList<ContentValues> reports = new ArrayList<>();

  private int mode; // 0 == save 1 == details
  private static final int MODE_SAVE = 0;
  private static final int MODE_DETAILS = 1;
  private boolean edit = false;
  private boolean uploading = false;

  private DetailPageState pageState = null;
  private ComposeView rootView;
  private MaterialTitleSpinner sport = null;
  private MaterialTitleSpinner manualDistance = null;
  private EditText notes = null;
  private View hrZonesBarLayout;
  private LinearLayout graphView;

  private MapWrapper mapWrapper = null;
  private GraphWrapper graphWrapper = null;

  private SyncManager syncManager = null;
  private Formatter formatter = null;

  /** Inset of the toolbar overflow popup, it is drawn 8px from the right edge of the window. */
  private static final int overflowPopupInset = 8;

  private View overflowAnchor = null;
  private PopupMenu overflowMenu = null;

  private long mStartTime = 0; // activity start time in unix timestamp
  private ContentValues headerData = new ContentValues();

  private final ActivityResultLauncher<Intent> editAccountLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> requery());

  private final ActivityResultLauncher<Intent> configureLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (syncManager != null) {
              syncManager.handleAuthResult(result.getResultCode(), result.getData());
            }
            requery();
          });

  /**
   * The platform widgets of the tab panes are hosted by Compose, they take their layout params from
   * it instead of from a parent.
   */
  @SuppressLint("InflateParams")
  private View inflateUnparented(int resId) {
    return LayoutInflater.from(this).inflate(resId, null);
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
      // MapBox or Osmdroid, set mapWrapper.
      MapWrapper.start(this);
    }
    setContentView(R.layout.detail);
    rootView = findViewById(R.id.detail_view);

    Intent intent = getIntent();
    mID = intent.getLongExtra("ID", -1);
    String intentMode = intent.getStringExtra("mode");

    mDB = DBHelper.getReadableDatabase(this);
    syncManager = new SyncManager(this);
    syncManager.setConfigureLauncher(configureLauncher);
    formatter = new Formatter(this);

    if (intentMode.contentEquals("save")) {
      this.mode = MODE_SAVE;
    } else if (intentMode.contentEquals("details")) {
      this.mode = MODE_DETAILS;
    } else {
      if (BuildConfig.DEBUG) {
        throw new AssertionError();
      }
    }

    sport = (MaterialTitleSpinner) inflateUnparented(R.layout.detail_notes_sport);
    sport.setOnSetValueListener(
        new OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            return newValue;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            updateViewForSport(newValue);
            headerData.put(DB.ACTIVITY.SPORT, newValue);
            return newValue;
          }
        });

    sport.setArrayEntries(Sport.getStringArray(getResources()));

    manualDistance = (MaterialTitleSpinner) inflateUnparented(R.layout.detail_notes_distance);
    manualDistance.setOnSetValueListener(
        new OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            double dist = SafeParse.parseDouble(newValue, 0); // convert to meters
            headerData.put(DB.ACTIVITY.DISTANCE, dist);
            updateHeader(headerData, /* fromManualDistance= */ true);
            return newValue;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
          }
        });

    View notesField = inflateUnparented(R.layout.detail_notes_field);
    notes = notesField.findViewById(R.id.notes_text);
    hrZonesBarLayout = inflateUnparented(R.layout.detail_hrzones);
    graphView = new LinearLayout(this);
    graphView.setOrientation(LinearLayout.VERTICAL);

    MapViewWrapper mapView = null;
    if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
      mapView = new MapViewWrapper(this);
      mapWrapper = new MapWrapper(this, mDB, mID, formatter, mapView);
      mapWrapper.onCreate(savedInstanceState);
    }

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (uploading) {
                  // Ignore while uploading
                  return;
                }
                if (mode == MODE_SAVE) {
                  return;
                } else {
                  finish();
                }
              }
            });

    boolean use_distance_as_x = !Sport.isWithoutGps(sport.getValueInt());
    graphWrapper =
        new GraphWrapper(
            this,
            graphView,
            (LinearLayout) hrZonesBarLayout,
            formatter,
            mDB,
            mID,
            use_distance_as_x);

    pageState = new DetailPageState();
    DetailScreenHost.INSTANCE.attach(
        rootView,
        pageState,
        new DetailBindings(
            sport,
            manualDistance,
            notesField,
            hrZonesBarLayout,
            mapView,
            graphView,
            () -> getOnBackPressedDispatcher().onBackPressed(),
            this::onMenuItem,
            this::showOverflowMenu,
            () -> discardButtonClick.onClick(null),
            () -> saveButtonClick.onClick(null),
            () -> uploadButtonClick.onClick(null),
            () -> {
              boolean visible = !mapWrapper.areIntervalMarkersVisible();
              mapWrapper.setIntervalMarkersVisible(visible);
              pageState.setMapLapMarkersVisible(visible);
            },
            (name, checked) -> onSendChecked(name, checked),
            this::clearUploadClick,
            this::onClickAccountName,
            () ->
                editAccountLauncher.launch(
                    new Intent(DetailActivity.this, AccountListActivity.class))));

    if (this.mode == MODE_SAVE) {
      setEdit(true);
    } else if (this.mode == MODE_DETAILS) {
      setEdit(false);
    }

    fillHeaderData();
    requery();
  }

  private void setEdit(boolean value) {
    edit = value;
    pageState.setSaveVisible(value);
    pageState.setDiscardVisible(mode == MODE_SAVE);
    WidgetUtil.setEditable(notes, value);
    sport.setEnabled(value);
    updateViewForSport(sport.getValueInt());
  }

  private void updateViewForSport(int sportValue) {
    if (edit && Sport.hasManualDistance(sportValue)) {
      pageState.setManualDistanceVisible(true);
      manualDistance.setEnabled(true);
    } else {
      pageState.setManualDistanceVisible(false);
    }

    if (mapWrapper != null) {
      pageState.setMapTabVisible(!Sport.isWithoutGps(sportValue));
    }
    if (graphWrapper != null) {
      boolean use_distance_as_x = !Sport.isWithoutGps(sportValue);
      graphWrapper.setUseDistanceAsX(use_distance_as_x);
    }
  }

  private void setUploadVisibility() {
    pageState.setUploadVisible(!pendingSynchronizers.isEmpty());
  }

  /**
   * Shows the detail overflow menu. The button itself lives in the Compose toolbar, so an invisible
   * zero sized view is used as the anchor of the popup. A toolbar places its overflow popup at the
   * top right of the window, overlapping the toolbar instead of dropping below the button, hence
   * the anchor is the button corner moved up and in by the popup inset.
   */
  private void showOverflowMenu(int left, int top, int right, int bottom) {
    ViewGroup decor = (ViewGroup) getWindow().getDecorView();
    if (overflowAnchor == null) {
      overflowAnchor = new View(this);
      overflowAnchor.setVisibility(View.INVISIBLE);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
      params.gravity = Gravity.TOP | Gravity.START;
      params.leftMargin = right - overflowPopupInset;
      params.topMargin = top;
      decor.addView(overflowAnchor, params);

      overflowMenu = new PopupMenu(this, overflowAnchor, Gravity.END);
      overflowMenu.setOnMenuItemClickListener(
          item -> {
            onMenuItem(item.getItemId());
            return true;
          });
      overflowMenu.getMenuInflater().inflate(R.menu.detail_menu, overflowMenu.getMenu());
    }
    overflowMenu.show();
  }

  private void onMenuItem(int id) {
    if (id == R.id.menu_delete_activity) {
      deleteButtonClick.onClick(null);
    } else if (id == R.id.menu_edit_activity) {
      if (!edit) {
        setEdit(true);
        notes.requestFocus();
        requery();
      }
    } else if (id == R.id.menu_recompute_activity) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(org.runnerup.common.R.string.Recompute_activity)
          .setMessage(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                new ActivityCleaner().recompute(mDB, mID);
                requery();
                fillHeaderData();
                finish();
              })
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else if (id == R.id.menu_simplify_path) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(org.runnerup.common.R.string.path_simplification_menu)
          .setMessage(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                PathSimplifier simplifier = new PathSimplifier(this);
                ArrayList<String> ids = simplifier.getNoisyLocationIDsAsStrings(mDB, mID);
                ActivityCleaner.deleteLocations(mDB, ids);
                new ActivityCleaner().recompute(mDB, mID);
                requery();
                fillHeaderData();
                finish();
              })
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else if (id == R.id.menu_share_activity) {
      shareActivity();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (mapWrapper != null) {
      mapWrapper.onResume();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (mapWrapper != null) {
      mapWrapper.onStart();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (mapWrapper != null) {
      mapWrapper.onStop();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (mapWrapper != null) {
      mapWrapper.onPause();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mapWrapper != null) {
      mapWrapper.onSaveInstanceState(outState);
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    if (mapWrapper != null) {
      mapWrapper.onLowMemory();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
    syncManager.close();
    if (mapWrapper != null) {
      mapWrapper.onDestroy();
    }
  }

  private void requery() {
    {
      /*
       * Laps
       */
      String[] from =
          new String[] {
            "_id",
            DB.LAP.LAP,
            DB.LAP.INTENSITY,
            DB.LAP.TIME,
            DB.LAP.DISTANCE,
            DB.LAP.PLANNED_TIME,
            DB.LAP.PLANNED_DISTANCE,
            DB.LAP.PLANNED_PACE,
            DB.LAP.AVG_HR
          };

      Cursor c =
          mDB.query(
              DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + mID, null, null, null, "_id", null);

      laps = DBHelper.toArray(c);
      c.close();
      lapHrPresent = false;
      for (ContentValues v : laps) {
        if (v.containsKey(DB.LAP.AVG_HR) && v.getAsInteger(DB.LAP.AVG_HR) > 0) {
          lapHrPresent = true;
          break;
        }
      }
      updateLapRows();
    }

    {
      /*
       * Accounts/reports
       */
      String sql =
          "SELECT DISTINCT "
              + "  acc._id, "
              + ("  acc." + DB.ACCOUNT.NAME + ", ")
              + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
              + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
              + ("  acc." + DB.ACCOUNT.FORMAT + ", ")
              + ("  rep._id as repid, ")
              + ("  rep." + DB.EXPORT.ACCOUNT + ", ")
              + ("  rep." + DB.EXPORT.ACTIVITY + ", ")
              + ("  rep." + DB.EXPORT.EXTERNAL_ID + ", ")
              + ("  rep." + DB.EXPORT.STATUS)
              + " FROM "
              + DB.ACCOUNT.TABLE
              + " acc "
              + (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
              + (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT)
              + ("     AND rep." + DB.EXPORT.ACTIVITY + " = " + mID + " )");

      Cursor c = mDB.rawQuery(sql, null);
      alreadySynched.clear();
      synchedExternalId.clear();
      pendingSynchronizers.clear();
      reports.clear();
      if (c.moveToFirst()) {
        do {
          ContentValues tmp = DBHelper.get(c);
          Synchronizer synchronizer = syncManager.add(tmp);
          // Note: Show all configured accounts (also those are not currently enabled)
          // Uploaded but removed accounts are not displayed
          if (synchronizer == null
              || !synchronizer.checkSupport(Feature.UPLOAD)
              || !synchronizer.isConfigured()) {
            continue;
          }

          String name = tmp.getAsString(DB.ACCOUNT.NAME);
          reports.add(tmp);
          if (tmp.containsKey("repid")) {
            alreadySynched.add(name);
            if (tmp.containsKey(DB.EXPORT.STATUS)
                && tmp.getAsInteger(DB.EXPORT.STATUS)
                    == Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
              String url =
                  syncManager
                      .getSynchronizerByName(name)
                      .getActivityUrl(synchedExternalId.get(name));
              if (url != null) {
                synchedExternalId.put(name, tmp.getAsString(DB.EXPORT.EXTERNAL_ID));
              }
            }
          } else if (tmp.containsKey(DB.ACCOUNT.FLAGS)
              && Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS), DB.ACCOUNT.FLAG_UPLOAD)) {
            pendingSynchronizers.add(name);
          }
        } while (c.moveToNext());
      }
      c.close();
    }

    if (mode == MODE_DETAILS) {
      setUploadVisibility();
    }

    updateReportRows();
  }

  private void updateLapRows() {
    ArrayList<LapRow> rows = new ArrayList<>(laps.length);
    for (ContentValues lap : laps) {
      int i = lap.getAsInteger(DB.LAP.INTENSITY);
      Intensity intensity = Intensity.values()[i];
      String type = "";
      switch (intensity) {
        case ACTIVE:
          break;
        case COOLDOWN:
        case RESTING:
        case RECOVERY:
        case WARMUP:
        case REPEAT:
          type =
              String.format(
                  Locale.getDefault(), "(%s)", getResources().getString(intensity.getTextId()));
          break;
        default:
          break;
      }
      double d = lap.containsKey(DB.LAP.DISTANCE) ? lap.getAsDouble(DB.LAP.DISTANCE) : 0;
      long t = lap.containsKey(DB.LAP.TIME) ? lap.getAsLong(DB.LAP.TIME) : 0;
      int hr = lap.containsKey(DB.LAP.AVG_HR) ? lap.getAsInteger(DB.LAP.AVG_HR) : 0;
      String hrText = null;
      boolean hrReserved = false;
      if (hr > 0) {
        hrText = formatter.formatHeartRate(Formatter.Format.TXT_LONG, hr);
      } else if (lapHrPresent) {
        hrReserved = true;
      }
      rows.add(
          new LapRow(
              type,
              formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t),
              formatter.formatDistance(Formatter.Format.TXT_LONG, (long) d),
              t != 0
                  ? formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / t)
                  : "",
              hrText,
              hrReserved));
    }
    pageState.setLaps(rows);
  }

  private void updateReportRows() {
    ArrayList<ReportRow> rows = new ArrayList<>(reports.size());
    for (ContentValues report : reports) {
      String name = report.getAsString(DB.ACCOUNT.NAME);
      boolean synced = alreadySynched.contains(name);
      rows.add(
          new ReportRow(
              name,
              synced || pendingSynchronizers.contains(name),
              synced ? org.runnerup.common.R.string.Uploaded : org.runnerup.common.R.string.Upload,
              synced && !TextUtils.isEmpty(synchedExternalId.get(name))));
    }
    pageState.setReports(rows);
  }

  private void fillHeaderData() {
    // Fields from the database (projection)
    // Must include the _id column for the adapter to work
    String[] from =
        new String[] {
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.COMMENT,
          DB.ACTIVITY.SPORT
        };

    Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null, null, null, null, null);
    c.moveToFirst();
    ContentValues tmp = DBHelper.get(c);
    c.close();

    if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
      long st = tmp.getAsLong(DB.ACTIVITY.START_TIME);
      mStartTime = st;
      pageState.setTitle(formatter.formatDateTime(st));
    }

    if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
      notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
    }

    headerData = tmp;
    updateHeader(tmp, /* fromManualDistance= */ false);
  }

  private void updateHeader(ContentValues data, boolean fromManualDistance) {
    double d = 0;
    if (data.containsKey(DB.ACTIVITY.DISTANCE)) {
      d = data.getAsDouble(DB.ACTIVITY.DISTANCE);
      String s = formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) d);
      pageState.setDistanceText(s);
      if (!fromManualDistance) {
        /** IF !fromManualDistance (e.g. from database) update the manual distance field */
        int distance = (int) d;
        manualDistance.setValue(Long.toString(distance));
        manualDistance.setValue(distance);
      }
    } else {
      pageState.setDistanceText("");
    }

    long t = 0;
    if (data.containsKey(DB.ACTIVITY.TIME)) {
      t = data.getAsInteger(DB.ACTIVITY.TIME);
      pageState.setTimeText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t));
    } else {
      pageState.setTimeText("");
    }

    if (t != 0) {
      pageState.setPaceVisible(true);
      pageState.setPaceText(
          formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / t));
    } else {
      pageState.setPaceVisible(false);
    }

    if (data.containsKey(DB.ACTIVITY.SPORT)) {
      sport.setValue(data.getAsInteger(DB.ACTIVITY.SPORT));
    }
  }

  private void saveActivity() {
    int sportValue = sport.getValueInt();
    ContentValues tmp = headerData;
    tmp.put(DB.ACTIVITY.COMMENT, notes.getText().toString());
    tmp.put(DB.ACTIVITY.SPORT, sportValue);
    String[] whereArgs = {Long.toString(mID)};
    mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", whereArgs);

    // path simplification (reduce resolution of location entries in database)
    try {
      PathSimplifier simplifier = PathSimplifier.getPathSimplifierForSave(this);
      if (simplifier != null) {
        ArrayList<String> ids = simplifier.getNoisyLocationIDsAsStrings(mDB, mID);
        ActivityCleaner.deleteLocations(mDB, ids);
        (new ActivityCleaner()).recompute(mDB, mID);
      }
    } catch (Exception e) {
      Log.e(getClass().getName(), "Failed to simplify path: " + e.getMessage());
    }
  }

  private void clearUploadClick(String name) {
    new MaterialAlertDialogBuilder(DetailActivity.this)
        .setTitle("Clear upload for " + name)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(
            org.runnerup.common.R.string.Yes,
            (dialog, which) -> {
              dialog.dismiss();
              syncManager.clearUpload(name, mID);
              requery();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.No,
            // Do nothing but close the dialog
            (dialog, which) -> dialog.dismiss())
        .show();
  }

  public void onClickAccountName(String name) {
    if (synchedExternalId.containsKey(name) && !TextUtils.isEmpty(synchedExternalId.get(name))) {
      String url =
          syncManager.getSynchronizerByName(name).getActivityUrl(synchedExternalId.get(name));
      if (url != null) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
      }
    }
  }

  private final View.OnClickListener saveButtonClick =
      new View.OnClickListener() {
        public void onClick(View v) {
          saveActivity();
          if (mode == MODE_DETAILS) {
            setEdit(false);
            requery();
            return;
          }
          uploading = true;
          syncManager.startUploading(
              (synchronizerName, status) -> {
                uploading = false;
                final Intent returnIntent = new Intent();
                int sportValue = sport.getValueInt();
                if (Sport.hasManualDistance(sportValue)) {
                  returnIntent.putExtra(
                      "MANUAL_DISTANCE", headerData.getAsDouble(DB.ACTIVITY.DISTANCE));
                }
                DetailActivity.this.setResult(RESULT_OK, returnIntent);
                DetailActivity.this.finish();
              },
              pendingSynchronizers,
              mID);
        }
      };

  private final View.OnClickListener discardButtonClick =
      v ->
          new MaterialAlertDialogBuilder(DetailActivity.this)
              .setTitle(org.runnerup.common.R.string.Discard)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    dialog.dismiss();
                    DetailActivity.this.setResult(RESULT_CANCELED);
                    DetailActivity.this.finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .show();

  private final View.OnClickListener uploadButtonClick =
      v -> {
        uploading = true;
        syncManager.startUploading(
            (synchronizerName, status) -> {
              uploading = false;
              requery();
            },
            pendingSynchronizers,
            mID);
      };

  private void onSendChecked(String name, boolean checked) {
    if (alreadySynched.contains(name)) {
      // Only accept long clicks
      return;
    }
    if (checked) {
      pendingSynchronizers.add(name);
    } else {
      pendingSynchronizers.remove(name);
    }
    if (mode == MODE_DETAILS) {
      setUploadVisibility();
    }
  }

  private final View.OnClickListener deleteButtonClick =
      v ->
          new MaterialAlertDialogBuilder(DetailActivity.this)
              .setTitle(org.runnerup.common.R.string.Delete_activity)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    DBHelper.deleteActivity(mDB, mID);
                    dialog.dismiss();
                    DetailActivity.this.setResult(RESULT_OK);
                    DetailActivity.this.finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .show();

  private void shareActivity() {
    final int[] which = {
      1 // TODO preselect tcx - choice should be remembered
    };
    final CharSequence[] items = {"gpx", "tcx"};
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(org.runnerup.common.R.string.Share_activity))
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, w) -> {
              if (which[0] == -1) {
                dialog.dismiss();
                return;
              }

              final Context context = DetailActivity.this;
              final CharSequence fmt = items[which[0]];
              final Intent intent = new Intent(Intent.ACTION_SEND);

              if (fmt.equals("tcx")) {
                intent.setType(TCX_MIME);
              } else {
                intent.setType(GPX_MIME);
              }

              // Use of content:// (or STREAM?) instead of file:// is not supported in ES and other
              // apps
              // Solid Explorer File Manager works though
              String actType = Sport.textOf(getResources(), sport.getValueInt());
              Uri uri =
                  Uri.parse(
                      "content://"
                          + ActivityProvider.AUTHORITY
                          + "/"
                          + fmt
                          + "/"
                          + mID
                          + "/"
                          + FileNameHelper.getExportFileName(mStartTime, actType)
                          + fmt);
              intent.putExtra(Intent.EXTRA_STREAM, uri);
              context.startActivity(
                  Intent.createChooser(
                      intent, getString(org.runnerup.common.R.string.Share_activity)));
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            // Do nothing but close the dialog
            (dialog, which1) -> {
              dialog.dismiss();
            })
        .setSingleChoiceItems(items, which[0], (dialog, w) -> which[0] = w)
        .show();
  }
}
