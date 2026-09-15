# Personal Records as Medal Badges in the Progress Tab — Design

Date: 2026-09-14

## Problem

The current personal-record feature lives in a dedicated third tab (History → "Records") as full-width
rows. The user wants records to live **inside the Progress tab**, shown as compact, eye-catching
medal-style badges (2-column grid) at the bottom of the statistics scroll view. Records should be
filterable by the existing sport chip row, clickable to open the record's activity detail, and ranked
by more granular running distances with a suffix rule.

## Requirements

1. **Location:** Records render at the bottom of the Progress tab (`statistics.xml` scroll content),
   not as a separate tab. The History `TabLayout` returns to two tabs (Activities, Progress).
2. **Badge anatomy (per badge):** record label (e.g. `5K`), time, date, pace — styled as a medal:
   - label (uppercase label-medium, dim)
   - round "medal ring" holding the time — each distance gets its own ring color
   - below ring: `date · pace` line
   - a thin ribbon gradient bar across the card bottom, matching the ring color
3. **Grid:** 2 badges per row (weighted 50/50), 8dp gutters, section inside a `LinearLayout` that
   wraps into the existing `ScrollView`. Odd counts pad the last row with an invisible spacer.
4. **Click:** tapping a badge opens `DetailActivity` for the record activity (existing `openActivity`).
5. **Sport filter:** existing chip row drives `currentSport` (already saved in prefs). Records respect
   it: a selected sport shows only that sport's badges; "All sports" shows all sports' badges grouped
   under a per-sport header row (`Sport.textOf`).
6. **Running records:** fastest 1K, 1Mi, 2Mi, 5K, 5Mi, 10K, half marathon, full marathon, plus
   "Longest" (by distance).
   - **Suffix rule / truncation:** the cutoff is the **longest activity in the DB** for that sport.
     Only distance standards `D <= longestDistance` are shown; half/full (and any others above the
     cutoff) are omitted. "Longest" always shows last.
7. **Other sports:** only the "Longest" badge. By **distance** for GPS-tracked sports; by **time**
   for non-GPS sports (`Sport.isWithoutGps()` → treadmill, gym, stationary bike).
8. **Trophy on History rows:** keep. Trophy badges still appear on rows in the Activities tab whose
   activity holds any current record (derived from the same compute).
9. **Empty state:** if the selected filter yields no records, the entire records section (header +
   grid) is hidden.

## Non-Goals

- No achievement history, no chart, no "times beaten", no new DB tables.
- No change to the statistics cards/chart logic above the records section.
- Imperial/metric unit strings are presentation only; distances are compared in meters.

## UI Sketch

```
Progress tab (ScrollView)
┌────────────────────────────┐
│ this week | this month | 1y │  (existing)
│ [Distance|Time|Elev]        │
│ ▓▓▓▅▇ chart ▓▓▓            │
│ day|week|month|year toggle  │
│                            │
│ PERSONAL RECORDS            │
│ Running                    │← per-sport header when All sports
│ ┌──────────┐ ┌──────────┐  │
│ │  5K      │ │  1K      │  │
│ │  (24:40) │ │  (4:12)  │  │
│ │ 8/1·4:56 │ │ 6/2·4:12 │  │
│ │ ▁▂▂▁ribbon▁▂▂▁ │ ▁▁▂▁ │  │
│ └──────────┘ └──────────┘  │
│ Biking                    │
│ ┌──────────┐ ┌ (spacer)  │  │
│ │ Longest  │ │           │  │
│ └──────────┘ └──────────┘  │
└────────────────────────────┘
```

## Architecture & Components

### Data layer (unchanged compute core)

- `computeRecords(...)`: now sport-aware. Returns per-sport records with a sport, distance-standard
  label, activity id, time, distance, start time.
