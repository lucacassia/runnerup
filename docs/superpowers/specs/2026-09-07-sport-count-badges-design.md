# Sport Count Badges on History Filter Chips

## Problem

The History screen's horizontal sport filter row currently shows a single static
count ("31 activities") in a TextView next to the chips. The per-sport count data
already exists (`Statistics.sportCounts()`), but it isn't surfaced on the chips.

## Goal

Show each sport's activity count as a small, sport-colored pill **inside** its
filter chip, at the trailing edge of the label. Remove the standalone count text.
A sport with zero activities shows **no pill**. The "All sports" chip shows the
grand total in a neutral grey pill.

## Decisions (validated)

- **Placement:** B — an in-chip pill at the trailing end of the label. Icon and
  label stay; pill is flush inside the chip after the text.
- **Zero count:** B — no pill when count == 0; the chip shows just icon + label.
  Zero-count sports remain filterable as today.
- **Freshness:** A — badges refresh on every list reload (`onLoadFinished`), on
  chip selection change, and at screen init, so they stay truthful after
  add/delete.
- **Approach:** I — render the pill as a custom `ReplacementSpan` (rounded
  background, white number) appended to the chip's label in the text flow. No
  manual coordinate math; inherits chip padding and font scaling.

## Current behavior being replaced

- `HistoryFragment.updateSportCount()` computes a single value (grand total when
  "All sports" selected, else the selected sport's count) and writes it to
  `sportCountText` (`history_sport_count` TextView).
- Called at init (line 207) and on chip-selection change (line 205).

## Design

### Pill span

New `PillSpan extends ReplacementSpan` (in `app/src/main/org/runnerup/view/`):

- `getSize()`: width = blank-char width (so a leading gap separates it from the
  label) + number glyph widths + horizontal padding (dp-scaled); height = font
  metrics descent - ascent.
- `draw()`: paint a fully-rounded rectangle filled with the pill color; draw the
  number centered in white. Vertical centering via font ascent/descent.

### Pill decision helper (TDD target)

Pure, unit-testable logic that decides what to render for a given chip:

- `All sports` (index 0): pill with grand total (sum of `counts`), neutral grey
  color, white number.
- sport `i`: pill with `counts[i]`, color = `Sport.colorOf(i)`, white number.
- `counts[i] == 0`: no pill — plain label.
- Guard bounds: if `i` is out of range of `counts`, no pill.

Colors: sport colors come from `Sport.colorOf()` (existing). Neutral grey for
All-sports chosen from solarized palette (`#586e75`), confirmed non-colliding
with any sport color at implementation.

### Data + refresh

- Reuse `Statistics.sportCounts(db)` (single grouped query, already off the main
  thread). Counts reflect all non-deleted activities regardless of the currently
  selected chip (same semantics as the old count text).
- Replace `updateSportCount()` with `refreshSportBadges()`; call it:
  - at screen init,
  - on chip-selection change,
  - from `onLoadFinished` (after each reload).

### Cleanup

- Remove `history_sport_count` TextView from `history.xml`; drop the
  `sportCountText` field and its write in `HistoryFragment`.
- Tighten/remove the chip scroller `paddingEnd` that was sized for the static text.
- Delete `Statistics_activities_count` plural from `common` strings only if it
  becomes unused (verify no other references first) to avoid a new lint
  `UnusedResources` issue.

## Testing

- TDD first: unit tests for the pill decision helper — All-sports total,
  per-sport value, zero → no pill, out-of-range safety.
- Span drawing is canvas work; verified by build + on-device smoke.
- Gate: `./gradlew test`, `:app:lintLatestDebug` (no new issues beyond
  `app/lint-baseline.xml`), `spotlessCheck`, `:app:assembleLatestDebug`.
- On-device smoke (phone `6a6743fd`): pills visible with correct numbers per
  chip, zero-count sport shows no pill, All-sports grey total, chip tap still
  filters, adding an activity refreshes badges on return.

## Out of scope

- No change to which sports appear (all sports remain filterable).
- No change to the selected-chip filter behavior.
- No change to sport icons in chips.
