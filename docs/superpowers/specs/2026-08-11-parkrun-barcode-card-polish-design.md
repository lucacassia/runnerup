# Parkrun Barcode Card Polish

Date: 2026-08-11
Status: Approved by user
Relates to: `2026-08-11-parkrun-barcode-card-design.md` (stored-state card restyle)

## Scope

Small visual polish of the stored-state card in `ParkrunBarcodeActivity`: more space between the logo and the barcode, a 50% taller barcode, the delete FAB replaced with an outlined button, and the parkrun logo recolored black. Scanner, empty state, persistence, and behavior are unchanged.

## Changes

### 1. Logo-to-barcode spacing

`app/res/layout/parkrun_barcode.xml`: add `android:layout_marginTop="24dp"` to `barcode_view`. The gap shows the card's white surface (card background is white), so it reads as whitespace between the logo and the bars. 24dp matches the empty state's `empty_scan_button` top margin.

### 2. Barcode height

`app/res/values/dimens.xml`: `barcode_height` `180dp` → `270dp` (50% taller). The rendered bitmap is `width × barcode_height`, so bars scale proportionally with the view; no letterboxing, no aspect change, still scannable.

### 3. Delete action

`app/res/layout/parkrun_barcode.xml`: replace the `FloatingActionButton` (`@id/delete_button`) with a `MaterialButton`:

- Style: `@style/Widget.Material3.Button.OutlinedButton` (M3 outlined style, theme colors).
- Text: the existing `Delete` string (no new string resource).
- Placement: centered at the card bottom, `layout_marginTop="16dp"`, `layout_marginBottom="24dp"` (same margins as the FAB it replaces).
- Keep the view id `delete_button`; the existing click listener → `confirmDelete()` in `ParkrunBarcodeActivity.java` is unchanged.

### 4. Parkrun logo color

`app/res/drawable-nodpi/parkrun_logo.png`: recolor to pure black. The opaque pixels are currently dark purple `#2B233D`. Set RGB to `(0,0,0)` for every pixel while preserving the alpha channel (keeps anti-aliased edges). File size/geometry (270×126) unchanged; convert the palette-mode PNG to RGBA and save.

## Files touched

- `app/res/layout/parkrun_barcode.xml` — barcode `marginTop`; FAB → OutlinedButton.
- `app/res/values/dimens.xml` — `barcode_height` 270dp.
- `app/res/drawable-nodpi/parkrun_logo.png` — recolored black.
- No Java changes.

## Verification

- `./gradlew test` — BUILD SUCCESSFUL.
- `./gradlew :app:lintLatestDebug` — no new issues beyond the 25-item baseline.
- `./gradlew spotlessApply` then `spotlessCheck`.
- `./gradlew :app:assembleLatestDebug` — BUILD SUCCESSFUL.
- Device check (Nexus 5X): card shows black logo, 24dp gap, 270dp barcode, outlined "Delete" button; delete → confirm dialog → empty state; rescan stores and renders.
