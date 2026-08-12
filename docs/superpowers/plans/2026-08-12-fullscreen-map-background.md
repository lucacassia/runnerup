# Full-Screen Map Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full-screen map view as background in RunActivity showing live GPS position and recorded track, behind all existing opaque UI elements.

**Architecture:** Change root layout to FrameLayout with MapViewWrapper as first child (full screen, conditionally visible). Existing UI (stats card, bottom sheet, buttons) remains on top. Use existing LiveMap/MapWrapper infrastructure for live tracking and historical route loading.

**Tech Stack:** osmdroid/mapbox via source sets, LiveMap class, MapViewWrapper, BottomSheetBehavior, MaterialCardView

## Global Constraints
- Only active when `BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED`
- nomap flavor has no-op LiveMap/MapWrapper stubs
- Stats card, bottom sheet, buttons remain opaque (no transparency)
- Existing stats card click-to-expand animation unchanged
- Map active whenever RunActivity visible (not just during run)
- All gates: `./gradlew :app:test`, `:app:lintLatestDebug`, `spotlessCheck` must pass

---

### Task 1: Update run.xml Layout - Change Root to FrameLayout & Add MapViewWrapper

**Files:**
- Modify: `app/res/layout/run.xml:1-50` (root element and top-level structure)
- Test: Manual verification on device

**Interfaces:**
- Produces: `run_mapview` (MapViewWrapper) with id `@+id/run_mapview`, `recenter_button` (FloatingActionButton) referenced by LiveMap

- [ ] **Step 1: Change root LinearLayout to FrameLayout**

```xml
<!-- Before: line 17 -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/start_view"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent"
    android:orientation="vertical">

<!-- After: line 17 -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/start_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
```

- [ ] **Step 2: Add MapViewWrapper as first child (full screen, gone by default)**

```xml
<!-- Add after FrameLayout opening tag, before existing UI -->
    <org.runnerup.util.MapViewWrapper
        android:id="@+id/run_mapview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />
```

- [ ] **Step 3: Wrap existing UI in a LinearLayout (vertical) as second child**

```xml
<!-- Wrap everything from MaterialCardView to control buttons LinearLayout in: -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">
        <!-- existing stats card, bottom sheet host, control buttons -->
    </LinearLayout>
```

