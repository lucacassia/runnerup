# Recording Activity Screen Redesign — Spec Document

**Date:** 2026-08-20  
**Status:** Approved  
**Topic:** Modern Visual & Functional Redesign of the Activity Recording Screen (`RunActivity.java` / `run.xml`)  
**Design System:** Aero HUD & Live Interval Sheet (Material 3 + `opencode.json` dark/light theme palette)

---

## 1. Executive Summary & Goals

Overhaul the recording activity screen (`RunActivity`) — the live tracking view visible after starting a run, cycle, or workout — into a modern, visually intuitive, and glancable interface.

### Key Requirements
- **Preserve All Content:** Keep live GPS tracking map, all activity stats (Distance, Elapsed Time, Current Pace, Lap Distance, Lap Pace, Current HR), and the Workout Plan interval list.
- **Visual Style:** Modern, clean, and catchy visual hierarchy inspired by top fitness platforms (Apple Fitness+, Nike Run Club), strictly using the color palette defined in `opencode.json`.
- **Layout Architecture:** Floating elevated Stats HUD hovering over a full-screen live map background, with a live interval progress banner embedded in a collapsible bottom sheet drawer, and a floating control dock.

---

## 2. Layout & UI Component Architecture (`run.xml`)

### Root Canvas (`@+id/start_view`)
- `FrameLayout` filling `match_parent` height and width.

### 2.1 Live Map Viewport (`@+id/run_mapview`)
- Positioned at root level (`match_parent` height/width).
- Displays live GPS location and route track line when maps are enabled (`BuildConfig.OSMDROID_ENABLED` or `BuildConfig.MAPBOX_ENABLED`).
- Background: Full screen dark map tile styling.
- GPS Track: `#5c9cf5` (Dark) / `#3b7dd8` (Light) rounded stroke (5dp).
- Current Position Dot: `#fab283` (Dark) / `#2968c3` (Light) center marker with pulsing translucent halo.
- Attribution tag (`@+id/map_attribution`) anchored at bottom-start above the bottom sheet.

### 2.2 Top Floating Stats HUD Card (`@+id/table_layout1`)
- `MaterialCardView` floating over top portion of the screen:
  - `layout_width="match_parent"`, `layout_marginStart="12dp"`, `layout_marginEnd="12dp"`, `layout_marginTop="12dp"`
  - `app:cardCornerRadius="16dp"`
  - Background: `?attr/colorSurfaceContainerLow` (`#1e1e1e` dark / `#ffffff` light)
  - Stroke: `1dp` `?attr/colorOutlineVariant` (`#3c3c3c` dark / `#d4d4d4` light)

#### Content Structure
1. **Hero Metric Header:**
   - Primary Distance (`@+id/run_activity_distance`): Bold `36sp–40sp` typography (`#eeeeee` dark / `#1a1a1a` light), paired with unit label (`km` / `mi` in `#fab283` dark / `#3b7dd8` light).
   - Primary Time (`@+id/run_activity_time`): Bold `22sp` typography (`#5c9cf5` dark / `#7b5bb6` light).
2. **2×2 Secondary Metric Grid:**
   - **Top Left:** Current Pace (`@+id/run_activity_pace`) — Label `@string/Pace`.
   - **Top Right:** Heart Rate (`@+id/current_hr`) + Heart Rate Zone Pill (`@+id/hr_zone_pill`).
   - **Bottom Left:** Lap Distance (`@+id/lap_distance`) — Label `@string/LapDistance`.
   - **Bottom Right:** Lap Pace (`@+id/lap_pace`) — Label `@string/LapPace`.
3. **Giant Single-Metric Readout:**
   - Retains tap gesture on `@+id/table_layout1` to toggle expanded single-stat readout for high-intensity or direct sunlight conditions.

### 2.3 Live Workout Sheet (`@+id/run_bottom_sheet`)
- `CoordinatorLayout` + `BottomSheetBehavior` container (`@+id/run_sheet_host` / `@+id/run_bottom_sheet`).
- Background: `bg_run_bottom_sheet` (`#141414` dark / `#fafafa` light).

#### Collapsed Peek Banner (~64dp height)
- **Drag Handle Bar:** Centered pill handle (`36dp` × `4dp`, `#484848`).
- **Intensity Badge (`@+id/step_intensity_badge`):** Color-coded pill (`[WARMUP]`, `[ACTIVE]`, `[RECOVERY]`, `[COOLDOWN]`).
- **Active Step Progress Title (`@+id/active_step_title`):** e.g., `Step 3/6: 400m remaining` or `01:30 left`.
- **Target Indicator (`@+id/active_step_target_text`):** e.g. `Target: 4:45 - 5:00 /km`.
- **Step Progress Bar (`@+id/active_step_progress_bar`):** Horizontal progress indicator filled with brand accent gradient (`#fab283` to `#5c9cf5`).

#### Expanded Drawer Content
- `RecyclerView` (`@+id/workout_list`): Full workout step list using modern step cards:
  - Active step highlighted with container outline (`?attr/colorPrimary`) and bold typography.
  - Completed steps dimmed (`opacity=0.5`) with a checkmark indicator.
  - Upcoming steps cleanly formatted.

