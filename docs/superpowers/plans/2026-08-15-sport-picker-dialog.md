# Sport Picker Dialog with Icons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the cramped auto-complete sport dropdown on the record page with a roomy Material 3 dialog whose 8 sport rows each show a tinted vector icon + label, and show the selected sport's icon in the toolbar.

**Architecture:** Keep the existing `MaterialSportSpinner` widget (toolbar element) but change its tap behavior to fire an open-callback instead of `showDropDown()`. `StartFragment` opens a `MaterialAlertDialogBuilder` with a custom `BaseAdapter` (icon + label + radio rows). Row selection reuses the existing item-selected listener chain (`StartFragment.java:313-331`), so pref persistence (via `SpinnerPresenter.setValue`) and GPS-required toggling keep working unchanged. `Sport.colorOf()`/`drawableColored16Of()` gain entries for Treadmill, Gym, Stationary bike with 3 new Material vector icons and 3 new Solarized colors.

**Tech Stack:** Java, AndroidX, Material 3 (com.google.android.material:1.14.0), Gradle multi-module (only `app` module changes).

## Global Constraints

- Change ONLY the `app` module. `common`, `wear`, `hrdevice` are untouched.
- Sport entries are indexed by DB value (adapter position == DB value, 0-7). Do NOT reorder or re-index them.
- Verify before finishing each task, in order:
  1. `./gradlew test` — unit tests live in `app/test/java` (non-standard path)
  2. `./gradlew :app:lintLatestDebug` — do NOT fail on `app/lint-baseline.xml` (25 pre-existing issues); only NEW issues matter
  3. `./gradlew spotlessApply` then `./gradlew spotlessCheck` — googleJavaFormat
  4. `./gradlew :app:assembleLatestDebug`
  5. `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap` — ALWAYS LAST (nomap overwrites the APK)
- Conventional commits (`feat:`/`fix:`/`refactor:`/`style:`/`docs:`).
- Do NOT stage: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- No code comments unless the user asks.

---

### Task 1: Sport icons, colors, and `Sport` mapping

**Files:**
- Create: `app/res/drawable/sport_treadmill.xml`
- Create: `app/res/drawable/sport_gym.xml`
- Create: `app/res/drawable/sport_stationary_bike.xml`
- Modify: `app/res/values/colors.xml` (add 3 colors after line 40)
- Modify: `app/src/main/org/runnerup/workout/Sport.java:151-185`
- Test: `app/test/java/org/runnerup/workout/SportTest.java` (new)

**Interfaces:**
- Produces: `Sport.colorOf(int dbValue)` returns `R.color.sportTreadmill` for `DB.ACTIVITY.SPORT_TREADMILL`, `R.color.sportGym` for `SPORT_GYM`, `R.color.sportStationaryBike` for `SPORT_STATIONARY_BIKE`; `Sport.drawableColored16Of(int dbValue)` returns `R.drawable.sport_treadmill` / `R.drawable.sport_gym` / `R.drawable.sport_stationary_bike` for the same values. Existing 5 sports unchanged. Task 2 consumes these unchanged signatures.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/workout/SportTest.java`:

```java
package org.runnerup.workout;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;

public class SportTest {
  @Test
  public void treadmillHasOwnColor() {
    assertEquals(R.color.sportTreadmill, Sport.colorOf(DB.ACTIVITY.SPORT_TREADMILL));
  }

  @Test
  public void gymHasOwnColor() {
    assertEquals(R.color.sportGym, Sport.colorOf(DB.ACTIVITY.SPORT_GYM));
  }

  @Test
  public void stationaryBikeHasOwnColor() {
    assertEquals(R.color.sportStationaryBike, Sport.colorOf(DB.ACTIVITY.SPORT_STATIONARY_BIKE));
  }

