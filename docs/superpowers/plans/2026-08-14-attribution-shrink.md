# Attribution Pill Shrink Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the map attribution pill ("© OpenStreetMap contributors © CARTO") small and unobtrusive while keeping it legally present.

**Architecture:** Layout-only change. Shrink the pill's text and padding in `run.xml` and drop the pill background alpha in the day/night color resources. Attribution remains visible (required by CARTO ToS + OSM ODbL).

**Tech Stack:** Android XML resources, Gradle 9.6.1 (wrapper), JDK 17.

## Global Constraints

- Text size 8sp (explicit `android:textSize` overriding `?attr/textAppearanceLabelSmall`).
- Padding: horizontal 4dp, vertical 1dp.
- `mapAttributionBg`: `#66FFFFFF` → `#40FFFFFF` (day), `#66000000` → `#40000000` (night).
- Pill position/margins/string/drawable/wiring unchanged; pill must stay `VISIBLE`.
- No unit tests (layout-only). No comments added.
- Verify gates in order before finishing: `./gradlew test`, `:app:lintLatestDebug` (25 baseline-filtered allowed, no new), `spotlessApply` then `spotlessCheck`, `:app:assembleLatestDebug` then `:app:assembleLatestDebug -Porg.runnerup.nomap`.
- Conventional commit (`style:`).
- Do NOT stage user-local files: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.

---

### Task 1: Shrink the attribution pill

**Files:**
- Modify: `app/res/layout/run.xml:31-46`
- Modify: `app/res/values/colors.xml:43`
- Modify: `app/res/values-night/colors.xml:7`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new; visual change only.

- [ ] **Step 1: Shrink text and padding in `run.xml`**

In the `@+id/map_attribution` TextView, change the four padding attributes and add `android:textSize`:

```xml
        android:paddingStart="4dp"
        android:paddingTop="1dp"
        android:paddingEnd="4dp"
        android:paddingBottom="1dp"
        android:text="@string/map_attribution"
        android:textSize="8sp"
        android:textAppearance="?attr/textAppearanceLabelSmall"
```

(`paddingStart/End` 8dp → 4dp, `paddingTop/Bottom` 2dp → 1dp, `textSize` 8sp added after `android:text`.)

- [ ] **Step 2: Reduce pill background alpha**

`app/res/values/colors.xml:43`: `<color name="mapAttributionBg">#66FFFFFF</color>` → `<color name="mapAttributionBg">#40FFFFFF</color>`.

`app/res/values-night/colors.xml:7`: `<color name="mapAttributionBg">#66000000</color>` → `<color name="mapAttributionBg">#40000000</color>`.

- [ ] **Step 3: Run gates**

Run in order:

```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: all pass; lint reports no new issues (25 filtered by `lint-baseline.xml`); spotless makes no unexpected changes.

- [ ] **Step 4: On-device verification**

Build the map variant APK last (the nomap build above overwrites the shared output path), install on the Nexus 5X (serial `025b46e24edcbca6`), launch, navigate to the run screen (Start → Treadmill → confirm GPS → Start), and confirm in BOTH day (`adb shell cmd uimode night no`) and night (`adb shell cmd uimode night yes`, force-stop + relaunch) that the pill renders smaller with a fainter background and the text is still readable. Screenshot to `/tmp/opencode/attribution_shrink_day.png` and `/tmp/opencode/attribution_shrink_night.png`. Record results (visible text, smaller pill, faint bg) in the plan's SDD workspace report.

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/run.xml app/res/values/colors.xml app/res/values-night/colors.xml
git commit -m "style: shrink and de-emphasize map attribution pill"
```

---

## Self-Review Notes

- **Spec coverage:** textSize (Step 1), padding (Step 1), alpha day+night (Step 2), gates (Step 3), on-device day+night (Step 4). Position/margins/string/wiring untouched. ✓
- **Placeholder scan:** no TBDs; all values literal.
- **Consistency:** `mapAttributionBg` referenced only by `map_attribution_bg.xml` (verified); the pill stays `VISIBLE` via unchanged LiveMap wiring.
