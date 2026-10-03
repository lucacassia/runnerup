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

package org.runnerup.view;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.FileSynchronizer;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Formatter;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.Sport;

public class UploadActivity extends AppCompatActivity implements Constants {

  private String mSynchronizerName = null;
  private SyncManager.SyncMode syncMode = SyncManager.SyncMode.UPLOAD;
  private SyncManager syncManager = null;

  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private final List<SyncActivityItem> allSyncActivities = new ArrayList<>();

  private int syncCount = 0;
  private final UploadPageState pageState = new UploadPageState();
  private CharSequence actionButtonText = null;

  private boolean fetching = false;
  private final StringBuffer cancelSync = new StringBuffer();

  private final ActivityResultLauncher<Intent> detailLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> fillData());

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.upload);

    Intent intent = getIntent();
    mSynchronizerName = intent.getStringExtra("synchronizer");
    syncMode = SyncManager.SyncMode.valueOf(intent.getStringExtra("mode"));

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);
    syncManager = new SyncManager(this);

    if (syncMode.equals(SyncManager.SyncMode.DOWNLOAD)) {
      actionButtonText = getText(org.runnerup.common.R.string.Download);
      pageState.setActionLabel(actionButtonText.toString());
    } else {
      actionButtonText = getText(org.runnerup.common.R.string.Upload);
      pageState.setActionLabel(actionButtonText.toString());
    }
    pageState.setTitle(actionButtonText.toString());
    UploadScreenHost.INSTANCE.attach(
        (ComposeView) findViewById(R.id.upload_root),
        pageState,
        new UploadBindings(
            this::finish,
            id -> {
              Intent detailIntent = new Intent(UploadActivity.this, DetailActivity.class);
              detailIntent.putExtra("ID", id);
              detailIntent.putExtra("mode", "details");
              detailLauncher.launch(detailIntent);
            },
            (id, checked) -> {
              for (SyncActivityItem item : allSyncActivities) {
                if (item.getId() == id) {
                  item.setSkipFlag(!checked);
                  break;
                }
              }
              updateSyncCount();
              requery();
            },
            () -> setAllButtonClick.onClick(null),
            () -> clearAllButtonClick.onClick(null),
            () -> {
              if (syncMode.equals(SyncManager.SyncMode.DOWNLOAD)) {
                downloadButtonClick.onClick(null);
              } else {
                uploadButtonClick.onClick(null);
              }
            }));

    fillData();
    {
      // synchronizer initialized in fillData() for DOWNLOAD only
      Synchronizer synchronizer = syncManager.getSynchronizerByName(mSynchronizerName);

      if (synchronizer == null || synchronizer.getIconId() == 0) {
        pageState.setAccountName(mSynchronizerName);
        pageState.setAccountIcon(0);
      } else {
        pageState.setAccountName(null);
        pageState.setAccountIcon(synchronizer.getIconId());
      }
    }

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (fetching) {
                  cancelSync.append("1");
                  return;
                }

                finish();
              }
            });
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
    syncManager.close();
  }

  private void fillData() {
    if (syncMode.equals(SyncManager.SyncMode.DOWNLOAD)) {
      syncManager.load(mSynchronizerName);
      syncManager.loadActivityList(
          allSyncActivities,
          mSynchronizerName,
          (synchronizerName, status) -> {
            filterAlreadyPresentActivities();
            requery();
          });
    } else {
      // Fields from the database (projection)
      final String[] from =
          new String[] {
            DB.PRIMARY_KEY,
            DB.ACTIVITY.START_TIME,
            DB.ACTIVITY.DISTANCE,
            DB.ACTIVITY.TIME,
            DB.ACTIVITY.SPORT
          };
      String[] args = {mSynchronizerName};
      final String w =
          "NOT EXISTS (SELECT 1 FROM "
              + DB.EXPORT.TABLE
              + " r,"
              + DB.ACCOUNT.TABLE
              + " a WHERE "
              + "r."
              + DB.EXPORT.ACTIVITY
              + " = "
              + DB.ACTIVITY.TABLE
              + "._id "
              + " AND r."
              + DB.EXPORT.ACCOUNT
              + " = a."
              + "_id"
              + " AND a."
              + DB.ACCOUNT.NAME
              + " = ?)";

      Cursor c =
          mDB.query(
              DB.ACTIVITY.TABLE,
              from,
              " deleted == 0 AND " + w,
              args,
              null,
              null,
              "_id desc",
              null);
      allSyncActivities.clear();
      int i = 0;
      final int maxUpload = 10;
      if (c.moveToFirst()) {
        do {
          ActivityEntity ac = new ActivityEntity(c);
          SyncActivityItem ai = new SyncActivityItem(ac);
          // Limit default to upload, except for local
          if (!mSynchronizerName.contentEquals(FileSynchronizer.NAME) && i++ >= maxUpload) {
            ai.setSkipFlag(true);
          }
          allSyncActivities.add(ai);
        } while (c.moveToNext());
      }
      c.close();
      syncCount = allSyncActivities.size();
      requery();
    }
  }

  private void filterAlreadyPresentActivities() {
    List<SyncActivityItem> presentActivities = new ArrayList<>();
    final String[] from =
        new String[] {
          DB.PRIMARY_KEY,
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.SPORT
        };

    Cursor c =
        mDB.query(DB.ACTIVITY.TABLE, from, " deleted = 0", null, null, null, "_id desc", null);

    if (c.moveToFirst()) {
      do {
        ActivityEntity av = new ActivityEntity(c);
        SyncActivityItem ai = new SyncActivityItem(av);
        presentActivities.add(ai);
      } while (c.moveToNext());
    }
    c.close();

    for (SyncActivityItem toDown : allSyncActivities) {
      for (SyncActivityItem present : presentActivities) {
        if (toDown.isSimilarTo(present)) {
          toDown.setPresentFlag(Boolean.TRUE);
          toDown.setSkipFlag(Boolean.FALSE);
          break;
        }
      }
    }
    updateSyncCount();
  }

  private void updateSyncCount() {
    syncCount = 0;
    for (SyncActivityItem ai : allSyncActivities) {
      if (ai.synchronize(syncMode)) {
        syncCount++;
      }
    }
  }

  private void requery() {
    List<UploadRowItem> rows = new ArrayList<>(allSyncActivities.size());
    for (SyncActivityItem ai : allSyncActivities) {
      String startTime =
          ai.getStartTime() == null ? "" : formatter.formatDateTime(ai.getStartTime());
      int sport =
          ai.getSport() == null
              ? DB.ACTIVITY.SPORT_RUNNING
              : Sport.valueOf(ai.getSport()).getDbValue();
      rows.add(
          new UploadRowItem(
              ai.getId(),
              startTime,
              Sport.textOf(getResources(), sport),
              !ai.skipActivity(),
              ai.isRelevantForSynch(syncMode)));
    }
    pageState.setRows(rows);
    pageState.setActionEnabled(syncCount > 0);
    if (syncCount > 0) {
      pageState.setActionLabel(
          String.format(Locale.getDefault(), "%s (%d)", actionButtonText, syncCount));
    } else {
      pageState.setActionLabel(actionButtonText.toString());
    }
  }

  private final OnClickListener uploadButtonClick =
      new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (allSyncActivities.isEmpty()) {
            return;
          }
          List<SyncActivityItem> upload = getSelectedActivities();
          Log.i(Constants.LOG, "Start uploading " + upload.size());
          fetching = true;
          cancelSync.delete(0, cancelSync.length());
          syncManager.syncActivities(
              SyncManager.SyncMode.UPLOAD, syncCallback, mSynchronizerName, upload, cancelSync);
        }
      };

  private final OnClickListener downloadButtonClick =
      new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (allSyncActivities.isEmpty()) {
            return;
          }
          List<SyncActivityItem> download = getSelectedActivities();
          Log.i(Constants.LOG, "Start downloading " + download.size());
          fetching = true;
          cancelSync.delete(0, cancelSync.length());
          syncManager.syncActivities(
              SyncManager.SyncMode.DOWNLOAD, syncCallback, mSynchronizerName, download, cancelSync);
        }
      };

  private List<SyncActivityItem> getSelectedActivities() {
    List<SyncActivityItem> selected = new ArrayList<>();
    for (SyncActivityItem tmp : allSyncActivities) {
      if (tmp.synchronize(syncMode)) selected.add(tmp);
    }
    return selected;
  }

  private final SyncManager.Callback syncCallback =
      (synchronizerName, status) -> {
        fetching = false;
        if (cancelSync.length() > 0 || status == Status.CANCEL) {
          finish();
          return;
        }
        if (syncMode.equals(SyncManager.SyncMode.UPLOAD)) {
          fillData();
        } else {
          filterAlreadyPresentActivities();
          requery();
        }
      };

  private final OnClickListener clearAllButtonClick =
      v -> {
        for (SyncActivityItem tmp : allSyncActivities) {
          if (tmp.isRelevantForSynch(syncMode)) {
            tmp.setSkipFlag(Boolean.TRUE);
          }
        }
        updateSyncCount();
        requery();
      };

  private final OnClickListener setAllButtonClick =
      v -> {
        int i = 0;
        final int maxUpload = 30;
        for (SyncActivityItem ai : allSyncActivities) {
          if (ai.isRelevantForSynch(syncMode)) {
            // Limit uploads by default, to not overload services (even if action is not all)
            Boolean upload =
                mSynchronizerName.contentEquals(FileSynchronizer.NAME) || i++ < maxUpload;
            ai.setSkipFlag(!upload);
          }
        }
        updateSyncCount();
        requery();
      };
}
