# Sport Count Badges on History Filter Chips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each History sport filter chip's activity count as a small sport-colored pill inside the chip, replacing the standalone "N activities" text.

**Architecture:** Render each pill as a custom `ReplacementSpan` appended to the chip's label in the text flow, so it positions itself after the label with no manual coordinates. A pure, unit-testable helper decides whether a chip gets a pill and what count it shows, consuming the existing `Statistics.sportCounts()` per-sport array. Colors are resolved in the fragment (per-sport from `Sport.colorOf`, All-sports from a fixed grey). Badge refresh is wired into the existing chip-selection and list-reload callbacks.

**Tech Stack:** Java + AndroidX (Material Chip). GPLv3. Test framework: JUnit 4 (`app/test/java`, non-standard root; `sourceSets` root is `test`).

## Global Constraints

- No code comments unless already present / explicitly requested.
- googleJavaFormat governs formatting; `./gradlew spotlessApply` then `spotlessCheck` — CI gates on `spotlessCheck`.
- `:app:lintLatestDebug` must introduce NO new issues beyond the pre-existing `app/lint-baseline.xml` (25 issues); `app/lint.xml` promotes `InlinedApi`/`InconsistentArrays` to fatal.
- Gate order: `./gradlew test` → `./gradlew :app:lintLatestDebug` → `./gradlew spotlessApply` then `spotlessCheck` → `./gradlew :app:assembleLatestDebug`.
- `app` uses its own non-transitive `org.runnerup.R`; resources added to `app/res` are referenced as `org.runnerup.R.xxx`. Sport DB values are contiguous `0..7` (`Constants.DB.ACTIVITY.SPORT_MAX = 7`). `Sport.getStringArray(getResources())` returns an 8-entry array indexed by DB value. `Statistics.sportCounts(SQLiteDatabase)` returns `int[SPORT_MAX + 1]` indexed by sport DB value = all-time non-deleted count. `Sport.colorOf(int dbValue)` returns a color resource id from `org.runnerup.R`.
- Existing sport colors (`app/res/values/colors.xml`): running `#859900`, biking `#cb4b16`, orienteering `#d33682`, walking `#6c71c4`, other `#b58900`, treadmill `#2aa198`, gym `#dc322f`, stationary bike `#268bd2`. The new All-sports grey `#586e75` does not collide with any.

---

### Task 1: Pill decision helper (TDD)

**Files:**
- Create: `app/src/main/org/runnerup/view/SportCountBadge.java`
- Create: `app/test/java/org/runnerup/view/SportCountBadgeTest.java`

**Interfaces:**
- Consumes: none (pure logic, no Android deps, no color knowledge).
- Produces: `SportCountBadge.Badge` — nested public static final class with `public final int count` and `public Badge(int count)` constructor; `SportCountBadge.forSport(Integer sport, int[] counts)` → nullable `Badge`; `SportCountBadge.grandTotal(int[] counts)` → `int`.

**Behavior contract:**
- `sport == null` (All sports) → `Badge(count = grandTotal(counts))`, always non-null.
- `sport != null`: `count = counts[sport]`; return non-null `Badge` **only if** `count > 0`; else `null`.
- Bounds safety: if `sport` is negative or `>= counts.length`, return `null` (no pill), never throw.
- `grandTotal(counts)` = sum of all entries; empty array → 0.

**Design note:** `forSport` takes an `Integer` so `null` cleanly represents the All-sports chip. Colors are NOT part of this helper — the fragment resolves them.

- [ ] **Step 1: Write the failing tests**

