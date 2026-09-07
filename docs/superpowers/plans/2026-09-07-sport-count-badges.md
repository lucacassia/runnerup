# Sport Count Badges on History Filter Chips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each History sport filter chip's activity count as a small sport-colored pill inside the chip, replacing the standalone "N activities" text.

**Architecture:** Render each pill as a custom `ReplacementSpan` appended to the chip's label in the text flow, so it positions itself after the label with no manual coordinates. A pure, unit-testable helper decides whether a chip gets a pill and what count/color it shows, consuming the existing `Statistics.sportCounts()` per-sport array. Badge refresh is wired into the existing chip-selection and list-reload callbacks.

**Tech Stack:** Java + AndroidX (Material Chip). GPLv3. Test framework: JUnit 4 (`app/test/java`, non-standard root; `sourceSets` root is `test`).

## Global Constraints

- No code comments unless already present / explicitly requested.
- googleJavaFormat governs formatting; `./gradlew spotlessApply` then `spotlessCheck` — CI gates on `spotlessCheck`.
- `:app:lintLatestDebug` must introduce NO new issues beyond the pre-existing `app/lint-baseline.xml` (25 issues); `app/lint.xml` promotes `InlinedApi`/`InconsistentArrays` to fatal.
- Gate order: `./gradlew test` → `./gradlew :app:lintLatestDebug` → `./gradlew spotlessApply` then `spotlessCheck` → `./gradlew :app:assembleLatestDebug`.
- Do NOT use `android:title` on MaterialToolbar (renders nothing); use `app:title` (already correct in `history.xml`).
- Sport DB values are contiguous `0..7` (`Constants.DB.ACTIVITY.SPORT_MAX = 7`). `Sport.getStringArray(getResources())` returns an 8-entry array indexed by DB value. `Statistics.sportCounts(SQLiteDatabase)` returns `int[SPORT_MAX + 1]` indexed by sport DB value = all-time non-deleted count.
- Pill colors come from `Sport.colorOf(int dbValue)` (a color-resource id, resolved via `ContextCompat.getColor`); All-sports uses a neutral grey.

---

### Task 1: Pill decision helper (TDD)

**Files:**
- Create: `app/src/main/org/runnerup/view/SportCountBadge.java`
- Create: `app/test/java/org/runnerup/view/SportCountBadgeTest.java`

**Interfaces:**
- Consumes: none (pure logic, no Android deps).
- Produces: `SportCountBadge.Badge` — fields `int count`, `int color`; `SportCountBadge.forSport(Integer sport, int[] counts, int allSportsColor)` → nullable `Badge`; `SportCountBadge.grandTotal(int[] counts)` → `int`.

**Behavior contract:**
- `sport == null` (All sports) → `Badge(count = grandTotal, color = allSportsColor)`, always non-null.
- `sport != null`: `count = counts[sport]`; return non-null `Badge` **only if** `count > 0`; else `null`.
- `grandTotal(counts)` = sum of all entries.
- Bounds safety: if `sport` is out of range of `counts` (negative or `>= counts.length`), return `null` (no pill), never throw.

**Design note:** `forSport` takes an `Integer` so `null` cleanly represents the All-sports chip; `Badge` is a plain `final` class with a `public` constructor. Put `SportCountBadge` and `Badge` in one file (nested static class `Badge`).

- [ ] **Step 1: Write the failing tests**

