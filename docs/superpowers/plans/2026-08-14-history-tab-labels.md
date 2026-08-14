# History Tab Labels — Activities | Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the "History" toolbar title from the History screen and rename the in-page sub-tabs from "History | Statistics" to "Activities | Progress".

**Architecture:** Three small, atomic edits: (1) delete the `MaterialToolbar` from `history.xml` and re-anchor the sub-tab `TabLayout` to the top of the screen; (2) point the two sub-tab labels at two new `common` strings and remove the orphaned `Statistics` string; (3) verify on device. No logic changes, no unit tests — gates are build, lint, spotless, and a device screenshot.

**Tech Stack:** Java + AndroidX/Material 3. Resources split: labels/strings live in `common` (`org.runnerup.common.R`), app layouts in `app/res/`.

## Global Constraints

- Verify each task with: `./gradlew test`, `./gradlew :app:lintLatestDebug` (no NEW issues beyond the 25 in `app/lint-baseline.xml`), `./gradlew spotlessApply` + `spotlessCheck`, `./gradlew :app:assembleLatestDebug`, and `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap` (build the map variant LAST — nomap overwrites the APK path).
- No code comments. Google Java Format via spotless.
- String resources referenced from Java use the fully-qualified `org.runnerup.common.R.string.*` form (see existing pattern at `HistoryFragment.java:142`).
- Conventional commits: `feat:`, `docs:`, `style:`.
- Do NOT stage user-local files: `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- Spec: `docs/superpowers/specs/2026-08-14-history-tab-labels-design.md`.

---

### Task 1: Remove the top toolbar from the history page

**Files:**
- Modify: `app/res/layout/history.xml:26-33,41`

**Interfaces:**
- Consumes: nothing.
- Produces: a layout where the `@id/history_tabs` TabLayout is anchored to the parent top (no `history_actionbar`). Task 2 depends only on `HistoryFragment` Java, not this layout.

- [ ] **Step 1: Delete the `MaterialToolbar` element**

In `app/res/layout/history.xml`, delete the entire block:

```xml
    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/history_actionbar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:title="@string/History" />
```

- [ ] **Step 2: Re-anchor the TabLayout to the top**

In the same file, on the `history_tabs` element, replace:

```xml
        app:layout_constraintTop_toBottomOf="@id/history_actionbar" />
```

with:

```xml
        app:layout_constraintTop_toTopOf="parent" />
```

The `history_list_content` FrameLayout and the `statistics_content` include stay anchored below `history_tabs` and need no change. The toolbar is referenced nowhere else in the project (no Java, no menu).

- [ ] **Step 3: Build and lint**

Run:
```bash
./gradlew :app:assembleLatestDebug
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
./gradlew :app:lintLatestDebug
```
Expected: no new issues (baseline-filtered message is fine).

- [ ] **Step 4: Spotless check**

Run:
```bash
./gradlew spotlessApply && ./gradlew spotlessCheck
```
Expected: both pass (no Java changed here, so no-op).

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/history.xml
git commit -m "style: remove history page toolbar title"
```

---

### Task 2: Rename sub-tabs to Activities and Progress

**Files:**
- Modify: `app/src/main/org/runnerup/view/HistoryFragment.java:142-143`
- Modify: `common/src/main/res/values/strings.xml` (lines 259-260 area)

**Interfaces:**
- Consumes: the two string ids added in Step 2 (`org.runnerup.common.R.string.Activities`, `org.runnerup.common.R.string.Progress`).
- Produces: sub-tabs labeled "Activities" and "Progress". Later tasks/tests do not consume these.

- [ ] **Step 1: Update the sub-tab labels**

In `app/src/main/org/runnerup/view/HistoryFragment.java`, lines 142-143, change:

```java
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.History));
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Statistics));
```

to:

```java
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Activities));
    historyTabs.addTab(historyTabs.newTab().setText(org.runnerup.common.R.string.Progress));
```

- [ ] **Step 2: Update common strings**

In `common/src/main/res/values/strings.xml`, in the block:

```xml
    <string name="Start">Start</string>
    <string name="Record">Record</string>
    <string name="History">History</string>
    <string name="Statistics">Statistics</string>
    <string name="Statistics_7_days">Last 7 days</string>
```

replace the `Statistics` line with the two new labels:

```xml
    <string name="Start">Start</string>
    <string name="Record">Record</string>
    <string name="History">History</string>
    <string name="Activities">Activities</string>
    <string name="Progress">Progress</string>
    <string name="Statistics_7_days">Last 7 days</string>
```

Do NOT touch `History` (still used by `app/res/menu/bottom_nav_menu.xml:26`) or any `Statistics_*` string (chart titles/toggle labels still use them).

- [ ] **Step 3: Verify no dangling references**

Run:
```bash
rg -n "string\.Statistics\b|R\.string\.Statistics" app/src common/src app/test
```
Expected: no matches (the exact-name `Statistics` string is gone; `Statistics_*` names still exist and are expected).

- [ ] **Step 4: Build, lint, spotless, tests**

Run:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply && ./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```
Expected: tests pass, lint shows no new issues, spotless clean, both assemble variants BUILD SUCCESSFUL. Note: `./gradlew test` may fail AFTER this task only if the app module fails to compile strings — it must pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/org/runnerup/view/HistoryFragment.java common/src/main/res/values/strings.xml
git commit -m "feat: rename history sub-tabs to activities and progress"
```

---

### Task 3: Verify on device

**Files:** (none — verification only)

**Interfaces:**
- Consumes: the changes from Tasks 1 and 2.

- [ ] **Step 1: Install and open the History page**

Device: Nexus 5X serial `025b46e24edcbca6` (currently connected). Build the map variant LAST (Task 2 already did), then:

```bash
adb -s 025b46e24edcbca6 install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk
adb -s 025b46e24edcbca6 shell am start -n org.runnerup.debug/org.runnerup.view.MainLayout
```

Navigate to the History tab (tap its bottom-nav item) and confirm:

- No "History" title bar at the top of the screen; the sub-tabs sit at the very top.
- Sub-tabs read **Activities | Progress**.
- Tapping each sub-tab still shows the list / statistics page as before.
- Bottom navigation still reads Record / History / Settings (first item is "Record" in the current app; the "History" item is unchanged).

- [ ] **Step 2: Capture evidence**

```bash
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/history_tabs.png
```

Also capture the Statistics ("Progress") page for completeness:
```bash
adb -s 025b46e24edcbca6 shell input tap 540 350
adb -s 025b46e24edcbca6 exec-out screencap -p > /tmp/opencode/progress_page.png
```

- [ ] **Step 3: Report**

Record in the SDD ledger (`.superpowers/sdd/2026-08-14-history-tab-labels/progress.md`): what was verified, screenshot paths, gate results (test/lint/spotless/assemble x2). No commit for this task.