  @Test
  public void existingSportColorsUnchanged() {
    assertEquals(R.color.sportRunning, Sport.colorOf(DB.ACTIVITY.SPORT_RUNNING));
    assertEquals(R.color.sportBiking, Sport.colorOf(DB.ACTIVITY.SPORT_BIKING));
    assertEquals(R.color.sportWalking, Sport.colorOf(DB.ACTIVITY.SPORT_WALKING));
    assertEquals(R.color.sportOrienteering, Sport.colorOf(DB.ACTIVITY.SPORT_ORIENTEERING));
    assertEquals(R.color.sportOther, Sport.colorOf(DB.ACTIVITY.SPORT_OTHER));
  }

  @Test
  public void newSportsHaveOwnDrawables() {
    assertEquals(R.drawable.sport_treadmill, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_TREADMILL));
    assertEquals(R.drawable.sport_gym, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_GYM));
    assertEquals(
        R.drawable.sport_stationary_bike, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_STATIONARY_BIKE));
  }

  @Test
  public void existingSportDrawablesUnchanged() {
    assertEquals(R.drawable.sport_running, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_RUNNING));
    assertEquals(R.drawable.sport_biking, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_BIKING));
    assertEquals(R.drawable.sport_walking, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_WALKING));
    assertEquals(R.drawable.sport_orienteering, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_ORIENTEERING));
    assertEquals(R.drawable.sport_other, Sport.drawableColored16Of(DB.ACTIVITY.SPORT_OTHER));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.runnerup.workout.SportTest"`
Expected: FAIL — `R.color.sportTreadmill`, `R.color.sportGym`, `R.color.sportStationaryBike`, `R.drawable.sport_treadmill`, `R.drawable.sport_gym`, `R.drawable.sport_stationary_bike` do not exist yet, so the test does not compile.

- [ ] **Step 3: Add the three color resources**

In `app/res/values/colors.xml`, after the `<color name="sportOther">#b58900</color>` line (line 40), add:

```xml
<color name="sportTreadmill">#2aa198</color>
<color name="sportGym">#dc322f</color>
<color name="sportStationaryBike">#268bd2</color>
```

- [ ] **Step 4: Create the three vector drawables**

Create `app/res/drawable/sport_treadmill.xml` (Material Symbols `exercise` glyph — Material has no dedicated treadmill icon; this is the closest Material-system icon; path data from Google's Material Symbols via iconify):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#2aa198"
        android:pathData="m20.975 9.025l-6.05-6.05l.425-.425q.575-.575 1.425-.562t1.425.587L21.425 5.8Q22 6.375 22 7.213t-.575 1.412zM8.65 21.4q-.575.575-1.412.575T5.825 21.4L2.6 18.175q-.575-.575-.575-1.412T2.6 15.35l.4-.4L9.05 21zm3.625-.7q-.3.3-.7.3t-.7-.3L3.3 13.125q-.3-.3-.3-.7t.3-.7l1.425-1.45q.3-.3.713-.3t.712.3l1.575 1.575l4.15-4.15L10.3 6.125q-.3-.3-.3-.7t.3-.7l1.425-1.45q.3-.3.713-.3t.712.3l7.575 7.575q.3.3.3.713t-.3.712l-1.45 1.425q-.3.3-.7.3t-.7-.3L16.3 12.125l-4.15 4.15l1.575 1.575q.3.3.3.712t-.3.713z" />
</vector>
```

Create `app/res/drawable/sport_gym.xml` (Material `fitness_center` dumbbell glyph):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#dc322f"
        android:pathData="M13.4 21.9L12 20.5l3.55-3.55l-8.5-8.5L3.5 12l-1.4-1.4l1.4-1.45l-1.4-1.4l2.1-2.1L2.8 4.2l1.4-1.4l1.45 1.4l2.1-2.1l1.4 1.4l1.45-1.4L12 3.5L8.45 7.05l8.5 8.5L20.5 12l1.4 1.4l-1.4 1.45l1.4 1.4l-2.1 2.1l1.4 1.45l-1.4 1.4l-1.45-1.4l-2.1 2.1l-1.4-1.4z" />
</vector>
```

Create `app/res/drawable/sport_stationary_bike.xml` (Material `pedal_bike` glyph):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#268bd2"
        android:pathData="M5 20q-2.125 0-3.562-1.437T0 15t1.463-3.562T5 10q1.925 0 3.238 1.15T9.9 14h.65l-1.8-5H7V7h5v2h-1.1l.35 1h4.8L14.6 6H12V4h2.6q.65 0 1.163.35t.737.95l1.7 4.65h.8q2.075 0 3.538 1.463T24 14.95q0 2.1-1.45 3.575T19 20q-1.8 0-3.162-1.125T14.1 16H9.9q-.35 1.725-1.7 2.863T5 20m0-4h2.8v-2H5zm7.7-2h1.4q.125-.575.338-1.075T15 12h-3.05zm5.3 1.35l1.9-.7l-1-2.65l-1.85.7z" />
</vector>
```

- [ ] **Step 5: Update `Sport.colorOf` and `Sport.drawableColored16Of`**

In `app/src/main/org/runnerup/workout/Sport.java`:

`colorOf` (lines 151-167) — replace the whole switch:

```java
  public static int colorOf(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_RUNNING:
        return R.color.sportRunning;
      case DB.ACTIVITY.SPORT_BIKING:
        return R.color.sportBiking;
      case DB.ACTIVITY.SPORT_TREADMILL:
        return R.color.sportTreadmill;
      case DB.ACTIVITY.SPORT_GYM:
        return R.color.sportGym;
      case DB.ACTIVITY.SPORT_STATIONARY_BIKE:
        return R.color.sportStationaryBike;
      case DB.ACTIVITY.SPORT_WALKING:
        return R.color.sportWalking;
      case DB.ACTIVITY.SPORT_ORIENTEERING:
        return R.color.sportOrienteering;
      case DB.ACTIVITY.SPORT_OTHER:
        return R.color.sportOther;
      default:
        return R.color.colorText;
    }
  }
