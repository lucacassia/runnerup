# Recording Activity Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the activity recording screen (`RunActivity` / `run.xml`) into a modern, visually intuitive interface featuring a floating elevated stats HUD over a live full-screen map, an active interval progress banner in a collapsible bottom sheet, and a floating control dock.

**Architecture:** Update `run.xml` to nest stats metrics inside a floating 16dp-radius MaterialCardView with hero distance/time and a 2x2 grid (pace, lap pace, lap distance, HR zone). Update `RunActivity.java` to compute HR zone color pills dynamically, track interval progress percentage, and format active step targets cleanly.

**Tech Stack:** Java, Android XML, Material Components (Material 3), AndroidX CoordinatorLayout + BottomSheetBehavior.

## Global Constraints

- No comments added to code unless asked (`spotlessCheck` googleJavaFormat gate).
- Do NOT stage user-local files (`gradle.properties`, `gradle-daemon-jvm.properties`, `AGENTS.md`, `.opencode/`, `.superpowers/`).
- Lint gate: `./gradlew :app:lintLatestDebug` must report no NEW issues beyond pre-existing okhttp error.
- Verify before finishing: `./gradlew test`, `./gradlew :app:lintLatestDebug`, `spotlessApply` + `spotlessCheck`, `./gradlew :app:assembleLatestDebug`.
- Conventional commit messages (`feat:`, `refactor:`, `style:`, `docs:`).

---

### Task 1: Color resources & drawable scaffolding

**Files:**
- Modify: `app/res/values/colors.xml`
- Modify: `app/res/values-night/colors.xml`
- Create: `app/res/drawable/bg_run_stats_card.xml`
- Create: `app/res/drawable/bg_hr_zone_pill.xml`

**Interfaces:**
- Produces: color resources (`hrZone1`, `hrZone2`, `hrZone3`, `hrZone4`, `hrZone5`, `runStatsCardBg`, `runStatsCardBorder`) and drawables (`bg_run_stats_card`, `bg_hr_zone_pill`).

- [ ] **Step 1: Add color resources to app/res/values/colors.xml**

In `app/res/values/colors.xml`, append before `</resources>`:

```xml
    <!-- Recording activity screen colors -->
    <color name="runStatsCardBg">#FFFFFF</color>
    <color name="runStatsCardBorder">#D4D4D4</color>
    <color name="hrZone1">#3D9A57</color>
    <color name="hrZone2">#3D9A57</color>
    <color name="hrZone3">#B0851F</color>
    <color name="hrZone4">#D68C27</color>
    <color name="hrZone5">#D1383D</color>
```

- [ ] **Step 2: Add night color resources to app/res/values-night/colors.xml**

In `app/res/values-night/colors.xml`, append before `</resources>`:

```xml
    <!-- Recording activity screen night colors -->
    <color name="runStatsCardBg">#1E1E1E</color>
    <color name="runStatsCardBorder">#3C3C3C</color>
    <color name="hrZone1">#7FD88F</color>
    <color name="hrZone2">#7FD88F</color>
    <color name="hrZone3">#E5C07B</color>
    <color name="hrZone4">#F5A742</color>
    <color name="hrZone5">#E06C75</color>
```

- [ ] **Step 3: Create stats card background drawable**

Create `app/res/drawable/bg_run_stats_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/runStatsCardBg" />
            <stroke android:width="1dp" android:color="@color/runStatsCardBorder" />
            <corners android:radius="16dp" />
        </shape>
    </item>
</ripple>
```

- [ ] **Step 4: Create HR zone pill drawable**

Create `app/res/drawable/bg_hr_zone_pill.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/hrZone1" />
    <corners android:radius="6dp" />
</shape>
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/res/values/colors.xml app/res/values-night/colors.xml app/res/drawable/bg_run_stats_card.xml app/res/drawable/bg_hr_zone_pill.xml
git commit -m "feat: add color resources and drawables for recording screen redesign"
```

---

### Task 2: Layout Overhaul in `run.xml`

