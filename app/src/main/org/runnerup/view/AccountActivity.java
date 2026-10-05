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

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.FileSynchronizer;
import org.runnerup.export.RunnerUpLiveSynchronizer;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.util.Bitfield;
import org.runnerup.workout.FileFormats;

public class AccountActivity extends AppCompatActivity implements Constants {
  private String mSynchronizerName = null;
  private SQLiteDatabase mDB = null;
  private final ArrayList<Cursor> mCursors = new ArrayList<>();

  private long flags;
  private FileFormats format;
  private SyncManager syncManager = null;
  private String mRunnerUpLiveApiAddress = null;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.account);

    Intent intent = getIntent();
    mSynchronizerName = intent.getStringExtra("synchronizer");

    checkboxProxy = new CheckBox(this);

    mDB = DBHelper.getReadableDatabase(this);
    syncManager = new SyncManager(this);

    pageState.setTitle(mSynchronizerName);
    AccountScreenHost.INSTANCE.attach(
        (ComposeView) findViewById(R.id.account_root),
        pageState,
        new AccountBindings(
            this::finish,
            () -> {
              if (headerClickable) {
                urlButtonClick.onClick(null);
              }
            },
            this::showOverflowMenu,
            () -> disconnectButtonClick.onClick(null),
            () -> uploadButtonClick.onClick(null),
            () -> downloadButtonClick.onClick(null),
            () -> okButtonClick.onClick(null)));

    fillData();

    Synchronizer synchronizer = syncManager.getSynchronizerByName(mSynchronizerName);
    if (synchronizer == null) {
      return;
    }
  }

  private void showOverflowMenu(int left, int top, int right, int bottom) {
    ViewGroup decor = (ViewGroup) getWindow().getDecorView();
    if (overflowAnchor == null) {
      overflowAnchor = new View(this);
      overflowAnchor.setVisibility(View.INVISIBLE);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
      params.gravity = Gravity.TOP | Gravity.START;
      params.leftMargin = right - 8;
      params.topMargin = top;
      decor.addView(overflowAnchor, params);

      overflowMenu = new PopupMenu(this, overflowAnchor, Gravity.END);
      overflowMenu.setOnMenuItemClickListener(
          item -> {
            onOptionsItemSelected(item);
            return true;
          });
      overflowMenu.getMenuInflater().inflate(R.menu.account_menu, overflowMenu.getMenu());
    }
    overflowMenu.show();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    for (Cursor c : mCursors) {
      c.close();
    }
    DBHelper.closeDB(mDB);
    mCursors.clear();
    syncManager.close();
  }

  private void fillData() {
    // Fields from the database (projection)
    // Must include the _id column for the adapter to work
    String[] from =
        new String[] {
          "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.FLAGS, DB.ACCOUNT.FORMAT, DB.ACCOUNT.AUTH_CONFIG
        };

    String[] args = {mSynchronizerName};
    Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args, null, null, null);

    if (c.moveToFirst()) {
      Synchronizer synchronizer;
      {
        ContentValues tmp = DBHelper.get(c);
        synchronizer = syncManager.add(tmp);
        flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);
        format = new FileFormats(tmp.getAsString(DB.ACCOUNT.FORMAT));
        if (synchronizer == null) {
          return;
        }
      }

      headerClickable = true;
      if (synchronizer.getIconId() == 0 || mSynchronizerName.equals(FileSynchronizer.NAME)) {
        String publicUrl = synchronizer.getPublicUrl();
        pageState.setHeaderText(TextUtils.isEmpty(publicUrl) ? synchronizer.getName() : publicUrl);
        pageState.setHeaderUrl(TextUtils.isEmpty(publicUrl) ? null : publicUrl);
        pageState.setHeaderIcon(0);
        // FileSynchronizer: SDK 24 requires the file URI to be handled as FileProvider
        // Something like OI File Manager is needed too
        if (!TextUtils.isEmpty(publicUrl)
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || !synchronizer.getName().equals(FileSynchronizer.NAME))) {
          headerClickable = true;
        } else {
          headerClickable = false;
        }
      } else {
        pageState.setHeaderText(null);
        pageState.setHeaderUrl(
            TextUtils.isEmpty(synchronizer.getPublicUrl()) ? null : synchronizer.getPublicUrl());
        pageState.setHeaderIcon(synchronizer.getIconId());
        headerClickable = !TextUtils.isEmpty(synchronizer.getPublicUrl());
      }

      if (synchronizer.getName().equals(RunnerUpLiveSynchronizer.NAME)) {
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        final Resources res = this.getResources();
        final String POST_URL = "http://weide.devsparkles.se/api/Resource/";
        String postUrl =
            prefs.getString(res.getString(R.string.pref_runneruplive_serveradress), POST_URL);

        mRunnerUpLiveApiAddress = postUrl;
        addRow(
            getResources().getString(org.runnerup.common.R.string.RunnerUp_live_address) + ":",
            new AccountControl.Text(postUrl, value -> mRunnerUpLiveApiAddress = value));
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
        pageState.setUploadVisible(true);
        addRow(
            getResources().getString(org.runnerup.common.R.string.Automatic_upload),
            new AccountControl.Check(
                DB.ACCOUNT.FLAG_UPLOAD,
                Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD),
                this::onCheckboxChanged));
      } else {
        pageState.setUploadVisible(false);
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.FILE_FORMAT)) {
        // Add file format checkboxes
        addRow(getResources().getString(org.runnerup.common.R.string.File_format), null);
        for (FileFormats.Format f : FileFormats.ALL_FORMATS) {
          addRow(
              f.getName(),
              new AccountControl.Check(f, format.contains(f), this::onCheckboxChanged));
        }
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.LIVE)) {
        addRow(
            getResources().getString(org.runnerup.common.R.string.Live),
            new AccountControl.Check(
                DB.ACCOUNT.FLAG_LIVE,
                Bitfield.test(flags, DB.ACCOUNT.FLAG_LIVE),
                this::onCheckboxChanged));
      }

      pageState.setDownloadVisible(
          synchronizer.checkSupport(Synchronizer.Feature.ACTIVITY_LIST)
              && synchronizer.checkSupport(Synchronizer.Feature.GET_ACTIVITY));
    }
    publishRows();
    mCursors.add(c);
  }

  private void publishRows() {
    pageState.setRows(tableRows);
  }

  private CheckBox checkboxProxy = null;

  private void onCheckboxChanged(AccountControl.Check item, boolean checked) {
    checkboxProxy.setTag(item.getTag());
    checkboxProxy.setChecked(checked);
    sendCBChecked.onCheckedChanged(checkboxProxy, checked);
    if (checkboxProxy.isChecked() != checked) {
      // The platform restores the check box when the last file format is removed.
      sendCBChecked.onCheckedChanged(checkboxProxy, checkboxProxy.isChecked());
    }
    item.setChecked(checkboxProxy.isChecked());
  }

  private void addRow(String string, AccountControl control) {
    tableRows.add(new AccountTableRowItem(string, control));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.account_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.menu_clear_uploads) {
      clearUploadsButtonClick.onClick(null);
    } else if (id == R.id.menu_upload_workouts) {
      uploadButtonClick.onClick(null);
    } else if (id == R.id.menu_disconnect_account) {
      disconnectButtonClick.onClick(null);
    } else if (id == android.R.id.home) {
      return super.onOptionsItemSelected(item);
    }

    return true;
  }

  private final OnClickListener clearUploadsButtonClick =
      new OnClickListener() {
        @Override
        public void onClick(View v) {
          new MaterialAlertDialogBuilder(AccountActivity.this)
              .setTitle(org.runnerup.common.R.string.Clear_uploads)
              .setMessage(org.runnerup.common.R.string.Clear_uploads_from_phone)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, which) -> syncManager.clearUploadsByName(callback, mSynchronizerName))
              .setNegativeButton(
                  org.runnerup.common.R.string.Cancel,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .show();
        }
      };

  private final OnClickListener uploadButtonClick =
      v -> {
        final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
        intent.putExtra("synchronizer", mSynchronizerName);
        intent.putExtra("mode", SyncManager.SyncMode.UPLOAD.name());
        startActivity(intent);
      };

  private final OnClickListener downloadButtonClick =
      v -> {
        final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
        intent.putExtra("synchronizer", mSynchronizerName);
        intent.putExtra("mode", SyncManager.SyncMode.DOWNLOAD.name());
        startActivity(intent);
      };

  private final OnClickListener urlButtonClick =
      new OnClickListener() {
        @Override
        public void onClick(View v) {
          final Intent intent =
              new Intent(Intent.ACTION_VIEW).setData(Uri.parse((String) v.getTag()));
          try {
            startActivity(intent);
          } catch (Exception e) {
            Log.i(getClass().getName(), "No handler for file intent installed? " + e.getMessage());
          }
        }
      };

  private final List<AccountTableRowItem> tableRows = new ArrayList<>();

  private final AccountPageState pageState = new AccountPageState();
  private View overflowAnchor = null;
  private PopupMenu overflowMenu = null;
  private boolean headerClickable = false;

  private final OnCheckedChangeListener sendCBChecked =
      new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          ContentValues tmp = new ContentValues();
          Object flag = buttonView.getTag();
          if (flag instanceof FileFormats.Format) {
            if (isChecked) {
              format.add((FileFormats.Format) flag);
            } else {
              format.remove((FileFormats.Format) flag);
              // At least one format needed
              if (TextUtils.isEmpty(format.toString())) {
                // Recheck unchecked format
                format.add((FileFormats.Format) flag);
                buttonView.setChecked(true);
                Toast.makeText(
                        getApplicationContext(),
                        getResources().getString(org.runnerup.common.R.string.File_need_one_format),
                        Toast.LENGTH_SHORT)
                    .show();
              }
            }
            tmp.put(DB.ACCOUNT.FORMAT, format.toString());
          } else {
            switch ((int) flag) {
              case DB.ACCOUNT.FLAG_UPLOAD:
              case DB.ACCOUNT.FLAG_LIVE:
                flags = Bitfield.set(flags, (Integer) flag, isChecked);
                break;
            }
            tmp.put(DB.ACCOUNT.FLAGS, flags);
          }
          String[] args = {mSynchronizerName};
          mDB.update(DB.ACCOUNT.TABLE, tmp, "name = ?", args);
        }
      };

  private final OnClickListener okButtonClick =
      v -> {
        if (mRunnerUpLiveApiAddress != null) {
          final SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
          final Resources res = getResources();

          prefs
              .edit()
              .putString(
                  res.getString(R.string.pref_runneruplive_serveradress), mRunnerUpLiveApiAddress)
              .apply();
          mRunnerUpLiveApiAddress = null;
        }
        finish();
      };

  private final OnClickListener disconnectButtonClick =
      new OnClickListener() {
        public void onClick(View v) {
          final CharSequence[] items = {
            getString(org.runnerup.common.R.string.Clear_uploads_from_phone)
          };
          final boolean[] selected = {true};
          new MaterialAlertDialogBuilder(AccountActivity.this)
              .setTitle(org.runnerup.common.R.string.Disconnect_account)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, which) ->
                      syncManager.disableSynchronizer(
                          disconnectCallback, mSynchronizerName, selected[0]))
              .setNegativeButton(
                  org.runnerup.common.R.string.Cancel,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .setMultiChoiceItems(items, selected, (arg0, arg1, arg2) -> selected[arg1] = arg2)
              .show();
        }
      };

  private final SyncManager.Callback callback = (synchronizerName, status) -> {};

  private final SyncManager.Callback disconnectCallback = (synchronizerName, status) -> finish();
}
