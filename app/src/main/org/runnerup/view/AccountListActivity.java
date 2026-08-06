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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.util.ViewUtil;

public class AccountListActivity extends AppCompatActivity
    implements Constants, LoaderCallbacks<Cursor> {

  private SQLiteDatabase mDB = null;
  private SyncManager mSyncManager = null;
  private boolean mShowDisabled = false;
  private AccountListAdapter mCursorAdapter;

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
              this.mCursorAdapter.notifyDataSetChanged();
            }
          });

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    setContentView(R.layout.account_list);

    MaterialToolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    mDB = DBHelper.getReadableDatabase(this);
    mSyncManager = new SyncManager(this);
    mSyncManager.setConfigureLauncher(configureLauncher);
    RecyclerView listView = findViewById(R.id.account_list_list);
    listView.setLayoutManager(new LinearLayoutManager(this));

    // button footer
    MaterialButton showDisabledBtn = findViewById(R.id.account_list_show_disabled);
    showDisabledBtn.setText(org.runnerup.common.R.string.Show_disabled_accounts);
    showDisabledBtn.setOnClickListener(
        view -> {
          mShowDisabled = !mShowDisabled;
          if (mShowDisabled) {
            showDisabledBtn.setText(org.runnerup.common.R.string.Hide_disabled_accounts);
          } else {
            showDisabledBtn.setText(org.runnerup.common.R.string.Show_disabled_accounts);
          }
          getSupportLoaderManager().restartLoader(0, null, AccountListActivity.this);
        });

    // adapter
    mCursorAdapter = new AccountListAdapter(this);
    listView.setAdapter(mCursorAdapter);
    getSupportLoaderManager().initLoader(0, null, this);

    ViewUtil.Insets(findViewById(R.id.account_list_view), true);
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
    mCursorAdapter.swapCursor(arg1);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    mCursorAdapter.swapCursor(null);
  }

  class AccountListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final LayoutInflater inflater;
    Cursor cursor;

    AccountListAdapter(Context context) {
      inflater = LayoutInflater.from(context);
    }

    @SuppressLint("NotifyDataSetChanged")
    Cursor swapCursor(Cursor newCursor) {
      Cursor oldCursor = cursor;
      cursor = newCursor;
      notifyDataSetChanged();
      return oldCursor;
    }

    @Override
    public int getItemCount() {
      return cursor == null ? 0 : cursor.getCount();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new AccountViewHolder(inflater.inflate(R.layout.account_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      AccountViewHolder viewHolder = (AccountViewHolder) holder;
      cursor.moveToPosition(position);
      ContentValues values = DBHelper.get(cursor);

      final Synchronizer synchronizer = mSyncManager.add(values);
      final long flags = values.getAsLong(DB.ACCOUNT.FLAGS);
      final String name = values.getAsString(DB.ACCOUNT.NAME);
      boolean configured = synchronizer != null && synchronizer.isConfigured();

      View view = viewHolder.itemView;
      view.setTag(synchronizer);

      TextView sectionTitle = viewHolder.sectionTitle;
      ImageView accountIcon = viewHolder.accountIcon;
      TextView accountIconText = viewHolder.accountIconText;
      TextView accountNameText = viewHolder.accountNameText;
      SwitchCompat accountUploadBox = viewHolder.accountUploadBox;

      // category name
      int curPosition = cursor.getPosition();
      boolean prevConfigured = false;
      if (curPosition > 0) {
        // get data for previous item
        cursor.moveToPrevious();
        ContentValues values2 = DBHelper.get(cursor);

        final Synchronizer synchronizer2 = mSyncManager.add(values2);
        prevConfigured = synchronizer2 != null && synchronizer2.isConfigured();
        cursor.moveToPosition(curPosition);
      }

      if (curPosition > 0 && configured == prevConfigured) {
        sectionTitle.setVisibility(View.GONE);
      } else {
        int str =
            configured
                ? org.runnerup.common.R.string.accounts_category_connected
                : org.runnerup.common.R.string.accounts_category_unconnected;
        sectionTitle.setText(str);
        sectionTitle.setVisibility(View.VISIBLE);
      }

      accountNameText.setText(name);

      if (synchronizer == null) {
        accountUploadBox.setVisibility(View.GONE);
        accountIcon.setVisibility(View.GONE);
        accountIconText.setVisibility(View.GONE);
        accountNameText.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG);
        accountNameText.setEnabled(false);
        return;
      }
      accountNameText.setPaintFlags(0);
      accountNameText.setEnabled(true);
      accountUploadBox.setVisibility(View.VISIBLE);
      accountIcon.setVisibility(View.VISIBLE);
      accountIconText.setVisibility(View.VISIBLE);

      // service icon
      int synchronizerIcon = synchronizer.getIconId();
      if (synchronizerIcon == 0) {
        Drawable circle =
            AppCompatResources.getDrawable(getApplicationContext(), R.drawable.circle_40dp);
        circle.setColorFilter(
            ContextCompat.getColor(getApplicationContext(), synchronizer.getColorId()),
            PorterDuff.Mode.SRC_IN);
        accountIcon.setImageDrawable(circle);
        accountIconText.setText(name.substring(0, 1));
      } else {
        accountIcon.setImageDrawable(
            AppCompatResources.getDrawable(getApplicationContext(), synchronizerIcon));
        accountIconText.setText(null);
      }

      // upload box
      accountUploadBox.setTag(synchronizer);
      setCustomThumb(accountUploadBox, R.drawable.switch_upload, getApplicationContext());
      accountUploadBox.setOnCheckedChangeListener(
          (arg0, arg1) ->
              setFlag(((Synchronizer) arg0.getTag()).getName(), DB.ACCOUNT.FLAG_UPLOAD, arg1));

      if (configured && synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
        accountUploadBox.setEnabled(true);
        accountUploadBox.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD));
        accountUploadBox.setVisibility(View.VISIBLE);
      } else {
        accountUploadBox.setVisibility(View.GONE);
      }

      view.setOnClickListener(
          v -> {
            // Check network connection #1082
            if (!isNetworkAvailable(AccountListActivity.this)) {
              Toast.makeText(
                      AccountListActivity.this,
                      org.runnerup.common.R.string.check_internet_connection,
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }
            final Synchronizer sync = (Synchronizer) v.getTag();
            if (sync == null) {
              return;
            }
            if (sync.isConfigured()) {
              startActivity(sync.getName(), true);
            } else {
              mSyncManager.connect(callback, sync.getName());
            }
          });
    }
  }

  class AccountViewHolder extends RecyclerView.ViewHolder {
    final TextView sectionTitle;
    final ImageView accountIcon;
    final TextView accountIconText;
    final TextView accountNameText;
    final SwitchCompat accountUploadBox;

    AccountViewHolder(@NonNull View itemView) {
      super(itemView);
      sectionTitle = itemView.findViewById(R.id.account_row_section_title);
      accountIcon = itemView.findViewById(R.id.account_row_icon);
      accountIconText = itemView.findViewById(R.id.account_row_icon_text);
      accountNameText = itemView.findViewById(R.id.account_row_name);
      accountUploadBox = itemView.findViewById(R.id.account_row_upload);
    }
  }

  private void setCustomThumb(SwitchCompat switchCompat, int drawableId, Context context) {
    switchCompat.setThumbDrawable(AppCompatResources.getDrawable(context, drawableId));
    switchCompat.setThumbTintList(
        AppCompatResources.getColorStateList(context, R.color.switch_thumb));
    switchCompat.setThumbTintMode(PorterDuff.Mode.MULTIPLY);
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
