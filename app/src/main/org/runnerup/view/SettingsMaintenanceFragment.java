package org.runnerup.view;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.ExportOptions;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;
import org.runnerup.util.FileNameHelper;
import org.runnerup.workout.Sport;

public class SettingsMaintenanceFragment extends PreferenceFragmentCompat {

  private static final String TAG = "SettingsMaintenance";

  /**
   * ActivityResultLauncher for handling the result of the {@link Intent#ACTION_CREATE_DOCUMENT}
   * intent used for exporting the database.
   *
   * <p>When the user selects a destination file, this launcher receives the {@link Uri} and
   * initiates the database export process via {@link DBHelper#exportDatabase(Context, Uri)}. If the
   * export is cancelled or no Uri is returned, a toast message is shown and a warning is logged.
   */
  private final ActivityResultLauncher<Intent> exportDbLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Uri toUri = getUriFromResult(result);
            if (toUri != null) {
              DBHelper.exportDatabase(requireContext(), toUri);
            } else {
              Toast.makeText(
                      requireContext(),
                      org.runnerup.common.R.string.export_cancelled,
                      Toast.LENGTH_SHORT)
                  .show();
              Log.w(TAG, "Export cancelled or URI not found.");
            }
          });

  /**
   * ActivityResultLauncher for handling the result of the {@link Intent#ACTION_OPEN_DOCUMENT}
   * intent used for importing the database.
   *
   * <p>When the user selects a database file, this launcher receives its {@link Uri} and initiates
   * the database import process via {@link DBHelper#importDatabase(Context, Uri)}. If the import is
   * cancelled or no Uri is returned, a toast message is shown and a warning is logged.
   */
  private final ActivityResultLauncher<Intent> importDbLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Uri fromUri = getUriFromResult(result);
            if (fromUri != null) {
              DBHelper.importDatabase(requireContext(), fromUri);
            } else {
              Toast.makeText(
                      requireContext(),
                      org.runnerup.common.R.string.import_cancelled,
                      Toast.LENGTH_SHORT)
                  .show();
              Log.w(TAG, "Import cancelled or URI not found.");
            }
          });

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final android.os.Handler mainHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private volatile boolean exportCancelled = false;

  private boolean exportFormatIsGpx = false;

  private final ActivityResultLauncher<Intent> exportActivitiesLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Uri uri = getUriFromResult(result);
            if (uri != null) {
              startBulkExport(uri, exportFormatIsGpx);
            } else {
              Toast.makeText(
                      requireContext(),
                      org.runnerup.common.R.string.export_activities_cancelled,
                      Toast.LENGTH_SHORT)
                  .show();
            }
          });

  private final Preference.OnPreferenceClickListener onExportActivitiesClick =
      preference -> {
        String[] formats = {"GPX", "TCX"};
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(org.runnerup.common.R.string.Export_activities)
            .setSingleChoiceItems(
                formats,
                1,
                (dialog, which) -> {
                  exportFormatIsGpx = (which == 0);
                  dialog.dismiss();
                  Intent intent =
                      new Intent(Intent.ACTION_CREATE_DOCUMENT)
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .setType("application/zip")
                          .putExtra(Intent.EXTRA_TITLE, "runnerup-activities.zip");
                  exportActivitiesLauncher.launch(intent);
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
            .show();
        return true;
      };

  private final Preference.OnPreferenceClickListener onExportClick =
      preference -> {
        Intent intent =
            new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // Use "application/octet-stream" to be consistent with the mime type used in
                // the http intent-filter for MainLayout (in AndroidManifest).
                .setType("application/octet-stream")
                .putExtra(
                    Intent.EXTRA_TITLE,
                    "runnerup.db.export"); // Suggest a name (note: user may change it)
        exportDbLauncher.launch(intent);
        return true;
      };
  private final Preference.OnPreferenceClickListener onImportClick =
      preference -> {
        Intent intent =
            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        importDbLauncher.launch(intent);
        return true;
      };
  private final Preference.OnPreferenceClickListener onPruneClick =
      preference -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all_confirm_title)
            .setMessage(R.string.delete_all_confirm_message)
            .setPositiveButton(
                R.string.delete_all_confirm_ok,
                (dialog, which) -> {
                  if (DBHelper.clearAllActivities(requireContext())) {
                    new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.delete_all_confirm_title)
                        .setMessage(R.string.delete_all_done)
                        .setPositiveButton(org.runnerup.common.R.string.OK, (d, w) -> d.dismiss())
                        .show();
                  }
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
            .show();
        return true;
      };

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_maintenance, rootKey);
    Resources res = getResources();
    {
      Preference btn = findPreference(res.getString(R.string.pref_exportdb));
      if (btn != null) {
        btn.setOnPreferenceClickListener(onExportClick);
      }
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_importdb));
      if (btn != null) {
        btn.setOnPreferenceClickListener(onImportClick);
      }
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_prunedb));
      if (btn != null) {
        btn.setOnPreferenceClickListener(onPruneClick);
      }
    }
    {
      Preference btn =
          findPreference(res.getString(org.runnerup.common.R.string.pref_export_activities));
      if (btn != null) {
        btn.setOnPreferenceClickListener(onExportActivitiesClick);
      }
    }
  }

  private void startBulkExport(Uri zipUri, boolean isGpx) {
    Context ctx = requireContext();
    SQLiteDatabase db = DBHelper.getReadableDatabase(ctx);

    List<long[]> activities = new ArrayList<>();
    try (Cursor cursor =
        db.query(
            DB.ACTIVITY.TABLE,
            new String[] {DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME, DB.ACTIVITY.SPORT},
            DB.ACTIVITY.DELETED + " = 0",
            null,
            null,
            null,
            DB.ACTIVITY.START_TIME + " asc")) {
      while (cursor.moveToNext()) {
        activities.add(new long[] {cursor.getLong(0), cursor.getLong(1), cursor.getInt(2)});
      }
    }

    int total = activities.size();
    if (total == 0) {
      Toast.makeText(
              ctx, org.runnerup.common.R.string.export_activities_nothing, Toast.LENGTH_SHORT)
          .show();
      return;
    }

    ProgressDialog progressDialog = new ProgressDialog(ctx);
    progressDialog.setMessage(
        ctx.getString(org.runnerup.common.R.string.export_activities_progress, 0, total));
    progressDialog.setCancelable(true);
    progressDialog.setButton(
        DialogInterface.BUTTON_NEGATIVE,
        ctx.getString(org.runnerup.common.R.string.Cancel),
        (dialog, which) -> exportCancelled = true);
    progressDialog.show();

    exportCancelled = false;
    PathSimplifier simplifier = PathSimplifier.getPathSimplifierForExport(ctx);
    ExportOptions exportOptions = ExportOptions.getDefault();
    String ext = isGpx ? "gpx" : "tcx";

    executor.execute(
        () -> {
          int success = 0;
          int failed = 0;
          try (OutputStream fos = ctx.getContentResolver().openOutputStream(zipUri);
              BufferedOutputStream bos = new BufferedOutputStream(fos);
              ZipOutputStream zos = new ZipOutputStream(bos)) {

            for (int i = 0; i < total; i++) {
              if (exportCancelled) break;

              long[] row = activities.get(i);
              long id = row[0];
              long startTime = row[1];
              int sportDb = (int) row[2];

              try {
                Sport sport = Sport.valueOf(sportDb);
                String fileName =
                    FileNameHelper.getExportFileName(startTime, sport.TapiriikType()) + ext;
                zos.putNextEntry(new ZipEntry(fileName));

                Writer writer = new OutputStreamWriter(zos);
                if (isGpx) {
                  new GPX(db, exportOptions, simplifier).export(id, writer);
                } else {
                  new TCX(db, exportOptions, simplifier).export(id, writer);
                }
                writer.flush();
                zos.closeEntry();
                success++;
              } catch (Exception e) {
                Log.e(TAG, "Failed to export activity " + id, e);
                failed++;
              }

              final int current = i + 1;
              mainHandler.post(
                  () ->
                      progressDialog.setMessage(
                          ctx.getString(
                              org.runnerup.common.R.string.export_activities_progress,
                              current,
                              total)));
            }
          } catch (Exception e) {
            Log.e(TAG, "Bulk export failed", e);
          }

          final int fSuccess = success;
          final int fFailed = failed;
          mainHandler.post(
              () -> {
                progressDialog.dismiss();
                Toast.makeText(
                        ctx,
                        ctx.getString(
                            org.runnerup.common.R.string.export_activities_done,
                            fSuccess,
                            total,
                            fFailed),
                        Toast.LENGTH_SHORT)
                    .show();
              });
        });
  }

  /**
   * Helper method to check the result of an ActivityResult and extract the Uri.
   *
   * @param result The ActivityResult from the launcher.
   * @return The Uri if the result was OK and data is present, otherwise null.
   */
  @Nullable
  private Uri getUriFromResult(ActivityResult result) {
    if (result.getResultCode() == Activity.RESULT_OK) {
      Intent data = result.getData();
      if (data != null && data.getData() != null) {
        return data.getData();
      }
    }
    return null;
  }
}
