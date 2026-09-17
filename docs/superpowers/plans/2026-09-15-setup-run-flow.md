# Setup Run Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the cluttered record screen (spinners + inline step editor) with a minimal record screen (Start only) from which tapping Start pushes an iOS-style "Setup Run" page (Sport / Audio cues / Workout rows, optional inline step editing, pinned Start Run), plus pushed picker lists. GPS is driven entirely from the Setup Run page: selecting a GPS sport (or opening the page with one pre-selected) auto-starts GPS; selecting a non-GPS sport stops it. The record screen's GPS status bar keeps HR/wear status but no GPS controls; a GPS chip on the Setup Run footer shows live signal state and opens a signal-info popup when tapped.

**Architecture:** All UI lives inside the existing `StartFragment` (single-activity app). `start.xml`'s `content_root` FrameLayout hosts three mutually-exclusive roots — `record_root`, `setup_root`, `picker_root` — toggled with a small push-state stack + iOS-style slide transitions and an `OnBackPressedCallback`. Recording path (`startWorkout`, GPS/tracker, permissions, `RunActivity`, race-ready) is unchanged. The sport/audio/workout prefs (`startSport`/`advancedAudio`/`advancedWorkout`) keep being the single source of truth.

**Tech Stack:** Java, AndroidX Fragment/Material3, RecyclerView/ListView, SharedPreferences, `AnimationUtils`.

## Global Constraints

- Copy and UI language stays consistent with the app's existing style (Material 3, `?attr/colorPrimary` accents).
- No new Activity for the wizard; `RunActivity` handoff untouched.
- GPS-only runs require a non-null basic workout (`Tracker.start()` NPEs on null `workout`): build via `WorkoutBuilder.createDefaultWorkout(res, prefs, null)`.
- `RaceReady.enabled` / deferred `EXTRA_DEFERRED_START` semantics preserved for the Start Run button.
- Existing ids reused where possible; new ids prefixed `record_`/`setup_`/`picker_`.
- Gate per task: `./gradlew :app:assembleLatestDebug` compiles. Final gate per AGENTS: `./gradlew test`, `:app:lintLatestDebug` (no new baseline items), `spotlessApply` then `spotlessCheck`.

---

### Task 1: Strings, back icon, MainLayout navigation hooks

**Files:**
- Modify: `app/res/values/strings.xml`
- Create: `app/res/drawable/ic_arrow_back_24dp.xml`
- Modify: `app/src/main/org/runnerup/view/MainLayout.java`

**Interfaces:**
- Produces: `MainLayout#setStartFlowActive(boolean)` — hides the bottom nav and disables pager swiping only while the wizard is open on page 0; restores on exit. Used by `StartFragment` every time wizard state changes.

- [x] **Step 1: Add strings to `app/res/values/strings.xml`**

```xml
    <string name="Setup_run">Setup Run</string>
    <string name="Setup_section_activity">Activity</string>
    <string name="Setup_section_guidance">Guidance</string>
    <string name="Audio_cues">Audio cues</string>
    <string name="None">None</string>
    <string name="Start_run">Start Run</string>
    <string name="Setup_steps_hint">Tap a step to edit — optional</string>
```

- [x] **Step 2: Create `app/res/drawable/ic_arrow_back_24dp.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:tint="?attr/colorOnSurfaceVariant"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z" />
</vector>
```

- [x] **Step 3: Add navigation hooks to `MainLayout`**

Add a field and methods, and wire `onPageSelected`:

```java
  private boolean startFlowActive = false;

  public void setStartFlowActive(boolean active) {
    startFlowActive = active;
    applyStartFlowConstraints();
  }

  private void applyStartFlowConstraints() {
    pager.setUserInputEnabled(!startFlowActive || pager.getCurrentItem() != 0);
    findViewById(R.id.bottom_navigation)
        .setVisibility(startFlowActive && pager.getCurrentItem() == 0 ? View.GONE : View.VISIBLE);
  }
```

In the existing `onPageSelected` callback body add: `applyStartFlowConstraints();`

- [x] **Step 4: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/res/values/strings.xml app/res/drawable/ic_arrow_back_24dp.xml app/src/main/org/runnerup/view/MainLayout.java
git commit -m "feat: prepare strings, back icon, and nav hooks for Setup Run flow"
```

---

### Task 2: Rework `start.xml` into three roots

**Files:**
- Modify: `app/res/layout/start.xml`

**Interfaces:**
- Produces: ids `content_root`, `record_root`, `record_hint`, `setup_root`, `picker_root`. `record_root` contains the existing `start_fab` include (id `start_button`). `start_advanced.xml` is no longer included here (retired in Task 5).

- [x] **Step 1: Replace `tab_content` block**

Replace the `<FrameLayout android:id="@+id/tab_content">…</FrameLayout>` block (currently lines 32-40 containing `include start_advanced`) with:

```xml
    <FrameLayout
        android:id="@+id/content_root"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/status_layout"
        android:layout_below="@id/start_toolbar">

        <FrameLayout
            android:id="@+id/record_root"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:id="@+id/record_hint"
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:text="@string/Setup_steps_hint_record" />

            <include
                layout="@layout/start_fab"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_gravity="bottom"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginEnd="@dimen/activity_margin"
                android:layout_marginBottom="16dp" />
        </FrameLayout>

        <include
            android:id="@+id/setup_root"
            layout="@layout/start_setup"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <include
            android:id="@+id/picker_root"
            layout="@layout/start_picker"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />
    </FrameLayout>
