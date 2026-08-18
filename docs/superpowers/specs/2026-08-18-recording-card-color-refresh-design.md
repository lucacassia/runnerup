# Recording Screen Stats Card Color Refresh

**Date:** 2026-08-18
**Status:** Draft

## Summary

Refresh the recording screen stats card with a 3px blue border and blue shadow glow to solve three visual issues: card blends into the bottom sheet, too much white/flat feel, and the stats card feels plain.

## What Changes

### Stats card (`table_layout1` in `run.xml`)

- **Border:** 3px solid `#3B7DD8` (primary blue), with 12dp corner radius preserved
- **Shadow:** `0 4px 16px rgba(59,125,216,0.25)` — colored glow underneath
- **Background:** white (unchanged)
- **Stat values:** dark `#1A1A1A` (unchanged)
- **Stat labels:** gray `#595959` (unchanged)
- **Expand indicator:** gray (unchanged)

### What stays the same

- Bottom sheet: white, no changes
- Buttons (Next Lap / Pause): blue, no changes
- Map: no changes
- Expand/collapse animation: no changes
- All stat text content and formatting: no changes

## Implementation

### `app/res/layout/run.xml`

Replace the `MaterialCardView` attributes for `table_layout1`:

```xml
<!-- Before -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewFilledStyle"
    ...
    app:cardElevation="0dp">

<!-- After -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/table_layout1"
    style="?attr/materialCardViewOutlinedStyle"
    app:strokeColor="#3B7DD8"
    app:strokeWidth="3dp"
    app:cardElevation="4dp"
    app:cardBackgroundColor="@android:color/white"
    ...>
```

Note: `materialCardViewOutlinedStyle` gives us the stroke system. We also need to set `cardBackgroundColor` explicitly since outlined style defaults to transparent/secondary surface.

### Shadow color

MaterialCardView's elevation shadow is always black-based. To get a blue-tinted shadow, we have two options:

1. **Simple:** Keep `cardElevation="4dp"` — shadow will be subtle black, border provides the blue accent. Clean and standard.
2. **Custom:** Use `OutlineProvider` or a layered drawable to fake a blue shadow. More complex, may not be worth it.

**Recommendation:** Option 1 (simple elevation). The blue border already provides strong color differentiation. A black elevation shadow is standard Material3 and looks natural. The combined effect of blue border + elevation shadow achieves the goal without custom shadow code.

If the user specifically wants a blue-tinted shadow glow, that's a separate follow-up requiring a custom `ViewOutlineProvider` or wrapper drawable.

## Files to Modify

- `app/res/layout/run.xml` — card style attributes only

## Verification

1. `./gradlew test`
2. `./gradlew :app:lintLatestDebug`
3. `./gradlew spotlessApply && spotlessCheck`
4. `./gradlew :app:assembleLatestDebug`
5. Device: verify card has visible blue border, shadow lift, and clear separation from white bottom sheet