```

`drawableColored16Of` (lines 169-185) — replace the whole switch:

```java
  public static int drawableColored16Of(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_RUNNING:
        return R.drawable.sport_running;
      case DB.ACTIVITY.SPORT_BIKING:
        return R.drawable.sport_biking;
      case DB.ACTIVITY.SPORT_TREADMILL:
        return R.drawable.sport_treadmill;
      case DB.ACTIVITY.SPORT_GYM:
        return R.drawable.sport_gym;
      case DB.ACTIVITY.SPORT_STATIONARY_BIKE:
        return R.drawable.sport_stationary_bike;
      case DB.ACTIVITY.SPORT_WALKING:
        return R.drawable.sport_walking;
      case DB.ACTIVITY.SPORT_ORIENTEERING:
        return R.drawable.sport_orienteering;
      case DB.ACTIVITY.SPORT_OTHER:
        return R.drawable.sport_other;
      default:
        return R.drawable.sport_other;
    }
  }
```

Note: `Sport.drawableColored16Of(SPORT_TREADMILL)` previously returned `sport_running` and `Sport.colorOf(SPORT_TREADMILL)` returned `sportRunning`; the TREADMILL case is intentionally changed so treadmill now shows its own icon/color. This makes History rows for treadmill/gym/stationary-bike activities show their own icon+color — intended.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "org.runnerup.workout.SportTest"`
Expected: PASS, all 6 tests green.

- [ ] **Step 7: Run the full gate suite**