Create `app/test/java/org/runnerup/view/SportCountBadgeTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.view.SportCountBadge.Badge;

public class SportCountBadgeTest {
  @Test
  public void allSportsShowsGrandTotal() {
    Badge badge = SportCountBadge.forSport(null, new int[] {30, 1});
    assertEquals(31, badge.count);
  }

  @Test
  public void sportShowsItsCount() {
    Badge badge = SportCountBadge.forSport(0, new int[] {30, 1});
    assertEquals(30, badge.count);
  }

  @Test
  public void zeroCountSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(1, new int[] {30, 0}));
  }

  @Test
  public void negativeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(-1, new int[] {30}));
  }

  @Test
  public void outOfRangeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(8, new int[] {30}));
  }

  @Test
  public void grandTotalSumsAllEntries() {
    assertEquals(31, SportCountBadge.grandTotal(new int[] {30, 1, 0}));
  }

  @Test
  public void grandTotalOfEmptyIsZero() {
    assertEquals(0, SportCountBadge.grandTotal(new int[0]));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.runnerup.view.SportCountBadgeTest"`
Expected: FAIL to compile — `SportCountBadge` class does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/view/SportCountBadge.java`:

```java
package org.runnerup.view;

public final class SportCountBadge {
  private SportCountBadge() {}

  public static final class Badge {
    public final int count;

    public Badge(int count) {
      this.count = count;
    }
  }

  public static int grandTotal(int[] counts) {
    int total = 0;
    for (int count : counts) {
      total += count;
    }
    return total;
  }

