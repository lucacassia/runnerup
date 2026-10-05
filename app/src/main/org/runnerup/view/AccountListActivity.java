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

import static org.runnerup.util.NetworkUtils.isNetworkAvailable;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.util.SimpleCursorLoader;

public class AccountListActivity extends AppCompatActivity
    implements Constants, LoaderCallbacks<Cursor> {

  private SQLiteDatabase mDB = null;
  private SyncManager mSyncManager = null;
  private boolean mShowDisabled = false;
  private final AccountListState listState = new AccountListState();

  private final ActivityResultLauncher<Intent> editLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            mSyncManager.clear();
            getSupportLoaderManager().restartLoader(0, null, this);
          });

  @SuppressLint("NotifyDataSetChanged")
  private final ActivityResultLauncher<Intent> configureLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (mSyncManager != null) {
              mSyncManager.handleAuthResult(result.getResultCode(), result.getData());
              getSupportLoaderManager().restartLoader(0, null, this);
            }
          });

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    setContentView(R.layout.account_list);

    mDB = DBHelper.getReadableDatabase(this);
    mSyncManager = new SyncManager(this);
    mSyncManager.setConfigureLauncher(configureLauncher);

    AccountListScreenHost.INSTANCE.attach(
        (ComposeView) findViewById(R.id.account_list_root),
        listState,
        new AccountListBindings(
            this::finish,
            this::onAccountClick,
            this::onUploadChanged,
            () -> {
              mShowDisabled = !mShowDisabled;
              listState.setShowDisabled(mShowDisabled);
              getSupportLoaderManager().restartLoader(0, null, AccountListActivity.this);
            }));

    getSupportLoaderManager().initLoader(0, null, this);
  }

  private void onAccountClick(String name) {
    // Check network connection #1082
    if (!isNetworkAvailable(AccountListActivity.this)) {
      Toast.makeText(
              AccountListActivity.this,
              org.runnerup.common.R.string.check_internet_connection,
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    final Synchronizer sync = mSyncManager.getSynchronizerByName(name);
    if (sync == null) {
      return;
    }
    if (sync.isConfigured()) {
      startActivity(name, true);
    } else {
      mSyncManager.connect(callback, name);
    }
  }

  private void onUploadChanged(String name, boolean checked) {
    setFlag(name, DB.ACCOUNT.FLAG_UPLOAD, checked);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
    mSyncManager.close();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      return super.onOptionsItemSelected(item);
    }
    return true;
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    String[] from =
        new String[] {
          "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FORMAT, DB.ACCOUNT.FLAGS
        };
    String showDisabled = null;
    if (!mShowDisabled) {
      showDisabled = DB.ACCOUNT.ENABLED + "==1 or " + DB.ACCOUNT.AUTH_CONFIG + " is not null";
    }

    return new SimpleCursorLoader(
        this,
        mDB,
        DB.ACCOUNT.TABLE,
        from,
        showDisabled,
        null,
        DB.ACCOUNT.AUTH_CONFIG
            + " is null, "
            + DB.ACCOUNT.NAME
            + " collate nocase,"
            + DB.ACCOUNT.ENABLED
            + " desc ");
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
    publishRows(arg1);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    publishRows(null);
  }

  private void publishRows(Cursor cursor) {
    if (listState == null) {
      return;
    }
    List<AccountRowItem> rows = new ArrayList<>();
    if (cursor != null && cursor.moveToFirst()) {
      Boolean prevConfigured = null;
      do {
        ContentValues values = DBHelper.get(cursor);
        final String name = values.getAsString(DB.ACCOUNT.NAME);
        final long flags = values.getAsLong(DB.ACCOUNT.FLAGS);
        final Synchronizer synchronizer = mSyncManager.add(values);
        boolean available = synchronizer != null;
        boolean configured = available && synchronizer.isConfigured();
        String section = null;
        if (prevConfigured == null || configured != prevConfigured) {
          section =
              getString(
                  configured
                      ? org.runnerup.common.R.string.accounts_category_connected
                      : org.runnerup.common.R.string.accounts_category_unconnected);
        }
        prevConfigured = configured;

        int iconRes = available ? synchronizer.getIconId() : 0;
        int iconColor =
            available
                ? ContextCompat.getColor(AccountListActivity.this, synchronizer.getColorId())
                : 0;
        boolean showSwitch = configured && synchronizer.checkSupport(Synchronizer.Feature.UPLOAD);
        rows.add(
            new AccountRowItem(
                name,
                section,
                available,
                iconRes,
                iconColor,
                showSwitch,
                Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD)));
      } while (cursor.moveToNext());
    }
    listState.setRows(rows);
  }

  private void setFlag(String synchronizerName, int flag, boolean val) {
    if (val) {
      long bitval = (1L << flag);
      mDB.execSQL(
          "update "
              + DB.ACCOUNT.TABLE
              + " set "
              + DB.ACCOUNT.FLAGS
              + " = ( "
              + DB.ACCOUNT.FLAGS
              + "|"
              + bitval
              + ") where "
              + DB.ACCOUNT.NAME
              + " = '"
              + synchronizerName
              + "'");
    } else {
      long mask = ~(long) (1L << flag);
      mDB.execSQL(
          "update "
              + DB.ACCOUNT.TABLE
              + " set "
              + DB.ACCOUNT.FLAGS
              + " = ( "
              + DB.ACCOUNT.FLAGS
              + "&"
              + mask
              + ") where "
              + DB.ACCOUNT.NAME
              + " = '"
              + synchronizerName
              + "'");
    }
  }

  private final SyncManager.Callback callback =
      (synchronizerName, status) -> {
        if (status == Status.OK) {
          startActivity(synchronizerName, false);
        }
      };

  private void startActivity(String synchronizerName, boolean edit) {
    Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
    intent.putExtra("synchronizer", synchronizerName);
    // intent.putExtra("edit", edit);
    editLauncher.launch(intent);
  }
}