Run in order:
1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug` — no NEW issues (25 baseline pre-existing)
3. `./gradlew spotlessApply && ./gradlew spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap` (LAST)

- [ ] **Step 8: Commit**

```bash
git add app/res/values/colors.xml app/res/drawable/sport_treadmill.xml app/res/drawable/sport_gym.xml app/res/drawable/sport_stationary_bike.xml app/src/main/org/runnerup/workout/Sport.java app/test/java/org/runnerup/workout/SportTest.java
git commit -m "feat: add sport icons and colors for treadmill gym stationary bike"
```

---

### Task 2: Sport picker dialog + toolbar icon

**Files:**
- Modify: `app/src/main/org/runnerup/widget/MaterialSportSpinner.java:34,102-108` (add `OnOpenListener`, rewire tap)
- Modify: `app/src/main/org/runnerup/view/StartFragment.java:211-219,313-331` (wiring), plus new methods + inner adapter
- Create: `app/res/layout/sport_picker_row.xml`
- Delete: `app/res/layout/actionbar_dropdown_spinner.xml`
- Remove: `StartFragment.java:215` (`adapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner);`)

**Interfaces:**
- Consumes: from Task 1 — `Sport.colorOf(int)` and `Sport.drawableColored16Of(int)` (new entries for the 3 added sports).
- Consumes: `MaterialSportSpinner.setOnOpenListener(MaterialSportSpinner.OnOpenListener)` (added in this task) and existing `MaterialSportSpinner.getViewOnItemSelectedListener()` / `setViewSelection(int)`.
- Produces: `MaterialSportSpinner.setOnOpenListener(OnOpenListener)` where `OnOpenListener.onOpen()` is invoked when the toolbar element is tapped; `StartFragment.showSportPickerDialog(MaterialSportSpinner)`.

- [ ] **Step 1: Add `OnOpenListener` to `MaterialSportSpinner`**

In `app/src/main/org/runnerup/widget/MaterialSportSpinner.java`:

Add an interface at class level (after the class declaration, before `mPresenter`):

```java
  public interface OnOpenListener {
    void onOpen();
  }

  private OnOpenListener mOnOpenListener;
```

Add a setter (place it near the other setters, e.g. after `setViewOnItemSelectedListener`):

```java
  public void setOnOpenListener(OnOpenListener listener) {
    mOnOpenListener = listener;
  }
```

Replace `onTouchEvent` (lines 102-108):

```java
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_UP) {
      performClick();
      if (mOnOpenListener != null) {
        mOnOpenListener.onOpen();
      } else {
        showDropDown();
      }
    }
    return super.onTouchEvent(event);
  }
```

The `else { showDropDown(); }` branch is a harmless fallback for any view without a listener; the only current usage always sets the listener.

- [ ] **Step 2: Create the dialog row layout**

Create `app/res/layout/sport_picker_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="56dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">
  <ImageView
      android:id="@+id/sport_picker_icon"
      android:layout_width="24dp"
      android:layout_height="24dp"
      android:layout_marginEnd="16dp"
      android:importantForAccessibility="no" />
  <TextView
      android:id="@+id/sport_picker_label"
      android:layout_width="0dp"
      android:layout_height="wrap_content"
      android:layout_weight="1"
      android:ellipsize="end"
      android:maxLines="1"
      android:textSize="16sp" />
  <RadioButton
      android:id="@+id/sport_picker_radio"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:clickable="false"
      android:focusable="false" />
</LinearLayout>
```

- [ ] **Step 3: Wire the dialog in `StartFragment`**

In `app/src/main/org/runnerup/view/StartFragment.java`:

Add imports (googleJavaFormat orders them; spotlessApply will sort):

```java
import android.graphics.drawable.Drawable;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import androidx.appcompat.content.res.AppCompatResources;
```

Remove line 215: `adapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner);` and delete the file `app/res/layout/actionbar_dropdown_spinner.xml` (it becomes unused; leaving it would trip lint `UnusedResources`). Keep the `ArrayAdapter` and `R.layout.actionbar_spinner` (used for the collapsed toolbar text).

After the selection-listener block (which ends at line 331, `});`), add the wiring — the listener chain must already be installed, and the sport element must not be openable before then:

```java
    sportSpinner.setOnOpenListener(() -> showSportPickerDialog(sportSpinner));
    updateSportIcon(
        sportSpinner,
        prefs.getInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING));
