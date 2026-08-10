# parkrun barcode — design

**Date:** 2026-08-10
**Status:** Approved design (in review)

## Overview

Add a "parkrun barcode" entry to the app's Settings screen. It opens a window
that shows the athlete's parkrun barcode rendered black-on-white so it can be
scanned by another device at a parkrun event. Only one barcode is stored at a
time; it persists across app sessions.

## User workflow

1. From the root Settings screen, the user taps the **parkrun barcode** entry.
2. A window opens:
   - **Empty state** (no barcode stored): shows "no barcode stored" and a
     prominent **Scan barcode** button. Tapping it opens the camera scanner.
   - **Stored state**: shows the parkrun logo at the top, the barcode rendered
     black-on-white (tall, for reliable scanning by other devices), the raw
     value text, a **Delete** button, and a **Scan new barcode** button.
     (The logo is shown at the top of the window in both the empty and stored
     states.)
3. Scanning (camera): the user points the camera at a Code 128 barcode. On a
   successful read, the app stores the barcode's data.
   - If a barcode was already stored, a confirmation dialog
     ("Replace existing barcode?") appears; **Replace** stores the new value,
     **Cancel** keeps the old one.
4. Delete: with a barcode stored, the user taps **Delete**; a confirmation
   dialog appears; on confirm the stored barcode is removed and the window
   returns to the empty state, where a new barcode can be scanned.

Only one barcode is ever stored. A canceled scan changes nothing.

## Requirements

- Scanner accepts **Code 128 only**.
- Barcode data persists between sessions (SharedPreferences).
- The stored barcode is rendered from its data as a Code 128 barcode,
  black bars on a white background, tall enough to be scanned by other
  devices.
- The window shows the parkrun logo at the top (display-only use; the parkrun
  logo is a trademark of parkrun).
- Delete and re-scan both require confirmation when a barcode is stored.
- Works on all build variants (default, nomap, free). No Google Play services
  dependency (F-Droid-safe).

## Design

### Dependencies

Added to the `app` module's main `dependencies` block (`app/build.gradle`),
unconditionally:

- `com.google.zxing:core` (3.5.x) — Apache-2.0. Provides `Code128Reader`
  (decoding in the scanner) and `Code128Writer` (encoding for rendering).
- `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`,
  `camera-view` (1.4.x) — AndroidX/Jetpack, F-Droid-safe. Camera preview +
  frame analysis for the scanner.

Both are F-Droid-safe: the F-Droid CI `sed` stripping targets only
`play-services`, `com.mapbox.maps`, and `api.mapbox.com` lines.

### Settings entry

- New pref key `pref_parkrun_barcode` in `app/res/values/pref_keys.xml`.
- New strings in the `common` module (`common/src/main/res/values/strings.xml`,
  matching where Settings titles live): entry title "parkrun barcode", window
  titles, empty-state text, scan/delete button labels, and the two
  confirmation dialogs.
- New row in `app/res/xml/settings.xml` (root screen, placed after Units):
  a plain `<Preference>` with an `<intent>` to `ParkrunBarcodeActivity`
  (same pattern as the Audio cues / Accounts rows).
- AndroidManifest: add `<uses-permission android:name="android.permission.CAMERA"/>`
  and `<uses-feature android:name="android.hardware.camera" android:required="false"/>`.

### ParkrunBarcodeActivity (`app/src/main/org/runnerup/view/`)

The window opened by the settings entry.

- Layout `parkrun_barcode.xml`: parkrun logo `ImageView` at top, a white
  card holding the barcode `ImageView`, the raw value `TextView`, a Delete
  button and a Scan (new) barcode button, plus an empty-state label.
- Reads the stored value from default SharedPreferences on create/resume and
  renders it via `Code128Barcode`.
- Empty state: hides the barcode card + Delete/Scan-new buttons; shows the
  empty-state label + **Scan barcode** button.
- **Scan barcode / Scan new barcode** → starts `BarcodeScanActivity` via
  `registerForActivityResult(StartActivityForResult)`.
  - On a result: if a barcode is already stored, show the replace-confirmation
    `MaterialAlertDialogBuilder`; only on **Replace** write the new value.
    If nothing is stored, write it immediately. Then re-render.
- **Delete** → confirmation dialog → on confirm, remove the value and switch
  to the empty state.