  public static Badge forSport(Integer sport, int[] counts) {
    if (sport == null) {
      return new Badge(grandTotal(counts));
    }
    if (sport < 0 || sport >= counts.length) {
      return null;
    }
    int count = counts[sport];
    return count > 0 ? new Badge(count) : null;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.runnerup.view.SportCountBadgeTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Spotless + commit**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: spotlessCheck PASS.

```bash
git add app/src/main/org/runnerup/view/SportCountBadge.java app/test/java/org/runnerup/view/SportCountBadgeTest.java
git commit -m "feat: sport count badge decision helper"
```

---

### Task 2: PillSpan replacement span

**Files:**
- Create: `app/src/main/org/runnerup/view/PillSpan.java`

**Interfaces:**
- Consumes: nothing (pure android.graphics span; instantiated by Task 3).
- Produces: `PillSpan extends android.text.style.ReplacementSpan` with constructor `PillSpan(int colorArgb, float density)`; the span text is the decimal count string.

**Geometry contract (must be internally consistent between `getSize` and `draw`):**
- The span's horizontal box covers exactly the pill: `width = paint.measureText(value) + 2 * padPx` (Symmetrical padding, no extra gap — the caller appends a literal space before the span in the label).
- The pill is drawn as a rounded rectangle insets: `left = x + padPx`, `right = left + measureText(value) + 2 * padPx`; that matches the `getSize` width when `v` is the number string.
- Vertical: pill is vertically centered in the line box. Height = `bottom - top` (the line box passed to draw). Radius `rx = (bottom - top) / 2`. Vertical center = `(top + bottom) / 2`.
- Number is centered horizontally and vertically within the pill: `paint.setTextAlign(Paint.Align.CENTER)`; draw at `x + width/2`; baseline `y` such that glyphs center at the pill's vertical center: `baseline = (top + bottom) / 2 - (fm.ascent + fm.descent) / 2`, where `fm = paint.getFontMetrics()`.
- `getSize` must return the same width that `draw` paints: `Math.round(paint.measureText(value) + 2 * padPx)`. It should NOT mutate `fm` (leave the line metrics untouched — the pill draws explicitly using `top/bottom`).
- Colors: pill fill = `color`, number = `0xFFFFFFFF`.

- [ ] **Step 1: Write the class**

Create `app/src/main/org/runnerup/view/PillSpan.java`:

```java
package org.runnerup.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

public class PillSpan extends ReplacementSpan {
  private static final int COLOR_WHITE = 0xFFFFFFFF;
  private static final float PADDING_DP = 6f;

  private final int color;
  private final float density;

  public PillSpan(int color, float density) {
    this.color = color;
    this.density = density;
  }

  private float padPx() {
    return PADDING_DP * density;
  }

  @Override
  public int getSize(
      @NonNull Paint paint,
      CharSequence text,
      int start,
      int end,
      FontMetricsInt fm) {
    String value = text.subSequence(start, end).toString();
    return Math.round(paint.measureText(value) + 2 * padPx());
  }

  @Override
  public void draw(
      @NonNull Canvas canvas,
      CharSequence text,
      int start,
      int end,
      float x,
      int top,
      int y,
      int bottom,
      @NonNull Paint paint) {
    String value = text.subSequence(start, end).toString();
    float width = paint.measureText(value) + 2 * padPx();
    float centerY = (top + bottom) / 2f;
    float radius = (bottom - top) / 2f;
    int saveColor = paint.getColor();

    paint.setColor(color);
    canvas.drawRoundRect(x, centerY - radius, x + width, centerY + radius, radius, radius, paint);
    paint.setColor(COLOR_WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics fm = paint.getFontMetrics();
    float baseline = centerY - (fm.ascent + fm.descent) / 2f;
    canvas.drawText(value, x + width / 2f, baseline, paint);

    paint.setTextAlign(Paint.Align.LEFT);
    paint.setColor(saveColor);
  }
}
```

Note: pill vertical padding is intentionally 0 relative to the line box — the pill fills the chip's line height edge-to-edge and relies on the chip's own label padding for visual air. If it looks cramped on device (Task 5), reduce to `radius = (bottom - top) / 2f - 1dp` and inset `top`/`bottom` correspondingly in both `getSize` (via `fm`) and `draw`; do NOT change only one of the two.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileLatestDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spotless + commit**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: spotlessCheck PASS.

```bash
git add app/src/main/org/runnerup/view/PillSpan.java
git commit -m "feat: pill span for chip count badges"
```

---

### Task 3: Wire badges into the chip row

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java` (chip-creation block ~171-184; `updateSportCount` ~407-432; field `sportCountText` at ~105, assignment at ~159)
- Modify: `app/res/layout/history.xml` (remove `history_sport_count` TextView, lines 76-82)
- Modify: `app/res/values/colors.xml` (add `historyBadgeAllSports`)
- Modify (conditional): `common/src/main/res/values/strings.xml` (remove `Statistics_activities_count` if lint flags it unused)
- Test: `app/test/java/org/runnerup/view/SportCountBadgeTest.java` (extend with badge-color helper test — see Step 2)

**Interfaces:**
- Consumes: `SportCountBadge.forSport(Integer, int[])` → `SportCountBadge.Badge`/`null`, `SportCountBadge.Badge.count` (Task 1); `PillSpan(int, float)` (Task 2); `Statistics.sportCounts(SQLiteDatabase)` → `int[]`; `Sport.colorOf(int)` → `org.runnerup.R.color` resource id.
- Produces: private `refreshSportBadges()`, `applySportBadges(int[])`, and `buildChipText(String, int, int)`.

**Logic:**

1. In `onViewCreated`, capture the chips in order as they are created. Add a field:

```java
private final List<Chip> sportChips = new ArrayList<>();
```

2. In the chip-creation block (existing code adds the All-sports chip then a per-sport chip in a loop, via `chipGroup.addView(...)`), additionally append each chip to `sportChips` immediately after its `addView` — All-sports chip first (tag `null`), then each sport chip in DB-value order. The chip's tag stays the source of truth (`null` = All sports, else the sport DB value).

3. Substitute `updateSportCount()` (lines 407-432) with `refreshSportBadges()` running the count query off-thread (same executor/handler pattern as the current method) plus `applySportBadges(int[])` + `buildChipText(String, int, int)`:

```java
private void refreshSportBadges() {
  if (mDB == null) {
    return;
  }
  statisticsExecutor.execute(
      () -> {
        int[] counts = Statistics.sportCounts(mDB);
        mainHandler.post(
            () -> {
              applySportBadges(counts);
            });
      });
}

private void applySportBadges(int[] counts) {
  int allSportsColor =
      ContextCompat.getColor(
          requireContext(), org.runnerup.R.color.historyBadgeAllSports);
  for (int i = 0; i < sportChips.size(); i++) {
    Chip chip = sportChips.get(i);
    Integer sport = (Integer) chip.getTag();
    SportCountBadge.Badge badge = SportCountBadge.forSport(sport, counts);
    if (badge == null) {
      chip.setText(chip.getText().toString());
      continue;
    }
    int pillColor =
        sport == null
            ? allSportsColor
            : ContextCompat.getColor(requireContext(), Sport.colorOf(sport));
    chip.setText(
        buildChipText(
            chip.getText().toString(), pillColor, badge.count,
            getResources().getDisplayMetrics().density));
  }
}

private CharSequence buildChipText(String label, int pillColor, int count, float density) {
  SpannableStringBuilder sb = new SpannableStringBuilder(label);
  sb.append(" ");
  int start = sb.length();
  sb.append(Integer.toString(count));
  int end = sb.length();
  sb.setSpan(new PillSpan(pillColor, density), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  return sb;
}
```

   The label passed to `buildChipText` is the chip's **current text without any prior pill span** so re-refresh does not nest spans. Because the current text carries the label plus any previously-set pill, obtain the plain label once per chip: set it explicitly from a stored plain-label list (see step 4) rather than reading it back from the chip after spans were set.

4. Store the plain label per chip at creation time so `applySportBadges` never rebuilds from a span-carrying string. Add a parallel field:

```java
private final List<String> sportChipLabels = new ArrayList<>();
```

   Populate it in the same loop as `sportChips` (All-sports chip: the "All sports" string; sport chips: `sportNames[dbValue]`). In `applySportBadges`, build from `sportChipLabels.get(i)` instead of `chip.getText().toString()`, and for the null-badge branch reset `chip.setText(sportChipLabels.get(i))`.

5. Replace the two `updateSportCount()` call sites (init ~207 and chip-selection ~205) with `refreshSportBadges()`.

6. Add `refreshSportBadges()` to `onLoadFinished` (after `adapter.setData(arg1)` at ~357) so badges refresh on every reload.

7. Remove the `sportCountText` field (line 105), its assignment (line 159), and its `setText` usage. Remove the `history_sport_count` TextView from `history.xml` (lines 76-82). The scroller `paddingEnd="8dp"` stays as-is.

8. Add the neutral grey color to `app/res/values/colors.xml` after the `sportStationaryBike` line:

```xml
<color name="historyBadgeAllSports">#586e75</color>
```

9. **Check imports in `HistoryFragment.java`:** `ContextCompat` is already imported (used at line 180). Add `java.util.ArrayList`, `java.util.List`, `android.text.SpannableStringBuilder`, `android.text.Spanned`. `Sport` (`org.runnerup.workout.Sport`) is already imported for chip creation.

**Extending the helper test (Step 2 below):** because color resolution lives in the fragment (resource ids), the pure helper stays color-free. Do NOT add color tests to `SportCountBadgeTest` — the existing 7 tests are sufficient for the helper.

- [ ] **Step 1: Add the All-sports color resource**

Edit `app/res/values/colors.xml`:

```xml
<color name="historyBadgeAllSports">#586e75</color>
```

- [ ] **Step 2: Implement wiring in `HistoryFragment.java`**

Add fields, populate lists, replace `updateSportCount`, swap call sites, add `onLoadFinished` call, remove `sportCountText`, add imports.

Run: `./gradlew :app:compileLatestDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Remove `history_sport_count` from `history.xml`**

Delete the `TextView` block (lines 76-82).

- [ ] **Step 4: Unit tests + lint**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug`
Expected: tests PASS (7 existing SportCountBadge tests + all pre-existing); lint reports NO new issues beyond the 25 baseline. If lint flags `Statistics_activities_count` as unused (its only reference was the removed `sportCountText.setText`), remove the `<plurals name="Statistics_activities_count">` block from `common/src/main/res/values/strings.xml` (lines 279-282) and re-run lint.

- [ ] **Step 5: Spotless + commit**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: spotlessCheck PASS.

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java app/res/layout/history.xml app/res/values/colors.xml
git commit -m "feat: sport count badges on history filter chips"
```

*(If `Statistics_activities_count` was removed in Step 4, include `common/src/main/res/values/strings.xml` in the `git add`.)*

---

### Task 4: Full gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full verification gate**

Run:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```
Expected: all PASS; lint shows no new issues beyond the baseline; spotlessCheck PASS.

- [ ] **Step 2: Commit any spotless fixes if made**

Only if `spotlessApply` changed files:

```bash
git add -A -- 'app/*' 'common/*'
git commit -m "style: format after badge wiring"
```

---

### Task 5: On-device smoke test

**Files:** none (verification only). **Requires** the debug APK from Task 4 and the test phone `6a6743fd` (Xiaomi `frost_eea`, package `org.runnerup.debug`).

- [ ] **Step 1: Install and launch**

```bash
adb -s 6a6743fd install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 6a6743fd shell am force-stop org.runnerup.debug
adb -s 6a6743fd shell monkey -p org.runnerup.debug -c android.intent.category.LAUNCHER 1
```

Navigate to History (Start tab).

- [ ] **Step 2: Verify pill behavior**

Use `adb shell uiautomator dump /sdcard/ui.xml` and `adb shell dumpsys activity top` to read state. The device DB has 31 non-deleted activities (30 running, 1 walking) — see the ledger from the History Screen Redesign smoke for the baseline state; verify counts against the current DB if it changed:
- All sports chip shows a grey pill with 31.
- Running chip shows a green pill with 30.
- Walking chip shows a purple pill with 1.
- Every other sport chip shows no pill (count 0).
- Tapping a chip still filters the list; its pill persists.

- [ ] **Step 3: Verify refresh on add**

Add a manual activity (Existing "Add manual entry" flow), return to History, and confirm the affected chip's pill count increments.

- [ ] **Step 4: Capture evidence**

Archive a screenshot and the `ui.xml` to `/tmp/opencode/smoke/` and record PASS/FAIL per bullet in the SDD ledger.

- [ ] **Step 5: Log results**

Append results to the ledger (`progress.md`). `git log --oneline -6` to confirm the commit set.

---

## Self-Review

**Spec coverage:**
- Pill inside chip, trailing label → Task 2 + 3 (PillSpan + buildChipText). ✔
- Zero count → no pill → Task 1 (`forSport` returns null) + Task 3 (null → plain label). ✔
- All-sports grey total pill → Task 1 (`grandTotal`) + Task 3 (`historyBadgeAllSports`). ✔
- Sport pills use sport color → Task 3 (`Sport.colorOf`). ✔
- Refresh on reload/selection/init → Task 3 (`onLoadFinished` + both call sites). ✔
- Remove standalone count text → Task 3. ✔
- Delete unused plural if orphaned → Task 3 Step 4. ✔
- TDD for decision helper → Task 1. ✔
- Gate → Task 4. ✔
- Smoke → Task 5. ✔

**Placeholder scan:** No TBD/TODO. All code blocks concrete. The PillSpan note about a possible 1dp inset is a conditional instruction, not a placeholder.

**Type consistency:** `SportCountBadge.forSport(Integer, int[])` → `Badge`/`null`; `Badge.count` (int); `grandTotal(int[])` → `int`; `PillSpan(int, float)`; `refreshSportBadges()`; `applySportBadges(int[])`; `buildChipText(String, int, int, float)` — consistent across tasks. No placeholder `Badge.color`; color resolution lives only in `applySportBadges`.

**Resources:** `historyBadgeAllSports` added to `app/res` → referenced as `org.runnerup.R.color.historyBadgeAllSports` (non-transitive R). Sport colors resolved via `org.runnerup.R` through `Sport.colorOf`.