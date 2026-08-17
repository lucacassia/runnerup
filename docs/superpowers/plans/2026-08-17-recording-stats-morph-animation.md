# Recording Stats Morph Animation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the cross-fade in the recording stats container with a shared-element morph: values and labels animate translation, scale, and alpha from compact horizontal positions to expanded vertical positions over 250ms, and reverse on collapse.

**Architecture:** Use a single `ValueAnimator` (250ms, matching existing height animator) that simultaneously drives the card/area height expansion AND the per-view morph. Before each expand animation, capture the expanded views' layout positions once via an `OnGlobalLayoutListener` (measure-once trick), then animate the 3 compact value TextViews + 3 compact label TextViews toward those targets. Collapse reuses the same morph in reverse.

**Tech Stack:** Android SDK, `ValueAnimator`, `FrameLayout.LayoutParams`, `ViewTreeObserver.OnGlobalLayoutListener`, `java.util.Arrays`.

## Global Constraints

- Android minSdk as per `app/build.gradle`; Java source.
- No comments in code unless explicitly requested.
- Run `spotlessApply` before every commit; run full gates after final commit: `./gradlew test`, `./gradlew :app:lintLatestDebug`, `./gradlew spotlessApply && ./gradlew spotlessCheck`.
- Never stage `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `local.properties`, `.opencode/`, `.superpowers/`, `AGENTS.md`, `opencode.json`.
- `Formatter.java:823` AppBundleLocaleChanges is pre-existing on master — do not fix.

---

## Task 1: Layout and field scaffolding

**Files:**
- Modify: `app/res/layout/run.xml:115-121, 143-148, 171-176`
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:108-123, 227-240`

**Interfaces:**
- Produces: fields `distanceLabel`, `timeLabel`, `paceLabel` (`TextView`) in `RunActivity`; IDs `@+id/distance_label`, `@+id/time_label`, `@+id/pace_label` in `run.xml`.

- [ ] **Step 1: Add IDs to the 3 compact label TextViews in run.xml**

Edit `app/res/layout/run.xml` — add `android:id` to each compact label:

Line 115-121 (Distance label, inside first column `LinearLayout`):
```xml
<TextView
    android:id="@+id/distance_label"
    style="@style/RunStatLabel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:gravity="center_horizontal"
    android:text="@string/Distance" />
```

Line 143-148 (Time label, inside second column `LinearLayout`):
```xml
<TextView
    android:id="@+id/time_label"
    style="@style/RunStatLabel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:gravity="center_horizontal"
    android:text="@string/Time" />
```

Line 171-176 (Pace label, inside third column `LinearLayout`):
```xml
<TextView
    android:id="@+id/pace_label"
    style="@style/RunStatLabel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:gravity="center_horizontal"
    android:text="@string/Pace" />
```

- [ ] **Step 2: Declare the 3 label fields and a clip-saved-state boolean in RunActivity**

After line 113 (`private TextView activityPaceExpanded = null;`), add:
```java
private TextView distanceLabel = null;
private TextView timeLabel = null;
private TextView paceLabel = null;
```

After line 122 (`private boolean statsAnimating = false;`), add:
```java
private boolean statsClipChildrenSaved = false;
```

- [ ] **Step 3: Bind the new fields in onCreate**

After line 235 (`activityPaceExpanded.setLayerType(...)`), add:
```java
distanceLabel = findViewById(R.id.distance_label);
timeLabel = findViewById(R.id.time_label);
paceLabel = findViewById(R.id.pace_label);
```

- [ ] **Step 4: Run spotlessApply and commit**

Run: `./gradlew spotlessApply`

```bash
git add app/res/layout/run.xml app/src/main/org/runnerup/view/RunActivity.java
git commit -m "feat: add IDs to compact stat labels for morph animation"
```

---

