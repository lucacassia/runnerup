# Recording Card Color Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 3px themed border and 4dp elevation to the recording stats card for better visual separation.

**Architecture:** Single XML attribute change in `run.xml`. MaterialCardView switches from filled to outlined style with themed stroke color.

**Tech Stack:** Android XML layouts, Material3 CardView

## Global Constraints

- Never stage `gradle.properties`, `gradle-daemon-jvm.properties`, `local.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`
- No code comments in edits
- Gates order: `./gradlew test` → `:app:lintLatestDebug` → `spotlessApply`/`spotlessCheck` → `:app:assembleLatestDebug` → `:app:assembleLatestDebug -Porg.runnerup.nomap`
- Lint baseline `app/lint-baseline.xml` has 25 pre-existing issues; do not fix pre-existing lint issues

---

### Task 1: Update stats card style in run.xml

**Files:**
- Modify: `app/res/layout/run.xml:55-66` (MaterialCardView attributes)

**Interfaces:**
- Consumes: none
- Produces: none (XML-only change)

- [ ] **Step 1: Modify the MaterialCardView style attributes**

Replace the card's style and attributes in `run.xml`:

```xml
<!-- Before (lines 55-66) -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewFilledStyle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    android:layout_marginTop="12dp"
    android:layout_marginEnd="16dp"
    android:clickable="true"
    android:focusable="true"
    app:cardCornerRadius="@dimen/history_row_corner"
    app:cardElevation="0dp">

<!-- After -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewOutlinedStyle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    android:layout_marginTop="12dp"
    android:layout_marginEnd="16dp"
    android:clickable="true"
    android:focusable="true"
    app:cardCornerRadius="@dimen/history_row_corner"
    app:cardBackgroundColor="?attr/colorSurface"
    app:strokeColor="?attr/colorPrimary"
    app:strokeWidth="3dp"
    app:cardElevation="4dp">
```

- [ ] **Step 2: Run spotlessApply**

```bash
./gradlew spotlessApply
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build debug APK**

```bash
./gradlew :app:assembleLatestDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Build nomap variant**

```bash
./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/res/layout/run.xml
git commit -m "style: add themed border and elevation to recording stats card"
```
