# Start-Flow Speed (Sub-Project A) — Design Spec

**Date:** 2026-09-11
**Status:** Proposed
**Scope:** Race-ready start flow, favorites & resume pins, start-screen action-card redesign.

This is sub-project A of a three-part brainstorm (A: start-flow speed; B: motivation suite —
PRs/streaks/summary sheet, future script; C: history list upgrade, future script). This spec
covers A only.

## Background

Today the start screen requires a full wait-for-GPS-fix before the Start button even exists:
the user taps "Start GPS", waits for a fix, then a Start FAB appears, then taps Start, then the
countdown runs. The workout plan preview is inert. There is one workout spinner with no way to
pin favorites or quickly re-run the last workout.

Goals (from brainstorm with the owner):

- Lean casual: fast, low-friction start.
- Offline-first, no account.
- Simple, explainable behavior — no estimation models.
- Keep existing pro features (countdown, auto-pause, audio cue schemes) intact and configurable.

## Design Summary

1. A unified **action card** replaces the sport row + GPS status strip + floating Start FAB
   with a single "how to start this run" unit.
2. A **race-ready start flow**: tapping Start opens the run screen immediately; if GPS is not
   yet fixed, a countdown runs (if enabled), then the run enters a paused **Waiting for GPS**
   state that auto-starts on the first fix (short vibration + optional cue).
3. A **pinned favorites row** (horizontal chips) with "Last used" + starred workouts replaces
   the advanced-workout spinner as the primary workout chooser.
4. Preferences to keep it configurable and to store favorites/last-used.

## 1. Start Screen Layout

New top-to-bottom structure of the Record fragment (`start.xml` + related):

1. **Toolbar "Record"** — unchanged.
2. **Action card** (single card, subtle surface tint, rounded corners):
   - **Sport row** — sport dropdown (with sport icon), same picker as today.
   - **GPS status row** — replaces the old `status_layout` GPS bits: `Searching GPS… · 6/18`,
     `Good GPS · ±4 m`, or `GPS off`, using the existing 4-level GPS dots. Tapping when off
     opens the existing GPS enable flow.
   - **Start button** — full-width pill *inside* the card. This replaces the floating
     `start_fab` overlay as the primary start control.
   - Optional second tappable detail line for GPS accuracy details (the old "expand" detail).
3. **Pinned favorites row** — horizontal, scrollable-if-needed chips:
   - `↻ Last used` (first, sticky) then starred workouts; the row scrolls horizontally when
     the chips overflow.
   - `choose workout…` entry at the end → opens **ManageWorkoutsActivity** (the full workout
     list), which serves both picking any workout and starring favorites.
   - Tapping a chip selects that workout and fills the plan preview.
4. **Audio cue scheme row** — unchanged spinner, demoted below favorites.
5. **Workout plan preview** — existing `WorkoutPlanAdapter` list fills remaining space.

**Removals:** the floating `start_fab` include and the `status_layout` merge into the action
card; the FAB-gating logic in `StartFragment.updateStartButtonView()` is removed and replaced
by race-ready Start-enabling logic.

## 2. Race-Ready Start Flow

**Start tap (GPS sport, no fix yet):**

1. Tap **Start** → RunActivity opens immediately (no pre-fix gating of the button).
2. The existing **countdown** (`pref_countdown_active`/`pref_countdown_time`) runs on-screen;
   the countdown header shows animated GPS dots + live satellite count.
3. If there is **no fix when the countdown ends** (or countdown disabled), the run enters
   **Waiting for GPS**: a Tracker-level paused-until-fix state. Elapsed time and distance are
   frozen. Larger `Waiting for GPS` banner + animated GPS icon; workout plan still visible;
   a visible **Cancel** affordance.
4. On the **first fix**: short vibration + optional "GPS locked" cue → the run **auto-starts**
   from the waiting state.

**Non-GPS sports:** no wait, starts immediately (unchanged). GPS already fixed: the wait never
appears. No workout selected: uses the existing default/basic workout (matches today).

**Cancel while Waiting for GPS:** fully stops the run (stops tracker, returns to start screen;
same semantics as today's stop-without-save).

**Scope guardrail:** the gate applies only to the start of a run. Mid-run GPS loss remains on
the existing auto-pause behavior — no new behavior.

### Implementation approach: Tracker-level gate (chosen)

Extend the running mechanism with a lightweight "waiting for fix" condition so the run is in a
true paused state until the first fix. Consistent with all pause/auto-pause paths, background-
safe, elapsed clock always correct. Rejected alternatives: RunActivity-level gate (possible
clock desync, odd coach/auto-lap behavior) and start-screen-only wait (weakest, run cannot
actually start without a fix).

## 3. Favorites & Resume

- **Last used:** most recently started workout name in `pref_last_workout`, updated on every
  start. Always the `↻ Last used` chip.
- **Favorites:** starred workouts as an **ordered list in preferences**
  (`pref_favorite_workouts`), no schema change. Each workout row in **ManageWorkoutsActivity**
  gets a star toggle (filled = favorite).
- **Dedup:** if last-used is also a favorite, show once.
- **Selection:** tapping a chip selects the workout (plan preview + card reflect it); Start
  runs that workout. The current "advanced workout spinner" chooser role is replaced by the
  pinned row + the `choose workout…` entry.
- **Stale chips:** favorite/last-used file missing → chip hidden.

## 4. Settings & Preferences

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `pref_race_ready_start` | boolean | true | Enables one-tap countdown → wait → auto-start. Off restores today's strict "button only after fix" gating. |
| `pref_gps_locked_cue` | boolean | true | "GPS locked" audio cue at wait → start transition (honors mute + audio scheme). Short vibration always emitted. |
| `pref_favorite_workouts` | ordered list | empty | Favorite workout names. |
| `pref_last_workout` | string | empty | Most recently started workout name. |

**Reused unchanged:** countdown prefs, auto-pause, GPS enable/permission flows, audio cue
schemes, `WorkoutOrder`.

## Edge Cases

- Location permission revoked mid-start → existing permission dialog path.
- Tracker fails to CONNECT → existing dialog path.
- Stale favorite/last-used (deleted file) → chip hidden.
- Waiting state + app killed → nothing recoverable (recover-interrupted-run is explicitly out
  of scope; it is a later feature).

## Testing

- **Unit:** favorites serialization round-trip (ordered list, dedup, stale removal); last-used
  update on start; `pref_race_ready_start` off → old gating behavior.
- **Tracker-level gate:** wait → auto-start on simulated fix; countdown → wait when no fix;
  cancel = stop; no wait for GPS-less sports.
- **UI smoke (device):** start with GPS off → countdown → wait banner → mid-wait GPS enable →
  auto-start on fix → silent mode (vibration only); pinned row selection updates plan preview;
  ManageWorkouts star toggles appear in pins.

## Out of Scope (future scripts)

- Motivation suite (PR/best-efforts, streaks/milestones, run summary sheet) — sub-project B.
- History list upgrade (map thumbnails, elevation previews, week trend) — sub-project C.
- Crash-proof auto-save / interrupted-run recovery.
- Recorded-conditions recap (temperature/pressure).