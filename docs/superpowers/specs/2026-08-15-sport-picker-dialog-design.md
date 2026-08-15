# Design: Sport picker dialog with icons

**Date:** 2026-08-15
**Status:** Approved

## Problem

The sport selector on the record page (StartFragment toolbar) is a
`MaterialSportSpinner` (an `MaterialAutoCompleteTextView` —
`app/src/main/org/runnerup/widget/MaterialSportSpinner.java:34`). Tapping it
opens a narrow, anchored exposed-dropdown list (`app/res/layout/actionbar_dropdown_spinner.xml`
— a plain `TextView` row with 8dp padding). The rows are visually too dense and
cramped, and entries have no icons.

## Scope findings

- The selector sits in the `MaterialToolbar` (`app/res/layout/start.xml:26-44`),
  shared by all three tabs. The collapsed element shows the sport name as
  plain title-styled text plus a drop-down arrow end-drawable
  (`MaterialSportSpinner.java:57-64`).
- 8 sports, adapter position == DB value (0-7):
  Running, Biking, Other, Orienteering, Walking, Treadmill, Gym, Stationary
  bike (`app/src/main/org/runnerup/workout/Sport.java:27-35,63-90`).
- 5 of 8 sports already have vector icons: `app/res/drawable/sport_running.xml`,
  `sport_biking.xml`, `sport_walking.xml`, `sport_orienteering.xml`,
  `sport_other.xml` (16dp, per-sport Solarized fill). Treadmill, Gym, and
  Stationary bike have no icon and no entry in `Sport.colorOf()`
  (`Sport.java:151-167`).
- `MaterialAlertDialogBuilder` (Material 1.14.0) is already used throughout
  `StartFragment`; `setSingleChoiceItems` dialog patterns exist in
  `HRSettingsActivity.java:399,507` and `DetailActivity.java:1083`.
- Selection listener already exists at `StartFragment.java:313-331`: it
  delegates to the presenter listener, then `setGpsNotRequired(...)` and
  `updateView()`. The sport pref is `pref_sport` = `"startSport"`
  (`app/res/values/pref_keys.xml:76`).

## Change

### 1. Selection surface: Material alert dialog with icon rows

Tapping the toolbar sport element opens a `MaterialAlertDialogBuilder` dialog
instead of the auto-complete dropdown.

- Title: "Sport" (existing `common` string, `common/src/main/res/values/strings.xml:75`).
- One row per sport: 24dp vector icon tinted with the sport's Solarized color
  (`Sport.colorOf()`), sport label (from `Sport.getStringArray`, translated),
  trailing radio indicator. Row height ~56dp, 16dp padding.
- The current selection (from the `startSport` pref) is pre-checked. Tapping a
  row selects and closes immediately — no OK button.
- Cancel via outside-tap / back is unchanged.

### 2. Toolbar display: icon + name

The collapsed toolbar element shows the sport icon (tinted with its sport
color) next to the name, e.g. "» Running ▾" — matching the History list
treatment. Keep the existing `MaterialSportSpinner` widget: replace its
`showDropDown()` on tap (`MaterialSportSpinner.java:102-108`) with firing an
open callback to `StartFragment`, which shows the dialog. The dropdown
machinery (filter, popup) becomes inert.

- Selection from the dialog reuses the existing listener path
  (`StartFragment.java:313-331`), so GPS-required toggling and pref
  persistence keep working unchanged.
- The leading icon is set on the widget's text view with
  `Sport.drawableColored16Of(...)`-style tinting via
  `Sport.colorOf(...)`.

### 3. Icons

- Reuse the 5 existing `sport_*.xml` vectors unchanged (already vectors).
- Add 3 new vector drawables in `app/res/drawable/`, 16dp like the existing
  ones (scale freely to 24dp in the dialog), path data from the Material icon
  set (Google's material-design-icons / Material Symbols):
  - `sport_treadmill.xml` — Material `treadmill` glyph
  - `sport_gym.xml` — Material `fitness_center` (dumbbell)
  - `sport_stationary_bike.xml` — Material `pedal_bike` glyph
- All new icons are vector XML; no PNGs. Exact path data verified from the
  Material icon sources during implementation.

### 4. Colors

Extend `Sport.colorOf()` with the 3 unused Solarized accents; existing 5
entries unchanged:

- Treadmill → cyan `#2aa198`
- Gym → red `#dc322f`
- Stationary bike → blue `#268bd2`

### 5. Not changed

- History, Detail, and wear sport displays (they already show icons/colors).
- The other StartFragment selectors (goal, pace, HR, audio cues).
- The 5 existing sport icons and their colors.

## Verification

- Extend existing `Sport` tests for the new color mapping (`app/test/java`).
- `./gradlew test`, `./gradlew :app:lintLatestDebug` (no new issues beyond the
  25 in `app/lint-baseline.xml`), `./gradlew spotlessApply` /
  `spotlessCheck`, `./gradlew :app:assembleLatestDebug` (+
  `-Porg.runnerup.nomap` variant).
- On-device: open the record page, tap the sport element — the dialog shows 8
  icon rows with the current selection checked; picking another sport updates
  the toolbar (icon + name + tint) and GPS-required state and persists the
  pref; verify both day and night themes. Capture screenshots for evidence.
