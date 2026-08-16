# Rounded Top Corners for the Recording Bottom Sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Round the two top corners of the recording-activity bottom sheet (`run_bottom_sheet`) to 12dp, matching the app's existing corner convention.

**Architecture:** A declarative shape drawable fills the sheet's background with the theme's `colorSurface` and rounds only the top corners; the layout swaps its flat `?attr/colorSurface` background for the drawable. No Java changes.

**Tech Stack:** Android XML resources (shape drawable), Material 3 attributes.

## Global Constraints

- Corner radius must be exactly `@dimen/history_row_corner` (12dp) — the app's existing corner value used by the stats card above the sheet.
- Fill color must stay `?attr/colorSurface` (theme-aware; light and dark themes both work).
- Only the TOP two corners round. Bottom corners stay 0 — the sheet extends to the screen bottom when expanded.
- No Java changes; `RunActivity.java` untouched.
- No changes to sheet content (handle bar, "Workout" title, workout list) or padding.
- No comments in resource files unless the existing file style uses them.
- Build verification: `./gradlew :app:assembleLatestDebug` (drawable + attribute references must resolve).

---
### Task 1: Rounded top corners for the recording bottom sheet

**Files:**
- Create: `app/res/drawable/bg_run_bottom_sheet.xml`
- Modify: `app/res/layout/run.xml:495` (the `run_bottom_sheet` LinearLayout background attribute)

**Interfaces:**
- Consumes: `@dimen/history_row_corner` (12dp, defined in `app/res/values/dimens.xml:9`); `?attr/colorSurface` (theme attribute).
- Produces: `@drawable/bg_run_bottom_sheet` — the new sheet background. Nothing else references it.

- [ ] **Step 1: Create the shape drawable**

Create `app/res/drawable/bg_run_bottom_sheet.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners
        android:topLeftRadius="@dimen/history_row_corner"
        android:topRightRadius="@dimen/history_row_corner" />
    <solid android:color="?attr/colorSurface" />
</shape>
```

Note: bottom corners are intentionally unset (default 0) — the sheet's bottom edge stays square because it reaches the screen bottom when expanded.

- [ ] **Step 2: Point the sheet's background at the drawable**

In `app/res/layout/run.xml`, find the `run_bottom_sheet` LinearLayout (`android:id="@+id/run_bottom_sheet"`, ~line 490-498). Change its background attribute:

```xml
android:background="?attr/colorSurface"
```

to:

```xml
android:background="@drawable/bg_run_bottom_sheet"
```

Do NOT change anything else in the layout.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL (drawable and dimension references resolve).

- [ ] **Step 4: Verify diff scope**

Run: `git status --short`
Expected: exactly two files — the new drawable and `app/res/layout/run.xml`. No other changes.

- [ ] **Step 5: Commit**

```bash
git add app/res/drawable/bg_run_bottom_sheet.xml app/res/layout/run.xml
git commit -m "style: round top corners of the recording bottom sheet"
```

---

## Self-Review

**1. Spec coverage:** drawable (✓ Task 1 Step 1), layout background swap (✓ Step 2), 12dp via `history_row_corner` (✓ Step 1), theme-aware `colorSurface` (✓ Step 1), top-only rounding (✓ Step 1 note), no Java changes (✓ no such step), build gate (✓ Step 3).

**2. Placeholder scan:** No TBD/TODO; every step shows exact content; full XML and attribute replacement included.

**3. Type consistency:** `@dimen/history_row_corner` and `?attr/colorSurface` referenced exactly as they exist in the codebase; drawable name `bg_run_bottom_sheet` used consistently across Steps 1-2.
