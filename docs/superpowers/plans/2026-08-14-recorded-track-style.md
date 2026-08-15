# Match Recorded Track Style to Live Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the recorded activity track on the DetailActivity "Map" tab use the same theme-aware colors as the live recording map's track (day blue `#3B7DD8` + white edge, night orange `#FAB283` + near-black edge).

**Architecture:** A single-file change in the osmdroid map flavor: `MapWrapper` computes night-mode from the device config (mirroring `LiveMap.isNightMode()`), then colors its edge/track polylines with `MapTheme.edgeColor(isNight)`/`MapTheme.routeColor(isNight)` instead of the fixed `#FFB680`/`#FF6D00`. The mapbox flavor already matches (both live and recorded draw red @3.0f) and is untouched. Basemap tiles stay MAPNIK (user chose track-only).

**Tech Stack:** Java, osmdroid `Polyline`, Material/AndroidX app.

## Global Constraints

- Verify with: `./gradlew test`, `./gradlew :app:lintLatestDebug` (no NEW issues beyond the 25 in `app/lint-baseline.xml`), `./gradlew spotlessApply` + `spotlessCheck`, `./gradlew :app:assembleLatestDebug`, and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap` (build the map variant LAST — nomap overwrites the APK).
- No code comments. Google Java Format via spotless.
- `MapWrapper` lives in `app/src/osmdroid/org/runnerup/util/` (do NOT touch the mapbox or nomap variants in `app/src/mapbox` / `app/src/nomap`).
- `MapTheme` is in the same package `org.runnerup.util` — no import needed.
- Conventional commits: `feat:` / `style:` / `docs:`.
- Do NOT stage user-local files: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- Spec: `docs/superpowers/specs/2026-08-14-recorded-track-style-design.md`.

---

### Task 1: Make recorded track colors theme-aware

**Files:**
- Modify: `app/src/osmdroid/org/runnerup/util/MapWrapper.java:60-61,82-124,127-130`

**Interfaces:**
- Consumes: `MapTheme.routeColor(boolean)` / `MapTheme.edgeColor(boolean)` (`app/src/osmdroid/org/runnerup/util/MapTheme.java:22-28`); `MapView` from the existing constructor.
- Produces: a `MapWrapper.onCreate` that picks colors from `MapTheme` based on device night mode. Task 2 consumes nothing from this task's code.

- [ ] **Step 1: Delete the fixed color constants**

In `app/src/osmdroid/org/runnerup/util/MapWrapper.java`, remove these two lines (currently lines 60-61):

```java
  private static final int TRACK_COLOR = Color.parseColor("#FF6D00");
  private static final int TRACK_EDGE_COLOR = Color.parseColor("#FFB680");
