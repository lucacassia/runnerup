# "Setup Run" Record Flow — Design

Date: 2026-09-15

## Problem

The record screen forces every run setup onto one cluttered surface: sport spinner,
audio-cue spinner, workout spinner, and an inline step editor all visible at once,
with the Start button pinned to GPS state. Choosing a workflow is buried in spinners,
the step editor is always present even when the user wants an untouched workout, and
there is no guided progression from "start" to "go".

## Goal

An iOS-style, streamlined flow: a minimal record screen with exactly two actions
(**Start** and **Start GPS**); tapping Start pushes a "Setup Run" page where sport,
audio cues, and workout are chosen from lists (last-used values pre-selected) and where
the chosen workout's steps can be optionally edited in place; the workflow always allows
returning to a previous step; recording itself behaves exactly as today.

## Requirements

1. **Record screen is minimal.** It shows only the Start button (a pill FAB, always
   enabled) and the existing GPS status bar with its "Start GPS" control. The
   `start_advanced` tab contents (spinners + inline step list) are gone from this surface.
2. **Tap Start → Setup Run page.** A pushed, full-screen page inside the Record tab
   (bottom nav hidden), with a back chevron returning to the record screen.
3. **Setup Run rows** (iOS grouped list, value + chevron on the trailing side):
   - Section **Activity** → row **Sport**.
   - Section **Guidance** → row **Audio cues**.
   - Section **Workout** → row **Workout** (value "None" allowed → GPS-only run).
   - When a workout is selected, its **editable step list** renders beneath the Workout
     row ("Tap a step to edit — optional"), using the exact step-row UI and edit
     behavior used on the record screen today (repeat groups, timer/pause/delete).
4. **Pickers.** Tapping a row pushes a list (sport / audio cues / workouts) with radio
   selection; back returns to Setup Run with the new value applied. Workouts list keeps a
   "Manage workouts…" row → `ManageWorkoutsActivity`, and a "None" entry.
5. **Pre-selection.** The page opens with the last-used sport, audio scheme, and workout
   pre-selected (the same pref keys written/read today), so a returning runner can go
   straight to **Start Run**.
6. **Start Run gating is unchanged.** The confirm button follows today's exact rule:
   disabled until tracker `CONNECTED`, except sports that need no GPS (Manual /
   `sportWithoutGps`) and the race-ready deferred-start path. A GPS chip above the button
   reflects live state ("GPS ready" / "Waiting for GPS…").
7. **Recording behavior is unchanged.** Tapping **Start Run** calls the existing
   `startWorkout()` path — prepare workout, apply audio cues, race-ready/deferred
   decision, launch `RunActivity`. Step edits persist to the workout file via the
   existing `onWorkoutChanged()` → `WorkoutSerializer.writeFile` path.
8. **Always return.** System Back and the toolbar chevron unwind picker → Setup Run →
   record screen, in order.

## Non-goals

- No changes to workout creation UI, `RunActivity`, tracker/GPS/HR/Bluetooth handling,
  or the workout file format.
- No separate Activity for the flow; no changes to the recording data model.
- No favorites pins / action-card (removed feature stays removed).

## Architecture

All within `StartFragment` (single-activity app). `tab_content` (the existing
`FrameLayout` in `start.xml`) hosts three mutually-exclusive visibility roots:
`record_root` (reworked `start_advanced` → minimal), `start_setup` (the Setup Run page),
and picker roots. Voice/UX is "pushed page": horizontal slide + fade transitions, bottom
nav hidden via a new `MainLayout.showBottomNavigation(boolean)` hook, restored on back.

| Root | Content |
|---|---|
| `record_root` | Toolbar "Record"; hero "Start" pill (always visible); bottom GPS `status_layout` (unchanged). |
| `start_setup` | Toolbar "‹ Record" + "Setup Run"; grouped-list section headers + rows; editable step list; footer GPS chip + Start Run button. |
| `picker_root` | Generic full-screen list reused for sport / audio / workouts; radio selection; "Manage workouts…" row only for workouts. |