- [ ] **Step 4: Verify recenter_button exists in layout** (should already be there from previous code, but ensure it's in the LinearLayout wrapper)

```xml
<!-- recenter_button should be in the LinearLayout wrapper, typically near bottom -->
<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/recenter_button"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="end|bottom"
    android:layout_margin="16dp"
    android:contentDescription="@string/Recenter"
    android:visibility="gone"
    app:srcCompat="@drawable/ic_recenter"
    app:tint="?attr/colorOnPrimaryContainer" />
```

- [ ] **Step 5: Build and verify layout compiles**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/res/layout/run.xml
git commit -m "layout: change RunActivity root to FrameLayout, add MapViewWrapper background"
```

---

### Task 2: Add Map Member Variables to RunActivity

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:76-140` (imports and member variables)

**Interfaces:**
- Produces: `MapViewWrapper runMapview`, `LiveMap liveMap` member variables

- [ ] **Step 1: Add imports for MapViewWrapper and LiveMap**

```java
// Add after existing imports (around line 76)
import org.runnerup.util.LiveMap;
import org.runnerup.util.MapViewWrapper;
```

- [ ] **Step 2: Add member variables (after line 126, before BottomSheetBehavior)**

```java
// Add after: private BottomSheetBehavior<?> runBottomSheetBehavior = null;
private MapViewWrapper runMapview = null;
private LiveMap liveMap = null;
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "RunActivity: add MapViewWrapper and LiveMap member variables"
```

---

### Task 3: Initialize Map in onCreate()

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:180-250` (onCreate method)

**Interfaces:**
- Consumes: `runMapview`, `liveMap` member variables from Task 2
- Produces: Initialized LiveMap with recenter button, map visible when enabled

- [ ] **Step 1: Add map initialization in onCreate() after setContentView**

```java
// After: setContentView(R.layout.run);
// Around line 190
if (BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED) {
    runMapview = findViewById(R.id.run_mapview);
    runMapview.setVisibility(View.VISIBLE);
    liveMap = new LiveMap(runMapview, findViewById(R.id.recenter_button));
    liveMap.onCreate(savedInstanceState);
}
```

- [ ] **Step 2: Remove old MapWrapper.start(this) call** (already removed in previous commit, verify it's gone)

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "RunActivity: initialize LiveMap in onCreate when map enabled"
```

---

### Task 4: Wire Live Location Updates in onTick()

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:420-440` (onTick method)

**Interfaces:**
- Consumes: `liveMap` from Task 2, `mTracker` from existing code
- Produces: Live location updates to map

- [ ] **Step 1: Add liveMap.onLocationChanged() call in onTick()**

```java
// In onTick(), after getting location from tracker (around line 433)
if (mTracker != null) {
    Location l2 = mTracker.getLastKnownLocation();
    if (l2 != null && !l2.equals(l)) {
        l = l2;
    }
    // Add this block:
    if (liveMap != null) {
        liveMap.onLocationChanged(l2);
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "RunActivity: forward live location updates to LiveMap"
```

---

### Task 5: Add Lifecycle Delegation for LiveMap

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:305-345` (onPause, onResume, onDestroy)

**Interfaces:**
- Consumes: `liveMap` from Task 2
- Produces: Proper lifecycle management for map

- [ ] **Step 1: Add liveMap.onPause() in onPause()**

```java
@Override
public void onPause() {
    super.onPause();
    if (holdToStopListener != null) {
        holdToStopListener.cancel();
    }
    if (liveMap != null) {
        liveMap.onPause();
    }
}
```

- [ ] **Step 2: Add liveMap.onResume() in onResume()**

```java
@Override
public void onResume() {
    // ... existing code ...
    super.onResume();
    showOnLockScreen(showOnLockScreen);
    if (liveMap != null) {
        liveMap.onResume();
    }
}
```

- [ ] **Step 3: Add liveMap.onDestroy() in onDestroy()**

```java
@Override
public void onDestroy() {
    super.onDestroy();
    if (holdToStopListener != null) {
        holdToStopListener.cancel();
    }
    unbindGpsTracker();
    stopTimer();
    if (liveMap != null) {
        liveMap.onDestroy();
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "RunActivity: delegate lifecycle to LiveMap"
```

---

### Task 6: Backfill Historical Route in onGpsTrackerBound()

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:350-380` (onGpsTrackerBound method)

**Interfaces:**
- Consumes: `liveMap` from Task 2, `mTracker` from existing code, `DBHelper` from existing imports
- Produces: Historical route loaded on map when tracker binds

- [ ] **Step 1: Add liveMap.onFirstShow() call in onGpsTrackerBound()**

```java
// In onGpsTrackerBound(), after workout is set (around line 360)
// Add after: workout = mTracker.getWorkout();
if (liveMap != null && mTracker != null) {
    long activityId = mTracker.getActivityId();
    if (activityId >= 0) {
        liveMap.onFirstShow(DBHelper.getReadableDatabase(this), activityId);
    }
}
```

- [ ] **Step 2: Ensure DBHelper import exists** (should already be there)

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:compileLatestDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "RunActivity: backfill historical route when tracker binds"
```

---

### Task 7: Run Full Test Suite and Gates

**Files:**
- None (verification only)

**Interfaces:**
- Consumes: All previous tasks

- [ ] **Step 1: Run unit tests**

Run: `./gradlew :app:testLatestDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: BUILD SUCCESSFUL (no new issues)

- [ ] **Step 3: Run spotless check**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Build debug APK**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/latest/debug/app-latest-debug.apk`

- [ ] **Step 5: Manual verification on device** (if device available)
- Install APK
- Start RunActivity
- Verify: map visible behind UI, live location updates, recenter button works
- Verify: stats card expand animation still works
- Verify: bottom sheet shows only "Workout" title when collapsed

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: full-screen map background in RunActivity with live tracking and historical route"
```

---

## Self-Review Checklist

- [x] Spec coverage: All requirements from spec mapped to tasks (layout, init, live updates, lifecycle, backfill)
- [x] No placeholders: All steps have actual code blocks
- [x] Type consistency: Member variable names match across tasks (runMapview, liveMap)
- [x] Feature flag handling: All map code guarded by `BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED`
- [x] nomap flavor: No changes needed (stubs already exist)
- [x] Gate commands: All verification commands match AGENTS.md requirements