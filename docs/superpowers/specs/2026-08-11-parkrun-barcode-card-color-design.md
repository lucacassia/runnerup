# Parkrun Barcode Card Color Match

Date: 2026-08-11
Status: Approved by user
Relates to: `2026-08-11-parkrun-barcode-card-design.md` and `2026-08-11-parkrun-barcode-card-polish-design.md` (stored-state card)

## Scope

Make the stored-state barcode render as black bars on the same background color as the card it sits on. Currently the barcode's background is hardcoded white, which in the light theme reads as a stark white plate against the card's light blue/gray surface. The card, scanner, empty state, and behavior are unchanged.

## Background

- The card (`MaterialCardView`, `materialCardViewElevatedStyle`, `cardBackgroundColor` white) renders its surface in light theme as `#F5F8FD` — the M3 elevated-surface tint (white + ~5% `colorPrimary`). In dark theme the surface renders pure white.
- The barcode `ImageView` has `android:background="@android:color/white"`; the rendered bitmap (`Code128Barcode.pixels`) fills the background with opaque white and draws black bars. Because the white gaps between bars are bitmap pixels (not the ImageView background), matching the card requires the bitmap background itself to be transparent so the card's surface shows through.

## Changes

### 1. Transparent barcode background

`app/src/main/org/runnerup/util/Code128Barcode.java`:

- In `pixels(BitMatrix, int, int)`, replace `Arrays.fill(pixels, Color.WHITE)` with `Arrays.fill(pixels, Color.TRANSPARENT)` (i.e. `0x00000000`). Bars stay opaque black (`Color.BLACK`). The only caller of `pixels`/`renderToBitmap` is `ParkrunBarcodeActivity.renderBarcode`.

### 2. Card shows through

`app/res/layout/parkrun_barcode.xml`:

- Remove `android:background="@android:color/white"` from `barcode_view` so the card surface shows through the transparent quiet zones. The bitmap is exactly view-sized (`width × barcode_height`), so nothing else about the ImageView changes.

### 3. Test updates

`app/test/java/org/runnerup/util/Code128BarcodeTest.java`:

- `renderQuietZonesAreWhite` → assert quiet-zone pixels are `0x00000000` (transparent) instead of `0xFFFFFFFF`.
- `renderDecodesBack` → composite the transparent pixels over a light backdrop (`#F5F8FD`, the light-theme card color) before feeding `RGBLuminanceSource`, mirroring what the screen actually displays (a transparent pixel is `RGB(0,0,0)` to `RGBLuminanceSource`, which would otherwise decode as black).
- `quietZonesAreWhite` (matrix-level) is unchanged — it asserts the encoding's quiet zone, not the render.

## Result

- Light theme: black bars on the card's `#F5F8FD` surface — barcode background exactly matches the card.
- Dark theme: black bars on the white card surface — visually identical to today (already matched).

## Files touched

- `app/src/main/org/runnerup/util/Code128Barcode.java` — fill `Color.TRANSPARENT`.
- `app/res/layout/parkrun_barcode.xml` — drop `barcode_view` background.
- `app/test/java/org/runnerup/util/Code128BarcodeTest.java` — quiet-zone assertion + decode backdrop.
- No resource changes, no string changes.

## Verification

- `./gradlew test` — BUILD SUCCESSFUL (updated barcode tests pass).
- `./gradlew spotlessApply` then `spotlessCheck`.
- `./gradlew :app:lintLatestDebug` — no new issues beyond the 25-item baseline.
- `./gradlew :app:assembleLatestDebug` — BUILD SUCCESSFUL.
- Device check (Nexus 5X): light theme shows black bars directly on the card surface with no white plate; dark theme unchanged (black bars on white card).