**Files:**
- Modify: `app/res/layout/run.xml`

**Interfaces:**
- Consumes: Drawables from Task 1.
- Produces: Layout hierarchy with floating Stats HUD (`table_layout1`), active interval progress banner in bottom sheet (`run_bottom_sheet`), and bottom action dock (`run_table_row1`). Preserves existing view IDs (`run_mapview`, `run_activity_distance`, `run_activity_time`, `run_activity_pace`, `lap_distance`, `lap_time`, `lap_pace`, `current_pace`, `current_hr`, `workout_list`, `pause_button`, `next_lap_button`).

- [ ] **Step 1: Rewrite app/res/layout/run.xml**

Update `app/res/layout/run.xml` to implement the Aero HUD & Live Interval Sheet layout:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/start_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:ignore="MergeRootFrame">

    <org.runnerup.util.MapViewWrapper
        android:id="@+id/run_mapview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />

    <TextView
        android:id="@+id/map_attribution"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|start"
        android:layout_marginStart="16dp"
        android:layout_marginBottom="@dimen/run_recenter_bottom_margin"
        android:background="@drawable/map_attribution_bg"
        android:paddingStart="4dp"
        android:paddingTop="1dp"
        android:paddingEnd="4dp"
        android:paddingBottom="1dp"
        android:text="@string/map_attribution"
        android:textSize="8sp"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorOnSurface"
        android:visibility="gone"
        tools:ignore="SmallSp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/table_layout1"
            style="?attr/materialCardViewOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_marginTop="12dp"
            android:layout_marginEnd="12dp"
            android:clickable="true"
            android:focusable="true"
            app:cardBackgroundColor="@color/runStatsCardBg"
            app:cardCornerRadius="16dp"
            app:cardElevation="6dp"
            app:strokeColor="@color/runStatsCardBorder"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <ImageView
                    android:id="@+id/stats_expand_indicator"
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:layout_gravity="end"
                    android:layout_marginBottom="4dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_expand_down_white_24dp"
                    app:tint="?attr/colorOnSurfaceVariant" />

                <FrameLayout
                    android:id="@+id/stats_3_area"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <LinearLayout
                        android:id="@+id/stats_3_horizontal"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical">

                        <!-- Hero Stats Row: Distance & Time -->
                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:baselineAligned="false"
                            android:gravity="center_vertical"
                            android:orientation="horizontal"
                            android:paddingBottom="12dp">

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1.2"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/run_activity_distance"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="start"
                                    android:textAppearance="?attr/textAppearanceHeadlineLarge"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textSize="36sp"
                                    android:textStyle="bold"
                                    app:autoSizeMaxTextSize="36sp"
                                    app:autoSizeMinTextSize="18sp"
                                    app:autoSizeStepGranularity="1sp"
                                    app:autoSizeTextType="uniform" />

                                <TextView
                                    android:id="@+id/distance_label"
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="start"
                                    android:text="@string/Distance" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/run_activity_time"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="end"
                                    android:textAppearance="?attr/textAppearanceHeadlineLarge"
                                    android:textColor="?attr/colorPrimary"
                                    android:textSize="26sp"
                                    android:textStyle="bold"
                                    app:autoSizeMaxTextSize="26sp"
                                    app:autoSizeMinTextSize="16sp"
                                    app:autoSizeStepGranularity="1sp"
                                    app:autoSizeTextType="uniform" />

                                <TextView
                                    android:id="@+id/time_label"
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="end"
                                    android:text="@string/Time" />
                            </LinearLayout>
                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="1dp"
                            android:layout_marginBottom="12dp"
                            android:background="?attr/colorOutlineVariant" />

                        <!-- 2x2 Secondary Metrics Grid -->
                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal">

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_marginEnd="6dp"
                                android:layout_weight="1"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/run_activity_pace"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="start"
                                    android:textAppearance="?attr/textAppearanceTitleLarge"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textStyle="bold" />

                                <TextView
                                    android:id="@+id/pace_label"
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="start"
                                    android:text="@string/Pace" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_marginStart="6dp"
                                android:layout_weight="1"
                                android:orientation="vertical">

                                <LinearLayout
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="end|center_vertical"
                                    android:orientation="horizontal">

                                    <TextView
                                        android:id="@+id/current_hr"
                                        android:layout_width="wrap_content"
                                        android:layout_height="wrap_content"
                                        android:textAppearance="?attr/textAppearanceTitleLarge"
                                        android:textColor="?attr/colorOnSurface"
                                        android:textStyle="bold" />

                                    <TextView
                                        android:id="@+id/hr_zone_pill"
                                        android:layout_width="wrap_content"
                                        android:layout_height="wrap_content"
                                        android:layout_marginStart="6dp"
                                        android:background="@drawable/bg_hr_zone_pill"
                                        android:paddingStart="6dp"
                                        android:paddingTop="2dp"
                                        android:paddingEnd="6dp"
                                        android:paddingBottom="2dp"
                                        android:text="Z2"
                                        android:textColor="#0A0A0A"
                                        android:textSize="10sp"
                                        android:textStyle="bold"
                                        android:visibility="gone" />
                                </LinearLayout>

                                <TextView
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="end"
                                    android:text="Heart Rate" />
                            </LinearLayout>
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="10dp"
                            android:orientation="horizontal">

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_marginEnd="6dp"
                                android:layout_weight="1"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/lap_distance"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="start"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textStyle="bold" />

                                <TextView
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="start"
                                    android:text="@string/LapDistance" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_marginStart="6dp"
                                android:layout_weight="1"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/lap_pace"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:gravity="end"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textStyle="bold" />

                                <TextView
                                    style="@style/RunStatLabel"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:gravity="end"
                                    android:text="@string/LapPace" />
                            </LinearLayout>
                        </LinearLayout>
                    </LinearLayout>

                    <!-- Expanded Giant Single Metric View (Toggled on card click) -->
                    <LinearLayout
                        android:id="@+id/stats_3_vertical"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:baselineAligned="false"
                        android:orientation="vertical"
                        android:visibility="gone">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            android:layout_weight="1"
                            android:gravity="center"
                            android:orientation="vertical">

                            <TextView
                                android:id="@+id/run_activity_distance_expanded"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:gravity="center"
                                android:maxLines="1"
                                android:textAppearance="?attr/textAppearanceDisplayMedium"
                                android:textColor="?attr/colorOnSurface"
                                android:textStyle="bold"
                                app:autoSizeMaxTextSize="72sp"
                                app:autoSizeMinTextSize="24sp"
                                app:autoSizeStepGranularity="1sp"
                                app:autoSizeTextType="uniform" />

                            <TextView
                                android:id="@+id/distance_expanded_label"
                                style="@style/RunStatLabel"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginBottom="8dp"
                                android:gravity="center_horizontal"
                                android:text="@string/Distance" />
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            android:layout_weight="1"
                            android:gravity="center"
                            android:orientation="vertical">

                            <TextView
                                android:id="@+id/run_activity_time_expanded"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:gravity="center"
                                android:maxLines="1"
                                android:textAppearance="?attr/textAppearanceDisplayMedium"
                                android:textColor="?attr/colorOnSurface"
                                android:textStyle="bold"
                                app:autoSizeMaxTextSize="72sp"
                                app:autoSizeMinTextSize="24sp"
                                app:autoSizeStepGranularity="1sp"
                                app:autoSizeTextType="uniform" />

                            <TextView
                                android:id="@+id/time_expanded_label"
                                style="@style/RunStatLabel"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginBottom="8dp"
                                android:gravity="center_horizontal"
                                android:text="@string/Time" />
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            android:layout_weight="1"
                            android:gravity="center"
                            android:orientation="vertical">

                            <TextView
                                android:id="@+id/run_activity_pace_expanded"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:gravity="center"
                                android:maxLines="1"
                                android:textAppearance="?attr/textAppearanceDisplayMedium"
                                android:textColor="?attr/colorOnSurface"
                                android:textStyle="bold"
                                app:autoSizeMaxTextSize="72sp"
                                app:autoSizeMinTextSize="24sp"
                                app:autoSizeStepGranularity="1sp"
                                app:autoSizeTextType="uniform" />

                            <TextView
                                android:id="@+id/pace_expanded_label"
                                style="@style/RunStatLabel"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginBottom="8dp"
                                android:gravity="center_horizontal"
                                android:text="@string/Pace" />
                        </LinearLayout>
                    </LinearLayout>
                </FrameLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Bottom Sheet Host for Workout Steps -->
        <androidx.coordinatorlayout.widget.CoordinatorLayout
            android:id="@+id/run_sheet_host"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1">

            <LinearLayout
                android:id="@+id/run_bottom_sheet"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:layout_gravity="bottom"
                android:background="@drawable/bg_run_bottom_sheet"
                android:elevation="8dp"
                android:orientation="vertical"
                app:behavior_hideable="false"
                app:behavior_peekHeight="90dp"
                app:layout_behavior="com.google.android.material.bottomsheet.BottomSheetBehavior">

                <!-- Bottom Sheet Handle -->
                <View
                    android:layout_width="36dp"
                    android:layout_height="4dp"
                    android:layout_gravity="center_horizontal"
                    android:layout_marginTop="8dp"
                    android:layout_marginBottom="6dp"
                    android:background="@drawable/bottom_sheet_handle" />

                <!-- Collapsed Peek Active Step Banner -->
                <LinearLayout
                    android:id="@+id/active_step_banner"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:paddingStart="16dp"
                    android:paddingEnd="16dp"
                    android:paddingBottom="8dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:gravity="center_vertical"
                        android:orientation="horizontal">

                        <TextView
                            android:id="@+id/step_intensity_badge"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:background="@drawable/bg_hr_zone_pill"
                            android:paddingStart="8dp"
                            android:paddingTop="2dp"
                            android:paddingEnd="8dp"
                            android:paddingBottom="2dp"
                            android:text="ACTIVE"
                            android:textAllCaps="true"
                            android:textColor="#0A0A0A"
                            android:textSize="10sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/active_step_title"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="8dp"
                            android:layout_weight="1"
                            android:ellipsize="end"
                            android:maxLines="1"
                            android:textAppearance="?attr/textAppearanceTitleSmall"
                            android:textColor="?attr/colorOnSurface"
                            android:textStyle="bold"
                            tools:text="Step 3/6: 400m remaining" />

                        <TextView
                            android:id="@+id/active_step_target_text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textAppearance="?attr/textAppearanceLabelSmall"
                            android:textColor="?attr/colorPrimary"
                            tools:text="4:45 - 5:00 /km" />
                    </LinearLayout>

                    <com.google.android.material.progressindicator.LinearProgressIndicator
                        android:id="@+id/active_step_progress_bar"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="8dp"
                        app:indicatorColor="?attr/colorPrimary"
                        app:trackColor="?attr/colorOutlineVariant"
                        app:trackCornerRadius="3dp" />
                </LinearLayout>

                <!-- Expanded Workout Step List -->
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_marginTop="4dp"
                    android:layout_marginEnd="16dp"
                    android:layout_marginBottom="4dp"
                    android:text="@string/Workout"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorOnSurface"
                    android:textStyle="bold" />

                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1">

                    <androidx.recyclerview.widget.RecyclerView
                        android:id="@+id/workout_list"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:clipToPadding="false"
                        android:paddingStart="16dp"
                        android:paddingTop="4dp"
                        android:paddingEnd="16dp"
                        android:paddingBottom="8dp" />

                    <TextView
                        android:id="@+id/hr_debug"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:scrollbars="vertical"
                        android:visibility="gone" />
                </FrameLayout>
            </LinearLayout>
        </androidx.coordinatorlayout.widget.CoordinatorLayout>

        <!-- Bottom Action Dock -->
        <LinearLayout
            android:id="@+id/run_table_row1"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="?attr/colorSurface"
            android:clipToPadding="false"
            android:gravity="center"
            android:orientation="horizontal"
            android:paddingStart="16dp"
            android:paddingTop="8dp"
            android:paddingEnd="16dp"
            android:paddingBottom="16dp">

            <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
                android:id="@+id/next_lap_button"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:layout_weight="1"
                android:text="@string/NextLap"
                android:textColor="?attr/colorOnSurface"
                app:backgroundTint="?attr/colorSurfaceContainerHigh"
                app:icon="@drawable/ic_skip_next"
                app:iconTint="?attr/colorOnSurface"
                app:strokeColor="?attr/colorOutline"
                app:strokeWidth="1dp" />

            <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
                android:id="@+id/pause_button"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:text="@string/Pause"
                android:textColor="?attr/colorOnPrimary"
                app:backgroundTint="?attr/colorPrimary"
                app:icon="@drawable/ic_pause"
                app:iconTint="?attr/colorOnPrimary" />
        </LinearLayout>
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/recenter_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end|bottom"
        android:layout_marginStart="16dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="16dp"
        android:layout_marginBottom="@dimen/run_recenter_bottom_margin"
        android:contentDescription="@string/Recenter"
        android:visibility="gone"
        app:srcCompat="@drawable/ic_recenter"
        app:tint="?attr/colorOnPrimaryContainer" />

