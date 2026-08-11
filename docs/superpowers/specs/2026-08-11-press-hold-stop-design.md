# Press-and-hold Stop button

## Problem

Stopping a running activity is a final, destructive action, yet the Stop
button on the recording screen (`RunActivity`) fires on a single tap. A
missed tap or a palm/glove hit during an interval workout stops the run
and loses the in-progress session. The user wants an explicit
confirmation gesture: the user must press and hold the Stop button for a
short duration to trigger the stop.

## Goal

- While the workout is paused, the right button shows **Stop** (red
  pill, square icon) as today.
- To stop, the user **presses and holds** the Stop button for **1.5s**.
- During the hold, a circular progress ring draws around the square stop
  icon, filling from top around to a full circle as the hold progresses.
- When the hold completes, stop fires exactly as today (`doStop()`).
- If the user releases before 1.5s (a quick tap), no stop happens; a hint
  **Toast** ("Press and hold to stop") is shown, once per early release.
- If the touch is cancelled (finger slides off, system interrupt), the
  ring resets and no hint is shown.
- While the workout is running, the right button stays **Next Lap**
  (normal tap, no ring, no hold).

## Non-goals

- No change to tracker notification actions (pause/resume/stop from the
  notification are untouched).
- No change to the automatic stop path in `updateView()` when the
  workout finishes its steps.
- No change to `DetailActivity` save/discard buttons.
- No change to the left button (Pause/Resume) behavior.
- No accessibility-specific treatment beyond keeping the existing touch
  semantics; the hold gesture is a deliberate safety feature.

## Design

### Hold behavior state machine

The hold only applies when the workout is paused (button in Stop mode).
In that mode the button consumes touch events entirely; the normal click
listener does not fire (RunActivity's `nextLapButtonClick` currently
calls `doStop()` on a paused tap — this is removed; the hold listener
owns stopping).

Transitions, keyed on `workout.isPaused()`:

| Event | Paused (Stop mode) | Running (Next Lap mode) |
|---|---|---|
| `ACTION_DOWN` | start 1.5s animator, begin ring fill | pass through to normal click |
| animator completes | fire `doStop()`, reset ring | — |
| `ACTION_UP` early | cancel animator, reset ring, show hint toast | pass through |
| `ACTION_CANCEL` | cancel animator, reset ring, no hint | pass through |

### Ring drawable

New `StopProgressDrawable` (`app/src/main/org/runnerup/view/`):

- A custom `Drawable` drawing the stop square (same path as
  `ic_stop.xml`) plus a ring around it.
- `setProgress(float p)` where `p` is 0..1; ring sweep =
  `p * 360°` starting from the top. Calls `invalidateSelf()`.
- Draws opaque white paths; the FAB's existing `iconTint`
  (`colorOnError`, set in `updateButtons()`) colors it — exactly how
  `ic_stop` is tinted today, no theme resolution needed in the
  drawable.
- When progress is 0 the ring is absent (a plain square icon, matching
  today's look).

### Hold listener

New `HoldToStopListener` (`app/src/main/org/runnerup/view/`) — a
`View.OnTouchListener`:

- Constructor takes the `View`, the `StopProgressDrawable`, a `long`
  hold duration, and two callbacks: `onComplete` (stop) and
  `onHint` (show the hint toast).
- Runs a `ValueAnimator` 0→1 over the duration; on each frame calls
  `drawable.setProgress(...)` and `view.invalidate()`.
- Completes → `onComplete.run()`, then resets progress to 0.
- Early `ACTION_UP` → cancel animator, reset progress, `onHint.run()`.
- `ACTION_CANCEL` → cancel animator, reset progress, no hint.
- `onTouch()` returns `true` in Stop mode (consumes events so the normal
  click is suppressed); returns `false` in Next Lap mode so the click
  falls through.
- Exposes `cancel()` to stop/reset the animation (lifecycle).
- Duplicate `ACTION_DOWN` while animating is ignored (a second finger /
  repeat event).

### RunActivity wiring (`RunActivity.java`)

- `updateButtons()`: when paused, set the right button's icon to a
  `StopProgressDrawable`; when running, back to `ic_skip_next`. The
  listener is attached to the right button in `onCreate` and reads
  `workout.isPaused()` at touch time.
- `nextLapButtonClick`: remove the `doStop()` branch on pause-tap; a tap
  while paused now does nothing (consumed by the hold listener). The
  running branch (`newLap()`) is unchanged.
- `onPause()`/`onDestroy()`: call `holdToStopListener.cancel()`.
- The automatic stop path in `updateView()` is unchanged.

### Strings

Add to `app/res/values/strings.xml`:

- `press_hold_to_stop` → "Press and hold to stop"

(There is no `common` module change: `Stop`, `Next Lap`, etc. already
exist and are untouched.)

## Verification

- Gates: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond
  the baseline), `spotlessApply` + `spotlessCheck`,
  `:app:assembleLatestDebug`.
- Unit tests (pure JUnit, no Android framework, following the
  `RunButtonStateTest` pattern):
  - `buttonState()` tests unchanged (still green).
  - New `HoldToStopListenerTest` with a fake clock: hold-to-completion
    fires `onComplete` exactly once and resets; early release fires
    `onHint` and resets; cancel resets without hint; running state
    returns `false` (falls through to click).
  - `StopProgressDrawable.setProgress` bounds/negation behavior
    (clamp 0..1).
- Device (test phone, interval workout): start a run; while paused,
  quick-tap Stop does nothing and shows the hint toast; press-and-hold
  Stop fills the ring and stops after 1.5s; while running, Next Lap still
  laps on a normal tap; no ring appears on Next Lap.
