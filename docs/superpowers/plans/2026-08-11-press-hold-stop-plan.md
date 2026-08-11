# Press-and-Hold Stop Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require the user to press and hold the recording screen's Stop button for 1.5s to stop a paused workout, showing a progress ring during the hold and a hint toast on early release.

**Architecture:** A pure-Java `HoldState` owns all timing/event logic (testable with a fake clock via explicit millisecond timestamps — no Android framework). `StopProgressDrawable` draws the white stop square plus a ring. `HoldToStopListener` is a thin `View.OnTouchListener` that translates `MotionEvent`s, drives a `postOnAnimation` frame loop, and delegates all decisions to `HoldState`. `RunActivity` attaches the listener to the right button, swaps in the ring drawable when paused, and removes the tap-to-stop branch from `nextLapButtonClick`.

**Tech Stack:** Java 17, AndroidX AppCompat / Material 3 (`ExtendedFloatingActionButton`), JUnit 4.13.2 + Mockito 5.23.0 (no Robolectric).

## Global Constraints

- Hold duration is exactly 1500ms (constant `HOLD_TO_STOP_MILLIS`).
- Ring fills from the top, clockwise; ring sweep = `progress * 360°`; at progress 0 there is no ring (plain square icon).
- Hint toast text is exactly "Press and hold to stop" (new string `press_hold_to_stop`), shown once per early release.
- On-screen Stop only: tracker notification pause/resume/stop actions and the automatic stop in `RunActivity.updateView()` are unchanged.
- No theme resolution inside the drawable: it draws opaque white paths; the FAB's existing `iconTint` (`colorOnError`, set in `updateButtons()`) colors them.
- Left button (Pause/Resume) and `DetailActivity` save/discard are untouched.
- `buttonState()` and `RunButtonStateTest` are unchanged (paused `rightIcon` stays `ic_stop`); only `updateButtons()` stops using that icon in the paused branch.
- Unit tests are pure JUnit4 + Mockito in `app/test/java/org/runnerup/view/`; no Robolectric. `testOptions.unitTests.returnDefaultValues = true` is already set in `app/build.gradle`.
- Code style: googleJavaFormat via `spotlessApply` then `spotlessCheck`; no comments in code unless asked.
- Gates: `./gradlew test`, `./gradlew :app:lintLatestDebug` (only NEW issues matter; `app/lint-baseline.xml` holds 25 pre-existing), `./gradlew :app:assembleLatestDebug`.
- Verification order per AGENTS.md: `test` → `lintLatestDebug` → `spotlessApply` → `spotlessCheck` → device smoke test.

---

### Task 1: HoldState (pure state machine) + tests

**Files:**
- Create: `app/src/main/org/runnerup/view/HoldState.java`
- Test: `app/test/java/org/runnerup/view/HoldStateTest.java`

**Interfaces:**
- Produces: `org.runnerup.view.HoldState` — public class with constructor `HoldState(long holdDurationMillis)` and methods:
  - `boolean onDown(long nowMillis)` — start a press; returns `false` if a press is already active.
  - `boolean isPressing()` — true between a successful `onDown` and reset.
  - `float progress(long nowMillis)` — elapsed/duration clamped to `[0, 1]`; `0` when idle.
  - `enum Result { NONE, COMPLETE, EARLY_RELEASE }`
  - `Result onTick(long nowMillis)` — returns `COMPLETE` exactly once when `nowMillis - down >= duration` (resets after), else `NONE`.
  - `Result onUp(long nowMillis)` — returns `EARLY_RELEASE` if released before the duration, else `NONE`; resets the press either way.
  - `void onCancel()` — resets without any event.
- Consumes: nothing (pure Java).

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/view/HoldStateTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.view.HoldState.Result;

public class HoldStateTest {
  private static final long DURATION = 1500L;
  private static final long T0 = 10000L;

  private final HoldState state = new HoldState(DURATION);

  @Test
  public void idleState() {
    assertFalse(state.isPressing());
    assertEquals(0f, state.progress(T0), 0f);
    assertEquals(Result.NONE, state.onTick(T0));
    assertEquals(Result.NONE, state.onUp(T0));
  }

  @Test
  public void onDownStartsHold() {
    assertTrue(state.onDown(T0));
    assertTrue(state.isPressing());
  }

  @Test
  public void doubleDownIsRejected() {
    assertTrue(state.onDown(T0));
    assertFalse(state.onDown(T0 + 1L));
  }

