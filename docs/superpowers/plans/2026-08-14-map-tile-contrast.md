# Map Tile Contrast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the CARTO tiles on the osmdroid recording map easier to read by applying a per-mode color contrast filter to the tile layer.

**Architecture:** Add two raw `float[20]` ColorMatrix constants to `MapTheme` (pure data, JVM-safe for unit tests). In `LiveMap.onCreate`, apply the matching `ColorMatrixColorFilter` to the tiles overlay via osmdroid's supported `TilesOverlay.setColorFilter` hook (applied to every tile at draw time). Retune `mapBackground` so the unfiltered canvas matches the filtered tile tone at edges.

**Tech Stack:** Java 17, AndroidX, osmdroid 6.1.x, JUnit 4, Gradle 9.6.1 (wrapper).

## Global Constraints

- Day matrix = scale-from-white `c=1.3` (translation `255*(1-c) = -76.5f`); Night matrix = scale-from-black `c=1.8` (translation `0`).
- Both matrices are identity except the R/G/B diagonal scale `> 1`; alpha row untouched (`[15]=[16]=[17]=0, [18]=1, [19]=0`); all cross terms 0.
- Filter touches the tile layer only — route, markers, attribution unchanged.
- `mapBackground`: day `#f5f5f5` → `#eeeeee`, night `#0a0a0a` → `#121212`.
- No new dependencies. No comments added to code unless asked.
- Verify gates in order before finishing: `./gradlew test`, `:app:lintLatestDebug` (25 baseline-filtered issues allowed, no new), `spotlessApply` then `spotlessCheck`, `:app:assembleLatestDebug` then `:app:assembleLatestDebug -Porg.runnerup.nomap`.
- Conventional commits (`feat:`, `test:`, `style:`).
- Do NOT stage user-local files: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.

---

### Task 1: Filter matrix constants in MapTheme + unit tests

**Files:**
- Modify: `app/src/osmdroid/org/runnerup/util/MapTheme.java:7-25`
- Test: `app/test/java/org/runnerup/util/MapThemeTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public static final float[] MapTheme.DAY_TILE_MATRIX` (length 20), `public static final float[] MapTheme.NIGHT_TILE_MATRIX` (length 20). Task 2 constructs `new ColorMatrix(MapTheme.DAY_TILE_MATRIX)` / `new ColorMatrix(MapTheme.NIGHT_TILE_MATRIX)` from these.

- [ ] **Step 1: Write the failing tests**

Append to `app/test/java/org/runnerup/util/MapThemeTest.java`:

```java
  private static void assertIdentityExceptScale(float[] m, float scale) {
    assertEquals(20, m.length);
    for (int i = 0; i < 20; i++) {
      switch (i) {
        case 0:
        case 6:
        case 12:
          assertEquals(scale, m[i], 0f);
          break;
        case 18:
          assertEquals(1f, m[i], 0f);
          break;
        default:
          assertEquals(0f, m[i], 0f);
      }
    }
  }

  @Test
  public void dayMatrixScalesFromWhite() {
    float[] m = MapTheme.DAY_TILE_MATRIX;
    assertIdentityExceptScale(m, 1.3f);
    assertEquals(255f * (1f - 1.3f), m[4], 0.001f);
    assertEquals(255f * (1f - 1.3f), m[9], 0.001f);
    assertEquals(255f * (1f - 1.3f), m[14], 0.001f);
  }

  @Test
  public void nightMatrixScalesFromBlack() {
    float[] m = MapTheme.NIGHT_TILE_MATRIX;
    assertIdentityExceptScale(m, 1.8f);
    assertEquals(0f, m[4], 0f);
    assertEquals(0f, m[9], 0f);
    assertEquals(0f, m[14], 0f);
  }

  @Test
  public void matricesIncreaseContrast() {
    assertTrue(MapTheme.DAY_TILE_MATRIX[0] > 1f);
    assertTrue(MapTheme.NIGHT_TILE_MATRIX[0] > 1f);
  }
```

Add the missing import `import static org.junit.Assert.assertTrue;`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol` for `DAY_TILE_MATRIX` / `NIGHT_TILE_MATRIX`.

- [ ] **Step 3: Implement the constants**

Add to `MapTheme.java` (after the `CARTO_LIGHT_BASE` line):

```java
  public static final float[] DAY_TILE_MATRIX = {
    1.3f, 0, 0, 0, -76.5f,
    0, 1.3f, 0, 0, -76.5f,
    0, 0, 1.3f, 0, -76.5f,
    0, 0, 0, 1, 0
  };

  public static final float[] NIGHT_TILE_MATRIX = {
    1.8f, 0, 0, 0, 0,
    0, 1.8f, 0, 0, 0,
    0, 0, 1.8f, 0, 0,
    0, 0, 0, 1, 0
  };
```

(`-76.5f` = `255f * (1f - 1.3f)`; write it as the literal.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test`
Expected: PASS — all `MapThemeTest` cases green.