```

Also add `Setup_steps_hint_record` string: `Tap Start to set up your run` to `strings.xml`.

- [x] **Step 2: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD FAILURE — `start_setup` and `start_picker` layouts do not exist yet. This is expected; the failure is the gate marker that Task 3 creates them. To keep Task 2 independently green, create the two layout files first as buildable placeholders in Task 3 before running this gate. Order: run Task 3 → Task 4 → then this gate and commit.

- [x] **Step 3: Commit (after Task 3 and Task 4 exist)**

```bash
git add app/res/layout/start.xml app/res/values/strings.xml
git commit -m "feat: restructure record screen into minimal root with wizard hosts"
```

---

### Task 3: `start_setup.xml` — the iOS-style Setup Run page

**Files:**
- Create: `app/res/layout/start_setup.xml`

**Interfaces:**
- Produces ids: `setup_back_button` (ImageButton), `setup_title` (TextView), `setup_sport_value`, `setup_audio_value`, `setup_workout_value` (TextView, set text to current selection), `setup_sport_row`, `setup_audio_row`, `setup_workout_row` (clickable views), `setup_steps_hint` (TextView), `advanced_step_list` (RecyclerView — reused id from retired `start_advanced.xml`), `setup_gps_indicator` (ImageView), `setup_gps_message` (TextView), `start_run_button` (MaterialButton).

- [x] **Step 1: Write `app/res/layout/start_setup.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/setup_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurfaceContainerLow"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/setup_toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        app:navigationIcon="@drawable/ic_arrow_back_24dp"
        app:title="@string/Setup_run"
        tools:ignore="MissingTitle" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="16dp">

            <TextView
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="12dp"
                android:text="@string/Setup_section_activity" />

            <LinearLayout
                android:id="@+id/setup_sport_row"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="6dp"
                android:layout_marginEnd="@dimen/activity_margin"
                android:background="?attr/colorSurface"
                android:clickable="true"
                android:focusable="true"
                android:orientation="horizontal"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/Sport" />

                <ImageView
                    android:id="@+id/setup_sport_icon"
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:contentDescription="@null" />

                <TextView
                    android:id="@+id/setup_sport_value"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:textColor="?attr/colorOnSurfaceVariant" />

                <ImageView
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:layout_marginStart="8dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_chevron_right_24dp" />
            </LinearLayout>

            <TextView
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="16dp"
                android:text="@string/Setup_section_guidance" />

            <LinearLayout
                android:id="@+id/setup_audio_row"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="6dp"
                android:layout_marginEnd="@dimen/activity_margin"
                android:background="?attr/colorSurface"
                android:clickable="true"
                android:focusable="true"
                android:orientation="horizontal"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/Audio_cues" />

                <TextView
                    android:id="@+id/setup_audio_value"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:textColor="?attr/colorOnSurfaceVariant" />

                <ImageView
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:layout_marginStart="8dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_chevron_right_24dp" />
            </LinearLayout>

            <TextView
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="16dp"
                android:text="@string/Workout" />

            <LinearLayout
                android:id="@+id/setup_workout_row"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="6dp"
                android:layout_marginEnd="@dimen/activity_margin"
                android:background="?attr/colorSurface"
                android:clickable="true"
                android:focusable="true"
                android:orientation="horizontal"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/Workout_label" />

                <TextView
                    android:id="@+id/setup_workout_value"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:textColor="?attr/colorOnSurfaceVariant" />

                <ImageView
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:layout_marginStart="8dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_chevron_right_24dp" />
            </LinearLayout>

            <TextView
                android:id="@+id/setup_steps_hint"
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/activity_margin"
                android:layout_marginTop="8dp"
                android:text="@string/Setup_steps_hint"
                android:visibility="gone" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/advanced_step_list"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:clipToPadding="false"
                android:minWidth="48dp"
                android:minHeight="48dp" />
        </LinearLayout>
    </ScrollView>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/colorSurface"
        android:elevation="8dp"
        android:gravity="center_horizontal"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:id="@+id/setup_gps_chip"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/setup_gps_indicator"
                android:layout_width="16dp"
                android:layout_height="16dp"
                android:layout_marginEnd="6dp"
                android:contentDescription="@null" />

            <TextView
                android:id="@+id/setup_gps_message"
                style="@style/StatusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/start_run_button"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/Start_run"
            android:textSize="18sp" />
    </LinearLayout>
