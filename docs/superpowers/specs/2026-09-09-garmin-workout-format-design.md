# Garmin Modern Workout Format — Design Spec

**Goal:** Switch RunnerUp's workout file storage/import to Garmin's modern workout JSON format (`workoutSegments` / `ExecutableStepDTO` / `RepeatGroupDTO`, SI units, `zoneNumber` HR targets). Full switch: read & write new format only; legacy `com.garmin.connect.workout.json.UserWorkoutJson` files are no longer loadable.

**Scope:** `WorkoutSerializer.java` (single serialization point), import UX in `ManageWorkoutsActivity`, 4 bundled workout assets, 6 test-device workouts, one new unit test file, one new string. No changes to workout creation UI or the Basic/Interval run-builder path.

## Background (current state)

- `WorkoutSerializer` (de)serializes `com.garmin.connect.workout.json.UserWorkoutJson` — a flat `workoutSteps[]` with `groupId`/`parentGroupId` two-pass linking (`WorkoutSerializer.readJSON` L71-101, `findRepeatStep` L104-111), and legacy units (time in `ms`, distance in `cm`, pace in `centimetersPerMillisecond`; `scale()` L223, `putDuration`/`putTarget` L184-331).
- Internal model is SI and already recursive: `Workout.steps` is `ArrayList<Step>`; a `RepeatStep` (subclass of `Step`) holds its own nested `steps` list (`RepeatStep.java`) and flattens recursively via `Step.getSteps`. HR targets are always stored as explicit bpm `Range` (`Dimension.HR`) — UI (`StepButton`) resolves a zone to a bpm range at edit/save time.
- Consumers pass files through and need no change: `CreateAdvancedWorkout` (create/edit/rename), `StartFragment` (load advanced workout), `WorkoutListAdapter`, `WorkoutFileProvider` (MIME `application/vnd.garmin.workout+json`), `SyncManager.loadWorkouts` (`downloadWorkout` is a no-op today). Wear module does not use `WorkoutSerializer`.
- Import entry: `ManageWorkoutsActivity.onNewIntent`/`onCreate` catches `importData` failures at L184 and shows a fixed `Failed_to_import` dialog.

## Design

### 1. Modern format (what we read and write)

Root ([garmin-connect-cli] example, minimal client format; RW07D01 server echoes add extra ignorable keys):

```json
{
  "workoutName": "Intervals 5x3min",
  "sportType": {"sportTypeId": 1, "sportTypeKey": "running"},
  "workoutSegments": [
    {
      "segmentOrder": 1,
      "sportType": {"sportTypeId": 1, "sportTypeKey": "running"},
      "workoutSteps": [
        {
          "type": "ExecutableStepDTO",
          "stepOrder": 1,
          "stepType": {"stepTypeId": 1, "stepTypeKey": "warmup"},
          "endCondition": {"conditionTypeId": 2, "conditionTypeKey": "time"},
          "endConditionValue": 300.0,
          "targetType": {"workoutTargetTypeId": 1, "workoutTargetTypeKey": "no.target"}
        },
        {
          "type": "RepeatGroupDTO",
          "stepOrder": 2,
          "stepType": {"stepTypeId": 6, "stepTypeKey": "repeat"},
          "numberOfIterations": 5,
          "smartRepeat": false,
          "endCondition": {"conditionTypeId": 7, "conditionTypeKey": "iterations"},
          "endConditionValue": 5.0,
          "workoutSteps": [
            {
              "type": "ExecutableStepDTO",
              "stepType": {"stepTypeId": 3, "stepTypeKey": "interval"},
              "endCondition": {"conditionTypeId": 2, "conditionTypeKey": "time"},
              "endConditionValue": 180.0,
              "targetType": {"workoutTargetTypeId": 4, "workoutTargetTypeKey": "heart.rate.zone"},
              "zoneNumber": 2
            }
          ]
        }
      ]
    }
  ]
}
```

Canonical constant tables (verified against garmin-connect-cli, the RW07D01 server download, and python-garminconnect round-trip findings — IDs are authoritative for Garmin):

- **stepTypeKey** (id): warmup=1, cooldown=2, interval=3, recovery=4, rest=5, repeat=6.
- **endCondition** (id/key): lap.button=1 (no value), time=2 (value **seconds**), distance=3 (value **meters**), iterations=7 (value = count). `preferredEndConditionUnit` (id 2, `kilometer`, factor 100000) may be present; value is already meters — ignore on read, omit on write.
- **workoutTargetTypeKey** (id): no.target=1, speed=2 / speed.zone (m/s range via `targetValueOne`/`targetValueTwo`), heart.rate.zone=4 (**only** via `zoneNumber`, 1-5; bpm pairs are misinterpreted as m/s by Garmin — do NOT write bpm), pace.zone=6 (m/s range).

Units are SI: seconds, meters, m/s. Internal model already uses seconds/meters; only PACE needs the existing invert-on-read (`scale` with `metersPerSecond`/`secondsPerMeter` unit dims). `zoneNumber` is 1-based, identical to `HRZones` semantics.

### 2. `WorkoutSerializer` changes