- [ ] **Step 5: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/MapTheme.java app/test/java/org/runnerup/util/MapThemeTest.java
git commit -m "feat: add day/night tile contrast matrices to MapTheme"
```

---

### Task 2: Apply filter in LiveMap + retune mapBackground

**Files:**
- Modify: `app/src/osmdroid/org/runnerup/util/LiveMap.java:5-9,77-83`
- Modify: `app/res/values/colors.xml:42`
- Modify: `app/res/values-night/colors.xml:6`

**Interfaces:**
- Consumes: `MapTheme.DAY_TILE_MATRIX`, `MapTheme.NIGHT_TILE_MATRIX` (Task 1).
- Produces: nothing new; behavior change only.

- [ ] **Step 1: Apply the tile color filter**

In `app/src/osmdroid/org/runnerup/util/LiveMap.java`, add imports:

```java
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
```

In `onCreate`, immediately after `mapView.setBackgroundColor(...)` (line 80):

```java
    mapView
        .getOverlayManager()
        .getTilesOverlay()
        .setColorFilter(
            new ColorMatrixColorFilter(
                new ColorMatrix(isNight ? MapTheme.NIGHT_TILE_MATRIX : MapTheme.DAY_TILE_MATRIX)));
```

- [ ] **Step 2: Retune mapBackground**

`app/res/values/colors.xml:42`: `#f5f5f5` → `#eeeeee`.
`app/res/values-night/colors.xml:6`: `#0a0a0a` → `#121212`.

- [ ] **Step 3: Run tests + lint + spotless + assemble**

Run in order:

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: all pass; lint reports no new issues (25 filtered by `lint-baseline.xml`); spotless makes no unexpected reformat of `LiveMap.java`.

- [ ] **Step 4: Commit**

```bash
git add app/src/osmdroid/org/runnerup/util/LiveMap.java app/res/values/colors.xml app/res/values-night/colors.xml
git commit -m "feat: apply day/night tile contrast filter on recording map"
```

---

### Task 3: On-device day/night verification

**Files:**
- Evidence: `/tmp/opencode/*.png` screenshots (not committed)
- Modify: `.superpowers/sdd/2026-08-14-map-tile-contrast/task-3-report.md` (create) and `progress.md` (create ledger)

**Interfaces:**
- Consumes: Task 2 APK (`app/build/outputs/apk/latest/debug/app-latest-debug.apk`, osmdroid build — build map variant LAST so nomap does not overwrite it).

- [ ] **Step 1: Install and launch**

Device: Nexus 5X serial `025b46e24edcbca6`. Ensure day mode, install, launch:

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night no
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Navigate to the run screen (Start tab → Treadmill → confirm GPS → Start). Wait for tiles to load.

- [ ] **Step 2: Probe day pixel tones**

```bash
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/contrast_day.png
```

Using the raw PNG, probe: an interior background pixel (expect ~`#EE`), a road pixel (expect clearly darker than bg, ~`#B7`), a label pixel (expect near-black). Verify bg is `#EE` ±6 and road is at least 25 points darker than bg.

- [ ] **Step 3: Probe night pixel tones**

```bash
adb -s 025b46e24edcbca6 shell cmd uimode night yes
```

Restart the app (force-stop + relaunch) so RunActivity recreates with night mode, re-navigate to the run screen, wait for tiles:

```bash
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/contrast_night.png
```

Probe: interior background pixel (expect ~`#12`), a road pixel (expect clearly lighter than bg, ~`#36`), a label pixel (expect bright, ~`#D6`). Verify bg is `#12` ±6 and road is at least 25 points lighter than bg.

- [ ] **Step 4: Compare against pre-change baseline**

Re-probe a few identical map coordinates on the pre-fix screenshots (`/tmp/opencode/fix_final_day.png`, `fix_final_night.png`) and confirm the new bg/road separation is visibly larger (road-vs-bg gap roughly doubled for day, substantially larger for night).

- [ ] **Step 5: Record results**

Write the tone table (before/after per mode) into `.superpowers/sdd/2026-08-14-map-tile-contrast/task-3-report.md` and a one-line ledger in `.superpowers/sdd/2026-08-14-map-tile-contrast/progress.md` (this directory stays untracked, like the palette-map plan's ledger). If any target is missed, adjust the matrix scale in `MapTheme` (±0.1), re-run Task 2's gates and Steps 2-3, and note the deviation. Report the tone table to the user. No commit in this task — no source changes.

---

## Self-Review Notes

- **Spec coverage:** matrices (T1), filter application (T2), background retune (T2), tests (T1), on-device probes + gates (T2/T3). Route/markers/attribution untouched. ✓
- **Type consistency:** constants named `DAY_TILE_MATRIX`/`NIGHT_TILE_MATRIX` throughout; `ColorMatrix(float[])` + `ColorMatrixColorFilter(ColorMatrix)` used consistently in Task 2.
- **Background side effect:** `mapBackground` is referenced only in osmdroid `LiveMap.onCreate` (verified by grep) — DetailActivity's MAPNIK tab is unaffected.