  @Test
  public void progressIsElapsedOverDuration() {
    state.onDown(T0);
    assertEquals(0f, state.progress(T0), 0f);
    assertEquals(0.5f, state.progress(T0 + 750L), 0.0001f);
    assertEquals(1f, state.progress(T0 + DURATION), 0f);
  }

  @Test
  public void progressIsClampedToUnitRange() {
    state.onDown(T0);
    assertEquals(0f, state.progress(T0 - 100L), 0f);
    assertEquals(1f, state.progress(T0 + DURATION * 3L), 0f);
  }

  @Test
  public void tickCompletesExactlyAtDuration() {
    state.onDown(T0);
    assertEquals(Result.NONE, state.onTick(T0 + DURATION - 1L));
    assertEquals(Result.COMPLETE, state.onTick(T0 + DURATION));
    assertFalse(state.isPressing());
  }

  @Test
  public void tickAfterCompleteReturnsNone() {
    state.onDown(T0);
    state.onTick(T0 + DURATION);
    assertEquals(Result.NONE, state.onTick(T0 + DURATION + 1L));
  }

  @Test
  public void earlyReleaseIsEarly() {
    state.onDown(T0);
    assertEquals(Result.EARLY_RELEASE, state.onUp(T0 + 500L));
    assertFalse(state.isPressing());
  }

  @Test
  public void releaseAfterCompleteIsNotEarly() {
    state.onDown(T0);
    state.onTick(T0 + DURATION);
    assertEquals(Result.NONE, state.onUp(T0 + DURATION));
  }