- **Running:** query the max distance `D_max` for the sport (longest activity). For each standard in
  ascending order `[1K, 1Mi, 2Mi, 5K, 5Mi, 10K, half, full]`, include the best-time record only if
  `standardDistance <= D_max`. Always append "Longest" (max distance). `1K=1000m, 1Mi=1609.344m,
  2Mi=3218.688m, 5K=5000m, 5Mi=8046.72m, 10K=10000m, half=21097.5m, full=42195m`.
- **Other sports:** single record — for GPS sports order by distance desc, else by time desc, filter
  nulls, `LIMIT 1`.
- Distance-band best-time query: `distance BETWEEN D*0.95 AND D*1.05 ORDER BY time ASC LIMIT 1` —
  the ±5% tolerance (both directions) credits runs that measure just under the standard (e.g. a
  4,999 m run still counts toward the 5K; a "1K" run finishing at 999.9 m still counts).

### New / changed files

- **New `app/res/layout/progress_records_section.xml`** (or inline in `statistics.xml`): contains
  `records_section` (title `@string/records_section_title`), a `LinearLayout` grid container
  `records_grid`, sits at the bottom of `statistics.xml`'s vertical `LinearLayout`.
- **New `app/res/layout/record_badge.xml`**: single medal badge card, `layout_width=match_parent`
  inside a 50/50 weighted row at runtime.
- Badges need **no** trophy icon — the ring + ribbon is the visual. (The existing `ic_trophy_20dp`
  stays for History-row trophies.)
- **Edit `app/res/layout/statistics.xml`**: add records section at bottom; keep everything above.
- **Edit `history.xml`**: remove `records_content` FrameLayout + `history_tab1_layout`'s third-tab
  leftovers (the `TabLayout` is populated in code, so mainly remove the records FrameLayout).
- **Edit `HistoryFragment.java`**:
  - Remove `TAB_RECORDS_INDEX` and the third `addTab`. `selectTab` no longer toggles records content.
  - Records state: `recordsGrid` (LinearLayout), built on main thread from async computed records;
    uses a small `RecordBadgeHolder`/view-binding helper instead of a `RecyclerView` adapter.
  - `computeRecords` reworked as above; results posted to main thread → `renderRecordBadges(...)`.
  - `renderRecordBadges`: for the filtered/grouped list, inflate `record_badge.xml` per badge, group
    under sport-header views (only when `currentSport == null`), add weighted rows of 2, hide section
    when empty, keep `recordHolderActivityIds` in sync for row trophies, then
    `adapter.notifyDataSetChanged()`.
  - Trigger `loadRecords()` on Progress tab select/reselect/resume and on chip change (as today).
- **New color resources** in `app/res/values/colors.xml` (or kept local to code): per-distance ring
  colors (violet 1K, blue 1Mi, teal 2Mi, gold 5K, orange 5Mi, red 10K, deep purple half, midnight
  full, bronze-gold Longest).
- **Strings** in `app/res/values/strings.xml`: `records_section_title` ("Personal records"),
  labels without "Best": `1K`, `1Mi`, `2Mi`, `5K`, `5Mi`, `10K`, `Half Marathon`, `Marathon`,
  `Longest`. Remove/replace obsolete `records_*` best strings.

### Threading (unchanged pattern)

`statisticsExecutor` → `computeRecords(mDB)` → `mainHandler.post { renderRecordBadges(badges) }`.

### Trophy sync

`recordHolderActivityIds` is filled during badge build (same set as today), so History rows still show
the gold trophy for record-holding activities, now reflecting the new distance set.

## Error Handling

- No records for the filter → hide `records_section` entirely.
- Record with null time/distance → skipped by the query `IS NOT NULL` filters; badge never rendered.

## Testing

- `./gradlew test` (+ `:app:lintLatestDebug`, `spotlessApply`/`spotlessCheck`, `assembleLatestDebug`).
- Manual device check: Progress tab shows badges for running (with your real 1K–10K + Longest data),
  truncation hides half/full, switching chips filters/regroups, tapping badges opens the activity,
  Activities rows keep gold trophies.