**Read** — `readJSON(Reader in, HRZones zones)`:
- Parse root `workoutSegments[]`; concatenate each segment's `workoutSteps` in order into `Workout.steps`.
- `RepeatGroupDTO.type` → `RepeatStep` with `repeatCount` from `numberOfIterations` (fall back to `endConditionValue`), then recursively parse nested `workoutSteps` into `RepeatStep.steps`.
- `ExecutableStepDTO` → `Step` via existing `getIntensity`/`getDuration`/`getTarget` logic adapted to nested objects (`stepType.stepTypeKey`, `endCondition.conditionTypeKey`, `endConditionValue`, `targetType.workoutTargetTypeKey`, `targetValueOne/Two`, `zoneNumber`).
- Delete: `jsonstep` class, `groupId`/`parentGroupId` parsing/linking, `findRepeatStep`, cm/ms `scale()` for durations, `endConditionUnitKey`/`targetValueUnitKey` handling ("new only" — the legacy units tables are dropped).
- HR: `heart.rate.zone` with `zoneNumber` → `zones.getHRValues(zone)` → bpm `Range(min,max)`; if `zones == null`/unconfigured or zone invalid, log and drop the target (`no.target`).
- Throw new `public static class UnsupportedFormatException extends Exception` when the root lacks `workoutSegments` (covers legacy classic files and arbitrary JSON).

**Write** — `createJSON(Workout workout, HRZones zones)` and `writeFile(...)`:
- Recursive emit from `Workout.steps`: `RepeatStep` → `RepeatGroupDTO` (`numberOfIterations`, `smartRepeat:false`, `endCondition` iterations = count, nested `workoutSteps`); other steps → `ExecutableStepDTO`.
- `stepOrder` = monotonically increasing counter over the whole tree (matches examples).
- Duration: time → seconds, distance → meters, `null` duration → `lap.button` (id 1, no value).
- Targets: `no.target` default; PACE/SPEED → m/s ranges via existing `1/pace` math with `metersPerSecond` semantics; HR → `zones.match(min,max)` → `zoneNumber`; if zones unconfigured or no match, write `no.target` **and `Log.w`** (no silent data loss).
- Top level: `workoutName` (from the file name passed by callers, `.json` stripped), `sportType` (root + each segment) from a small `sportId → sportTypeId/key` map, `running` default (1). Omit `estimatedDurationInSecs`.
- Expose `createJSON(Workout, HRZones)` and `writeJSON(Writer, Workout, HRZones)` at package visibility for unit tests. Keep single-arg `readJSON(Reader)` (delegates, `zones = null`).

**Context threading** — `readFile(ctx, name)`/`writeFile(ctx, name, workout)` build `new HRZones(ctx)`; `ManageWorkoutsActivity.importData` passes `new HRZones(this)` to the new overload.

### 3. Import UX

In `ManageWorkoutsActivity` catch block (L184): if `e instanceof WorkoutSerializer.UnsupportedFormatException`, show dialog title `Error` + new string `Unsupported_workout_format` ("Unsupported workout format" — only supports Garmin workouts in the modern format); else keep existing `Failed_to_import` dialog. Add the string to `common/src/main/res/values/strings.xml` (lint ignores `MissingTranslation`).

### 4. Assets + device

- Rewrite 4 bundled assets (`app/assets/bundled/app_workouts/Super1000.json`, `8-6-4-2.json`, `MalinEwerlov.json`, `4x4.json`) to the modern format, preserving step structure/paces. `4x4.json` uses `lap.button` warmup/cooldown — expressible as conditionTypeId 1.
- Convert + re-push the 6 test-device workouts (RunnersWorld 10x400m, Intervals 5x3min, Easy Run 30min, Super1000, 8-6-4-2, MalinEwerlov) in modern format; smoke-test via Manage Workouts + editor.

### 5. Tests (`app/test/java/org/runnerup/workout/WorkoutSerializerTest.java`)

- Round trip: build a `Workout` (warmup + `RepeatStep` with interval/recovery; distance + time + lap.button steps; PACE target; HR target) → `createJSON` → `readJSON` → assert structure, units, nesting, targets.
- Parse fixture identical to garmin-connect-cli `workout-intervals.json` → assert HR zone→bpm resolution with a configured `HRZones` (use `HRZones(Resources, SharedPreferences)` + `save()` with a test prefs) and no-target when `HRZones` is null.
- `UnsupportedFormatException` thrown for a legacy classic-format file and arbitrary JSON.

### 6. Verification gate

`./gradlew test` → `:app:lintLatestDebug` (29-item baseline; only new issues matter) → `spotlessApply`/`spotlessCheck` → `:app:assembleLatestDebug` → device smoke test.

## Risks / behaviors to note

- **HR round-trip requires configured HR zones**: files write `zoneNumber` (1-based); read resolves zone→bpm via `HRZones`. If zones are cleared between creation and save, or unconfigured at import, HR targets become `no.target` with a log line.
- **Legacy classic workflows no longer load** (accepted): import shows the new `Unsupported_workout_format` dialog; `CreateAdvancedWorkout` shows its existing load-failure dialog for legacy files on disk.
- **Creation/UI unaffected**: Basic/Interval builds from prefs via `WorkoutBuilder` (no serializer); Advanced editing UI is unchanged — only the file format round-trip changes.

[garmin-connect-cli]: https://github.com/frankmatheron/garmin-connect-cli