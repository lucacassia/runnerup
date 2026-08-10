# Remove app-name bar from the recording screen

## Problem

While a workout is being recorded, `RunActivity` shows a window action bar
with the app name ("RunnerUp") at the top of the screen. The title bar is
not needed while recording and wastes vertical space.

## Goal

- Remove the app-name bar from the recording screen.
- Keep the system status bar visible; the recording content should start
  just below it (i.e., the space formerly occupied by the action bar is
  reclaimed, but content does not draw behind the status bar).

## Non-goals

- No change to other screens.
- No change to the status bar / navigation bar visibility.

## Changes

1. `app/AndroidManifest.xml`: change `RunActivity`'s theme from
   `@style/AppTheme` to `@style/AppTheme.NoActionBar` (removes the window
   action bar).
2. `app/src/main/org/runnerup/view/RunActivity.java` (onCreate inset
   listener on `R.id.start_view`): change the root view padding so the top
   is `insets.top` instead of the existing (zero) layout padding, keeping
   content below the status bar now that nothing else occupies that space.

`RunActivity` has no menu or action-bar API usage, so removing the bar
breaks nothing.

## Verification

- Build `:app:assembleLatestDebug`, install on the test device.
- Open the recording screen: no app-name bar, content starts below the
  status bar, lap/pause/stop FABs still clear the navigation bar.
- `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond the 25
  baseline), `spotlessApply` + `spotlessCheck`.