Create `app/test/java/org/runnerup/view/SportCountBadgeTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.view.SportCountBadge.Badge;

public class SportCountBadgeTest {
  private static final int GREY = 0xFF586E75;

  private static int[] counts(int... values) {
    return values;
  }

  @Test
  public void allSportsShowsGrandTotal() {
    Badge badge = SportCountBadge.forSport(null, counts(30, 1), GREY);
    assertEquals(31, badge.count);
    assertEquals(GREY, badge.color);
  }

  @Test
  public void allSportsColorPassedThrough() {
    Badge badge = SportCountBadge.forSport(null, counts(0), 0xFF123456);
    assertEquals(0xFF123456, badge.color);
  }

  @Test
  public void sportShowsItsCount() {
    Badge badge = SportCountBadge.forSport(0, counts(30, 1), GREY);
    assertEquals(30, badge.count);
  }

  @Test
  public void zeroCountSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(1, counts(30, 0), GREY));
  }

  @Test
  public void negativeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(-1, counts(30), GREY));
  }

  @Test
  public void outOfRangeSportHasNoBadge() {
    assertNull(SportCountBadge.forSport(8, counts(30), GREY));
  }

  @Test
  public void grandTotalSumsAllEntries() {
    assertEquals(31, SportCountBadge.grandTotal(counts(30, 1, 0)));
  }

  @Test
  public void grandTotalOfEmptyIsZero() {
    assertEquals(0, SportCountBadge.grandTotal(counts()));
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
    public final int color;

    public Badge(int count, int color) {
      this.count = count;
      this.color = color;
    }
  }

  public static int grandTotal(int[] counts) {
    int total = 0;
    for (int count : counts) {
      total += count;
    }
    return total;
  }

  public static Badge forSport(Integer sport, int[] counts, int allSportsColor) {
    if (sport == null) {
      return new Badge(grandTotal(counts), allSportsColor);
    }
    if (sport < 0 || sport >= counts.length) {
      return null;
    }
    int count = counts[sport];
    return count > 0 ? new Badge(count, 0) : null;
  }
}
```

Note: the `Badge` returned for a sport chip is created with a placeholder color `0` in Task 1 — Task 3 wires in `Sport.colorOf(sport)`. The unit tests for sport chips only assert `count`, so this is correct for now.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.runnerup.view.SportCountBadgeTest"`
Expected: PASS (8 tests).

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
- Produces: `PillSpan extends android.text.style.ReplacementSpan` with constructor `PillSpan(int colorArgb)`; the span text is the decimal count string.

**Behavior:**
- `getSize(Paint, CharSequence, int start, int end, FontMetricsInt)`: width = a blank-chars of advance (acts as the gap after the label) + the width of each char in the span text + horizontal padding `2 * rx` where `rx` is half the pill height. Height fills the line (sets `fm.top/fm.ascent/fm.descent/fm.bottom` if non-null so vertical metrics are preserved).
- `draw(Canvas, CharSequence, int start, int end, float x, int top, int y, int bottom, Paint)`: draw a rounded rectangle (`rx = lineHeight/2`) centered vertically in the line, filled with `colorArgb`; draw the text centered in `colorWhite` (`0xFFFFFFFF`).
- Use `dp` for padding via a density-independent computation: multiply by `getResources().getDisplayMetrics().density` — but the span gets `Paint` only, no Context. Resolve density lazily: read it in the constructor from a static display-metrics grab guarded by `Build.VERSION.SDK_INT`, or simpler, pass density into the constructor. The span is created in `HistoryFragment` which has a `Context`. **Constructor signature: `PillSpan(int colorArgb, float density)`.**

Concrete numbers (rounded, at density 1.0):
- vertical padding `pillPaddingVerticalDp = 2dp`; horizontal `pillPaddingHorizontalDp = 6dp`.
- pill radius `rx = (lineHeight / 2f)` where `lineHeight = descent - ascent` from `FontMetricsInt`.
- height clamp: `pillHeight = min(lineHeight, textSize + 2 * 2dp)`; use `rx = pillHeight / 2f` and center within the line.

Keep the span thin: prefer a fixed pill height = `textSize + 2 * 2dp`, capped at line height, so a 1-digit and 3-digit count keep the same pill height.

- [ ] **Step 1: Write the class skeleton**

Create `app/src/main/org/runnerup/view/PillSpan.java`:

```java
package org.runnerup.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

public class PillSpan extends ReplacementSpan {
  private static final int COLOR_WHITE = 0xFFFFFFFF;
  private static final float PADDING_DP = 2f;

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
    Rect bounds = new Rect();
    paint.getTextBounds(text.subSequence(start, end).toString(), 0, end - start, bounds);
    float height = pillHeight(paint, fm);
    if (fm != null) {
      fm.ascent = -Math.round(height / 2f);
      fm.descent = Math.round(height / 2f);
      fm.top = fm.ascent;
      fm.bottom = fm.descent;
    }
    return Math.round(bounds.width() + 2 * padPx() + padPx());
  }

  private float pillHeight(@NonNull Paint paint, FontMetricsInt fm) {
    if (fm != null) {
      return fm.descent - fm.ascent;
    }
    Paint.FontMetrics metrics = paint.getFontMetrics();
    return metrics.descent - metrics.ascent;
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
    Paint.FontMetrics fm = paint.getFontMetrics();
    float height = fm.descent - fm.ascent;
    float left = x + padPx();
    float right = left + paint.measureText(value) + 2 * padPx();
    float centerY = (fm.ascent + fm.descent) / 2f;
    float rx = height / 2f;
    float pillTop = centerY - rx;
    float pillBottom = centerY + rx;

    paint.setColor(color);
    canvas.drawRoundRect(left, pillTop, right, pillBottom, rx, rx, paint);
    paint.setColor(COLOR_WHITE);
    canvas.drawText(value, left + padPx(), centerY - fm.ascent / 2f, paint);
  }
}
```