</FrameLayout>
```

- [ ] **Step 2: Spotless formatting check & build verification**

Run: `./gradlew spotlessApply && ./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/res/layout/run.xml
git commit -m "feat: redesign recording activity screen layout with floating stats card and interval banner"
```

---

### Task 3: Activity Code Integration in `RunActivity.java`

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java`

**Interfaces:**
- Consumes: Layout elements from Task 2 (`hr_zone_pill`, `active_step_title`, `active_step_progress_bar`, `active_step_target_text`, `step_intensity_badge`).
- Produces: Dynamic calculation and display of HR zones, active step interval remaining progress, target range formatting, and HRM disconnection handling.

- [ ] **Step 1: Declare new view fields in RunActivity.java**

In `app/src/main/org/runnerup/view/RunActivity.java`:
- Add private view fields for the active step banner and HR zone pill:

```java
  private TextView hrZonePill;
  private TextView activeStepTitle;
  private TextView activeStepTargetText;
  private TextView stepIntensityBadge;
  private com.google.android.material.progressindicator.LinearProgressIndicator activeStepProgressBar;
```

- In `onCreate(Bundle)`: initialize the new view lookups:

```java
    hrZonePill = findViewById(R.id.hr_zone_pill);
    activeStepTitle = findViewById(R.id.active_step_title);
    activeStepTargetText = findViewById(R.id.active_step_target_text);
    stepIntensityBadge = findViewById(R.id.step_intensity_badge);
    activeStepProgressBar = findViewById(R.id.active_step_progress_bar);
```

