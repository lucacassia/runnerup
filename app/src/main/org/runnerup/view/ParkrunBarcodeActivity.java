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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.runnerup.R;
import org.runnerup.util.Code128Barcode;
import org.runnerup.util.ViewUtil;

public class ParkrunBarcodeActivity extends AppCompatActivity {
  private SharedPreferences prefs;
  private View emptyState;
  private View storedState;
  private ImageView barcodeView;
  private TextView valueView;

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

    Toolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    emptyState = findViewById(R.id.empty_state);
    storedState = findViewById(R.id.stored_state);
    barcodeView = findViewById(R.id.barcode_view);
    valueView = findViewById(R.id.barcode_value);

    findViewById(R.id.empty_scan_button).setOnClickListener(v -> launchScanner());
    findViewById(R.id.delete_button).setOnClickListener(v -> confirmDelete());

    refresh();

    ViewUtil.Insets(findViewById(R.id.parkrun_barcode_root), true);
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
    if (barcode == null || barcode.isEmpty()) {
      storedState.setVisibility(View.GONE);
      emptyState.setVisibility(View.VISIBLE);
    } else {
      emptyState.setVisibility(View.GONE);
      storedState.setVisibility(View.VISIBLE);
      if (barcodeView.getWidth() <= 0) {
        barcodeView.post(() -> renderBarcode(barcode));
      } else {
        renderBarcode(barcode);
      }
      valueView.setText(barcode);
    }
  }

  private void renderBarcode(String barcode) {
    int heightPx = (int) getResources().getDimension(R.dimen.barcode_height);
    int widthPx = barcodeView.getWidth();
    if (widthPx <= 0) {
      return;
    }
    barcodeView.setImageBitmap(
        Code128Barcode.renderToBitmap(Code128Barcode.encode(barcode), widthPx, heightPx));
  }
}