Note: the plan author verified the span compiles conceptually against `ReplacementSpan`'s abstract `getSize`/`draw`. Adjust the vertical text centering so the number is visually centered (use `centerY - fm.ascent/2f` or `y + (height - textHeight)/2f` as needed; verify on device in Task 5).

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
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java` (chip loop at ~171-184; `updateSportCount` at ~407-432; field `sportCountText` at ~105, assignment at ~159)
- Modify: `app/res/layout/history.xml` (remove `history_sport_count` TextView, lines 76-82; tighten `paddingEnd` on `history_sport_chips_scroller`, line 63)
- Test: `app/test/java/org/runnerup/view/SportCountBadgeTest.java` (add color assertion, step 2)

**Interfaces:**
- Consumes: `SportCountBadge.forSport(Integer, int[], int)`, `SportCountBadge.Badge`, `SportCountBadge.grandTotal(int[])` (Task 1); `PillSpan(int, float)` (Task 2); `Statistics.sportCounts(SQLiteDatabase)` → `int[]`; `Sport.colorOf(int)` → color resource id.
- Produces: `refreshSportBadges()` private method; calls `Sport.colorOf(sport)` where `sport` is the chip's DB value.

**Logic:**

1. In `onViewCreated`, capture the chip list once. Currently chips are added to `chipGroup` directly (`chipGroup.addView(chip)`). Change the loop so each created `Chip` is appended to a `List<Chip> sportChips` (local), kept in chip order (index 0 = All sports, index `i+1` = sport `sportNames[i]`'s dbValue). The All-sports chip (tag `null`) is `sportChips` index 0.

2. Add field:

```java
private final List<Chip> sportChips = new ArrayList<>();
```

3. In the chip-creation block, replace the two `chipGroup.addView(...)` calls with `sportChips.add(...); chipGroup.addView(...);`. Note the existing code adds both the All-sports chip and the per-sport chips directly; keep those `addView` calls, additionally appending to `sportChips`.

4. Replace `updateSportCount()` (lines 407-432) with `refreshSportBadges()` that runs the count query off the main thread and posts a UI update:

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
          requireContext(), org.runnerup.common.R.color.historyBadgeAllSports);
  for (int i = 0; i < sportChips.size(); i++) {
    Chip chip = sportChips.get(i);
    Integer sport = (Integer) chip.getTag();
    SportCountBadge.Badge badge = SportCountBadge.forSport(sport, counts, allSportsColor);
    chip.setText(buildChipText(chip.getText().toString(), badge));
  }
}

private CharSequence buildChipText(String label, SportCountBadge.Badge badge) {
  if (badge == null) {
    return label;
  }
  int color = badge.color;
  SpannableStringBuilder sb = new SpannableStringBuilder(label);
  sb.append(" ");
  int start = sb.length();
  sb.append(Integer.toString(badge.count));
  int end = sb.length();
  sb.setSpan(
      new PillSpan(color, getResources().getDisplayMetrics().density),
      start,
      end,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  return sb;
}
```

   Careful: build the pill color from the sport. For sport chips the badge color came from `forSport`; currently `forSport` returns `0` for sport chips. **Resolve the real color here**: change `applySportBadges` to compute the pill color per chip — for All sports use `historyBadgeAllSports`; for sport chips use `ContextCompat.getColor(context, Sport.colorOf(sport))`. Pass that resolved color into `forSport`? No — `forSport` for sport chips ignores `allSportsColor`. Instead, after getting the badge, if `badge != null && sport != null`, build the pill with `ContextCompat.getColor(context, Sport.colorOf(sport))`.

