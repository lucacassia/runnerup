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

package org.runnerup.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.runnerup.R;
import org.runnerup.util.ViewUtil;

public class ParkrunBarcodeActivity extends AppCompatActivity {
  private SharedPreferences prefs;
  private final ParkrunBarcodePageState pageState = new ParkrunBarcodePageState();

  private final ActivityResultLauncher<Intent> scanLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
              return;
            }
            String scanned = result.getData().getStringExtra(BarcodeScanActivity.BARCODE_EXTRA);
            if (scanned == null || scanned.isEmpty()) {
              return;
            }
            saveBarcode(scanned);
          });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.parkrun_barcode);

    prefs = PreferenceManager.getDefaultSharedPreferences(this);

    pageState.setTitle(getString(org.runnerup.common.R.string.Parkrun_barcode));
    ParkrunBarcodeScreenHost.INSTANCE.attach(
        (ComposeView) findViewById(R.id.parkrun_barcode_compose),
        pageState,
        new ParkrunBarcodeBindings(this::finish, () -> launchScanner(), this::confirmDelete));

    refresh();

    ViewUtil.Insets(findViewById(R.id.parkrun_barcode_root), true);
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }

  private void launchScanner() {
    scanLauncher.launch(new Intent(this, BarcodeScanActivity.class));
  }

  private void saveBarcode(String barcode) {
    prefs.edit().putString(getString(R.string.pref_parkrun_barcode), barcode).apply();
    refresh();
  }

  private void confirmDelete() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(org.runnerup.common.R.string.Delete_barcode)
        .setMessage(org.runnerup.common.R.string.Delete_barcode_text)
        .setPositiveButton(
            org.runnerup.common.R.string.Delete,
            (dialog, which) -> {
              prefs.edit().remove(getString(R.string.pref_parkrun_barcode)).apply();
              refresh();
            })
        .setNegativeButton(org.runnerup.common.R.string.Cancel, null)
        .show();
  }

  private void refresh() {
    String barcode = prefs.getString(getString(R.string.pref_parkrun_barcode), null);
    pageState.setBarcode(barcode == null || barcode.isEmpty() ? null : barcode);
  }
}