</LinearLayout>
```

Add strings: `Workout_label` → `Workout`.

Note: the `androidx.recyclerview` widget requires the layout's root not force a fixed height, so `wrap_content` with `fillViewport` ScrollView gives the scrollable steps list.

- [x] **Step 2: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (as long as `start_picker.xml` from Task 4 exists; otherwise failure only for the missing picker layout, resolved in Task 4).

- [x] **Step 3: Commit after Task 4**

```bash
git add app/res/layout/start_setup.xml app/res/values/strings.xml
git commit -m "feat: add Setup Run page layout with grouped rows and step editor host"
```

---

### Task 4: `start_picker.xml` — the generic picker root

**Files:**
- Create: `app/res/layout/start_picker.xml`
- Create: `app/res/layout/picker_item.xml`

**Interfaces:**
- Produces ids: `picker_back_button` (ImageButton), `picker_title` (TextView), `picker_list` (ListView). Picker rows show a trailing check icon for the selected entry.

- [x] **Step 1: Write `app/res/layout/start_picker.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/picker_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurfaceContainerLow"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/picker_toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        app:navigationIcon="@drawable/ic_arrow_back_24dp"
        app:title=""
        tools:ignore="MissingTitle" />

    <TextView
        android:id="@+id/picker_title"
        style="@style/StatusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/activity_margin"
        android:layout_marginTop="12dp"
        android:textSize="20sp"
        android:textStyle="bold" />

    <ListView
        android:id="@+id/picker_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:divider="?android:attr/listDivider"
        android:dividerHeight="1dp" />
</LinearLayout>
```

- [x] **Step 2: Write `app/res/layout/picker_item.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:minHeight="56dp"
    android:paddingStart="@dimen/activity_margin"
    android:paddingEnd="@dimen/activity_margin"
    android:textColor="?android:attr/textColorPrimary"
    android:textSize="16sp"
    app:drawableEndCompat="@drawable/ic_check"
    app:tint="?attr/colorPrimary" />
```

- [x] **Step 3: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/res/layout/start_picker.xml app/res/layout/picker_item.xml
git commit -m "feat: add generic picker root layout"
```

---

### Task 5: StartFragment navigation — page stack, transitions, back handling

**Files:**
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`

**Interfaces:**
- Consumes: `MainLayout#setStartFlowActive(boolean)` from Task 1; ids `content_root`, `record_root`, `record_hint`, `setup_root`, `picker_root` from Task 2.
- Produces: `pushPage(Page)`, `popPage()`, `goRecord()`, `showPage(Page)`; `Page` enum `{RECORD, SETUP, PICKER}`; `pageStack` field; `OnBackPressedCallback`.

- [x] **Step 1: Add page state fields and enum**

```java
  private enum Page {
    RECORD,
    SETUP,
    PICKER
  }

  private final ArrayDeque<Page> pageStack = new ArrayDeque<>();
  private View recordRoot = null;
  private View setupRoot = null;
  private View pickerRoot = null;
  private View startButton = null;
```

- [x] **Step 2: Wire roots + back callback in `onViewCreated`**

Immediately after existing `startButton = view.findViewById(R.id.start_button);` add:

```java
    recordRoot = view.findViewById(R.id.record_root);
    setupRoot = view.findViewById(R.id.setup_root);
    pickerRoot = view.findViewById(R.id.picker_root);
```

And at the end of `onViewCreated` register:

```java
    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (!pageStack.isEmpty()) {
                  popPage();
                } else {
                  setEnabled(false);
                }
              }
            });
```

- [x] **Step 3: Navigation methods**

```java
  private void pushPage(Page page) {
    pageStack.push(page);
    showPage(page);
  }

  private void popPage() {
    if (pageStack.isEmpty()) return;
    pageStack.pop();
    showPage(pageStack.isEmpty() ? Page.RECORD : pageStack.peek());
  }

  private void goRecord() {
    pageStack.clear();
    showPage(Page.RECORD);
  }

  private void showPage(Page page) {
    boolean record = page == Page.RECORD;
    animateRoot(recordRoot, record);
    animateRoot(setupRoot, page == Page.SETUP);
    animateRoot(pickerRoot, page == Page.PICKER);
    view.findViewById(R.id.status_layout).setVisibility(record ? View.VISIBLE : View.GONE);
    if (getActivity() instanceof MainLayout mainLayout) {
      mainLayout.setStartFlowActive(!record);
    }
    updateView();
  }

  private void animateRoot(View root, boolean show) {
    if (root == null) return;
    if (show) {
      root.setVisibility(View.VISIBLE);
      root.setAlpha(0f);
      root.animate().alpha(1f).setDuration(180).start();
    } else {
      root.animate().cancel();
      root.setVisibility(View.GONE);
    }
  }
```

Add the `android.util.ArrayDeque` import (or `java.util.ArrayDeque`).

- [x] **Step 4: `startButtonClick` opens the wizard (GPS auto-start preserved)**

Replace the current `startButtonClick` body with:

```java
  private final OnClickListener startButtonClick =
      v -> {
        if (mTracker == null) return;
        boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
        if (raceReady && !sportWithoutGps) {
          if (mGpsStatus == null || !mGpsStatus.isStarted()) {
            if (checkPermissions(true)) {
              updateView();
              return;
            }
            startGps();
          }
        }
        pushPage(Page.SETUP);
      };
```

- [x] **Step 5: Broadcast receiver starts directly (Wear), skipping the wizard**

Replace `startEventBroadcastReceiver.onReceive` body:

```java
          requireActivity()
              .runOnUiThread(
                  () -> {
                    if (mTracker == null || !pageStack.isEmpty()) return;
                    handleExternalStartRequest();
                  });
```

