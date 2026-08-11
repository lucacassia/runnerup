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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.util.ViewUtil;

public class BarcodeScanActivity extends AppCompatActivity {
  public static final String BARCODE_EXTRA = "barcode";

  private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
  private boolean decoded;

  private final ActivityResultLauncher<String> cameraPermissionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (granted) {
              startCamera();
            } else {
              Toast.makeText(
                      this, org.runnerup.common.R.string.Camera_permission_text, Toast.LENGTH_LONG)
                  .show();
              finish();
            }
          });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.barcode_scan);

    Toolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    ViewUtil.Insets(findViewById(R.id.barcode_scan_root), true);

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      startCamera();
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    analysisExecutor.shutdown();
  }

  private void startCamera() {
    ListenableFuture<ProcessCameraProvider> providerFuture =
        ProcessCameraProvider.getInstance(this);
    providerFuture.addListener(
        () -> {
          try {
            ProcessCameraProvider provider = providerFuture.get();
            if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
              Toast.makeText(
                      this, org.runnerup.common.R.string.Camera_unavailable_text, Toast.LENGTH_LONG)
                  .show();
              finish();
              return;
            }
            PreviewView viewFinder = findViewById(R.id.preview_view);
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(new Size(1280, 720))
                    .build();
            analysis.setAnalyzer(analysisExecutor, this::analyze);

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
          } catch (ExecutionException | InterruptedException | CameraInfoUnavailableException e) {
            Log.e("BarcodeScanActivity", "failed to start camera", e);
            Toast.makeText(
                    this, org.runnerup.common.R.string.Camera_unavailable_text, Toast.LENGTH_LONG)
                .show();
            finish();
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void analyze(ImageProxy image) {
    if (decoded) {
      image.close();
      return;
    }
    try {
      byte[] yPlane = extractYPlane(image);
      PlanarYUVLuminanceSource source =
          rotateToUpright(
              yPlane,
              image.getWidth(),
              image.getHeight(),
              image.getImageInfo().getRotationDegrees());
      BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
      Result result = new Code128Reader().decode(binaryBitmap);
      decoded = true;
      String value = result.getText();
      runOnUiThread(
          () -> {
            Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
            Intent data = new Intent();
            data.putExtra(BARCODE_EXTRA, value);
            setResult(RESULT_OK, data);
            finish();
          });
    } catch (NotFoundException | FormatException e) {
      // no barcode in this frame
    } finally {
      image.close();
    }
  }

  private static byte[] extractYPlane(ImageProxy image) {
    ImageProxy.PlaneProxy plane = image.getPlanes()[0];
    ByteBuffer buffer = plane.getBuffer();
    int rowStride = plane.getRowStride();
    int pixelStride = plane.getPixelStride();
    int width = image.getWidth();
    int height = image.getHeight();
    byte[] y = new byte[width * height];
    if (pixelStride == 1 && rowStride == width) {
      buffer.get(y);
      return y;
    }
    int offset = 0;
    for (int row = 0; row < height; row++) {
      buffer.position(row * rowStride);
      for (int col = 0; col < width; col++) {
        y[offset] = buffer.get();
        offset++;
        if (pixelStride > 1) {
          buffer.position(buffer.position() + pixelStride - 1);
        }
      }
    }
    return y;
  }

  private static PlanarYUVLuminanceSource rotateToUpright(
      byte[] y, int width, int height, int rotation) {
    switch (rotation) {
      case 90:
        byte[] r90 = new byte[y.length];
        for (int y0 = 0; y0 < height; y0++) {
          for (int x0 = 0; x0 < width; x0++) {
            r90[x0 * height + (height - 1 - y0)] = y[y0 * width + x0];
          }
        }
        return new PlanarYUVLuminanceSource(r90, height, width, 0, 0, height, width, false);
      case 270:
        byte[] r270 = new byte[y.length];
        for (int y0 = 0; y0 < height; y0++) {
          for (int x0 = 0; x0 < width; x0++) {
            r270[(width - 1 - x0) * height + y0] = y[y0 * width + x0];
          }
        }
        return new PlanarYUVLuminanceSource(r270, height, width, 0, 0, height, width, false);
      case 180:
        byte[] r180 = new byte[y.length];
        for (int y0 = 0; y0 < height; y0++) {
          for (int x0 = 0; x0 < width; x0++) {
            r180[(height - 1 - y0) * width + (width - 1 - x0)] = y[y0 * width + x0];
          }
        }
        return new PlanarYUVLuminanceSource(r180, width, height, 0, 0, width, height, false);
      default:
        return new PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false);
    }
  }
}