  @Test
  public void cancelResetsWithoutHintEvent() {
    state.onDown(T0);
    state.onCancel();
    assertEquals(Result.NONE, state.onUp(T0 + 10L));
    assertTrue(state.onDown(T0 + 20L));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.HoldStateTest`
Expected: FAIL — compilation error, class `HoldState` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/view/HoldState.java`:

```java
package org.runnerup.view;

public final class HoldState {
  public enum Result {
    NONE,
    COMPLETE,
    EARLY_RELEASE
  }

  private final long holdDurationMillis;
  private long downMillis = -1L;

  public HoldState(long holdDurationMillis) {
    this.holdDurationMillis = holdDurationMillis;
  }

  public boolean onDown(long nowMillis) {
    if (downMillis >= 0L) {
      return false;
    }
    downMillis = nowMillis;
    return true;
  }

  public boolean isPressing() {
    return downMillis >= 0L;
  }

  public float progress(long nowMillis) {
    if (downMillis < 0L) {
      return 0f;
    }
    float p = (float) (nowMillis - downMillis) / (float) holdDurationMillis;
    if (p < 0f) {
      return 0f;
    }
    return p > 1f ? 1f : p;
  }

  public Result onTick(long nowMillis) {
    if (downMillis < 0L) {
      return Result.NONE;
    }
    if (nowMillis - downMillis >= holdDurationMillis) {
      downMillis = -1L;
      return Result.COMPLETE;
    }
    return Result.NONE;
  }

  public Result onUp(long nowMillis) {
    if (downMillis < 0L) {
      return Result.NONE;
    }
    boolean early = nowMillis - downMillis < holdDurationMillis;
    downMillis = -1L;
    return early ? Result.EARLY_RELEASE : Result.NONE;
  }

  public void onCancel() {
    downMillis = -1L;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.HoldStateTest`
Expected: PASS — all 10 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/HoldState.java app/test/java/org/runnerup/view/HoldStateTest.java
git commit -m "feat: add press-and-hold stop state machine"
```

---

### Task 2: StopProgressDrawable (ring drawable)

**Files:**
- Create: `app/src/main/org/runnerup/view/StopProgressDrawable.java`

**Interfaces:**
- Produces: `org.runnerup.view.StopProgressDrawable` — public `Drawable` subclass with:
  - `StopProgressDrawable()` — no-arg constructor.
  - `void setProgress(float p)` — stores `p` and calls `invalidateSelf()`. Caller guarantees `p` in `[0, 1]` (clamping lives in `HoldState.progress`).
  - Intrinsic size 24x24; draws in a 24x24 viewport (same coordinate space as `ic_stop.xml`).
- Consumes: nothing (no unit test — requires the framework; verified visually on device in Task 5).

- [ ] **Step 1: Write the implementation**

Create `app/src/main/org/runnerup/view/StopProgressDrawable.java`:

```java
package org.runnerup.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class StopProgressDrawable extends Drawable {
  private static final float SQUARE_LEFT = 6f;
  private static final float SQUARE_TOP = 6f;
  private static final float SQUARE_RIGHT = 18f;
  private static final float SQUARE_BOTTOM = 18f;
  private static final float RING_RADIUS = 10f;
  private static final float RING_STROKE_WIDTH = 2.5f;

  private final Paint squarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF ringBounds =
      new RectF(
          12f - RING_RADIUS, 12f - RING_RADIUS, 12f + RING_RADIUS, 12f + RING_RADIUS);
  private float progress = 0f;

  public StopProgressDrawable() {
    squarePaint.setColor(0xffffffff);
    squarePaint.setStyle(Paint.Style.FILL);
    ringPaint.setColor(0xffffffff);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(RING_STROKE_WIDTH);
    ringPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  public void setProgress(float p) {
    progress = p;
    invalidateSelf();
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    canvas.drawRect(SQUARE_LEFT, SQUARE_TOP, SQUARE_RIGHT, SQUARE_BOTTOM, squarePaint);
    if (progress > 0f) {
      canvas.drawArc(ringBounds, -90f, 360f * progress, false, ringPaint);
    }
  }

  @Override
  public void setAlpha(int alpha) {
    squarePaint.setAlpha(alpha);
    ringPaint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth() {
    return 24;
  }

  @Override
  public int getIntrinsicHeight() {
    return 24;
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileLatestDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/StopProgressDrawable.java
git commit -m "feat: add stop progress ring drawable"
```

---

### Task 3: HoldToStopListener + tests

**Files:**
- Create: `app/src/main/org/runnerup/view/HoldToStopListener.java`
- Test: `app/test/java/org/runnerup/view/HoldToStopListenerTest.java`

**Interfaces:**
- Consumes: `HoldState` (Task 1), `StopProgressDrawable` (Task 2).
- Produces: `org.runnerup.view.HoldToStopListener` — public `View.OnTouchListener` with constructor:
  - `HoldToStopListener(View view, StopProgressDrawable drawable, long holdDurationMillis, BooleanSupplier stopMode, Runnable onComplete, Runnable onHint)`
  - `boolean onTouch(View v, MotionEvent event)` (public; annotated `@SuppressLint("ClickableViewAccessibility")`)
  - `void cancel()` (public; lifecycle)
  - Package-private (for tests, same package): `boolean onEvent(int action, long nowMillis)`, `boolean tick(long nowMillis)`.

- [ ] **Step 1: Write the failing test**

Create `app/test/java/org/runnerup/view/HoldToStopListenerTest.java`:

```java
package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.view.MotionEvent;
import android.view.View;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class HoldToStopListenerTest {
  private static final long DURATION = 1500L;
  private static final long T0 = 10000L;

  private final View view = mock(View.class);
  private final StopProgressDrawable drawable = mock(StopProgressDrawable.class);
  private final AtomicInteger completeCalls = new AtomicInteger();
  private final AtomicInteger hintCalls = new AtomicInteger();

  private HoldToStopListener listener(boolean stopMode) {
    return new HoldToStopListener(
        view,
        drawable,
        DURATION,
        () -> stopMode,
        completeCalls::incrementAndGet,
        hintCalls::incrementAndGet);
  }

  @Test
  public void runningModeFallsThroughToClick() {
    HoldToStopListener l = listener(false);
    assertFalse(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertFalse(l.onEvent(MotionEvent.ACTION_UP, T0 + 10L));
  }

  @Test
  public void stopModeConsumesEvents() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 100L));
  }

  @Test
  public void holdToCompletionFiresOnCompleteOnceAndResets() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertFalse(l.tick(T0 + DURATION - 1L));
    assertTrue(l.tick(T0 + DURATION));
    assertFalse(l.tick(T0 + DURATION + 1L));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
    verify(drawable).setProgress(1f);
    verify(drawable, times(2)).setProgress(0f);
  }

  @Test
  public void earlyReleaseFiresHintOnceAndResets() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertFalse(l.tick(T0 + 750L));
    verify(drawable).setProgress(0.5f);
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + 900L));
    assertEquals(1, hintCalls.get());
    assertEquals(0, completeCalls.get());
  }