Add:

```java
  private void handleExternalStartRequest() {
    boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
    TrackerState st = mTracker.getState();
    if (raceReady && !sportWithoutGps) {
      if (mGpsStatus == null || !mGpsStatus.isStarted()) {
        if (checkPermissions(true)) {
          updateView();
          return;
        }
        startGps();
      }
      startWorkout();
      return;
    }
    if (st == TrackerState.CONNECTED) {
      startWorkout();
      return;
    }
    updateView();
  }
```

- [x] **Step 6: Reset wizard after a run; FAB always visible**

In `runLauncher` callback, before `runActivityPending = false;`, add `goRecord();`.

Replace `updateStartButtonView()` body:

```java
  private void updateStartButtonView() {
    startButton.setVisibility(View.VISIBLE);
  }
```

Remove now-unused strings/imports the compiler flags only after everything compiles (deferred to Task 7 cleanup).

- [x] **Step 7: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (unused `advancedWorkoutSpinner`/`SportAdapter` fields are still referenced by onViewCreated wiring — cleaned in Task 7).

- [x] **Step 8: Commit**

```bash
git add app/src/main/org/runnerup/view/StartFragment.java
git commit -m "feat: add wizard page stack, transitions, and back handling to start"
```

---

### Task 6: Setup page behavior — rows, values, Start Run gating, GPS chip

**Files:**
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`

**Interfaces:**
- Consumes: ids from Task 3; `startButtonClick` from Task 5.
- Produces: `updateSetupValues()`, `updateStartRunButtonView()`, `updateSetupGpsChip()`; fields `setupSportValue`, `setupAudioValue`, `setupWorkoutValue`, `setupStepsHint`, `setupGpsIndicator`, `setupGpsMessage`, `setupGpsChip`, `startRunButton`.

- [x] **Step 1: Add fields**

```java
  private TextView setupSportValue = null;
  private TextView setupAudioValue = null;
  private TextView setupWorkoutValue = null;
  private TextView setupStepsHint = null;
  private View setupGpsChip = null;
  private ImageView setupGpsIndicator = null;
  private TextView setupGpsMessage = null;
  private MaterialButton startRunButton = null;
```

- [x] **Step 2: Wire the setup page in `onViewCreated`**

```java
    setupSportValue = view.findViewById(R.id.setup_sport_value);
    setupAudioValue = view.findViewById(R.id.setup_audio_value);
    setupWorkoutValue = view.findViewById(R.id.setup_workout_value);
    setupStepsHint = view.findViewById(R.id.setup_steps_hint);
    setupGpsChip = view.findViewById(R.id.setup_gps_chip);
    setupGpsIndicator = view.findViewById(R.id.setup_gps_indicator);
    setupGpsMessage = view.findViewById(R.id.setup_gps_message);
    startRunButton = view.findViewById(R.id.start_run_button);
    startRunButton.setOnClickListener(startRunClick);

    view.findViewById(R.id.setup_sport_row).setOnClickListener(v -> openPicker(PickerKind.SPORT));
    view.findViewById(R.id.setup_audio_row).setOnClickListener(v -> openPicker(PickerKind.AUDIO));
    view.findViewById(R.id.setup_workout_row).setOnClickListener(v -> openPicker(PickerKind.WORKOUT));

    setupSportValue.setOnClickListener(
        v -> openPicker(PickerKind.SPORT));
    view.findViewById(R.id.setup_sport_icon)
        .setImageDrawable(
            AppCompatResources.getDrawable(
                requireContext(), Sport.drawableColored16Of(currentSportId())));
```

`currentSportId()` helper (add):

```java
  private int currentSportId() {
    return appPrefs.getInt(
        getString(R.string.pref_sport), org.runnerup.common.util.Constants.DB.ACTIVITY.SPORT_RUNNING);
  }
```

- [x] **Step 3: `startRunClick` (same gating body as old FAB click)**

```java
  private final OnClickListener startRunClick =
      v -> {
        if (mTracker == null) return;
        boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
        if (raceReady && !sportWithoutGps) {
          if (mGpsStatus == null || !mGpsStatus.isStarted()) {
            if (checkPermissions(true)) {
              updateView();
              return;
            }
            startGps();
          }
          startWorkout();
          return;
        }
        if (mTracker.getState() == TrackerState.CONNECTED) {
          startWorkout();
          return;
        }
        updateView();
      };
