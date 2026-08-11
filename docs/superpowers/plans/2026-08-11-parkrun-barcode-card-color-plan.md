# Parkrun Barcode Card Color Match Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the stored-state parkrun barcode as black bars on the card's own surface color instead of on a hardcoded white plate.

**Architecture:** The barcode bitmap's background pixels change from opaque white to transparent; the card's surface (M3 elevated tint in light theme, white in dark theme) shows through the transparent quiet zones. The `barcode_view` ImageView drops its white background. Two tests are updated to assert the new transparency and to decode the render composited over a light backdrop (a raw transparent pixel reads as black to `RGBLuminanceSource`).

**Tech Stack:** Java, Android (`android.graphics.Color`, `Bitmap`), JUnit 4, Google ZXing `Code128Writer`.

## Global Constraints

- Barcode bars stay opaque black (`Color.BLACK`); only the background fill changes.
- Only caller of `pixels`/`renderToBitmap` is `ParkrunBarcodeActivity.renderBarcode` — no other call sites.
- No resource changes, no string changes, no layout/theme color changes.
- Every commit must pass `./gradlew spotlessCheck` (googleJavaFormat).
- Working tree: worktree `/home/megadoro/local/runnerup/.worktrees/parkrun-barcode`, branch `parkrun-barcode`.

---
### Task 1: Transparent barcode background

**Files:**
- Modify: `app/src/main/org/runnerup/util/Code128Barcode.java:54-69` (`pixels` method)
- Modify: `app/test/java/org/runnerup/util/Code128BarcodeTest.java`

**Interfaces:**
- Consumes: `Code128Barcode.encode(String)` → `BitMatrix` (unchanged); `Code128Barcode.pixels(BitMatrix, int, int)` → `int[]` (unchanged signature).
- Produces: `pixels` returns ARGB pixels where the background is `0x00000000` (transparent) and bars are `0xFF000000`. Tests later rely on `renderToBitmap` (which wraps `pixels`) producing a view-sized transparent-backed bitmap.

- [ ] **Step 1: Write the failing tests**

In `app/test/java/org/runnerup/util/Code128BarcodeTest.java`, replace the `renderQuietZonesAreWhite` test (lines 62-76) with a transparent-assertion version, and update `renderDecodesBack` (lines 78-84) to composite over a light backdrop:

```java
  @Test
  public void renderQuietZonesAreTransparent() {
    BitMatrix matrix = Code128Barcode.encode("C1234567");
    int widthPx = 200;
    int[] pixels = Code128Barcode.pixels(matrix, widthPx, DECODE_HEIGHT);
    int matrixWidth = matrix.getWidth();
    for (int x = 0; x < 4; x++) {
      assertColumnsTransparent(
          pixels, widthPx, x * widthPx / matrixWidth, (x + 1) * widthPx / matrixWidth);
      assertColumnsTransparent(
          pixels,
          widthPx,
          (matrixWidth - 1 - x) * widthPx / matrixWidth,
          (matrixWidth - x) * widthPx / matrixWidth);
    }
  }

  @Test
  public void renderDecodesBack() throws ReaderException {
    String value = "C1234567";
    assertEquals(
        value,
        decode(
            overLightBackdrop(Code128Barcode.pixels(Code128Barcode.encode(value), 200, DECODE_HEIGHT)),
            200));
  }
```

Add these helpers (replacing `assertColumnsWhite`):

```java
  private static void assertColumnsTransparent(int[] pixels, int widthPx, int x0, int x1) {
    for (int px = x0; px < x1; px++) {
      for (int y = 0; y < DECODE_HEIGHT; y++) {
        assertEquals(0x00000000, pixels[y * widthPx + px]);
      }
    }
  }

  private static int[] overLightBackdrop(int[] pixels) {
    int[] out = new int[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      out[i] = (pixels[i] & 0xFF000000) == 0 ? 0xFFF5F8FD : pixels[i];
    }
    return out;
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.util.Code128BarcodeTest"`
Expected: `renderQuietZonesAreTransparent` FAILS (`expected: 0x0 but was: 0xffffffff`). `renderDecodesBack` passes (white background also composites fine) — that is expected; it is the regression guard for the new background.

