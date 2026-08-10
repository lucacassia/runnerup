# parkrun Barcode Settings Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "parkrun barcode" entry to Settings that lets the user scan a Code 128 barcode with the camera, stores it persistently, and displays a black-on-white rendering of it in a dedicated window.

**Architecture:** A plain `<Preference>` row with an `<intent>` in `settings.xml` opens a new `ParkrunBarcodeActivity` (the "window"). That window shows a parkrun logo, the stored barcode rendered tall black-on-white (via a new pure-Java `Code128Barcode` helper using zxing's `Code128Writer`), the raw value, and Delete / Scan-new buttons. A second new activity, `BarcodeScanActivity`, hosts the CameraX preview and decodes Code 128 frames with zxing's `Code128Reader`, returning the value as an activity result. Persistence is a single string in the default `SharedPreferences` under the `pref_parkrun_barcode` key.

**Tech Stack:** androidx.camera (core/camera2/lifecycle/view) 1.6.1, com.google.zxing:core 3.5.4, androidx.preference, JUnit 4.

## Global Constraints

- Format: Code 128 only (spec). No other barcode formats are decoded or rendered.
- minSdk 28, compileSdk/targetSdk 36.1, Java 17, AGP 9.3.1, Gradle 9.6.1 (wrapper), JDK 17.
- Dependencies must be F-Droid-safe: no play-services, no mapbox. CameraX and zxing:core are pure mavenCentral/Google-Maven artifacts (spec: F-Droid CI only strips `play-services`/`com.mapbox.maps`/`api.mapbox.com` lines). Do NOT wrap them in `allowNonFree`.
- Version numbers are centralized in root `build.gradle` `project.ext` and referenced from `app/build.gradle` via `${rootProject.ext.<name>}` (codebase convention, e.g. `appcompat_version`).
- User-visible strings live in the `common` module at `common/src/main/res/values/strings.xml` (shared `org.runnerup.common.R`); `app` uses its own non-transitive `org.runnerup.R`. New strings must use the codebase PascalCase keys.
- Preference keys live in `app/res/values/pref_keys.xml` as string resources; add `pref_parkrun_barcode`.
- Unit tests live in `app/test/java` (`sourceSets.test.setRoot('test')`) and run via `./gradlew test`. `testOptions.unitTests.returnDefaultValues = true` means android.jar calls return stubs — the unit test MUST NOT touch `Bitmap`.
- Runtime permission requests MUST use `registerForActivityResult` (Activity Result API); no `onActivityResult`/`startActivityForResult` (regression).
- Settings strings and rows: `settings.xml` rows either use `app:fragment` or a nested `<intent>` with `android:targetClass` + `android:targetPackage="@string/applicationIdFull"` (see the Audio cues row).
- Debug applicationId is `org.runnerup.debug`; free build applicationId is `org.runnerup.free`. Both must build.
- Code formatting is googleJavaFormat via `./gradlew spotlessApply` / `spotlessCheck`; CI gates on `spotlessCheck`.
- No comments in code unless asked (match existing GPL header convention on main-source files; test files have no header, e.g. `app/test/java/org/runnerup/view/RunButtonStateTest.java`).
- Only new lint issues matter; `app/lint-baseline.xml` (25 pre-existing) must not fail the build. `app/lint.xml` promotes `InlinedApi`/`InconsistentArrays` to fatal.

---

### Task 1: Add CameraX and ZXing dependencies

**Files:**
- Modify: `build.gradle` (root `project.ext` block, after `mockitoVersion`)
- Modify: `app/build.gradle` (dependencies block, after the okhttp line)

**Interfaces:**
- Consumes: nothing.
- Produces: resolved dependencies `androidx.camera:*` and `com.google.zxing:core` available to `app` (used by Tasks 2-4).

- [ ] **Step 1: Add version variables to root `build.gradle`**

In `build.gradle`, in the `project.ext { ... }` block, after the `mockitoVersion = '5.23.0'` line, add:

```groovy
    camerax_version = "1.6.1"
    zxingVersion = "3.5.4"
```

- [ ] **Step 2: Add the dependencies to `app/build.gradle`**

In `app/build.gradle`, in the `dependencies { ... }` block, after the `implementation "com.squareup.okhttp3:okhttp:5.4.0"` line, add:

```groovy
    implementation "androidx.camera:camera-core:${rootProject.ext.camerax_version}"
    implementation "androidx.camera:camera-camera2:${rootProject.ext.camerax_version}"
    implementation "androidx.camera:camera-lifecycle:${rootProject.ext.camerax_version}"
    implementation "androidx.camera:camera-view:${rootProject.ext.camerax_version}"
    implementation "com.google.zxing:core:${rootProject.ext.zxingVersion}"
```

- [ ] **Step 3: Build to verify the new dependencies resolve**

Run: `./gradlew :app:assembleLatestDebug`
Expected: `BUILD SUCCESSFUL` (new artifacts resolve from Google Maven / mavenCentral; no mapbox/play-services conditionals involved).

- [ ] **Step 4: Commit**

```bash
git add build.gradle app/build.gradle
git commit -m "build: add CameraX and ZXing dependencies for barcode scanning"
```

---

### Task 2: `Code128Barcode` encoder helper (TDD)

**Files:**
- Create: `app/src/main/org/runnerup/util/Code128Barcode.java`
- Test: `app/test/java/org/runnerup/util/Code128BarcodeTest.java`

**Interfaces:**
- Consumes: `com.google.zxing:core` (Task 1).
- Produces:
  - `public static BitMatrix encode(String content)` — Code 128 matrix at natural module scale (1:1) with a 4-module quiet zone on each side; `null`/empty → `IllegalArgumentException`. Used by Task 4 to render.
  - `public static Bitmap renderToBitmap(BitMatrix matrix, int heightPx)` — scales the matrix to `heightPx`, returns an ARGB_8888 black-on-white `Bitmap`. Used by Task 4.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/util/Code128BarcodeTest.java`:

```java
package org.runnerup.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.oned.Code128Reader;
import org.junit.Test;

public class Code128BarcodeTest {
  private static final int DECODE_HEIGHT = 64;

  @Test
  public void roundTripShortValue() throws ReaderException {
    String value = "C1234567";
    assertEquals(value, decode(Code128Barcode.encode(value)));
  }

  @Test
  public void roundTripLongValue() throws ReaderException {
    String value = "A12345678901234";
    assertEquals(value, decode(Code128Barcode.encode(value)));
  }

  @Test
  public void deterministicEncoding() {
    assertEquals(Code128Barcode.encode("C1234567"), Code128Barcode.encode("C1234567"));
  }

  @Test
  public void quietZonesAreWhite() {
    BitMatrix matrix = Code128Barcode.encode("C1234567");
    for (int x = 0; x < 4; x++) {
      assertFalse(matrix.get(x, 0));
      assertFalse(matrix.get(matrix.getWidth() - 1 - x, 0));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullContentRejected() {
    Code128Barcode.encode(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyContentRejected() {
    Code128Barcode.encode("");
  }

  private static String decode(BitMatrix matrix) throws ReaderException {
    BinaryBitmap image =
        new BinaryBitmap(new GlobalHistogramBinarizer(toLuminanceSource(matrix, DECODE_HEIGHT)));
    Result result = new Code128Reader().decode(image);
    return result.getText();
  }

  private static LuminanceSource toLuminanceSource(BitMatrix matrix, int height) {
    int width = matrix.getWidth();
    byte[] pixels = new byte[width * height];
    for (int x = 0; x < width; x++) {
      boolean dark = matrix.get(x, 0);
      for (int y = 0; y < height; y++) {
        pixels[y * width + x] = (byte) (dark ? 0 : 255);
      }
    }
    return new RGBLuminanceSource(width, height, pixels);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.util.Code128BarcodeTest"`
Expected: FAIL — test compilation error: `Code128Barcode` does not exist.

- [ ] **Step 3: Implement `Code128Barcode`**

Create `app/src/main/org/runnerup/util/Code128Barcode.java`:

```java
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

package org.runnerup.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class Code128Barcode {
  private static final int QUIET_ZONE_MODULES = 4;

  private Code128Barcode() {}

  public static BitMatrix encode(String content) {
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("barcode content must not be null or empty");
    }
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES);
    try {
      return new Code128Writer().encode(content, BarcodeFormat.CODE_128, 0, 1, hints);
    } catch (WriterException e) {
      throw new IllegalArgumentException("cannot encode barcode", e);
    }
  }

  public static Bitmap renderToBitmap(BitMatrix matrix, int heightPx) {
    int width =
        Math.max(1, (int) Math.round((double) matrix.getWidth() * heightPx / matrix.getHeight()));
    int[] pixels = new int[width * heightPx];
    Arrays.fill(pixels, Color.WHITE);
    for (int x = 0; x < matrix.getWidth(); x++) {
      if (matrix.get(x, 0)) {
        int x0 = x * width / matrix.getWidth();
        int x1 = (x + 1) * width / matrix.getWidth();
        for (int px = x0; px < x1; px++) {
          for (int y = 0; y < heightPx; y++) {
            pixels[y * width + px] = Color.BLACK;
          }
        }
      }
    }
    Bitmap bitmap = Bitmap.createBitmap(width, heightPx, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, heightPx);
    return bitmap;
  }
}
```

Note on `encode`: `Code128Writer` (extends `OneDimensionalCodeWriter`) renders `outputWidth = max(width, contentWidth + quietZone)` with bar scale `outputWidth / fullWidth`. Passing `width = 0` therefore yields the natural 1:1 module scale and a height of 1 row; the quiet zone (from the `MARGIN` hint) is added symmetrically. `renderToBitmap` then upscales to the requested pixel height.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.util.Code128BarcodeTest"`
Expected: PASS — all 6 tests green (round-trip for short and long values, deterministic output, white quiet zones, null/empty rejection).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/util/Code128Barcode.java app/test/java/org/runnerup/util/Code128BarcodeTest.java
git commit -m "feat: add Code128Barcode encode and render helpers"
```

---

### Task 3: `BarcodeScanActivity` (CameraX scanner)

**Files:**
- Create: `app/res/layout/barcode_scan.xml`
- Create: `app/src/main/org/runnerup/view/BarcodeScanActivity.java`
- Modify: `app/AndroidManifest.xml`
- Modify: `common/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: CameraX + zxing deps (Task 1).
- Produces:
  - `public static final String BarcodeScanActivity.BARCODE_EXTRA` — extra key (`"barcode"`). Launcher sets `RESULT_OK` with the decoded value under this key. Consumed by Task 4's `ActivityResultLauncher<Intent>`.
  - Manifest registrations: `CAMERA` permission, `<uses-feature android.hardware.camera.any required="false">`, `BarcodeScanActivity`.

- [ ] **Step 1: Add scanner strings to `common/src/main/res/values/strings.xml`**

Append these lines just before the closing `</resources>` tag:

```xml
    <string name="Scan_parkrun_barcode">Scan parkrun barcode</string>
    <string name="Barcode_scan_hint">Point the camera at your parkrun barcode</string>
    <string name="Camera_permission_text">RunnerUp needs camera access to scan your parkrun barcode.</string>
```

- [ ] **Step 2: Create the scanner layout `app/res/layout/barcode_scan.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/barcode_scan_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/actionbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:title="@string/Scan_parkrun_barcode" />

    <androidx.camera.view.PreviewView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:padding="16dp"
        android:text="@string/Barcode_scan_hint" />
</LinearLayout>
```

- [ ] **Step 3: Create `BarcodeScanActivity`**

Create `app/src/main/org/runnerup/view/BarcodeScanActivity.java`:

```java
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
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
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
              Toast.makeText(this, R.string.Camera_permission_text, Toast.LENGTH_LONG).show();
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
            PreviewView viewFinder = findViewById(R.id.preview_view);
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(analysisExecutor, this::analyze);

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
          } catch (ExecutionException | InterruptedException e) {
            Log.e("BarcodeScanActivity", "failed to start camera", e);
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
    } catch (NotFoundException | ChecksumException | FormatException e) {
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
```

- [ ] **Step 4: Register the permission, feature, and activity in `app/AndroidManifest.xml`**

(a) After the `<uses-feature android:glEsVersion="0x00020000" android:required="true" />` block (line ~23), add:

```xml
    <uses-feature
        android:name="android.hardware.camera.any"
        android:required="false" />
```

(b) After the `ACCESS_NETWORK_STATE` uses-permission (line ~50), add:

```xml
    <uses-permission android:name="android.permission.CAMERA" />
```

(c) After the `AudioCueSettingsActivity` activity element (closing `</activity>` at line ~125), add:

```xml
        <activity
            android:name=".view.BarcodeScanActivity"
            android:label="@string/Scan_parkrun_barcode"
            android:theme="@style/AppTheme.NoActionBar" />
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Smoke-test the scanner on the device**

Install and launch the scanner directly (no settings entry exists yet):

```bash
./gradlew :app:installLatestDebug
adb shell am start -n org.runnerup.debug/.view.BarcodeScanActivity
```

Expected: camera permission dialog on first launch; after granting, the camera preview renders and the hint text shows. Point the phone at a printed Code 128 barcode (e.g. generate one at `https://barcode.tec-it.com/en/Code128?data=C1234567` and show it on a laptop screen). A toast with the decoded value appears and the activity finishes. If permission is denied, a toast shows and the activity finishes.

- [ ] **Step 7: Commit**

```bash
git add app/res/layout/barcode_scan.xml app/src/main/org/runnerup/view/BarcodeScanActivity.java app/AndroidManifest.xml common/src/main/res/values/strings.xml
git commit -m "feat: add Code 128 camera scanner activity"
```

---

### Task 4: Settings entry and `ParkrunBarcodeActivity` window

**Files:**
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `app/res/values/pref_keys.xml`
- Modify: `app/res/values/dimens.xml`
- Create: `app/res/drawable-nodpi/parkrun_logo.png` (asset)
- Create: `app/res/layout/parkrun_barcode.xml`
- Create: `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java`
- Modify: `app/AndroidManifest.xml`
- Modify: `app/res/xml/settings.xml`

**Interfaces:**
- Consumes: `Code128Barcode.encode/renderToBitmap` (Task 2); `BarcodeScanActivity.BARCODE_EXTRA` + `RESULT_OK` result (Task 3).
- Produces: Settings row "parkrun barcode" that opens `ParkrunBarcodeActivity`; persistent SharedPreferences value under key `pref_parkrun_barcode`.

- [ ] **Step 1: Add the remaining strings to `common/src/main/res/values/strings.xml`**

Append these lines just before the closing `</resources>` tag (note `\\n` renders as a literal newline in the string value):

```xml
    <string name="Parkrun_barcode">parkrun barcode</string>
    <string name="Scan_new_barcode">Scan new barcode</string>
    <string name="No_parkrun_barcode_text">No parkrun barcode stored yet.\nScan your barcode so you can show it at events.</string>
    <string name="Replace_barcode">Replace barcode?</string>
    <string name="Replace_barcode_text">A parkrun barcode is already stored. Replace it with %1$s?</string>
    <string name="Delete_barcode">Delete barcode?</string>
    <string name="Delete_barcode_text">The stored parkrun barcode will be removed.</string>
```

- [ ] **Step 2: Add the preference key to `app/res/values/pref_keys.xml`**

Append before the closing `</resources>` tag:

```xml
    <string name="pref_parkrun_barcode">pref_parkrun_barcode</string>
```

- [ ] **Step 3: Add the barcode height dimension to `app/res/values/dimens.xml`**

Append before the closing `</resources>` tag:

```xml
    <dimen name="barcode_height">180dp</dimen>
```

- [ ] **Step 4: Download the parkrun logo asset**

```bash
mkdir -p app/res/drawable-nodpi
curl -L -o app/res/drawable-nodpi/parkrun_logo.png \
  "https://upload.wikimedia.org/wikipedia/cy/0/0c/Parkrun_Logo.svg.png"
```

Verify with `file app/res/drawable-nodpi/parkrun_logo.png` → expect a PNG image (`270 x 126`). If that URL is unreachable, fall back to `https://images.seeklogo.com/logo-png/43/1/parkrun-logo-png_seeklogo-433559.png`. Do NOT commit a much larger logo; the 270x126 asset is sufficient for a display logo.

- [ ] **Step 5: Create the window layout `app/res/layout/parkrun_barcode.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/parkrun_barcode_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/actionbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:title="@string/Parkrun_barcode" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_horizontal"
            android:orientation="vertical"
            android:padding="32dp">

            <ImageView
                android:id="@+id/parkrun_logo"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:contentDescription="@string/Parkrun_barcode"
                android:src="@drawable/parkrun_logo" />

            <LinearLayout
                android:id="@+id/empty_state"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:paddingTop="32dp">

                <TextView
                    android:id="@+id/empty_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:gravity="center"
                    android:text="@string/No_parkrun_barcode_text"
                    android:textSize="16sp" />

                <Button
                    android:id="@+id/empty_scan_button"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="24dp"
                    android:text="@string/Scan_parkrun_barcode" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/stored_state"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_horizontal"
                android:orientation="vertical"
                android:paddingTop="32dp"
                android:visibility="gone">

                <ImageView
                    android:id="@+id/barcode_view"
                    android:layout_width="match_parent"
                    android:layout_height="@dimen/barcode_height"
                    android:background="@android:color/white"
                    android:contentDescription="@string/Parkrun_barcode"
                    android:scaleType="fitCenter" />

                <TextView
                    android:id="@+id/barcode_value"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:textIsSelectable="true"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="24dp"
                    android:orientation="horizontal">

                    <Button
                        android:id="@+id/scan_new_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/Scan_new_barcode" />

                    <Button
                        android:id="@+id/delete_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:text="@string/Delete" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 6: Create `ParkrunBarcodeActivity`**

Create `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java`:

```java
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
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
            String existing = prefs.getString(getString(R.string.pref_parkrun_barcode), null);
            if (existing != null && !existing.equals(scanned)) {
              new AlertDialog.Builder(this)
                  .setTitle(R.string.Replace_barcode)
                  .setMessage(getString(R.string.Replace_barcode_text, scanned))
                  .setPositiveButton(R.string.Yes, (dialog, which) -> saveBarcode(scanned))
                  .setNegativeButton(R.string.No, null)
                  .show();
            } else {
              saveBarcode(scanned);
            }
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
    findViewById(R.id.scan_new_button).setOnClickListener(v -> launchScanner());
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
    new AlertDialog.Builder(this)
        .setTitle(R.string.Delete_barcode)
        .setMessage(R.string.Delete_barcode_text)
        .setPositiveButton(R.string.Delete, (dialog, which) -> {
          prefs.edit().remove(getString(R.string.pref_parkrun_barcode)).apply();
          refresh();
        })
        .setNegativeButton(R.string.Cancel, null)
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
      int heightPx = (int) getResources().getDimension(R.dimen.barcode_height);
      barcodeView.setImageBitmap(Code128Barcode.renderToBitmap(Code128Barcode.encode(barcode), heightPx));
      valueView.setText(barcode);
    }
  }
}
```

- [ ] **Step 7: Register the activity in `app/AndroidManifest.xml`**

After the `BarcodeScanActivity` activity element added in Task 3, add:

```xml
        <activity
            android:name=".view.ParkrunBarcodeActivity"
            android:label="@string/Parkrun_barcode"
            android:theme="@style/AppTheme.NoActionBar" />
```

- [ ] **Step 8: Add the settings row to `app/res/xml/settings.xml`**

After the Units `</Preference>` block (closing at line 29), insert:

```xml
    <!--Parkrun barcode -->

    <Preference
        android:key="@string/pref_parkrun_barcode"
        android:title="@string/Parkrun_barcode"
        app:iconSpaceReserved="false">
        <intent
            android:targetClass="org.runnerup.view.ParkrunBarcodeActivity"
            android:targetPackage="@string/applicationIdFull" />
    </Preference>
```

- [ ] **Step 9: Build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Smoke-test the window (partial — scanner flow validated in Task 5)**

Install and navigate: `./gradlew :app:installLatestDebug`, open the app, go to Settings → "parkrun barcode".

Expected: the window shows the parkrun logo and the empty state ("No parkrun barcode stored yet.…" + a "Scan parkrun barcode" button). Tapping Scan opens `BarcodeScanActivity` (full scan flow validated end-to-end in Task 5). Back returns to Settings.

- [ ] **Step 11: Commit**

```bash
git add common/src/main/res/values/strings.xml app/res/values/pref_keys.xml app/res/values/dimens.xml app/res/drawable-nodpi/parkrun_logo.png app/res/layout/parkrun_barcode.xml app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java app/AndroidManifest.xml app/res/xml/settings.xml
git commit -m "feat: add parkrun barcode settings entry and display window"
```

---

### Task 5: Full verification, device smoke test, and docs

**Files:**
- Modify: `docs/superpowers/plans/2026-08-10-parkrun-barcode.md` (checkbox states)
- Other: `.superpowers/sdd/2026-08-10-parkrun-barcode/progress.md` (create, gitignored)

**Interfaces:**
- Consumes: Tasks 1-4.

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew test`
Expected: PASS (includes `Code128BarcodeTest`).

- [ ] **Step 2: Run lint on the debug variant**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL; the only permitted lint output is the 25 pre-existing baseline issues. Any NEW issue (e.g. `MissingPermission`, `SetTextI18n`, unused resources) must be fixed before proceeding.

- [ ] **Step 3: Apply and verify formatting**

Run: `./gradlew spotlessApply`
Then: `./gradlew spotlessCheck`
Expected: PASS (CI gate).

- [ ] **Step 4: Verify the F-Droid build still compiles**

Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.free`
Expected: BUILD SUCCESSFUL (CameraX + zxing are F-Droid-safe; no free/non-free conditional needed).

- [ ] **Step 5: End-to-end device smoke test**

With the test device (OnePlus Nord CE, serial `5717a66e`, already on `latestDebug`):

1. `./gradlew :app:installLatestDebug`.
2. Open the app → Settings → "parkrun barcode" → window shows empty state.
3. Tap "Scan parkrun barcode" → grant camera permission → point at a Code 128 barcode (e.g. `https://barcode.tec-it.com/en/Code128?data=C1234567` on a laptop screen) → activity closes and the window now shows: parkrun logo, tall black-on-white barcode, the value text, "Scan new barcode" and "Delete" buttons.
4. Tap "Delete" → confirmation dialog appears → confirm → empty state returns.
5. Scan `C1234567` again, then scan a second value (e.g. `C9999999`) → "Replace barcode?" dialog with the new value appears → "No" keeps `C1234567`; "Yes" stores `C9999999`.
6. Force-stop the app (`adb shell am force-stop org.runnerup.debug`) and reopen → the stored barcode is still shown (persistence across sessions).
7. Verify both installable ids still build: `./gradlew :app:assembleLatestDebug -Porg.runnerup.free` (from Step 4) — `org.runnerup.debug` and `org.runnerup.free` remain side-by-side installable.

- [ ] **Step 6: Update the plan and SDD ledger, then commit**

Mark all completed steps `[x]` in this plan. Record the implementation in `.superpowers/sdd/2026-08-10-parkrun-barcode/progress.md` (create the directory; this path is gitignored). Then:

```bash
git add docs/superpowers/plans/2026-08-10-parkrun-barcode.md
git commit -m "docs: mark parkrun barcode plan complete"
```

---

## Self-Review Notes

- **Spec coverage:** Settings entry (Task 4 Step 8), window with logo / tall black-on-white barcode / raw value / Delete / Scan-new (Tasks 4, 2), scan-into-window flow with replace-confirmation (Task 4 Step 6 + Task 3), delete confirmation (Task 4 Step 6), persistence across sessions (Task 4 `SharedPreferences` + Task 5 Step 5.6), Code 128 only (Task 2 writer + Task 3 `Code128Reader`), F-Droid-safe deps (Task 1, Task 5 Step 4).
- **Placeholder scan:** All code blocks are concrete; no TBD/TODO.
- **Type consistency:** `Code128Barcode.encode` → `BitMatrix`, `renderToBitmap(BitMatrix, int)` → `Bitmap` (Task 2) match usage in `ParkrunBarcodeActivity` (Task 4). `BarcodeScanActivity.BARCODE_EXTRA` and `RESULT_OK` (Task 3) match the launcher callback in Task 4. `pref_parkrun_barcode` key defined (Task 4 Step 2) and referenced by both `settings.xml` and `ParkrunBarcodeActivity`.