### Navigation state

A tiny push-state stack (`List<String>` of root ids) drives transitions and the
`OnBackPressedCallback`. Pushing a root slides in from the right while the previous
slides slightly left + fades; popping reverses.

### State / data flow

- Selection values are read from and written to the same `SharedPreferences` keys the
  spinners use today (`pref_sport`, `pref_advanced_audio`, `pref_advanced_workout`), so
  all existing settings wiring (Summary screen, race-ready defaults) keeps working.
- The selected workout is loaded/kept exactly as today (`advancedWorkout` field,
  `WorkoutListAdapter` keyed by name, `WorkoutBuilder.prepareWorkout` at start). Step
  edits mutate that object and persist via `onWorkoutChanged` (unchanged).
- GPS readiness flows from the existing `GpsStatusControl` / tracker state — the GPS chip
  on Setup Run reuses `gps_indicator`/`gps_message` and the state listeners already wired
  in `onTick()`/`updateView()` (unchanged logic, new surface).

### UI layout (per mockups, approved)

- Record: centered "Record" toolbar; Start pill button; bottom GPS bar identical to today.
- Setup Run: title bar with back chevron; grouped white cards on grey background:
  `Activity` (Sport row), `Guidance` (Audio cues row), `Workout` (Workout row + editable
  steps below with per-step Edit affordance and hint text); pinned footer containing the
  GPS chip and a filled "Start Run" button.
- Picker: "‹ Setup Run" title bar; list rows with check mark on the selected item; an
  inset "Manage workouts…" action row for the workouts list.

## Files & components

- Layouts: rework `app/res/layout/start.xml`; new `start_setup.xml`; new generic
  `picker_*.xml` roots (or one parameterized layout with three views per list). The step
  rows reuse the existing plan-adapter row layouts from `start_advanced.xml`.
- Code: `StartFragment.java` (wizard roots, push-state, transitions, back handling;
  remove dead `start_advanced` spinner wiring), `MainLayout.java` (nav visibility hook),
  small custom adapters for the lists reusing `SportAdapter`,
  `AudioSchemeListAdapter`, `WorkoutListAdapter`.
- Strings: several new in `app/values/strings.xml` (Setup Run, Activity, Guidance,
  Workout, Audio cues, None, Start Run, Waiting for GPS…, Tap a step to edit — optional).
- `start_advanced.xml` retired (its step-list widgets may move to `start_setup.xml`).

## Error handling

- No workout selectable / none installed: workouts list still offers "None" and
  "Manage workouts…" (which can create via `CreateAdvancedWorkout`); behavior mirrors the
  current `SpinnerPresenter` "Manage workouts…" auto-selection when none exist.
- `WorkoutSerializer.writeFile` failures during step edits surface the same
  `Failed_to_load_workout` dialog as today (unchanged `onWorkoutChanged`).

## Testing

- Existing unit tests continue to pass; add tests only for pure new logic if cheap in the
  existing `app/test/` harness (e.g. push-state ordering).
- Gates: `./gradlew test`, `:app:lintLatestDebug` (no new baseline items),
  `spotlessApply` + `spotlessCheck`, `:app:assembleLatestDebug`.
- Device smoke (OnePlus Nord CE `5717a66e`): record screen shows only Start + Start GPS;
  Start → Setup Run; change sport/audio/workout via pickers; edit a step; back at each
  level; GPS chip states; Start Run with GPS gated and race-ready deferred; verify
  persistence of choices after restart; confirm no regression to GPS/permission start.

## Risks & mitigations

- Risk: navigation/transition complexity in `StartFragment` (already a large file).
  Mitigation: transitions are thin (visibility + `AnimationUtils`), the push-state is a
  small list, and the recording path (`startWorkout`, GPS, `RunActivity`) is untouched.
- Risk: hiding the bottom nav surprises users. Mitigation: `MainLayout` hook is a simple
  visibility toggle restored on back/leave, matching the "pushed page" mental model.