- [ ] **Step 3: Implement the transparent background**

In `app/src/main/org/runnerup/util/Code128Barcode.java:56`, change:

```java
    Arrays.fill(pixels, Color.WHITE);
```

to:

```java
    Arrays.fill(pixels, Color.TRANSPARENT);
```

No other changes to the file (`Color` is already imported; bars already write `Color.BLACK`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLatestDebugUnitTest --tests "org.runnerup.util.Code128BarcodeTest"`
Expected: ALL PASS (quiet zones now transparent `0x00000000`; decode succeeds when composited over the `#F5F8FD` backdrop).

- [ ] **Step 5: Run formatting**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/util/Code128Barcode.java app/test/java/org/runnerup/util/Code128BarcodeTest.java
git commit -m "feat: render parkrun barcode with transparent background"
```

### Task 2: Let the card show through the barcode view

**Files:**
- Modify: `app/res/layout/parkrun_barcode.xml:86`

**Interfaces:**
- Consumes: Task 1 — the bitmap drawn into `barcode_view` is transparent-backed.
- Produces: `barcode_view` with no background attribute; the card surface shows through. `ParkrunBarcodeActivity.renderBarcode()` still sets the bitmap at view size (`width × barcode_height`), unchanged.

- [ ] **Step 1: Remove the white background**

In `app/res/layout/parkrun_barcode.xml`, delete line 86 (`android:background="@android:color/white"`) from the `barcode_view` ImageView. The view keeps `scaleType="fitCenter"`, `@dimen/barcode_height`, `layout_marginTop="24dp"`.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL with no NEW issues beyond the 25-item `app/lint-baseline.xml` baseline.

- [ ] **Step 4: Run formatting check**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL (no Java/XML changes needing format).

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/parkrun_barcode.xml
git commit -m "style: drop white background from parkrun barcode view"
```

### Task 3: On-device verification

**Files:** (none)

- [ ] **Step 1: Install and open**

The device (Nexus 5X, serial `025b46e24edcbca6`, package `org.runnerup.debug`) is currently in dark theme with stored barcode `A11543609`. Install: `adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`. Launch via `adb -s 025b46e24edcbca6 shell monkey -p org.runnerup.debug -c android.intent.category.LAUNCHER 1`, tap Settings tab (900,1688), then parkrun row (540,442).

- [ ] **Step 2: Verify dark theme**

Dark theme: card and barcode area both white; bars black. Screenshot and sample the barcode plate color — expected `(255,255,255)`, matching the card.

- [ ] **Step 3: Verify light theme**

Switch theme to Light (device Settings → Display → Dark theme off; the app applies the system theme). Screenshot: barcode plate must now show the card's `#F5F8FD` — no white rectangle. Sample a quiet-zone pixel at the same x/y used to measure `(255,255,255)` before; it must now equal the card interior color `(245,248,253)`.

- [ ] **Step 4: Verify scan/decode still round-trips**

Stored barcode `A11543609` decodes on screen (scan the displayed card with the device's camera app as a sanity check) — optional, since `renderDecodesBack` already covers decoder compatibility.

## Self-Review

**Spec coverage:** Spec §1 (transparent fill) → Task 1; §2 (drop ImageView background) → Task 2; §3 (test updates) → Task 1 Steps 1-2; Verification section (unit tests, spotless, lint, assemble, device light/dark) → Tasks 1-3. All covered.

**Placeholder scan:** No TBDs; every step has concrete code or commands.

**Type consistency:** `pixels(BitMatrix, int, int)` and `renderToBitmap` signatures unchanged; new helpers `assertColumnsTransparent`/`overLightBackdrop` are defined in the same task that uses them; `overLightBackdrop` returns `int[]` consumed by the existing `decode(int[], int)`.