- [ ] **Step 2: Implement Heart Rate Zone Pill calculation**

In `RunActivity.java`, add helper method `updateHrZonePill(double currentBpm)`:

```java
  private void updateHrZonePill(double currentBpm) {
    if (currentBpm <= 0 || hrZonePill == null) {
      if (hrZonePill != null) hrZonePill.setVisibility(View.GONE);
      return;
    }
    hrZonePill.setVisibility(View.VISIBLE);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    int maxHr = 190;
    try {
      maxHr = Integer.parseInt(prefs.getString(getString(R.string.pref_autolap_hr_max), "190"));
    } catch (Exception ignored) {
    }
    double pct = (currentBpm / maxHr) * 100.0;
    int zoneColorId;
    String zoneName;
    if (pct < 60.0) {
      zoneName = "Z1";
      zoneColorId = R.color.hrZone1;
    } else if (pct < 70.0) {
      zoneName = "Z2";
      zoneColorId = R.color.hrZone2;
    } else if (pct < 80.0) {
      zoneName = "Z3";
      zoneColorId = R.color.hrZone3;
    } else if (pct < 90.0) {
      zoneName = "Z4";
      zoneColorId = R.color.hrZone4;
    } else {
      zoneName = "Z5";
      zoneColorId = R.color.hrZone5;
    }
    hrZonePill.setText(zoneName);
    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
    bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
    bg.setCornerRadius(formatter.dp_to_px(6));
    bg.setColor(ContextCompat.getColor(this, zoneColorId));
    hrZonePill.setBackground(bg);
  }
```