```

- [x] **Step 4: `updateSetupValues` + gating + chip**

```java
  private void updateSetupValues() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    int sportId = prefs.getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    if (setupSportValue != null) {
      setupSportValue.setText(Sport.getStringArray(requireContext().getResources())[sportId]);
    }
    if (setupAudioValue != null) {
      String audio = prefs.getString(getString(R.string.pref_advanced_audio), null);
      setupAudioValue.setText(
          audio == null
              ? requireContext().getString(org.runnerup.common.R.string.Default)
              : audio);
    }
    if (setupWorkoutValue != null) {
      setupWorkoutValue.setText(
          selectedWorkoutName == null || selectedWorkoutName.isEmpty()
              ? requireContext().getString(R.string.None)
              : selectedWorkoutName);
    }
    int hasSteps = advancedWorkout == null ? View.GONE : View.VISIBLE;
    if (setupStepsHint != null) setupStepsHint.setVisibility(hasSteps);
    if (advancedStepList != null) advancedStepList.setVisibility(hasSteps);
  }

  private void updateStartRunButtonView() {
    if (mTracker == null || !mIsBound) {
      if (startRunButton != null) startRunButton.setEnabled(false);
      return;
    }
    if (startRunButton == null) return;
    boolean raceReady = RaceReady.enabled(getResources(), appPrefs);
    if (raceReady || sportWithoutGps) {
      startRunButton.setEnabled(true);
      return;
    }
    boolean ready =
        mGpsStatus.isStarted()
            && mGpsStatus.isLogging()
            && mGpsStatus.isFixed()
            && mTracker.getState() == TrackerState.CONNECTED;
    startRunButton.setEnabled(ready);
  }

  private void updateSetupGpsChip() {
    if (setupGpsChip == null) return;
    if (sportWithoutGps) {
      setupGpsChip.setVisibility(View.GONE);
      return;
    }
    setupGpsChip.setVisibility(View.VISIBLE);
    if (!mGpsStatus.isEnabled() || !mGpsStatus.isStarted() || !mGpsStatus.isLogging()) {
      setupGpsIndicator.setImageResource(R.drawable.ic_gps_0);
      setupGpsMessage.setText(org.runnerup.common.R.string.GPS_indicator_off);
      return;
    }
    var gpsLevel = getGpsLevel(getGpsAccuracy(), mGpsStatus.getSatellitesFixed());
    switch (gpsLevel) {
      case NOT_FIXED:
        setupGpsIndicator.setImageResource(R.drawable.ic_gps_0);
        setupGpsMessage.setText(org.runnerup.common.R.string.Waiting_for_GPS);
        break;
      case POOR:
        setupGpsIndicator.setImageResource(R.drawable.ic_gps_1);
        setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_poor);
        break;
      case ACCEPTABLE:
        setupGpsIndicator.setImageResource(R.drawable.ic_gps_2);
        setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_acceptable);
        break;
      case GOOD:
        setupGpsIndicator.setImageResource(R.drawable.ic_gps_3);
        setupGpsMessage.setText(org.runnerup.common.R.string.GPS_level_good);
        break;
    }
  }
```

- [x] **Step 5: Call from `updateView()`**

```java
  public void updateView() {
    updateStartGpsButtonView();
    updateStartButtonView();
    updateSetupValues();
    updateStartRunButtonView();
    updateGPSView();
    updateSetupGpsChip();
    boolean hrPresent = updateHRView();
    boolean wearPresent = updateWearOSView();
    if (!hrPresent && !wearPresent && statusDetailsShown) {
      noDevicesConnected.setVisibility(View.VISIBLE);
    } else {
      noDevicesConnected.setVisibility(View.GONE);
    }
  }
```

- [x] **Step 6: Compile gate**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD FAILURE only because `openPicker`/`PickerKind`/`selectedWorkoutName` are undefined — created in Task 7. To keep this commit green, either implement together with Task 7, or temporarily provide stub `openPicker`/enum/field. Recommended: commit together with Task 7.

---

### Task 7: Picker behavior + workout load refactor + cleanup

**Files:**
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`
- Modify: `app/src/main/org/runnerup/view/WorkoutPlanAdapter.java` (inner class within StartFragment — add `clear()`)

**Interfaces:**
- Consumes: `openPicker(PickerKind)`, `PickerKind`, `selectedWorkoutName` from Task 6.
- Produces: `selectedWorkoutName` field, `PickerKind` enum, `openPicker`, `applySportChoice`, `applyAudioChoice`, `applyWorkoutChoice`, `getPickerItems`, `PickerListAdapter`, `loadAdvanced` refactor, `prepareWorkout` default-basic fallback, `onWorkoutChanged` name source, `prefChangeListener` adaptation, removal of `advancedWorkoutSpinner`/`SportAdapter`/`sportSpinner`/`advancedAudioSpinner` wiring.

- [x] **Step 1: Add `selectedWorkoutName` field and `PickerKind` enum**

```java
  private String selectedWorkoutName = "";
```

```java
  private enum PickerKind {
    SPORT,
    AUDIO,
    WORKOUT
  }

  private PickerKind pickerKind = PickerKind.SPORT;
  private String[] pickerItems = new String[0];
  private int pickerSelected = 0;
```

- [x] **Step 2: Picker entry + list adapter**