## Task 2: Implement morph animation in toggleStatsExpanded

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:441-499`

**Interfaces:**
- Consumes: `distanceLabel`, `timeLabel`, `paceLabel`, `statsClipChildrenSaved` (Task 1).
- Produces: morph animator replaces the cross-fade in `toggleStatsExpanded()`.

- [ ] **Step 1: Replace the cross-fade block and integrate the morph into toggleStatsExpanded**

Replace lines 465-470 (the `gone`/`shown` cross-fade block) with a no-op comment or remove entirely, and replace lines 472-495 (the height-only animator + listener) with the combined height+morph animator below.

The full replacement for `toggleStatsExpanded()` (lines 441-499) is:

```java
private void toggleStatsExpanded() {
    if (statsAnimating || statsDelta <= 0 || statsCardNaturalHeight <= 0) {
      return;
    }
    statsAnimating = true;
    statsExpanded = !statsExpanded;

    final ViewGroup.LayoutParams cardLp = statsCard.getLayoutParams();
    final ViewGroup.LayoutParams areaLp = stats3Area.getLayoutParams();
    final int cardNatural = statsCardNaturalHeight;
    final int areaNatural = stats3AreaNaturalHeight;
    final int delta = statsDelta;

    final int cardStart = statsExpanded ? cardNatural : cardNatural + delta;
    final int cardEnd = statsExpanded ? cardNatural + delta : cardNatural;
    final int areaStart = statsExpanded ? areaNatural : areaNatural + delta;
    final int areaEnd = statsExpanded ? areaNatural + delta : areaNatural;

    cardLp.height = cardStart;
    ((ViewGroup.MarginLayoutParams) cardLp).bottomMargin = -(cardStart - cardNatural);
    statsCard.setLayoutParams(cardLp);
    areaLp.height = areaStart;
    stats3Area.setLayoutParams(areaLp);

    stats3Horizontal.setVisibility(View.VISIBLE);
    stats3Vertical.setVisibility(View.VISIBLE);
    stats3Horizontal.setAlpha(1f);
    stats3Vertical.setAlpha(1f);

    final boolean expanding = statsExpanded;
    final View[] compactValueViews = {activityDistance, activityTime, activityPace};
    final TextView[] compactLabelViews = {distanceLabel, timeLabel, paceLabel};
    final View[] expandedValueViews = {activityDistanceExpanded, activityTimeExpanded, activityPaceExpanded};

    for (View v : compactValueViews) {
      v.setPivotX(0f);
      v.setPivotY(0f);
    }
    for (TextView v : compactLabelViews) {
      v.setPivotX(0f);
      v.setPivotY(0f);
    }

    if (!expanding) {
      for (View v : expandedValueViews) {
        v.setAlpha(1f);
      }
    }

    if (!statsClipChildrenSaved) {
      stats3Area.setClipChildren(false);
      ViewParent parent = stats3Area.getParent();
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).setClipChildren(false);
      }
      statsClipChildrenSaved = true;
    }

    for (View v : compactValueViews) {
      v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    final float[] finalExpandedRowTops = new float[3];
    final float[] finalExpandedValueScaleX = new float[3];
    final float[] finalExpandedValueScaleY = new float[3];
    final float[] finalExpandedLabelTops = new float[3];
    final float[] finalExpandedLabelScaleX = new float[3];
    final float[] finalExpandedLabelScaleY = new float[3];

    Runnable startMorphAnimator = () -> {
      float rowH = (float) (areaNatural + delta) / 3f;
      for (int i = 0; i < 3; i++) {
        finalExpandedRowTops[i] = rowH * i;
        finalExpandedValueScaleX[i] = (float) expandedValueViews[i].getWidth() / compactValueViews[i].getWidth();
        finalExpandedValueScaleY[i] = (float) expandedValueViews[i].getHeight() / compactValueViews[i].getHeight();

        float expandedLabelTop = finalExpandedRowTops[i] + expandedValueViews[i].getHeight() + 4f * (areaNatural + delta) / (3f * 560f);
        finalExpandedLabelTops[i] = expandedLabelTop;
        finalExpandedLabelScaleX[i] = (float) (areaNatural + delta) / (3f * compactLabelViews[i].getWidth());
        finalExpandedLabelScaleY[i] = (float) (rowH * 0.35f) / compactLabelViews[i].getHeight();
      }

      if (expanding) {
        for (View v : expandedValueViews) {
          v.setAlpha(0f);
        }
      }

      ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
      animator.setDuration(250);
      animator.addUpdateListener(
          a -> {
            float f = a.getAnimatedFraction();
            int cardH = Math.round(cardStart + (cardEnd - cardStart) * f);
            int areaH = Math.round(areaStart + (areaEnd - areaStart) * f);
            cardLp.height = cardH;
            ((ViewGroup.MarginLayoutParams) cardLp).bottomMargin = -(cardH - cardNatural);
            statsCard.setLayoutParams(cardLp);
            areaLp.height = areaH;
            stats3Area.setLayoutParams(areaLp);

            for (int i = 0; i < 3; i++) {
              float targetTx = 0f;
              float targetTyValue = finalExpandedRowTops[i];
              float targetTyLabel = finalExpandedLabelTops[i];
              float targetSxValue = finalExpandedValueScaleX[i];
              float targetSyValue = finalExpandedValueScaleY[i];
              float targetSxLabel = finalExpandedLabelScaleX[i];
              float targetSyLabel = finalExpandedLabelScaleY[i];

              if (expanding) {
                compactValueViews[i].setTranslationX(targetTx * f);
                compactValueViews[i].setTranslationY(targetTyValue * f);
                compactValueViews[i].setScaleX(1f + (targetSxValue - 1f) * f);
                compactValueViews[i].setScaleY(1f + (targetSyValue - 1f) * f);
                compactValueViews[i].setAlpha(1f - f);

                compactLabelViews[i].setTranslationX(targetTx * f);
                compactLabelViews[i].setTranslationY(targetTyLabel * f);
                compactLabelViews[i].setScaleX(1f + (targetSxLabel - 1f) * f);
                compactLabelViews[i].setScaleY(1f + (targetSyLabel - 1f) * f);
                compactLabelViews[i].setAlpha(1f - f);

                expandedValueViews[i].setAlpha(f);
              } else {
                compactValueViews[i].setTranslationX(targetTx * (1f - f));
                compactValueViews[i].setTranslationY(targetTyValue * (1f - f));
                compactValueViews[i].setScaleX(targetSxValue - (targetSxValue - 1f) * f);
                compactValueViews[i].setScaleY(targetSyValue - (targetSyValue - 1f) * f);
                compactValueViews[i].setAlpha(f);

                compactLabelViews[i].setTranslationX(targetTx * (1f - f));
                compactLabelViews[i].setTranslationY(targetTyLabel * (1f - f));
                compactLabelViews[i].setScaleX(targetSxLabel - (targetSxLabel - 1f) * f);
                compactLabelViews[i].setScaleY(targetSyLabel - (targetSyLabel - 1f) * f);
                compactLabelViews[i].setAlpha(f);

                expandedValueViews[i].setAlpha(1f - f);
              }
            }
          });
      animator.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              statsAnimating = false;
              for (View v : compactValueViews) {
                v.setLayerType(View.LAYER_TYPE_NONE, null);
              }
              if (expanding) {
                stats3Horizontal.setVisibility(View.GONE);
                for (View v : compactValueViews) {
                  v.setTranslationX(0f);
                  v.setTranslationY(0f);
                  v.setScaleX(1f);
                  v.setScaleY(1f);
                  v.setAlpha(0f);
                }
                for (TextView v : compactLabelViews) {
                  v.setTranslationX(0f);
                  v.setTranslationY(0f);
                  v.setScaleX(1f);
                  v.setScaleY(1f);
                  v.setAlpha(0f);
                }
              } else {
                stats3Vertical.setVisibility(View.GONE);
                for (View v : expandedValueViews) {
                  v.setAlpha(0f);
                }
                for (View v : compactValueViews) {
                  v.setAlpha(1f);
                }
              }
              stats3Vertical.requestLayout();
              stats3Vertical.invalidate();
              statsCard.requestLayout();
              statsCard.invalidate();
            }
          });
      animator.start();
    };

    if (expanding) {
      startMorphAnimator.run();
    } else {
      startMorphAnimator.run();
    }

    statsExpandIndicator.animate().rotation(statsExpanded ? 180f : 0f).setDuration(250).start();
  }