- [ ] **Step 3: Implement Active Step Interval Banner updates**

In `RunActivity.java`, add helper method `updateActiveStepBanner(Step curr)`:

```java
  private void updateActiveStepBanner(Step curr) {
    if (curr == null || activeStepTitle == null) {
      return;
    }
    if (curr.getIntensity() != null && stepIntensityBadge != null) {
      stepIntensityBadge.setText(curr.getIntensity().getTextId());
    }
    double stepTime = workout.getTime(Scope.STEP);
    double stepDist = workout.getDistance(Scope.STEP);

    double progressPct = 0;
    String titleText = "";

    if (curr.getDurationType() == null) {
      titleText = getString(org.runnerup.common.R.string.Until_press);
    } else {
      double targetVal = curr.getDurationValue();
      if (curr.getDurationType() == org.runnerup.workout.Dimension.DISTANCE) {
        double remDist = Math.max(0, targetVal - stepDist);
        titleText = formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(remDist)) + " left";
        if (targetVal > 0) progressPct = Math.min(100, (stepDist / targetVal) * 100.0);
      } else if (curr.getDurationType() == org.runnerup.workout.Dimension.TIME) {
        double remTime = Math.max(0, targetVal - stepTime);
        titleText = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(remTime)) + " left";
        if (targetVal > 0) progressPct = Math.min(100, (stepTime / targetVal) * 100.0);
      }
    }
    activeStepTitle.setText(titleText);
    if (activeStepProgressBar != null) {
      activeStepProgressBar.setProgress((int) progressPct);
    }

    if (activeStepTargetText != null) {
      if (curr.getTargetType() == null) {
        activeStepTargetText.setText("");
      } else {
        double minVal = curr.getTargetValue().minValue;
        double maxVal = curr.getTargetValue().maxValue;
        if (minVal == maxVal) {
          activeStepTargetText.setText(formatter.format(Formatter.Format.TXT_SHORT, curr.getTargetType(), minVal));
        } else {
          activeStepTargetText.setText(
              String.format(
                  Locale.getDefault(),
                  "%s-%s",
                  formatter.format(Formatter.Format.TXT_SHORT, curr.getTargetType(), minVal),
                  formatter.format(Formatter.Format.TXT_SHORT, curr.getTargetType(), maxVal)));
        }
      }
    }
  }
```

- [ ] **Step 4: Connect updates inside updateView()**

In `RunActivity.java` inside `updateView()`:
- Call `updateHrZonePill(chr)` when HRM component is connected.
- Call `updateActiveStepBanner(curr)` on each update tick.

- [ ] **Step 5: Run Spotless & verify build**

Run: `./gradlew spotlessApply && ./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "feat: wire up HR zone pill calculation and active step banner progress in RunActivity"
```

---

### Task 4: Full verification suite & Device smoke test

**Files:** none.

- [ ] **Step 1: Run full gate suite**

Run in order:
```bash
./gradlew test
./gradlew :app:lintLatestDebug
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:assembleLatestDebug
```
Expected: All pass (lint reports only pre-existing okhttp error).

- [ ] **Step 2: Device smoke test**

Install the debug APK on connected device (`6a6743fd`).
Launch a workout and verify on-device:
- Floating elevated stats HUD card showing Hero Distance/Time + 2x2 secondary grid.
- HR zone pill updates or hides gracefully when HRM is absent.
- Bottom sheet collapsed peek banner showing active interval step badge, remaining step progress bar, and target text.
- Expanding bottom sheet displays workout step list with active step highlighted.
- Floating action dock FAB buttons respond cleanly.