```java
  private void openPicker(PickerKind kind) {
    pickerKind = kind;
    getPickerItems();
    if (pickerItems.length == 0) return;
    String title;
    switch (kind) {
      case SPORT:
        title = requireContext().getString(org.runnerup.common.R.string.Sport);
        break;
      case AUDIO:
        title = requireContext().getString(R.string.Audio_cues);
        break;
      default:
        title = requireContext().getString(org.runnerup.common.R.string.Workout);
        break;
    }
    TextView titleView = pickerRoot.findViewById(R.id.picker_title);
    titleView.setText(title);
    ListView list = pickerRoot.findViewById(R.id.picker_list);
    PickerListAdapter adapter = new PickerListAdapter(pickerItems, pickerSelected);
    list.setOnItemClickListener((parent, view, position, id) -> onPickerItemClick(position));
    list.setAdapter(adapter);
    pushPage(Page.PICKER);
  }

  private void getPickerItems() {
    switch (pickerKind) {
      case SPORT:
        {
          String[] sports = Sport.getStringArray(requireContext().getResources());
          pickerItems = sports;
          pickerSelected = currentSportId();
          break;
        }
      case AUDIO:
        {
          List<String> items = new ArrayList<>();
          items.add(requireContext().getString(org.runnerup.common.R.string.Default));
          advancedAudioListAdapter.reload();
          for (int i = 1; i < advancedAudioListAdapter.getCount() - 1; i++) {
            items.add((String) advancedAudioListAdapter.getItem(i));
          }
          items.add(
              String.format(
                  requireContext()
                      .getString(org.runnerup.common.R.string.dialog_ellipsis),
                  requireContext().getString(org.runnerup.common.R.string.Manage_audio_cues)));
          pickerItems = items.toArray(new String[0]);
          String current =
              appPrefs.getString(getString(R.string.pref_advanced_audio), null);
          pickerSelected =
              current == null
                  ? 0
                  : (items.contains(current) ? items.indexOf(current) : 0);
          break;
        }
      default:
        {
          List<String> items = new ArrayList<>();
          items.add(requireContext().getString(R.string.None));
          String[] workouts = WorkoutListAdapter.load(requireContext());
          if (workouts != null) items.addAll(java.util.Arrays.asList(workouts));
          items.add(
              String.format(
                  requireContext()
                      .getString(org.runnerup.common.R.string.dialog_ellipsis),
                  requireContext().getString(org.runnerup.common.R.string.Manage_workouts)));
          pickerItems = items.toArray(new String[0]);
          pickerSelected =
              selectedWorkoutName == null || selectedWorkoutName.isEmpty()
                  ? 0
                  : (items.contains(selectedWorkoutName)
                      ? items.indexOf(selectedWorkoutName)
                      : 0);
          break;
        }
    }
  }

  private void onPickerItemClick(int position) {
    if (position < 0 || position >= pickerItems.length) return;
    switch (pickerKind) {
      case SPORT:
        applySportChoice(position);
        break;
      case AUDIO:
        if (position == pickerItems.length - 1) {
          startActivity(new Intent(requireContext(), AudioCueSettingsActivity.class));
          return;
        }
        applyAudioChoice(position);
        break;
      default:
        if (position == pickerItems.length - 1) {
          startActivity(new Intent(requireContext(), ManageWorkoutsActivity.class));
          return;
        }
        applyWorkoutChoice(position);
        break;
    }
    popPage();
  }

  private void applySportChoice(int position) {
    appPrefs.edit().putInt(getString(R.string.pref_sport), position).apply();
    setGpsNotRequired(Sport.isWithoutGps(position));
    updateSetupSportIcon(position);
    updateView();
  }

  private void applyAudioChoice(int position) {
    String name = pickerItems[position];
    if (name.contentEquals(requireContext().getString(org.runnerup.common.R.string.Default))) {
      appPrefs.edit().remove(getString(R.string.pref_advanced_audio)).apply();
    } else {
      appPrefs.edit().putString(getString(R.string.pref_advanced_audio), name).apply();
    }
    updateView();
  }

  private void applyWorkoutChoice(int position) {
    String name = pickerItems[position];
    if (name.contentEquals(requireContext().getString(R.string.None))) {
      appPrefs.edit().remove(getString(R.string.pref_advanced_workout)).apply();
      loadAdvanced("");
      return;
    }
    appPrefs.edit().putString(getString(R.string.pref_advanced_workout), name).apply();
    loadAdvanced(name);
  }
```

- [x] **Step 3: `PickerListAdapter` inner class**

```java
  private class PickerListAdapter extends BaseAdapter {
    private final String[] items;
    private final int selected;

    PickerListAdapter(String[] items, int selected) {
      this.items = items;
      this.selected = selected;
    }

    @Override
    public int getCount() {
      return items.length;
    }

    @Override
    public Object getItem(int position) {
      return items[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView =
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.picker_item, parent, false);
      }
      TextView text = (TextView) convertView;
      text.setText(items[position]);
      boolean selectedItem = position == selected;
      Drawable check = null;
      if (selectedItem) {
        check =
            AppCompatResources.getDrawable(
                requireContext(), org.runnerup.common.R.drawable.ic_check);
      }
      text.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, check, null);
      return text;
    }
  }
```