  @Test
  public void upAfterCompleteDoesNotHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.tick(T0 + DURATION));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION));
    assertEquals(1, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }

  @Test
  public void cancelResetsWithoutHint() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_CANCEL, T0 + 100L));
    assertEquals(0, hintCalls.get());
    assertEquals(0, completeCalls.get());
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 200L));
  }

  @Test
  public void duplicateDownIsIgnoredWithoutSideEffects() {
    HoldToStopListener l = listener(true);
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0));
    assertTrue(l.onEvent(MotionEvent.ACTION_DOWN, T0 + 10L));
    assertTrue(l.onEvent(MotionEvent.ACTION_UP, T0 + DURATION));
    assertEquals(0, completeCalls.get());
    assertEquals(0, hintCalls.get());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.HoldToStopListenerTest`
Expected: FAIL — compilation error, class `HoldToStopListener` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/org/runnerup/view/HoldToStopListener.java`:

```java
package org.runnerup.view;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.BooleanSupplier;

public class HoldToStopListener implements View.OnTouchListener {
  private final View view;
  private final StopProgressDrawable drawable;
  private final BooleanSupplier stopMode;
  private final Runnable onComplete;
  private final Runnable onHint;
  private final HoldState state;

  private final Runnable frame =
      () -> {
        if (!tick(SystemClock.uptimeMillis()) && state.isPressing()) {
          view.postOnAnimation(frame);
        }
      };

  public HoldToStopListener(
      View view,
      StopProgressDrawable drawable,
      long holdDurationMillis,
      BooleanSupplier stopMode,
      Runnable onComplete,
      Runnable onHint) {
    this.view = view;
    this.drawable = drawable;
    this.stopMode = stopMode;
    this.onComplete = onComplete;
    this.onHint = onHint;
    this.state = new HoldState(holdDurationMillis);
  }

  @Override
  @SuppressLint("ClickableViewAccessibility")
  public boolean onTouch(View v, MotionEvent event) {
    return onEvent(event.getActionMasked(), SystemClock.uptimeMillis());
  }

  public void cancel() {
    stopAnimation();
    state.onCancel();
    drawable.setProgress(0f);
    view.invalidate();
  }

  boolean onEvent(int action, long nowMillis) {
    if (!stopMode.getAsBoolean()) {
      return false;
    }
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (state.onDown(nowMillis)) {
          drawable.setProgress(0f);
          view.invalidate();
          startAnimation();
        }
        return true;
      case MotionEvent.ACTION_UP:
        finishUp(nowMillis);
        return true;
      case MotionEvent.ACTION_CANCEL:
        cancel();
        return true;
      default:
        return true;
    }
  }

  boolean tick(long nowMillis) {
    if (!state.isPressing()) {
      return false;
    }
    drawable.setProgress(state.progress(nowMillis));
    view.invalidate();
    if (state.onTick(nowMillis) == HoldState.Result.COMPLETE) {
      stopAnimation();
      drawable.setProgress(0f);
      onComplete.run();
      return true;
    }
    return false;
  }

  private void startAnimation() {
    if (state.isPressing()) {
      view.postOnAnimation(frame);
    }
  }

  private void stopAnimation() {
    view.removeCallbacks(frame);
  }

  private void finishUp(long nowMillis) {
    stopAnimation();
    if (state.onUp(nowMillis) == HoldState.Result.EARLY_RELEASE) {
      onHint.run();
    }
    drawable.setProgress(0f);
    view.invalidate();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLatestDebugUnitTest --tests org.runnerup.view.HoldToStopListenerTest`
Expected: PASS — all 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/HoldToStopListener.java app/test/java/org/runnerup/view/HoldToStopListenerTest.java
git commit -m "feat: add press-and-hold stop touch listener"
```

---

### Task 4: Wire press-and-hold stop into RunActivity + string

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java` — imports (add `android.widget.Toast`), new field (line 97-98 area), new constant (near line 90), `onCreate` (after line 190), new `showPressHoldToStopHint()` method (near `newLap()` at line 439), `updateButtons()` (lines 449-463), `nextLapButtonClick` (lines 496-503), `onPause()` (line 271), `onDestroy()` (line 293).
- Modify: `app/res/values/strings.xml`

**Interfaces:**
- Consumes: `HoldToStopListener`, `StopProgressDrawable` (Tasks 2-3).
- Produces: The recording screen behaves per spec; `RunButtonStateTest` stays green (`buttonState()` is untouched).

- [ ] **Step 1: Add the string**

In `app/res/values/strings.xml`, add before `</resources>` (line 55):

```xml
    <string name="press_hold_to_stop">Press and hold to stop</string>
```

- [ ] **Step 2: Add the import and fields**

In `RunActivity.java`:
- After line 47 (`import android.widget.TextView;`) add `import android.widget.Toast;`.
- After line 90 (`private final Handler handler = new Handler();`) add:

```java
  private static final long HOLD_TO_STOP_MILLIS = 1500L;
```

- After line 98 (`private ExtendedFloatingActionButton nextLapButton = null;`) add:

```java
  private StopProgressDrawable stopProgressDrawable = null;
  private HoldToStopListener holdToStopListener = null;
```

- [ ] **Step 3: Build the listener in onCreate**

After line 190 (`nextLapButton = findViewById(R.id.next_lap_button);`) add:

```java
    stopProgressDrawable = new StopProgressDrawable();
    holdToStopListener =
        new HoldToStopListener(
            nextLapButton,
            stopProgressDrawable,
            HOLD_TO_STOP_MILLIS,
            () -> workout != null && workout.isPaused(),
            this::doStop,
            this::showPressHoldToStopHint);
    nextLapButton.setOnTouchListener(holdToStopListener);
```

- [ ] **Step 4: Add the hint method**

After `newLap()` (ends line 439) add:

```java
  private void showPressHoldToStopHint() {
    Toast.makeText(this, R.string.press_hold_to_stop, Toast.LENGTH_SHORT).show();
  }
```

- [ ] **Step 5: Use the ring drawable when paused**

In `updateButtons()` (lines 449-463), replace:

```java
    nextLapButton.setIconResource(s.rightIcon);
    nextLapButton.setText(s.rightText);
```

with:

```java
    if (paused) {
      stopProgressDrawable.setProgress(0f);
      nextLapButton.setIconDrawable(stopProgressDrawable);
    } else {
      nextLapButton.setIconResource(s.rightIcon);
    }
    nextLapButton.setText(s.rightText);
```

The rest of `updateButtons()` (background tint, icon tint, text color) is unchanged — `setIconTint` runs after `setIconDrawable` so `colorOnError` colors the new drawable.

- [ ] **Step 6: Remove the tap-to-stop branch**

Replace `nextLapButtonClick` (lines 496-503):

```java
  private final OnClickListener nextLapButtonClick =
      v -> {
        if (workout != null && workout.isPaused()) {
          doStop();
        } else {
          newLap();
        }
      };
```

with:

```java
  private final OnClickListener nextLapButtonClick = v -> newLap();
```

- [ ] **Step 7: Cancel the hold on lifecycle pause/destroy**

In `onPause()` (line 271), after `super.onPause();` add:

```java
    if (holdToStopListener != null) {
      holdToStopListener.cancel();
    }
```

In `onDestroy()` (line 293), after `super.onDestroy();` add the same two lines.

- [ ] **Step 8: Verify unit tests and build**

Run: `./gradlew test :app:assembleLatestDebug`
Expected: PASS — `RunButtonStateTest`, `HoldStateTest`, `HoldToStopListenerTest` all green; build succeeds.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java app/res/values/strings.xml
git commit -m "feat: wire press-and-hold stop into RunActivity"
```

---

### Task 5: Full verification + device smoke test

**Files:**
- None (verification only; commit only if a fix is needed).

**Interfaces:**
- Consumes: the complete feature from Tasks 1-4.

- [ ] **Step 1: Run all gates in AGENTS.md order**

Run:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
```
Expected: tests green; lint reports no NEW issues beyond `app/lint-baseline.xml`; spotless clean after `spotlessApply`.

- [ ] **Step 2: Install on the device**

Run: `./gradlew :app:installLatestDebug`
Then: `adb shell am start -n org.runnerup.debug/org.runnerup.view.RunActivity` (or launch via the app's Start screen).

- [ ] **Step 3: Manual smoke test (device, interval workout)**

1. Start an interval workout; while running, tap the left button to pause.
2. While paused: **quick-tap** the right (red Stop) button — no stop, hint toast "Press and hold to stop" appears once, ring does not appear.
3. While paused: **press-and-hold** the right button — ring fills from the top clockwise over ~1.5s, then the workout stops and the save screen opens.
4. Repeat a run; while running: the right button shows Next Lap; a **normal tap** creates a lap; the ring never appears.
5. Sanity: left button still toggles Pause/Resume; notification pause/resume/stop still work.

Expected: all of the above behave as described. Check with `adb shell dumpsys activity top` and screenshots if a step is ambiguous.

- [ ] **Step 4: Push and finish**

If the manual test is clean:
```bash
git log --oneline -5
git push fork <branch>
```
Then merge to `master` (fast-forward) and push, following the repo's `finishing-a-development-branch` workflow, or report back for review first.