```

Note: for the expand direction, both layouts are visible at the start of the animation (compact at alpha 1, expanded at alpha 0). The compact ghost fades out while translating/scaling to the expanded targets. The expanded layout is laid out at the current height, growing as the area expands, fading in.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:assembleLatestDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "feat: shared-element morph animation for recording stats expand/collapse"
```

---

## Task 3: Guard value sync during animation

**Files:**
- Modify: `app/src/main/org/runnerup/view/RunActivity.java:674-680`

**Interfaces:**
- Consumes: `statsAnimating` (existing boolean).

- [ ] **Step 1: Wrap the value sync block in a statsAnimating guard**

At lines 674-680, the `onNewStats` block sets text on both compact and expanded views every tick. During the ~250ms morph, a mid-flight `setText` would trigger a re-measure on one of the morphing views and cause a visual jump. Wrap the compact view `setText` calls in a guard:

Change:
```java
activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at)));
activityDistance.setText(
    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
activityPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap));
activityTimeExpanded.setText(activityTime.getText());
activityDistanceExpanded.setText(activityDistance.getText());
activityPaceExpanded.setText(activityPace.getText());
```

To:
```java
if (!statsAnimating) {
  activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at)));
  activityDistance.setText(
      formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
  activityPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap));
}
activityTimeExpanded.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at)));
activityDistanceExpanded.setText(
    formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
activityPaceExpanded.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap));
```