Check the actual ic_check resource location (app `R` vs common `R`) — use whichever `R` resolves: `org.runnerup.R.drawable.ic_check` is in app (it's in `app/res/drawable/ic_check.xml`), so use `org.runnerup.R.drawable.ic_check`.

- [x] **Step 4: `updateSetupSportIcon` helper**

```java
  private void updateSetupSportIcon(int sport) {
    if (setupSportValue == null) return;
    ImageView icon = setupSportValue.getRootView().findViewById(R.id.setup_sport_icon);
    if (icon == null) return;
    Drawable d = AppCompatResources.getDrawable(requireContext(), Sport.drawableColored16Of(sport));
    if (d != null) d.setTint(ContextCompat.getColor(requireContext(), Sport.colorOf(sport)));
    icon.setImageDrawable(d);
  }
```

- [x] **Step 5: Refactor `loadAdvanced` to track name + clear steps**

```java
  @SuppressLint("NotifyDataSetChanged")
  private void loadAdvanced(String name) {
    Context ctx = requireActivity().getApplicationContext();
    if (name == null) {
      name = appPrefs.getString(getString(R.string.pref_advanced_workout), "");
    }
    selectedWorkoutName = name;
    advancedWorkout = null;
    if (name.isEmpty()) {
      advancedWorkoutStepsAdapter.clear();
      updateView();
      return;
    }
    try {
      advancedWorkout = WorkoutSerializer.readFile(ctx, name);
      advancedWorkoutStepsAdapter.setWorkout(advancedWorkout);
      updateView();
    } catch (Exception ex) {
      ex.printStackTrace();
      new MaterialAlertDialogBuilder(requireActivity())
          .setTitle(getString(org.runnerup.common.R.string.Failed_to_load_workout))
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }
```

Add `clear()` to `WorkoutPlanAdapter`:

```java
    @SuppressLint("NotifyDataSetChanged")
    void clear() {
      workout = null;
      items.clear();
      notifyDataSetChanged();
    }
```

- [x] **Step 6: `prepareWorkout` supports GPS-only runs**

```java
  private Workout prepareWorkout() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    SharedPreferences audioPref =
        WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_advanced_audio));
    Workout w = advancedWorkout;
    if (w == null) {
      w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, null);
    }
    WorkoutBuilder.prepareWorkout(getResources(), pref, w);
    WorkoutBuilder.addAudioCuesToWorkout(getResources(), w, audioPref, pref);
    return w;
  }
```

(Remove the old `RaceReady.defaultWorkout` branch — the generic builder covers it.)

- [x] **Step 7: `onWorkoutChanged` uses `selectedWorkoutName`**

```java
  private final Runnable onWorkoutChanged =
      () -> {
        if (advancedWorkout != null && !selectedWorkoutName.isEmpty()) {
          Context ctx = requireActivity().getApplicationContext();
          try {
            WorkoutSerializer.writeFile(ctx, selectedWorkoutName, advancedWorkout);
          } catch (Exception ex) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
                .setMessage(ex.toString())
                .setPositiveButton(
                    org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
          }
        }
      };
```

- [x] **Step 8: Adapt `prefChangeListener` (spinner → row/values)**

```java
  private final SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
      (sharedPrefs, key) -> {
        assert key != null;
        if (key.equals(getString(R.string.pref_advanced_workout))) {
          String newName = sharedPrefs.getString(key, "");
          loadAdvanced(newName);
          updateView();
        }
      };
```

- [x] **Step 9: Strip old spinner wiring from `onViewCreated`**

Remove: `sportSpinner`/`setOnSetValueListener` block (keep `sportWithoutGps` init + `updateView`), the `advancedAudioSpinner` block, the `advancedWorkoutSpinner`/`OnConfigureWorkoutsListener` block. Keep creating `advancedAudioListAdapter` (used by the picker), `advancedWorkoutListAdapter` field, `advancedStepList` layout manager + `advancedWorkoutStepsAdapter` (used by setup page). Keep `updateSportFieldIcon(initialSport)` removed and replaced by keeping `sportWithoutGps` init:

```java
    int initialSport = prefs.getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    sportWithoutGps = Sport.isWithoutGps(initialSport);
    updateSetupSportIcon(initialSport);
```

Remove `OnConfigureAudioListener` and `OnConfigureWorkoutsListener` inner classes. Remove `sportSpinner`/`sportAdapter`/`sportInitialized`/`advancedWorkoutSpinner`/`advancedWorkoutListAdapter`/`advancedAudioListAdapter`-spinner field declarations that are no longer referenced (keep `advancedAudioListAdapter` as it feeds the audio picker). Update the `sportSpinner` field references and `setGpsNotRequired` (no spinner update needed).

- [x] **Step 10: Compile + fix unused warnings**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL; then run `./gradlew spotlessApply` and recompile so formatting is stable.

- [x] **Step 11: Commit**

```bash
git add app/src/main/org/runnerup/view/StartFragment.java
git commit -m "feat: wired Setup Run pickers, GPS-only runs, and removed spinner UI"
```

Delete retired `app/res/layout/start_advanced.xml` in the same commit:

```bash
git rm app/res/layout/start_advanced.xml
git commit --amend --no-edit # (or include in the same commit before pushing)
```

---

### Task 8: Full verification gates

**Files:** none (verification only).

- [x] **Step 1: Unit tests**

Run: `./gradlew test`
Expected: PASS (no new baseline failures). ✅ Ran — PASS.

- [x] **Step 2: Lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: only the 28 pre-existing baseline items (table in `app/lint-baseline.xml`); no new issues. If a new issue appears, fix it; do not edit the baseline. ✅ Ran — 29 baseline items (baseline had one more than planned); no new issues.

- [x] **Step 3: Formatting**

Run: `./gradlew spotlessApply` then `./gradlew spotlessCheck`
Expected: PASS. ✅ Ran — PASS.

- [x] **Step 4: Assemble**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/latest/debug/app-latest-debug.apk`. ✅ Ran — BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/res/layout/start.xml app/res/layout/start_setup.xml app/res/values/strings.xml app/src/main/org/runnerup/view/StartFragment.java
git commit -m "feat: auto-start GPS from sport selection, GPS info popup on Setup Run"
```

---

### Task 9: Device smoke test

**Files:** none (verification only).

- [ ] **Step 1: Install and launch**

```bash
adb -s 5717a66e install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 5717a66e shell am force-stop org.runnerup.debug
adb -s 5717a66e shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Verify record screen shows the toolbar "Record", the Start pill (always visible), and the HR/wear status bar — and none of the old spinners or the old Start GPS button.

- [ ] **Step 2: Wizard flow**

- Tap Start → Setup Run page appears, bottom nav hidden, back chevron visible; rows show pre-filled values.
- GPS auto-start: with a GPS sport already selected (Running), GPS starts as soon as the Setup Run page opens — GPS chip shows "Waiting for GPS…"/"Poor/Good GPS" and Start Run enables once connected. No explicit Start GPS action exists anymore.
- Sport override: change sport to a non-GPS sport (Manual) → GPS stops (chip hides, Start Run enabled immediately); switch back to Running → GPS restarts.
- GPS chip popup: tap the GPS chip → "GPS signal info" popup card shows signal detail (satellites/accuracy) and stays in sync while open; tap again → collapses.
- Tap Sport → picker list; change sport → back; value updated.
- Tap Audio cues → picker list with Default + schemes + Manage audio cues…; pick a scheme → value shown.
- Tap Workout → picker list with None + workouts + Manage workouts…; pick a workout → steps appear under the row with hint; pick None → steps hidden.
- Edit a step (tap the step row) → dialog opens (StepButton) → change a value → on dismiss the workout file updates.
- Back chevron and system Back unwind picker → setup → record, re-enabling bottom nav.

- [ ] **Step 3: Recording + persistence**

- With GPS ready: Start Run → RunActivity recording starts (real fix acquired). Pause/resume/stop per normal behavior; confirm DB has a completed activity.
- Non-GPS sport: pick Manual, Start Run → run records without GPS.
- After restarting the app, open Setup Run and confirm last-used sport/audio/workout pre-selected.

- [ ] **Step 4: Regression spot-checks**

- Wear-start intent path still functions (gated on `pageStack` empty).
- Race-ready deferred start still offered on a non-locked GPS sport.
- Swiping tabs with the wizard closed is normal; with the wizard open, swiping is disabled and bottom nav hidden.

---

### Task 10: GPS auto-start + GPS info popup

Follow-up to the original flow: remove the record screen's Start GPS control entirely and move GPS start/stop to sport selection on the Setup Run page. GPS auto-starts when a GPS-requiring sport is selected (including when the page opens with one already selected) and stops when a non-GPS sport is selected. The Setup Run footer's GPS chip (reusing the existing level logic) toggles a "GPS signal info" popup card showing signal level plus satellites/accuracy detail.

**Files:**
- Modify: `app/res/layout/start.xml` — remove GPS views (`gps_indicator`, `gps_message`, `gps_detail_*`, `expand_icon`, `gps_enable_button`) and the `status_frame` wrapper; keep HR/wear/`device_status`.
- Modify: `app/res/layout/start_setup.xml` — chip becomes clickable/focusable; add `setup_gps_popup` MaterialCardView (header `@string/GPS_signal_info`, `setup_gps_popup_indicator`, `_popup_message`, `_popup_satellites`).
- Modify: `app/res/values/strings.xml` — add `GPS_signal_info`.
- Modify: `app/src/main/org/runnerup/view/StartFragment.java` — remove record-root GPS fields/UI/`statusDetailsShown`, `toggleStatusDetails`, `updateGPSView`, `updateStartGpsButtonView`, `gpsEnableClick`; add sport-driven `autoStartGpsForSport()` (start on GPS sport, stop on non-GPS sport, keeps tracker CONNECTED for non-GPS runs), wire chip → `toggleSetupGpsPopup()`/`populateGpsPopup()`, and retry auto-start from the permission-launcher result so a freshly granted permission takes effect.

- [x] **Step 1: Strip record-root GPS UI from `StartFragment`/`start.xml`**
- [x] **Step 2: Add GPS chip popup card to `start_setup.xml` + `GPS_signal_info` string**
- [x] **Step 3: Sport-driven auto start/stop (`autoStartGpsForSport`, reworked `setGpsNotRequired`)**
- [x] **Step 4: Popup wiring (chip click, live sync while open)**
- [x] **Step 5: Permission-grant retry for auto-start**
- [x] **Step 6: Gates** — `test`, `:app:lintLatestDebug` (no new baseline items), `spotlessApply`+`spotlessCheck`, `:app:assembleLatestDebug` all PASS.
- [ ] **Step 7: Device smoke test** (covered by Task 9 above)
- [x] **Step 8: Commit** — see Task 8 Step 5.