### 2.4 Bottom Action Dock (`@+id/run_table_row1`)
- Floating horizontal pill bar positioned at the bottom of the screen:
  - `pause_button`: `ExtendedFloatingActionButton` (Pause / Resume) with background tint `#fab283` (Dark) / `#3b7dd8` (Light) and bold text `#0a0a0a` (Dark) / `#ffffff` (Light).
  - `next_lap_button`: `ExtendedFloatingActionButton` (Next Lap / Hold to Stop) with outlined surface styling `#1e1e1e` (Dark) and border `#484848`.

---

## 3. Color Palette Mapping (`opencode.json`)

| Element | Dark Mode Color | Light Mode Color | Description / Token |
| :--- | :--- | :--- | :--- |
| **Screen Background** | `#0a0a0a` (`darkStep1`) | `#ffffff` (`lightStep1`) | Base canvas |
| **Stats HUD Background** | `#1e1e1e` (`darkStep3`) | `#ffffff` (`lightStep1`) | Surface Container |
| **Stats HUD Border** | `#3c3c3c` (`darkStep6`) | `#d4d4d4` (`lightStep6`) | Outline Variant |
| **Hero Metric Distance** | `#eeeeee` (`darkStep12`) | `#1a1a1a` (`lightStep12`) | Text Primary |
| **Hero Metric Unit** | `#fab283` (`darkStep9`) | `#3b7dd8` (`lightStep9`) | Primary Accent |
| **Hero Metric Time** | `#5c9cf5` (`darkSecondary`) | `#7b5bb6` (`lightSecondary`) | Secondary Accent |
| **HR Zone 1 / 2 (Easy)** | `#7fd88f` (`darkGreen`) | `#3d9a57` (`lightGreen`) | HR Zone Badge |
| **HR Zone 3 (Aerobic)** | `#e5c07b` (`darkYellow`) | `#b0851f` (`lightYellow`) | HR Zone Badge |
| **HR Zone 4 (Threshold)** | `#f5a742` (`darkOrange`) | `#d68c27` (`lightOrange`) | HR Zone Badge |
| **HR Zone 5 (Anaerobic)**| `#e06c75` (`darkRed`) | `#d1383d` (`lightRed`) | HR Zone Badge |
| **Primary Action FAB** | `#fab283` (`darkStep9`) | `#3b7dd8` (`lightStep9`) | Pause/Resume FAB |
| **Secondary Action FAB** | `#1e1e1e` (`darkStep3`) | `#f5f5f5` (`lightStep3`) | Next Lap FAB |
| **GPS Track Line** | `#5c9cf5` (`darkSecondary`) | `#3b7dd8` (`lightStep9`) | Map Route Line |

---

## 4. Dynamic Behaviors & Data Flow (`RunActivity.java`)

1. **Timer Loop Updates (`updateView()`):**
   - Calculates Elapsed Time (`Scope.ACTIVITY`), Distance (`Scope.ACTIVITY`), Pace (`Scope.ACTIVITY`).
   - Updates Hero Distance and Time TextViews with auto-sizing enabled.
   - Updates Secondary Grid TextViews for Lap Distance, Lap Pace, and Current HR.
   - Computes Heart Rate Zone based on user max HR settings and applies matching color tint + zone tag (`Z1`–`Z5`) to `@+id/hr_zone_pill`.

2. **Active Interval Progress Tracker:**
   - Obtains `Step curr = workout.getCurrentStep()`.
   - Computes elapsed distance/time fraction within the active step.
   - Updates `@+id/active_step_progress_bar` smooth progress value (`0–100%`).
   - Rebinds active step title, remaining metric, and intensity pill badge.

3. **Workout List Adapter (`WorkoutAdapter`):**
   - Automatically scrolls the current active step into view upon step transition.
   - Sets container background and text weight depending on step state (Active, Completed, Upcoming).

---

## 5. Edge Cases & Constraints

- **Maps Disabled / No Map Token (`nomap` variant):**
  - `@+id/run_mapview` stays `GONE`.
  - The HUD card and bottom sheet anchor against the dark theme background gracefully without shift or crash.
- **Heart Rate Monitor Disconnected:**
  - `@+id/current_hr` and `@+id/hr_zone_pill` smoothly set visibility to `GONE`.
  - The secondary metric grid collapses to 3 metrics cleanly.
- **Large Screens / Tablets / Landscape:**
  - Stats HUD card is constrained with `android:maxWidth="600dp"` and centered horizontally to maintain compact glancability.

---

## 6. Verification Plan

1. **Unit Tests:** Run `./gradlew test` to ensure tracker state calculations and workout step progression tests pass.
2. **Lint Check:** Run `./gradlew :app:lintLatestDebug` to ensure no new fatal lint issues are introduced.
3. **Spotless Formatting:** Run `./gradlew spotlessApply && ./gradlew spotlessCheck`.
4. **Build Compilation:** Run `./gradlew :app:assembleLatestDebug`.
5. **On-Device Smoke Test:**
   - Launch run activity on device.
   - Verify top floating HUD card, metrics auto-updating, active interval progress bar in bottom sheet, and action dock FABs.