### BarcodeScanActivity (`app/src/main/org/runnerup/view/`)

The camera scanner.

- Requests `CAMERA` at runtime on entry
  (`ActivityResultContracts.RequestPermission`). On permanent denial, show an
  explanation and finish. On denial with rationale available, re-request.
- CameraX: `ProcessCameraProvider` binds `Preview` (to a `PreviewView`) and
  `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`).
- Per frame: `Code128Reader` decodes the luminance (`RGBLuminanceSource` from
  `ImageProxy.toBitmap()`, or the Y-plane directly). Only `Code128` is
  decoded. On success: haptic feedback, then `RESULT_OK` with the decoded
  string in an extra, finish.
- UI: dark scan screen, `PreviewView` fills, a centered scan-target frame,
  a hint label, and a cancel/back affordance.
- Handles: no camera hardware / camera init failure (finish with a message),
  permission denied (message), and cancelled scans (finish without result).

### Code128Barcode (`app/src/main/org/runnerup/util/`)

Pure render helper, no Android framework state (testable).

- `encode(String content, int widthPx, int heightPx, int quietZonePx)` →
  `Bitmap` (ARGB_8888): black bars on white, using
  `Code128Writer.encode(content)` → `BitMatrix`. Width is scaled from the
  matrix so bar ratios are preserved; `heightPx` is the tall target height
  (target on screen: ~180dp tall, full window width minus padding).
- Re-encoding from the stored data means the rendered barcode always carries
  exactly the stored value regardless of how it was scanned.

### Persistence

- Stored in default SharedPreferences under key `pref_parkrun_barcode`
  (a string). Survives sessions. Single value = single barcode.
- Written by `ParkrunBarcodeActivity` on scan confirm; removed on delete.

### Logo asset

- The parkrun logo is added as an Android drawable (e.g.
  `app/res/drawable/parkrun_logo.png`), fetched from parkrun's official
  Resources Hub (resources.parkrun.com) or, failing a direct asset URL, a
  widely used PNG of the official logo.
- Trademark note: the parkrun logo is parkrun's trademark; inclusion is for
  display in the barcode window only.

## Edge cases and error handling

- No barcode stored: window shows empty state; nothing breaks.
- Scanner cancelled / back: window state unchanged.
- CAMERA denied (with or without "don't ask again"): explained, activity
  finishes, window unchanged.
- No camera hardware: `BarcodeScanActivity` finishes with a message.
- New scan when a barcode is stored: confirmation before replace.
- Delete: confirmation before removal.
- Unreadable barcode / no Code 128 found: scanner keeps analyzing frames;
  the user can cancel.

## Testing

- **Unit test** (`app/test/java`): `Code128Barcode` — encode a known string
  (e.g. `C0012345`), assert a non-null black-on-white bitmap of the requested
  height, and round-trip: decode the rendered bitmap with `Code128Reader`
  and assert the content matches.
- **Device smoke test** (as available): grant CAMERA; from Settings open the
  window (empty state → scan prompt); scan a Code 128 barcode; confirm the
  window renders the tall barcode + raw value; scan a new barcode and confirm
  the replace dialog; delete and confirm the empty state returns; verify the
  barcode survives app restart.

## Files touched

- `app/build.gradle` — CameraX + ZXing dependencies.
- `app/AndroidManifest.xml` — CAMERA permission, camera feature.
- `app/res/xml/settings.xml` — the parkrun barcode row.
- `app/res/values/pref_keys.xml` — `pref_parkrun_barcode`.
- `common/src/main/res/values/strings.xml` — UI strings.
- `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java` — window.
- `app/src/main/org/runnerup/view/BarcodeScanActivity.java` — scanner.
- `app/src/main/org/runnerup/util/Code128Barcode.java` — render helper.
- New layouts/drawables: `app/res/layout/parkrun_barcode.xml`,
  `app/res/layout/barcode_scan.xml`, `app/res/drawable/parkrun_logo.png`.
- `app/test/java/...` — `Code128Barcode` unit test.

## Non-goals

- No sharing/export of the barcode, no display elsewhere in the app, no
  multi-barcode storage, no scanning of formats other than Code 128.
- No use of the barcode beyond rendering (parkrun scanning is done by the
  event's own equipment).