```

Keep `TRACK_WIDTH_PX`, `TRACK_EDGE_WIDTH_PX`, and `MARKER_DIAMETER_PX`. These two lines are the ONLY uses of `android.graphics.Color` in the file (the `Color.parseColor` calls at lines 60-61; confirmed no other `Color.` reference exists in `MapWrapper.java`), so also remove the now-unused import line `import android.graphics.Color;`.

- [ ] **Step 2: Add the night-mode helper**

Add this private method (place it after `onCreate`, before `loadRouteAsync`), mirroring `LiveMap.isNightMode()` exactly:

```java
  private boolean isNightMode() {
    return (mapView.getContext().getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
  }
```

Do NOT add `import android.content.res.Configuration;` — `org.osmdroid.config.Configuration` is already imported (used at line 87) and the android one is fully-qualified inline to avoid the clash.

- [ ] **Step 3: Thread `isNight` through to the polyline creation**

In `onCreate`, change:

```java
    loadRouteAsync();
```

to:

```java
    loadRouteAsync(isNightMode());
```

Change the `loadRouteAsync` signature and its `loadRouteData` call:

```java
  private void loadRouteAsync(boolean isNight) {
    executor.execute(
        () -> {
          final Route route = loadRouteData(isNight);
```

and

```java
  private Route loadRouteData(boolean isNight) {
    Polyline edge = newPolyline(MapTheme.edgeColor(isNight), TRACK_EDGE_WIDTH_PX);
    Polyline track = newPolyline(MapTheme.routeColor(isNight), TRACK_WIDTH_PX);
```

The rest of `loadRouteData` is unchanged. This is the only place the two colors were used (previously `newPolyline(TRACK_EDGE_COLOR, TRACK_EDGE_WIDTH_PX)` / `newPolyline(TRACK_COLOR, TRACK_WIDTH_PX)`).

- [ ] **Step 4: Verify the change is complete**

Run and confirm there are no leftover references to the deleted constants:

```bash
rg -n "TRACK_COLOR|TRACK_EDGE_COLOR" app/src/osmdroid
```
Expected: no matches.

Also confirm `MapTheme.routeColor` / `MapTheme.edgeColor` are now used by both `MapWrapper` and `LiveMap`:
```bash
rg -n "MapTheme\.(routeColor|edgeColor)" app/src/osmdroid
```
Expected: matches in both `MapWrapper.java` and `LiveMap.java`.

- [ ] **Step 5: Run the gate suite**

Run in order:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply && ./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```
Expected: tests pass, lint shows no new issues (25 baseline-filtered), spotless clean, both assemble variants BUILD SUCCESSFUL. Note: spotlessApply may reflow your edits (e.g., wrap lines) — that's fine; re-run spotlessCheck after.

- [ ] **Step 6: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/MapWrapper.java
git commit -m "feat: make recorded track colors match live map"
```

---

### Task 2: Verify recorded map colors on device

**Files:** (none — verification only)

**Interfaces:**
- Consumes: the change from Task 1 (assemble the APK again if the task-1 build is stale: run `./gradlew :app:assembleLatestDebug` — map variant — LAST, then `adb install -r` the fresh APK).

- [ ] **Step 1: Install and open a recorded activity's Map tab**

Device: Nexus 5X serial `025b46e24edcbca6` (connected), app package `org.runnerup.debug`. The device DB already contains 6 activities WITH location rows (activity `_id=1` has 14864 points — use it).

```bash
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.DetailActivity --ei ID 1 --es mode details
```

If the screen is locked, stop and ask the controller. Find and tap the "Map" tab: use `adb -s 025b46e24edcbca6 shell uiautomator dump` (worked on this device in prior sessions) to get the Map tab's bounds, then tap its center. If uiautomator fails, ask the controller for tap coordinates.

- [ ] **Step 2: Capture day-mode screenshot + sample the track color**

```bash
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/recorded_map_day.png
```

Sample the dominant non-background color of the track area. Prefer ImageMagick if available, else python3 + PIL:
```bash
# ImageMagick: dominant colors
convert /tmp/opencode/recorded_map_day.png -resize 50% -colors 8 -format %c histogram:info:-
```
or
```bash
python3 -c "from PIL import Image; im=Image.open('/tmp/opencode/recorded_map_day.png').convert('RGB'); im=im.resize((im.width//3, im.height//3)); from collections import Counter; print(Counter(im.getdata()).most_common(8))"
```
Expected: the track color `#3B7DD8` (RGB 59,125,216) appears among the top colors. (The white edge `#FFFFFF` and MAPNIK tile colors also appear — that's fine.)

- [ ] **Step 3: Toggle night mode, capture + sample again**

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night yes
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.DetailActivity --ei ID 1 --es mode details
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/recorded_map_night.png
```
Sample as in Step 2. Expected: track color `#FAB283` (RGB 250,178,131) appears among the top colors; near-black edge `#0A0A0A`.

Then restore:
```bash
adb -s 025b46e24edcbca6 shell cmd uimode night no
```

- [ ] **Step 4: Report**

Write to `/home/megadoro/local/runnerup/.superpowers/sdd/2026-08-14-recorded-track-style/progress.md` (create it with a first line `# SDD ledger — plan: docs/superpowers/plans/2026-08-14-recorded-track-style.md` if it does not exist), appending:
- `Task 2: complete (verification only, no commit)` + the observed dominant colors (RGB triplets) for day and night, screenshot paths, and gate results.
- No commit for this task.