```java
private void applySportBadges(int[] counts) {
  int allSportsColor =
      ContextCompat.getColor(
          requireContext(), org.runnerup.common.R.color.historyBadgeAllSports);
  for (int i = 0; i < sportChips.size(); i++) {
    Chip chip = sportChips.get(i);
    Integer sport = (Integer) chip.getTag();
    SportCountBadge.Badge badge = SportCountBadge.forSport(sport, counts, allSportsColor);
    if (badge == null) {
      chip.setText(chip.getText().toString());
      continue;
    }
    int pillColor = sport == null ? allSportsColor
        : ContextCompat.getColor(requireContext(), Sport.colorOf(sport));
    chip.setText(buildChipText(chip.getText().toString(), pillColor, badge.count));
  }
}

private CharSequence buildChipText(String label, int pillColor, int count) {
  SpannableStringBuilder sb = new SpannableStringBuilder(label);
  sb.append(" ");
  int start = sb.length();
  sb.append(Integer.toString(count));
  int end = sb.length();
  sb.setSpan(new PillSpan(pillColor, getResources().getDisplayMetrics().density), start, end,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  return sb;
}
```

   This simplifies `buildChipText` (no `Badge.color` dependency) — the pill color is resolved in `applySportBadges`. Update Task 1's `Badge` accordingly: keep `Badge.count`; `Badge.color` is used only for the All-sports case (where `forSport` fills it with `allSportsColor`). To keep the pure helper free of resource lookups, this is fine: `forSport(null, ...)` returns `Badge(count, allSportsColor)`; for sport chips it returns `Badge(count, 0)` and the caller overrides the color. 

   To make it cleaner and keep `Badge.color` meaningful, have `applySportBadges` always resolve the color itself and use `badge.count` only. That matches the Task 1 contract. Document this in the final code comment-free form.

5. Replace the two `updateSportCount()` calls (init at ~207 and chip-selection at ~205) with `refreshSportBadges()`.

6. Add `refreshSportBadges()` to `onLoadFinished` (after `adapter.setData(arg1)` at ~357) so badges refresh on every reload.

7. Remove the `sportCountText` field (line 105) and its assignment (line 159). Remove `history_sport_count` from `history.xml` (lines 76-82) — the scroller's `paddingEnd` can stay 8dp (it only affects scroll reach, harmless) or be removed; keep it simple and leave it.

8. Add the neutral grey color to `app/res/values/colors.xml`:

```xml
<color name="historyBadgeAllSports">#586e75</color>
```

   Confirm `#586e75` does not collide with any sport color (sport colors: running `#859900`, biking `#cb4b16`, orienteering `#d33682`, walking `#6c71c4`, other `#b58900`, treadmill `#2aa198`, gym `#dc322f`, stationary bike `#268bd2`) — it does not.

9. Update Task 1's unit test to assert the All-sports color passes through (`forSport(null, counts, color)` returns that color) — this is already covered by `allSportsColorPassedThrough`. No sport-chip color assertion is possible in the pure unit test since sport color resolution needs resources; that's fine.

**Imports needed in `HistoryFragment.java`:** `android.text.SpannableStringBuilder`, `android.text.Spanned`, `android.widget.Spannable` (not needed), `java.util.ArrayList`, `java.util.List`, `androidx.core.content.ContextCompat`, `org.runnerup.util.Sport` (already imported? verify — `Sport` is `org.runnerup.workout.Sport`). **Verify existing imports:** `Sport` is imported to build chips; `ContextCompat` already used at line 180. Confirm `SpannableStringBuilder`/`Spanned` imports.

- [ ] **Step 1: Add the All-sports color resource**

Edit `app/res/values/colors.xml`, after the `sportStationaryBike` line:

```xml
<color name="historyBadgeAllSports">#586e75</color>
```

- [ ] **Step 2: Update Task 1 test with a grand-total-for-sport color note** (optional; no code change required — existing tests already cover color pass-through for All sports)

- [ ] **Step 3: Implement wiring in `HistoryFragment.java`**

Add field, list population, `refreshSportBadges`/`applySportBadges`/`buildChipText`, replace `updateSportCount` calls, add `onLoadFinished` call, remove `sportCountText` (field + assignment + imports).

- [ ] **Step 4: Remove `history_sport_count` from `history.xml`**

Delete the `TextView` block (lines 76-82).

- [ ] **Step 5: Compile + unit tests + lint**

