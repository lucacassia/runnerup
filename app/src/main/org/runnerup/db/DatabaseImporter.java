/*
 * Copyright (C) 2026
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

package org.runnerup.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.CheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.util.M3ProgressDialog;

/** Merges an imported RunnerUp database file into the current database. */
class DatabaseImporter implements Constants {

  private static final String TAG = "DatabaseImporter";

  private DatabaseImporter() {}

  static void merge(Context ctx, File tempDbFile) {
    if (DBHelper.hasOngoingActivity(ctx)) {
      new MaterialAlertDialogBuilder(ctx)
          .setTitle(R.string.import_choice_title)
          .setMessage(R.string.import_blocked_activity_in_progress)
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
      tempDbFile.delete();
      return;
    }

    final M3ProgressDialog progress = new M3ProgressDialog(ctx);
    progress.setTitle(R.string.import_choice_title);
    progress.show();

    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
        () -> {
          if (DBHelper.hasOngoingActivity(ctx)) {
            tempDbFile.delete();
            handler.post(
                () -> {
                  progress.dismiss();
                  new MaterialAlertDialogBuilder(ctx)
                      .setTitle(R.string.import_choice_title)
                      .setMessage(R.string.import_blocked_activity_in_progress)
                      .setPositiveButton(
                          org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                      .show();
                });
            return;
          }
          try {
            MergeResult result = doMerge(ctx, tempDbFile);
            handler.post(
                () -> {
                  progress.dismiss();
                  showResultDialog(ctx, result);
                });
          } catch (Exception e) {
            Log.e(TAG, "Merge failed", e);
            tempDbFile.delete();
            handler.post(
                () -> {
                  progress.dismiss();
                  new MaterialAlertDialogBuilder(ctx)
                      .setTitle(R.string.import_choice_title)
                      .setMessage("Merge failed: " + e)
                      .setNegativeButton(
                          org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
                      .show();
                });
          }
        });
  }

  private static MergeResult doMerge(Context ctx, File tempDbFile) throws Exception {
    SQLiteDatabase live = DBHelper.getWritableDatabase(ctx);
    SQLiteDatabase imported =
        SQLiteDatabase.openDatabase(
            tempDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    MergeResult result = new MergeResult();
    try {
      Map<String, Long> localIndex = readLocalIndex(live);
      List<ImportedActivity> importedActivities =
          readActivities(imported, tableColumns(live, DB.ACTIVITY.TABLE));

      List<ImportedActivity> toImport = new ArrayList<>();
      List<ImportedActivity> conflicts = new ArrayList<>();
      for (ImportedActivity activity : importedActivities) {
        if (activity.deleted) {
          result.skippedDeleted++;
          continue;
        }
        if (localIndex.containsKey(key(activity.startTime, activity.type))) {
          conflicts.add(activity);
        } else {
          toImport.add(activity);
        }
      }
      result.kept = conflicts.size();

      Set<String> lapCols = tableColumns(live, DB.LAP.TABLE);
      Set<String> locationCols = tableColumns(live, DB.LOCATION.TABLE);
      Set<String> reportCols = tableColumns(live, DB.EXPORT.TABLE);
      Set<String> audioCols = tableColumns(live, DB.AUDIO_SCHEMES.TABLE);

      Map<Long, Long> idMap = new HashMap<>();

      live.beginTransaction();
      try {
        for (ImportedActivity activity : toImport) {
          long newId = live.insert(DB.ACTIVITY.TABLE, null, activity.values);
          idMap.put(activity.oldId, newId);
          result.mergedActivityIds.add(newId);
          result.imported++;
        }

        importChildren(live, imported, idMap, lapCols, locationCols);
        importReport(live, imported, idMap, reportCols);
        importAudioSchemes(live, imported, audioCols);

        live.setTransactionSuccessful();
      } finally {
        live.endTransaction();
      }

      return result;
    } finally {
      imported.close();
      tempDbFile.delete();
    }
  }

  static String key(long startTime, Integer type) {
    return startTime + "|" + (type == null ? "null" : type);
  }

  private static Map<String, Long> readLocalIndex(SQLiteDatabase db) {
    Map<String, Long> index = new HashMap<>();
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            new String[] {"_id", DB.ACTIVITY.START_TIME, DB.ACTIVITY.SPORT},
            DB.ACTIVITY.DELETED + " = 0",
            null,
            null,
            null,
            null)) {
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        long startTime = cursor.getLong(1);
        Integer type = cursor.isNull(2) ? null : cursor.getInt(2);
        index.put(key(startTime, type), id);
      }
    }
    return index;
  }

  private static List<ImportedActivity> readActivities(SQLiteDatabase db, Set<String> liveCols) {
    List<ImportedActivity> list = new ArrayList<>();
    String sql = "SELECT * FROM " + DB.ACTIVITY.TABLE;
    try (Cursor cursor = db.rawQuery(sql, null)) {
      List<String> names = columnsOf(cursor);
      int startTimeIdx = cursor.getColumnIndex(DB.ACTIVITY.START_TIME);
      int typeIdx = cursor.getColumnIndex(DB.ACTIVITY.SPORT);
      int deletedIdx = cursor.getColumnIndex(DB.ACTIVITY.DELETED);
      while (cursor.moveToNext()) {
        ImportedActivity activity = new ImportedActivity();
        activity.oldId = cursor.getLong(0);
        activity.startTime =
            (startTimeIdx >= 0 && !cursor.isNull(startTimeIdx)) ? cursor.getLong(startTimeIdx) : 0;
        activity.type = (typeIdx >= 0 && !cursor.isNull(typeIdx)) ? cursor.getInt(typeIdx) : null;
        activity.deleted =
            deletedIdx >= 0 && !cursor.isNull(deletedIdx) && cursor.getInt(deletedIdx) != 0;
        activity.values = toValues(cursor, names, liveCols);
        list.add(activity);
      }
    }
    return list;
  }

  private static void importChildren(
      SQLiteDatabase live,
      SQLiteDatabase imported,
      Map<Long, Long> idMap,
      Set<String> lapCols,
      Set<String> locationCols) {
    importTable(live, imported, DB.LAP.TABLE, idMap, lapCols);
    importTable(live, imported, DB.LOCATION.TABLE, idMap, locationCols);
  }

  private static void importTable(
      SQLiteDatabase live,
      SQLiteDatabase imported,
      String table,
      Map<Long, Long> idMap,
      Set<String> liveCols) {
    String sql = "SELECT * FROM " + table;
    try (Cursor cursor = imported.rawQuery(sql, null)) {
      List<String> names = columnsOf(cursor);
      int activityIdx = cursor.getColumnIndex(DB.LAP.ACTIVITY);
      while (cursor.moveToNext()) {
        Long newActivityId = idMap.get(cursor.getLong(activityIdx));
        if (newActivityId == null) {
          continue;
        }
        ContentValues values = toValues(cursor, names, liveCols);
        values.put(DB.LAP.ACTIVITY, newActivityId);
        live.insert(table, null, values);
      }
    }
  }

  private static void importReport(
      SQLiteDatabase live, SQLiteDatabase imported, Map<Long, Long> idMap, Set<String> reportCols) {
    if (!tableExists(imported, DB.ACCOUNT.TABLE) || !tableExists(imported, DB.EXPORT.TABLE)) {
      return;
    }
    Map<Long, String> importedAccountName = new HashMap<>();
    try (Cursor cursor =
        imported.rawQuery("SELECT _id, " + DB.ACCOUNT.NAME + " FROM " + DB.ACCOUNT.TABLE, null)) {
      while (cursor.moveToNext()) {
        importedAccountName.put(cursor.getLong(0), cursor.getString(1));
      }
    }
    Map<String, Long> localAccountIdByName = readAccountNames(live);
    String sql = "SELECT * FROM " + DB.EXPORT.TABLE;
    try (Cursor cursor = imported.rawQuery(sql, null)) {
      List<String> names = columnsOf(cursor);
      int activityIdx = cursor.getColumnIndex(DB.EXPORT.ACTIVITY);
      int accountIdx = cursor.getColumnIndex(DB.EXPORT.ACCOUNT);
      while (cursor.moveToNext()) {
        Long newActivityId = idMap.get(cursor.getLong(activityIdx));
        if (newActivityId == null) {
          continue;
        }
        String accountName = importedAccountName.get(cursor.getLong(accountIdx));
        Long localAccountId = accountName == null ? null : localAccountIdByName.get(accountName);
        if (localAccountId == null) {
          continue;
        }
        ContentValues values = toValues(cursor, names, reportCols);
        values.put(DB.EXPORT.ACTIVITY, newActivityId);
        values.put(DB.EXPORT.ACCOUNT, localAccountId);
        live.insert(DB.EXPORT.TABLE, null, values);
      }
    }
  }

  private static Map<String, Long> readAccountNames(SQLiteDatabase db) {
    Map<String, Long> names = new HashMap<>();
    try (Cursor cursor =
        db.rawQuery("SELECT _id, " + DB.ACCOUNT.NAME + " FROM " + DB.ACCOUNT.TABLE, null)) {
      while (cursor.moveToNext()) {
        names.put(cursor.getString(1), cursor.getLong(0));
      }
    }
    return names;
  }

  private static void importAudioSchemes(
      SQLiteDatabase live, SQLiteDatabase imported, Set<String> audioCols) {
    if (!tableExists(imported, DB.AUDIO_SCHEMES.TABLE)) {
      return;
    }
    String sql = "SELECT * FROM " + DB.AUDIO_SCHEMES.TABLE;
    try (Cursor cursor = imported.rawQuery(sql, null)) {
      List<String> names = columnsOf(cursor);
      while (cursor.moveToNext()) {
        ContentValues values = toValues(cursor, names, audioCols);
        live.insertWithOnConflict(
            DB.AUDIO_SCHEMES.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
      }
    }
  }

  private static ContentValues toValues(Cursor cursor, List<String> names, Set<String> allowed) {
    ContentValues values = new ContentValues();
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      if ("_id".equals(name)) {
        continue;
      }
      if (allowed != null && !allowed.contains(name)) {
        continue;
      }
      switch (cursor.getType(i)) {
        case Cursor.FIELD_TYPE_INTEGER:
          values.put(name, cursor.getLong(i));
          break;
        case Cursor.FIELD_TYPE_FLOAT:
          values.put(name, cursor.getDouble(i));
          break;
        case Cursor.FIELD_TYPE_STRING:
          values.put(name, cursor.getString(i));
          break;
        case Cursor.FIELD_TYPE_BLOB:
          values.put(name, cursor.getBlob(i));
          break;
        case Cursor.FIELD_TYPE_NULL:
          break;
        default:
          break;
      }
    }
    return values;
  }

  private static List<String> columnsOf(Cursor cursor) {
    return Arrays.asList(cursor.getColumnNames());
  }

  private static Set<String> tableColumns(SQLiteDatabase db, String table) {
    Set<String> columns = new HashSet<>();
    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      while (cursor.moveToNext()) {
        columns.add(cursor.getString(1));
      }
    }
    return columns;
  }

  private static boolean tableExists(SQLiteDatabase db, String table) {
    try (Cursor cursor =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?",
            new String[] {"table", table})) {
      cursor.moveToFirst();
      return cursor.getInt(0) > 0;
    }
  }

  private static void showResultDialog(Context ctx, MergeResult result) {
    final CheckBox recompute = new CheckBox(ctx);
    recompute.setText(R.string.import_recompute_merged);
    new MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.import_choice_title)
        .setMessage(
            ctx.getString(
                R.string.import_result_message,
                result.imported,
                result.kept,
                result.overwritten,
                result.duplicated,
                result.skippedDeleted))
        .setView(recompute)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              if (recompute.isChecked() && !result.mergedActivityIds.isEmpty()) {
                recomputeMerged(ctx, result.mergedActivityIds);
              }
            })
        .show();
  }

  private static void recomputeMerged(Context ctx, List<Long> activityIds) {
    final M3ProgressDialog progress = new M3ProgressDialog(ctx);
    progress.setTitle(R.string.import_choice_title);
    progress.setMax(activityIds.size());
    progress.show();
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
        () -> {
          try {
            SQLiteDatabase db = DBHelper.getWritableDatabase(ctx);
            ActivityCleaner cleaner = new ActivityCleaner();
            int done = 0;
            for (Long id : activityIds) {
              cleaner.recompute(db, id);
              done++;
              final int progressValue = done;
              handler.post(() -> progress.setProgress(progressValue));
            }
          } finally {
            handler.post(() -> progress.dismiss());
          }
        });
  }

  private static class MergeResult {
    int imported;
    int kept;
    int overwritten;
    int duplicated;
    int skippedDeleted;
    final List<Long> mergedActivityIds = new ArrayList<>();
  }

  private static class ImportedActivity {
    long oldId;
    long startTime;
    Integer type;
    boolean deleted;
    ContentValues values;
  }
}