Rationale: The expanded views are updated even during animation (their alpha is 0 or near 0 when invisible, so no visual jump). The compact views are the morph actors and must not be re-measured mid-flight. The worst case staleness is one tick (~200ms on fast GPS, 1s on phone GPS).

- [ ] **Step 2: Commit**

```bash
git add app/src/main/org/runnerup/view/RunActivity.java
git commit -m "fix: suppress compact stat value sync during morph animation"
```

---

## Task 4: Verify

- [ ] **Step 1: Run unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (no animation tests; regression guard only)

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintLatestDebug`
Expected: no new warnings (only pre-existing in `app/lint-baseline.xml`; `Formatter.java:823` AppBundleLocaleChanges is pre-existing on master)

- [ ] **Step 3: Run spotlessApply and spotlessCheck**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL / PASS

- [ ] **Step 4: Build both flavors**

Run: `./gradlew :app:assembleLatestDebug`
Run: `./gradlew :app:assembleLatestDebug -Porg.runnerup.nomap`
Expected: both BUILD SUCCESSFUL

- [ ] **Step 5: Device smoke test**

Install: `adb install -r app/build/outputs/apk/latest/debug/app-latest-debug.apk`

Test cases:
1. Tap card → expand: compact values and labels translate up, scale up 2-3x, and fade out while expanded values fade in. Motion is smooth, no clipping. Chevron rotates.
2. Tap card → collapse: expanded values fade out, compact values translate back to natural position, scale down, and fade in. Chevron rotates back.
3. Rapid double-tap during animation → ignored (no crash, no visual glitch).
4. GPS tick fires during animation → values update on next non-animating tick, no mid-flight jump.
5. After collapse, values update correctly in compact layout.

Report pass/fail for each.