Run: `./gradlew test && ./gradlew :app:lintLatestDebug`
Expected: tests PASS; lint reports NO new issues (only the 25 baseline). If lint flags an unused resource because `history_sport_count` removal leaves `Statistics_activities_count` unused, remove the `<plurals>` block from `common/src/main/res/values/strings.xml` (verify no other reference — the only reference was line 427 of `HistoryFragment.java`, now removed).

- [ ] **Step 6: Spotless + commit**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: spotlessCheck PASS.

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java app/res/layout/history.xml app/res/values/colors.xml
git commit -m "feat: sport count badges on history filter chips"
```

*(If the `Statistics_activities_count` plural was removed in Step 5, add `common/src/main/res/values/strings.xml` to the `git add`.)*

---

### Task 4: Full gate

**Files:** none (verification only).

**Interfaces:** — (no code changes).

- [ ] **Step 1: Run the full verification gate**

Run:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```
Expected: all PASS; lint shows no new issues beyond the baseline; spotlessCheck PASS (if spotlessApply made changes, commit them).

- [ ] **Step 2: Commit any spotless fixes if made**

```bash
git add -A -- 'app/*' 'common/*'
git commit -m "style: format after badge wiring"
```
(Only if spotlessApply modified files; otherwise skip — only run `git add`/`commit` if there are real changes.)

---

### Task 5: On-device smoke test

**Files:** none (verification only). **Requires** the debug APK built in Task 4 and the test phone `6a6743fd` (Xiaomi `frost_eea`, package `org.runnerup.debug`).

**Interfaces:** — (verification).

- [ ] **Step 1: Install and launch**

```bash
./gradlew :app:assembleLatestDebug
adb -s 6a6743fd install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 6a6743fd shell am force-stop org.runnerup.debug
adb -s 6a6743fd shell monkey -p org.runnerup.debug -c android.intent.category.LAUNCHER 1
```
Navigate to History (Start tab).

- [ ] **Step 2: Verify pill behavior**

Use `adb shell uiautomator dump /sdcard/ui.xml` and `adb shell dumpsys activity top` to read state:
- Each populated sport chip shows a count pill after its label (e.g. All sports shows a grey total equal to the sum of all counts; Running shows its count).
- A sport with count 0 shows **no** pill (label + icon only).
- All sports shows a grey pill, not a sport-colored one.
- Tapping a chip still filters the list, and its pill persists.

- [ ] **Step 3: Verify refresh on add**

Add a manual activity (or the existing "Add manual entry" flow), return to History, and confirm the affected chip's pill count increments.

- [ ] **Step 4: Capture evidence**

Archive screenshot(s) and the `ui.xml` to `/tmp/opencode/smoke/` and note results in the plan/ledger.

- [ ] **Step 5: Log results**

Record PASS/FAIL for each bullet in Step 2-3 in the SDD ledger (`progress.md`) or commit note. `git log --oneline -8` to confirm the commit set.

---

## Self-Review

**Spec coverage:**
- Pill inside chip, trailing label → Task 2 + 3 (PillSpan + buildChipText). ✔
- Zero count → no pill → Task 1 (`forSport` returns null). ✔
- All-sports grey total pill → Task 1 + 3. ✔
- Refresh on reload/selection/init → Task 3 (`onLoadFinished` + both call sites). ✔
- Remove standalone count text → Task 3. ✔
- Delete unused plural if orphaned → Task 3 Step 5. ✔
- TDD for decision helper → Task 1. ✔
- Gate → Task 4. ✔
- Smoke → Task 5. ✔

**Placeholder scan:** No TBD/TODO. All code blocks are concrete. The PillSpan vertical-centering note flags a device-verify step, not a placeholder.

**Type consistency:** `SportCountBadge.forSport(Integer, int[], int)` → `Badge`/`null`; `grandTotal(int[])` → `int`; `PillSpan(int, float)`; `refreshSportBadges()`/`applySportBadges(int[])`/`buildChipText(String,int,int)` — all consistent across tasks. `Badge` nested public static, constructed via `new Badge(count, color)`.

**Task 1/3 color-consistency caveat:** In Task 1, sport-chip badges are created with color `0`; the real `Sport.colorOf` color is resolved in Task 3's `applySportBadges`. This matches the documented contract (only All-sports color flows through `forSport`). The Task 1 tests assert only `count` for sport chips, so no test breaks.
