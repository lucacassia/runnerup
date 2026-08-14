# Design: History page tab labels — Activities | Progress

**Date:** 2026-08-14
**Status:** Approved (user approved the design with one modification: "Statistics" → "Progress")

## Problem

On the History screen the top toolbar shows the title "History", and the two
sub-tabs read "History | Statistics". The user wants:

- The "History" title bar removed from the top of the screen.
- The in-page sub-tab "History" renamed to "Activities".
- The in-page sub-tab "Statistics" renamed to "Progress".

The bottom-navigation tab labeled "History" is unchanged.

## Changes

### 1. Remove the top toolbar (`app/res/layout/history.xml`)

- Delete the `MaterialToolbar` element (`history_actionbar`, currently the
  title "History" at `app/res/layout/history.xml:26-33`).
- Retarget the `history_tabs` TabLayout constraint from
  `Top_toBottomOf="@id/history_actionbar"` to `Top_toTopOf="parent"` so the
  sub-tabs sit at the very top of the screen.
- The toolbar has no menu, no navigation handling, and no Java references — it
  exists only to hold the title text (verified: only `history.xml` references
  `history_actionbar`). List/statistics content constraints are anchored to
  `history_tabs` and are unchanged.

### 2. Rename the sub-tab labels (`app/src/main/org/runnerup/view/HistoryFragment.java:142-143`)

- Tab 0: `org.runnerup.common.R.string.History` → `org.runnerup.common.R.string.Activities`
- Tab 1: `org.runnerup.common.R.string.Statistics` → `org.runnerup.common.R.string.Progress`

### 3. Common strings (`common/src/main/res/values/strings.xml`)

- Add `<string name="Activities">Activities</string>` (English only, matching
  the convention used for the statistics strings).
- Add `<string name="Progress">Progress</string>`.
- Remove the now-unused `<string name="Statistics">Statistics</string>`
  (verified unused: only `HistoryFragment.java:143` references it; it exists
  only in `common/src/main/res/values/strings.xml:260` — no translations), so
  no stale resource remains and no new `UnusedResources` lint warning appears.
- Keep `<string name="History">History</string>` — still referenced by the
  bottom-navigation menu (`app/res/menu/bottom_nav_menu.xml:26`).

## Not changed

- Bottom-navigation tab "History".
- Statistics screen content (totals cards, chart, titles like
  "Last 14 days") — only the sub-tab label changes.
- The `Statistics_*` strings (Statistics_last_14_days etc.) and the
  `org.runnerup.db.Statistics` class.

## Verification

- `./gradlew test` — no logic change; suite must stay green.
- `./gradlew :app:lintLatestDebug` — no new issues (watch for a new
  `UnusedResources` on the removed/added strings).
- `./gradlew spotlessApply` / `spotlessCheck`.
- `./gradlew :app:assembleLatestDebug` (+ `-Porg.runnerup.nomap` variant).
- On-device screenshot: toolbar gone, sub-tabs "Activities | Progress" at top,
  bottom nav still "History".
