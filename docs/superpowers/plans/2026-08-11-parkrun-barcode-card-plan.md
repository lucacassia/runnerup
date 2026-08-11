# Parkrun Barcode Stored-State Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `ParkrunBarcodeActivity`'s stored state as a white Material 3 elevated card (logo + full-bleed barcode, centered code text, small icon-only delete FAB) and remove the scan-while-stored / replace-confirm flow.

**Architecture:** Single-file UI restyle. `app/res/layout/parkrun_barcode.xml` wraps the stored state in a `MaterialCardView` (white surface always, elevated style; children clip to the rounded corners via CardView's built-in `setClipToOutline(true)`) holding the logo + barcode image area, the code as centered text, and a small `FloatingActionButton` delete action. `ParkrunBarcodeActivity.java` drops the `scan_new_button` wiring and simplifies the scan callback to save directly (a scan is only reachable from the empty state). Three now-dead strings are removed. No rendering, persistence, or scanner logic changes.

**Tech Stack:** Android Views (XML), Material 3 (`com.google.android.material:material:1.14.0`), existing `FloatingActionButton` convention from `start_fab.xml`.

## Global Constraints

- Non-transitive R (`android.nonTransitiveRClass=true`): common-module strings MUST be referenced as `org.runnerup.common.R.string.X`; app-local resources (layouts, ids, dimens, `pref_parkrun_barcode`) use `org.runnerup.R`.
- `registerForActivityResult` is the only activity-result mechanism (no legacy `onActivityResult`).
- The FAB must follow the `start_fab.xml` convention: `app:backgroundTint="?attr/colorPrimaryContainer"`, `app:tint="?attr/colorOnPrimaryContainer"`, `app:srcCompat="@drawable/ic_delete"`.
- Card surface is white in BOTH light and dark themes (`app:cardBackgroundColor="@android:color/white"`); the code text uses a fixed dark color (`@android:color/black`); the delete action follows the theme.
- No code comments unless asked. googleJavaFormat (spotless) enforced; CI gates on `spotlessCheck`.
- Lint gate: only the 25 pre-existing baseline issues are allowed; NO new issues (dead strings are removed so `UnusedResources` stays clean).
- Build: AGP 9.3.1, Java 17, minSdk 28, targetSdk 36.1. App variant `latestDebug`; unit-test task `:app:testLatestDebugUnitTest`; unit tests in `app/test/java` (sourceSets root `test`).
- `BarcodeScanActivity.BARCODE_EXTRA` (`"barcode"`), `RESULT_OK`, and `Code128Barcode.renderToBitmap(BitMatrix, int widthPx, int heightPx)` are unchanged dependencies.

---

### Task 1: Stored-state card restyle

**Files:**
- Modify: `app/res/layout/parkrun_barcode.xml`
- Modify: `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java:44-67,85-87`
- Modify: `common/src/main/res/values/strings.xml:400,402-403`

**Interfaces:**
- Consumes: `BarcodeScanActivity.BARCODE_EXTRA`; `R.string.pref_parkrun_barcode`; `@dimen/barcode_height` (180dp); existing ids `empty_state`, `stored_state`, `barcode_view`, `barcode_value`, `delete_button`, `empty_scan_button`, `parkrun_barcode_root`, `actionbar`.
- Produces: nothing new — this is a self-contained restyle. Note: the `delete_button` id moves from a `Button` to the FAB; the FAB's `contentDescription` uses the shared `@string/Delete`.

Note on test strategy: this task is declarative UI + a branch removal; there is no JVM-unit-testable logic change, so the "tests" are the build, lint, spotless, and device gates below. Do NOT add unit tests for the activity.

- [ ] **Step 1: Rewrite the stored-state layout as a white elevated card**

Replace the ENTIRE contents of `app/res/layout/parkrun_barcode.xml` (current 109-line file) with:

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

            <LinearLayout
                android:id="@+id/empty_state"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:paddingTop="32dp">

                <ImageView
                    android:id="@+id/parkrun_logo_empty"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:contentDescription="@string/Parkrun_barcode"
                    android:src="@drawable/parkrun_logo" />

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

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/stored_state"
                style="?attr/materialCardViewElevatedStyle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:visibility="gone"
                app:cardBackgroundColor="@android:color/white">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="center_horizontal"
                    android:orientation="vertical">

                    <ImageView
                        android:id="@+id/parkrun_logo"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:contentDescription="@string/Parkrun_barcode"
                        android:paddingTop="24dp"
                        android:src="@drawable/parkrun_logo" />

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
                        android:textColor="@android:color/black"
                        android:textIsSelectable="true"
                        android:textSize="20sp"
                        android:textStyle="bold" />

                    <com.google.android.material.floatingactionbutton.FloatingActionButton
                        android:id="@+id/delete_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:layout_marginBottom="24dp"
                        android:contentDescription="@string/Delete"
                        app:backgroundTint="?attr/colorPrimaryContainer"
                        app:fabSize="mini"
                        app:srcCompat="@drawable/ic_delete"
                        app:tint="?attr/colorOnPrimaryContainer" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

Notes: the logo now appears in BOTH states with distinct ids (`parkrun_logo_empty` for the empty state, `parkrun_logo` inside the card) — no code references either id. `scan_new_button` and the old horizontal button bar are gone.

- [ ] **Step 2: Simplify the scan callback and drop the scan_new_button wiring**

In `app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java`:

1. Replace the `scanLauncher` callback (lines 44-67) with the direct-save version — the `existing`/`Replace barcode?` dialog branch is removed:

```java
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
```

2. In `onCreate`, remove this line (the button no longer exists):

```java
    findViewById(R.id.scan_new_button).setOnClickListener(v -> launchScanner());
```

Keep `empty_scan_button` and `delete_button` listeners as-is (`delete_button` now refers to the FAB). `launchScanner()`, `saveBarcode()`, `confirmDelete()`, and `refresh()` are unchanged. No imports change (the `MaterialAlertDialogBuilder` import is still used by `confirmDelete()`).

- [ ] **Step 3: Remove the three dead strings**

In `common/src/main/res/values/strings.xml` delete these lines (currently ~400 and ~402-403):

```xml
    <string name="Scan_new_barcode">Scan new barcode</string>
    <string name="Replace_barcode">Replace barcode?</string>
    <string name="Replace_barcode_text">A parkrun barcode is already stored. Replace it with %1$s?</string>
```

Leave `Delete`, `Delete_barcode`, `Delete_barcode_text`, `Cancel`, `Yes`, `No`, `Scan_parkrun_barcode`, `No_parkrun_barcode_text`, `Parkrun_barcode`, and all other strings untouched.

- [ ] **Step 4: Build the debug app**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (catches any R/id/`?attr:` reference errors, e.g. a bad FAB attribute or a leftover `scan_new_button` reference).

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (existing suites — `:app:testLatestDebugUnitTest`, `:common:test`, `:wear:test`).

- [ ] **Step 6: Lint the debug variant**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL with no new issues beyond the 25 baseline entries in `app/lint-baseline.xml` (the string removal must not trigger `UnusedResources`, and the FAB/layout must not introduce `ButtonStyle`, `DuplicateId`, or other new issues).

- [ ] **Step 7: Apply and verify formatting**

Run: `./gradlew spotlessApply`
Then: `./gradlew spotlessCheck`
Expected: PASS; `git status` must show spotless touched only `ParkrunBarcodeActivity.java` (the XML/strings edits are formatting-neutral).

- [ ] **Step 8: Device smoke test (light + dark theme)**

With the attached device (`adb devices`; a Nexus 5X serial `025b46e24edcbca6` was used for this feature), on the `latestDebug` build:

1. Empty state: unchanged — logo, "No parkrun barcode stored yet…", "Scan parkrun barcode" button. No card.
2. Seed the pref (the stored-state render needs a value). There is no `sqlite3` on the device; the pref is a SharedPreferences XML file. `PreferenceManager.getDefaultSharedPreferences` reads `/data/data/org.runnerup.debug/shared_prefs/org.runnerup.debug_preferences.xml`. So: `adb shell am force-stop org.runnerup.debug`, then `adb exec-out run-as org.runnerup.debug cat shared_prefs/org.runnerup.debug_preferences.xml > /tmp/parkrun_prefs.xml`, on the host add/ensure the entry `<string name="pref_parkrun_barcode">C1234567</string>`, push back (`adb push /tmp/parkrun_prefs.xml /data/local/tmp/prefs.xml && adb shell chmod 666 /data/local/tmp/prefs.xml && adb exec-out run-as org.runnerup.debug cp /data/local/tmp/prefs.xml shared_prefs/org.runnerup.debug_preferences.xml`), then reopen Settings → "parkrun barcode".
3. Stored state: white elevated card with centered logo at top, full-width 180dp barcode flush to the card's rounded corners, the code `C1234567` centered below in bold 20sp, and a small themed FAB (trash icon) at the bottom. No "Scan new barcode" button.
4. Tap the FAB → "Delete barcode?" confirm dialog → confirm → empty state returns.
5. Repeat steps 2-4 after `adb shell cmd uimode night yes` (dark theme): card surface still white, code text still black, FAB still visible/on-theme. Then `adb shell cmd uimode night no`.
6. Force-stop and reopen → stored barcode still shown (persistence unchanged).

- [ ] **Step 9: Commit**

```bash
git add app/res/layout/parkrun_barcode.xml app/src/main/org/runnerup/view/ParkrunBarcodeActivity.java common/src/main/res/values/strings.xml
git commit -m "feat: show stored parkrun barcode as a white elevated card"
```

Do NOT stage `local.properties`, `gradle.properties`, `.superpowers/`, or any unrelated files.
