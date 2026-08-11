# Parkrun Barcode Card Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the stored-state parkrun barcode card: 24dp logo-to-barcode gap, barcode height 180dp → 270dp, delete FAB replaced with an M3 OutlinedButton, parkrun logo recolored pure black.

**Architecture:** Pure resource changes — two XML files and one PNG. No Java code changes. The barcode render path already reads `@dimen/barcode_height`, so the taller dimen automatically produces a taller bitmap; the view id `delete_button` is preserved so the existing listener in `ParkrunBarcodeActivity.java` needs no change.

**Tech Stack:** Android XML layouts, AndroidX Material Components 3 (OutlinedButton), PIL (Python) for the PNG recolor, Gradle + AGP 9.3.1.

## Global Constraints

- Worktree: `/home/megadoro/local/runnerup/.worktrees/parkrun-barcode`, branch `parkrun-barcode`. Run all `./gradlew` commands from the worktree root.
- Do NOT add `android:clipToOutline="true"` anywhere (lint-fatal `UnusedAttribute` at this repo's minSdk 28).
- No new string resources: reuse the existing `@string/Delete` (lives in `common`, resolves via resource merging like `@string/Scan_parkrun_barcode`).
- Do NOT add code comments unless asked.
- Commit messages: conventional commits. Do NOT stage `gradle.properties` or `gradle/gradle-daemon-jvm.properties`.
- Verification (AGENTS.md): `./gradlew test`, `./gradlew :app:lintLatestDebug` (25-item baseline may remain; no NEW issues), `./gradlew spotlessApply` then `spotlessCheck`, `./gradlew :app:assembleLatestDebug`.
- Device: Nexus 5X (serial `025b46e24edcbca6`), debug package `org.runnerup.debug`, stored barcode `A11543609` already present.

---

### Task 1: Card layout — spacing, taller barcode, outlined Delete button

**Files:**
- Modify: `app/res/layout/parkrun_barcode.xml`
- Modify: `app/res/values/dimens.xml`

**Interfaces:**
- Consumes: nothing (files exist on branch `parkrun-barcode`).
- Produces: `barcode_view` ImageView with `layout_marginTop="24dp"`; `@dimen/barcode_height` = `270dp`; `@id/delete_button` becomes a `MaterialButton` (same id, so `ParkrunBarcodeActivity.confirmDelete()` wiring is unchanged).

- [ ] **Step 1: Add the logo-to-barcode gap**

Edit `app/res/layout/parkrun_barcode.xml`. In the stored-state card, add `android:layout_marginTop="24dp"` to the `barcode_view` ImageView (currently lines 81-87, right after the `parkrun_logo` ImageView):

```xml
                    <ImageView
                        android:id="@+id/barcode_view"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/barcode_height"
                        android:layout_marginTop="24dp"
                        android:background="@android:color/white"
                        android:contentDescription="@string/Parkrun_barcode"
                        android:scaleType="fitCenter" />
```

- [ ] **Step 2: Replace the FAB with an OutlinedButton**

In the same file, replace the entire `FloatingActionButton` block (currently lines 99-109) with:

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/delete_button"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:layout_marginBottom="24dp"
                        android:text="@string/Delete" />
```

Keep the id `delete_button`. Do not add a `contentDescription` (the visible text is the label).

- [ ] **Step 3: Bump the barcode height**

Edit `app/res/values/dimens.xml`: change `barcode_height` from `180dp` to `270dp`:

```xml
    <dimen name="barcode_height">270dp</dimen>
```

- [ ] **Step 4: Verify the build compiles**

Run: `./gradlew :app:assembleLatestDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verify no new lint issues**

Run: `./gradlew :app:lintLatestDebug`
Expected: `Lint found no new issues (and 25 errors filtered by baseline lint-baseline.xml)` / `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/res/layout/parkrun_barcode.xml app/res/values/dimens.xml
git commit -m "feat: space out and enlarge parkrun barcode, outlined delete button"
```

---

### Task 2: Recolor the parkrun logo to pure black

**Files:**
- Modify: `app/res/drawable-nodpi/parkrun_logo.png`

**Interfaces:**
- Produces: `parkrun_logo.png` (270×126, RGBA) where every non-transparent pixel is `rgb(0,0,0)` with the original alpha preserved. Consumed by the existing `@drawable/parkrun_logo` references in `parkrun_barcode.xml` (empty state and card).

- [ ] **Step 1: Recolor the PNG**

Run this Python (PIL) snippet from the worktree root. It converts the palette image to RGBA and zeroes the RGB channels while keeping alpha (anti-aliased edges stay smooth):

```bash
python3 - <<'EOF'
from PIL import Image
path = 'app/res/drawable-nodpi/parkrun_logo.png'
im = Image.open(path).convert('RGBA')
r, g, b, a = im.split()
black = Image.new('L', im.size, 0)
im = Image.merge('RGBA', (black, black, black, a))
im.save(path)
print('recolored', im.size, im.mode)
EOF
```

- [ ] **Step 2: Verify every opaque pixel is pure black**

```bash
python3 - <<'EOF'
from PIL import Image
im = Image.open('app/res/drawable-nodpi/parkrun_logo.png').convert('RGBA')
bad = 0
for x in range(im.width):
    for y in range(im.height):
        r, g, b, a = im.getpixel((x, y))
        if a > 0 and not (r == 0 and g == 0 and b == 0):
            bad += 1
print('size', im.size, 'mode', im.mode)
print('non-black opaque pixels:', bad)
assert bad == 0
EOF
```

Expected: `size (270, 126) mode RGBA`, `non-black opaque pixels: 0`, no assertion error.

- [ ] **Step 3: Rebuild and verify no new lint issues**

Run: `./gradlew :app:assembleLatestDebug` and `./gradlew :app:lintLatestDebug`
Expected: `BUILD SUCCESSFUL` for both; lint reports no new issues.

- [ ] **Step 4: Commit**

```bash
git add app/res/drawable-nodpi/parkrun_logo.png
git commit -m "feat: recolor parkrun logo to black"
```

---

### Task 3: Full verification gate

**Files:**
- None (verification only).

**Interfaces:**
- Consumes: Tasks 1 and 2 outputs (changed layout/dimens/PNG).

- [ ] **Step 1: Unit tests**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` (existing suite; `Code128BarcodeTest` unchanged).

- [ ] **Step 2: Spotless**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: `BUILD SUCCESSFUL` for both. If `spotlessApply` changes files, verify the diff touches only expected files, then amend into the relevant task commit or commit as `style: apply google-java-format` — but no Java changed, so this should be a no-op.

- [ ] **Step 3: Lint (final)**

Run: `./gradlew :app:lintLatestDebug`
Expected: no new issues beyond the 25-item baseline.

- [ ] **Step 4: Install on device**

Run: `./gradlew :app:assembleLatestDebug` (fresh) then:
`adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`
Expected: `Success`.

- [ ] **Step 5: Device check**

On the Nexus 5X (stored barcode `A11543609`):
1. Open Settings → parkrun barcode. Confirm: black logo, ~24dp gap under it, barcode noticeably taller (≈270dp), outlined **Delete** button centered at the card bottom, value text `A11543609` above it.
2. Tap **Delete** → confirm dialog → empty state shown.
3. Tap **Scan parkrun barcode** → point at a Code128 barcode → success toast + stored-state card renders with the same polish.
4. Verify with `adb shell uiautomator dump /sdcard/ui.xml` that `barcode_view` bounds height ≈ 1.5× previous (was 473px at 180dp; expect ≈ 709px at 270dp on this 420dpi device) and that `delete_button` is a text button (bounds ~ text-sized, not a 147px circle).

- [ ] **Step 6: Report results**

Summarize the `git log --oneline` for the three commits and the device-check outcome.

---

## Self-Review Notes

- Spec coverage: gap (Task 1 Step 1), taller barcode (Task 1 Step 3), outlined Delete button (Task 1 Step 2), black logo (Task 2), verification suite (Task 3). All four spec sections covered.
- No placeholders: every step has concrete content or exact commands.
- Type/name consistency: `delete_button`, `barcode_view`, `barcode_height`, `parkrun_logo` names match across tasks and the spec.
