# Best-Effort Segment Records — Design

Date: 2026-09-15

Supersedes the "distance band" definition of running records from
`2026-09-14-records-progress-badges-design.md`.

## Problem

Today a running record for distance `D` (1K…Marathon) requires an activity whose **total** length
falls in `[0.95·D, 1.05·D]` and takes its best time from the whole run. That misses the useful case
most running apps handle: a run of, say, 10 km also contains consecutive stretches of 1K, 5K, etc.
If the fastest 5K someone ever ran happened inside a 10K run, it should be the 5K record — regardless
of what the 10K's total time was. This is Strava's "best effort" model.

## Requirements

1. **Rung records** (1K, 1Mi, 2Mi, 5K, 5Mi, 10K, half, full) for running = the **fastest candidate
   effort** of exactly `D` meters across that sport, where a candidate is either:
   - **(a) a GPS segment:** a consecutive stretch of exactly `D` m extracted from a run's
     `location` track — including stretches that end in the middle of a longer run; or
   - **(b) a whole-run credit:** any run whose stored total distance is within `[0.95·D, 1.05·D]`
     counts with its whole moving time (the current ±5% rule, kept). This preserves near-standard
     runs (e.g. a 4998 m / 21:35 "5K") and is the only way a trackless/manual run can earn a rung.
   - When a run qualifies both ways, the faster candidate wins.
2. **Moving time:** segment time uses the run's moving-time clock (`elapsed`), which already
   excludes paused wall time — consistent with every pace shown in the app.
3. **Segments may not span a pause:** a candidate window is rejected if it spans an inter-point
   pause (wall-clock minus elapsed) larger than `GAP_MS = 60_000`. This prevents a real pause
   from producing an artificially fast record, while allowing slow-but-continuous sampling
   (where wall-clock delta equals elapsed delta) to contribute. Note: the plan's implementation
   text ("wall-clock gap") is a simplification; the excess-over-elapsed form is what the tests
   require and what the `LocationEntity.LocationList` semantics (pause edges have elapsed delta = 0)
   make functionally equivalent on real data.
4. **Everything else stays:** truncation (`D <= longest` run), the "Longest" badge (single longest
   run), Longest-for-other-sports, badge anatomy/ring colors, sport-chip filtering, badge tap →
   `DetailActivity`, History-row trophies, empty-filter hides the section.
5. **Data source:** per-run points come from `LocationEntity.LocationList`
   (`org.runnerup.db.entities.LocationEntity`), which already reconstructs each point's cumulative
   distance and moving elapsed from the `location` table and skips pause/discard semantics. Windows
   use that cumulative-distance curve as-is (it tracks the stored total within ~0.5%).

## Non-Goals

- No new DB tables or schema changes; no efforts written at recording time.
- No split/lap visualization on the map; tapping a badge still opens the activity detail.
- No change to which distances are rungs or to other sports' Longest semantics.

## Architecture & Components

### New pure calculator: `BestEffort`

New file `app/src/main/org/runnerup/db/BestEffort.java` (plain Java, no Android deps — unit
testable like `RecordUtils`).

- Input: ordered run points `(cumulativeDistanceMeters, timeMs, elapsedMs)` and target distance `D`.
- Algorithm (per run, per distance, O(#dist · n) with a monotonic pointer):
  1. For each end point `i` with `cumDist[i] >= D`, keep the latest start index `s` such that
     `cumDist[s] <= cumDist[i] - D` (monotonic: as `i` advances, `s` never moves backward).
  2. The window start lies between points `s` and `s+1`; interpolate its timestamp linearly to sit
     at exactly `cumDist[i] − D`.
  3. Window time = `elapsed[i] − interpolatedStart`.
   4. Reject the window if any consecutive-point **pause** (wall-clock minus elapsed time)
      gap inside it exceeds `GAP_MS` (drop the window and restart the pointer across such gaps).
  5. Keep the minimum window time.
- Output: the best effort `{windowStartMs, windowEndMs, timeMs}` or `null` when the run never
  reaches `D`.
- Constants: `GAP_MS = 60_000`.

### Reworked record computation in `computeRecords()` (HistoryFragment.java:479)

- Keep the sport loop and the running truncation (`D <= longest`).
- For each running rung `D`:
  - If any whole-run candidates exist (stored distance ∈ `[0.95·D, 1.05·D]`), their time is their
    whole moving time.
  - If any run with a track that reaches `D` exists, `BestEffort` gives the fastest exactly-`D`
    window across all such runs.
  - The rung record = the minimum of both; carries `activityId` (of the run the winner came from),
    `timeMs`, `distance = D`, `startTime`, plus the existing label/ring-color wiring.
  - If neither produces a candidate for `D`, no badge (same as today).
- `queryBestRecord` band query is deleted; the ±5% whole-run credit is expressed directly as a
  whole-run candidate (one extra `SELECT _id, time, distance, start_time FROM activity ...` with the
  band predicate). `queryLongestDistance` / `queryLongestRecord` (Longest) are unchanged.
- Rendering, trophies (`recordHolderActivityIds`), and click handling are untouched.

### Caching: fingerprint memo

`loadRecords()` currently fires on resume, tab select/reselect and chip change — each would now do a
track scan. Memoize the computed rung records in memory: recompute only when a cheap fingerprint of
the relevant data changes (`max(_id)`, `count(*)` of `deleted == 0` activities). Computed on the
existing `statisticsExecutor`; the memo is held in the fragment's lifetime and invalidated on any
fingerprint change.

## Error Handling

- Run with no usable track (`location` count too small to cover `D`) → no segment candidates; only
  the ±5% whole-run credit may apply.
- Trackless/manual runs → whole-run ±5% credit only.
- Run/deleted activities already excluded by the fingerprint + queries (`deleted == 0`).
- Zero candidates for a rung → badge not rendered (section may still show other rungs).

## Testing

- `BestEffort` unit tests (`app/test/java/org/runnerup/db/BestEffortTest.java`), covering:
  - constant-speed run: window time == `D / speed`;
  - **faster interior stretch beats the whole run** (the motivating case);
  - run shorter than `D` → `null`;
  - window spanning a 120 s point gap → rejected;
  - interpolation lands exactly on distance `D`.
- Existing gates: `./gradlew test`, `spotlessApply`/`spotlessCheck`,
  `:app:assembleLatestDebug`, `:app:lintLatestDebug` (baseline tolerated).
- Device check against the current DB: the hybrid must still show 1K / 5K / 10K / Longest badges
  (dedicated fast runs still win unless an interior stretch is genuinely faster); confirm a badge
  found from inside a longer run opens the right activity; trophies and filters unchanged.