```

Add these three members after the `setGpsNotRequired(...)` method (after line 357, before `private class OnConfigureAudioListener`):

```java
  private void updateSportIcon(MaterialSportSpinner sportSpinner, int sport) {
    Drawable icon =
        AppCompatResources.getDrawable(requireContext(), Sport.drawableColored16Of(sport));
    if (icon != null) {
      icon.setTint(ContextCompat.getColor(requireContext(), Sport.colorOf(sport)));
    }
    Drawable arrow = sportSpinner.getCompoundDrawablesRelative()[2];
    sportSpinner.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, arrow, null);
  }

  private void showSportPickerDialog(MaterialSportSpinner sportSpinner) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    int checked =
        prefs.getInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    ListView list = new ListView(requireContext());
    list.setAdapter(new SportPickerAdapter(requireContext(), getResources(), checked));
    list.setDivider(null);
    list.setDividerHeight(0);
    AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(org.runnerup.common.R.string.Sport)
            .setView(list)
            .show();
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          AdapterView.OnItemSelectedListener l = sportSpinner.getViewOnItemSelectedListener();
          if (l != null) {
            l.onItemSelected(null, null, position, position);
          }
          updateSportIcon(sportSpinner, position);
          dialog.dismiss();
        });
  }

  private static class SportPickerAdapter extends BaseAdapter {
    private final Context context;
    private final String[] sports;
    private final int checked;

    SportPickerAdapter(Context context, Resources resources, int checked) {
      this.context = context;
      this.sports = Sport.getStringArray(resources);
      this.checked = checked;
    }

    @Override
    public int getCount() {
      return sports.length;
    }

    @Override
    public Object getItem(int position) {
      return sports[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View row =
          convertView != null
              ? convertView
              : LayoutInflater.from(parent.getContext())
                  .inflate(R.layout.sport_picker_row, parent, false);
      ImageView iconView = row.findViewById(R.id.sport_picker_icon);
      TextView labelView = row.findViewById(R.id.sport_picker_label);
      RadioButton radioView = row.findViewById(R.id.sport_picker_radio);
      iconView.setImageResource(Sport.drawableColored16Of(position));
      iconView.setColorFilter(ContextCompat.getColor(context, Sport.colorOf(position)));
      labelView.setText(sports[position]);
      radioView.setChecked(position == checked);
      return row;
    }
  }
```

Why this works (do not "simplify" the selection path):
- The dialog row click calls the SAME listener chain the dropdown used: `getViewOnItemSelectedListener()` returns the wrapper installed at lines 313-331, which delegates to `SpinnerPresenter`'s listener — that is what persists `startSport` (`SpinnerPresenter.setValue` → `pref.putInt`) and updates the toolbar text (`setViewSelection`). Then `updateSportIcon` sets the leading icon. `position` is used as both position and id because the sport adapter has no `values` indirection (position == DB value, see `Sport.getStringArray`).
- `setGpsNotRequired`/`updateView` run through that same chain (wrapped in the `if (l != null)` block by the wrapper at lines 313-331), so GPS-required toggling and the rest of the UI refresh for the new sport happen automatically.
- `checked` (pre-read from the pref) pre-checks the current sport's radio.
- `updateSportIcon` preserves the trailing drop-down arrow by re-passing `getCompoundDrawablesRelative()[2]` as the end drawable.

- [ ] **Step 4: Run the full gate suite**

Run in order:
1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug` — no NEW issues (25 baseline pre-existing)
3. `./gradlew spotlessApply && ./gradlew spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap` (LAST)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/widget/MaterialSportSpinner.java app/src/main/org/runnerup/view/StartFragment.java app/res/layout/sport_picker_row.xml
git rm app/res/layout/actionbar_dropdown_spinner.xml
git commit -m "feat: replace sport dropdown with icon picker dialog"
```

---

### Task 3: On-device verification (no commit)

**Files:** none (verification only).

**Context:**
- Test device: Nexus 5X, serial `025b46e24edcbca6`, Android 11, 1080x1920, app package `org.runnerup.debug`.
- `uiautomator dump` works on this device; `dumpsys activity top` exposes no text content.
- Night toggle: `adb shell cmd uimode night yes` / `cmd uimode night no`.
- The APK must be the map variant (`:app:assembleLatestDebug`); the nomap build overwrites it, so do not build nomap before this task.
- Day/night theme follows `uiMode`; `MaterialAlertDialogBuilder` colors adapt automatically.

- [ ] **Step 1: Build and install**

Build the map-variant APK and install:

```bash
./gradlew :app:assembleLatestDebug
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 025b46e24edcbca6 shell am force-stop org.runnerup.debug
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Wait for the Start (Record) tab to render.

- [ ] **Step 2: Open the sport picker dialog**

```bash
adb -s 025b46e24edcbca6 shell uiautomator dump /sdcard/ui.xml
adb -s 025b46e24edcbca6 shell cat /sdcard/ui.xml
```

Find the node whose `text` is the current sport (e.g. "Running") in the top toolbar. Tap its center via `adb shell input tap <x> <y>`. Expected: a dialog titled "Sport" appears with 8 rows, each with a tinted icon + label; the current sport's radio is checked.

- [ ] **Step 3: Capture evidence (day)**

```bash
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/sport_picker_day.png
```

Verify by pixel logic if needed (icons tinted `#859900` running, `#cb4b16` biking, `#2aa198` treadmill, `#dc322f` gym, `#268bd2` stationary bike, `#6c71c4` walking, `#d33682` orienteering, `#b58900` other).

- [ ] **Step 4: Select a different sport**

From the dialog, tap the "Treadmill" row (position 5). Expected: dialog closes; the toolbar shows the treadmill icon + "Treadmill"; the GPS status row reflects without-GPS state (Gym/Treadmill/Stationary bike are `isWithoutGps`, `Sport.java:224-233`).

```bash
adb -s 025b46e24edcbca6 shell uiautomator dump /sdcard/ui.xml
adb -s 025b46e24edcbca6 shell cat /sdcard/ui.xml
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/sport_picker_treadmill_selected.png
```

- [ ] **Step 5: Verify persistence**

Force-stop and relaunch; the toolbar should still show "Treadmill":

```bash
adb -s 025b46e24edcbca6 shell am force-stop org.runnerup.debug
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
adb -s 025b46e24edcbca6 shell uiautomator dump /sdcard/ui.xml
```

- [ ] **Step 6: Verify night theme**

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night yes
```

Reopen the picker and screenshot:

```bash
adb -s 025b46e24edcbca6 shell uiautomator dump /sdcard/ui.xml
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/sport_picker_night.png
adb -s 025b46e24edcbca6 shell cmd uimode night no
```

Confirm the dialog renders legibly in both themes (Material colors adapt). Screenshots are supplementary evidence; describe what is visible in the report.

- [ ] **Step 7: Restore state and record**

Restore the sport to Running (tap the toolbar, pick "Running") so the device is left in a sensible state. Append a ledger entry to `.superpowers/sdd/2026-08-15-sport-picker-dialog/progress.md`:

```
Task 3: complete (verification only, no commit) — dialog shows 8 icon rows, day+night ok, selection persists, GPS-required toggles. Screenshots: /tmp/opencode/sport_picker_day.png, /tmp/opencode/sport_picker_night.png, /tmp/opencode/sport_picker_treadmill_selected.png
```

---

## Self-Review Notes (verified while writing)

- **Spec coverage:** every spec requirement maps to a task — dialog surface (Task 2), toolbar icon+name (Task 2), 5 existing icons reused + 3 new vectors (Task 1), colors `#2aa198`/`#dc322f`/`#268bd2` (Task 1), selection reuse + persistence (Task 2 Step 3), verification (Task 3).
- **Icon substitution:** Material Symbols has NO `treadmill` icon (confirmed via Iconify search); `exercise` is the closest Material-system glyph. Documented in Task 1 Step 4. `fitness_center` and `pedal_bike` are Material glyphs.
- **Type/signature consistency:** `setOnOpenListener` produced by Task 2 Step 1, consumed in Task 2 Step 3. `Sport.colorOf`/`drawableColored16Of` signatures unchanged by Task 1. Adapter position == DB value invariant holds throughout.
- **Dead code:** `actionbar_dropdown_spinner.xml` removed together with its only reference to avoid `UnusedResources`; `actionbar_spinner.xml` stays (collapsed text). `showDropDown()` kept as fallback branch.
