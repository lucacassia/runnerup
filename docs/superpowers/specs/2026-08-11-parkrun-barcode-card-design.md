# Parkrun Barcode: Stored-State Card Restyle

Date: 2026-08-11
Status: Approved by user
Relates to: `2026-08-10-parkrun-barcode-design.md` (original feature, implemented and review-clean on branch `parkrun-barcode`)

## Scope

Restyle the **stored state** of `ParkrunBarcodeActivity` as a Material 3 elevated card, and simplify the scan flow to Delete-then-scan. The empty state, the scanner (`BarcodeScanActivity`), `Code128Barcode`, persistence, and the Settings row are **unchanged**.

## Stored-state card

The stored state (a barcode value exists in the default `SharedPreferences` under `pref_parkrun_barcode`) renders as a single `MaterialCardView`:

- **Style/elevation:** `com.google.android.material.card.MaterialCardView` with the M3 elevated style (`?attr/materialCardViewElevatedStyle`), full window width minus the existing 32dp outer padding.
- **Surface color:** `app:cardBackgroundColor` is white (`@android:color/white`) **in both light and dark themes** for maximum barcode readability.
- **Corner clipping:** `android:clipToOutline="true"` so the flush barcode follows the card's rounded corners.
- **Contents (top to bottom, vertical):**
  1. **Image area** — no horizontal padding, so the barcode spans the full card width:
     - `ImageView` `@id/parkrun_logo`, centered, `wrap_content`, `android:paddingTop="24dp"`.
     - `ImageView` `@id/barcode_view`, `match_parent` × `@dimen/barcode_height` (180dp), `background="@android:color/white"`, `scaleType="fitCenter"`, `contentDescription` as today.
  2. **Text section** — `TextView` `@id/barcode_value`: the code only (no label), `20sp` bold, centered, `textIsSelectable`, `textColor="@android:color/black"` (fixed because the surface is always white).
  3. **Bottom action** — a small icon-only `FloatingActionButton` `@id/delete_button`:
     - `app:fabSize="small"`, `app:srcCompat="@drawable/ic_delete"` (the standard Material design-system trash glyph; no text), `contentDescription="@string/Delete"`.
     - Themed via `app:backgroundTint="?attr/colorPrimaryContainer"` and `app:tint="?attr/colorOnPrimaryContainer"` (same convention as `start_fab.xml`), centered at the card bottom.

## Behavior changes

- **Delete-then-scan:** while a barcode is stored, the only action is Delete (the small FAB). There is no "Scan new barcode" entry in the stored state. To change the code, the user deletes first, then scans from the empty state.
- **Empty state unchanged:** logo + `No_parkrun_barcode_text` + `Scan_parkrun_barcode` button (outside any card).
- **Scan launcher callback simplifies:** a scan can only be launched from the empty state, so the result always stores directly — the replace-confirm dialog is removed. The `Refresh`/render behavior (3-arg `Code128Barcode.renderToBitmap` with measured `barcode_view` width, `barcode_height`) is unchanged.
- **Delete-confirm dialog unchanged:** title `Delete_barcode`, message `Delete_barcode_text`, positive `Delete` / negative `Cancel`.

## Resource changes

- Removed (no longer referenced; prevents new `UnusedResources` lint issues):
  - `app/res/layout/parkrun_barcode.xml`: `@+id/empty_scan_button` **stays**; `@+id/scan_new_button` and the horizontal button bar are removed; the stored state becomes the card.
  - `common/src/main/res/values/strings.xml`: `Scan_new_barcode`, `Replace_barcode`, `Replace_barcode_text`.
- Kept and reused: `ic_delete` (existing drawable), `Delete`, `Delete_barcode`, `Delete_barcode_text`, `Cancel`, `Yes`, `No` (shared strings), `Scan_parkrun_barcode`, `No_parkrun_barcode_text`.

## Files touched

- `app/res/layout/parkrun_barcode.xml` — stored state becomes the `MaterialCardView`; remove `scan_new_button`/button bar.
- `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java` — remove the replace-confirm branch in the scan callback (save directly); wire the FAB to `confirmDelete()`; render path unchanged.
- `common/src/main/res/values/strings.xml` — remove the three dead strings.

## Verification

- `./gradlew test` — BUILD SUCCESSFUL (existing suite; no logic under test changes).
- `./gradlew :app:lintLatestDebug` — no new issues beyond the 25-item baseline (dead strings removed so `UnusedResources` stays clean; the FAB follows the `start_fab` pattern).
- `./gradlew spotlessApply` then `spotlessCheck`.
- `./gradlew :app:assembleLatestDebug` — BUILD SUCCESSFUL.
- Device check (light + dark theme): empty state unchanged; stored state shows the white card with centered logo, full-bleed barcode, centered code text, and the small themed delete FAB; Delete → confirm dialog → empty state; force-stop persistence intact.
