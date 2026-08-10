# Recording screen bottom-button workflow

## Problem

The recording screen (`RunActivity`) shows three icon-only FABs
(`new_lap`, `pause`, `stop`) at all times. The stop action is reachable
mid-run even though stopping is a final action, and icon-only controls
give no label for what they do. After Stop the summary screen
(`DetailActivity` save mode) shows three icon-only FABs
(resume/save/discard) and lets the user resume from the summary, which is
inconsistent with a clear record/pause/stop/save workflow.

## Goal

- Recording state: only **Pause** (left) and **Next Lap** (right), as wide
  buttons that fill the row width with padding, equal size, icon on the
  left and text label.
- Paused state: the same two slots become **Resume** (left) and **Stop**
  (right), same style.
- Pressing **Stop** opens the activity summary where the bottom buttons
  are **Save** and **Discard** (in that order), same wide style.
- Keep the existing pause/resume recording semantics and the existing
  save/discard result handling.

## Non-goals

- No change to tracker notification actions (pause/resume/stop from the
  notification are untouched).
- No change to `DetailActivity` in MODE_DETAILS (viewing a saved activity
  from history).
- No change to audio cues or feedback logic.

## Design

### Button style

All bottom action buttons on both screens become Material Extended FABs:
`app:icon` on the left, `app:text` label, `colorPrimary` background with
`colorOnPrimary` icon/text, except destructive actions which use
`colorError`/`colorOnError`. Two buttons sit in a full-width horizontal
row, each `0dp` wide with `layout_weight=1` (equal size, fill the width),
container padding 16dp start/end, 8dp top, 16dp bottom, 8dp between the
buttons.

### Recording screen (`app/res/layout/run.xml`, `RunActivity.java`)

Replace the three FABs in `run_table_row1` with two Extended FABs,
keeping the existing ids:

- `pause_button` (left): recording shows `ic_pause` + `Pause`; paused
  shows `ic_play_arrow` + `Resume`. Always `colorPrimary`.
- `next_lap_button` (right): recording shows `ic_skip_next` + `Next Lap`
  (`colorPrimary`); paused shows `ic_stop` + `Stop` (`colorError`).

The right button's id changes from `new_lap_button` to `next_lap_button`
(field/click handler renamed to match; behavior unchanged). The two views
swap their icon/text/tint from a single method driven by
`workout.isPaused()`, called at bind time and after each pause/resume
toggle. `Next Lap` keeps its exact behavior
(`workout.onNewLapOrNextStep()`); pause/resume keep the existing
`togglePause()` semantics.

Remove `stop_button` from the layout and its field/click wiring. The
`setPauseButtonEnabled` pause/play icon swap is replaced by the state
method above.

### Stop -> summary (`RunActivity.java`, `DetailActivity.java`)

`Stop` (visible only while paused) keeps the current `doStop` flow:
`workout.onStop()`, stop the timer, drop the foreground notification,
launch `DetailActivity` with `mode="save"` and the activity ID. The two
launchers (`resumeLauncher`/`pausedLauncher`) collapse into one; the dead
`RESULT_FIRST_USER` branch in `onWorkoutResult` is removed (save ->
`RESULT_OK`, discard -> `RESULT_CANCELED`).

`DetailActivity` save mode: remove `resume_button`. Replace the
`save`/`discard` FABs with two Extended FABs, **Save** (`ic_check`,
`colorPrimary`) left and **Discard** (`ic_delete`, `colorError`) right, in
the existing horizontal LinearLayout inside the `buttons` container with
the same wide/equal style. The `upload_button` stays as-is (still hidden
in save mode until a sync is configured). Save/discard handlers are
unchanged. The back handler in save mode consumes the back event and does
nothing; MODE_DETAILS keeps its current back behavior (`finish()`).

### Lock feature removal (`RunActivity.java`)

Remove the double-tap-on-header lock gesture: the `OnTouchListener` on
`table_layout1`, the `mTapArray`/`mTapIndex` fields, and the
`Lock_activity_buttons_message` toast usage.

### Strings

Add `Next Lap` to `common/src/main/res/values/strings.xml`
(`<string name="NextLap">Next Lap</string>`). `Pause`, `Resume`, `Stop`,
`Save`, `Discard` already exist.

## Verification

- Gates: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond
  the baseline), `spotlessApply` + `spotlessCheck`,
  `:app:assembleLatestDebug`.
- Device (test phone, interval workout): recording shows wide Pause +
  Next Lap; Pause shows wide Resume + Stop; Stop opens the summary with
  wide Save + Discard; back on the summary does nothing; Save persists
  the activity and returns; Discard shows the confirm dialog and deletes.
  Verify no icon-only FABs remain on either screen and buttons clear the
  navigation bar.
