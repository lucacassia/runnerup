# DetailActivity save-mode FABs

## Context

The run screen's bottom controls were already converted to icon-only `FloatingActionButton`s
(commit `36f92b73`). The DetailActivity "save" screen shown after pressing Stop still uses
full-width `MaterialButton`s (`detail.xml` `buttons` LinearLayout): Resume, Upload, Save, Discard.
This spec converts Resume/Save/Discard to icon-only FABs to match the run screen. Upload stays
a full-width button (only shown when sync accounts are configured).

## Design

### Layout (`app/res/layout/detail.xml`)

Replace the vertical button stack with:

```
<LinearLayout id="buttons" alignParentBottom vertical>
    <LinearLayout id="action_row" horizontal gravity=center padding 16/8/16/16dp>
        FAB resume_button   ic_play_arrow  normal  colorPrimaryContainer
        FAB save_button     ic_check*      normal  btn_green bg + white icon
        FAB discard_button  ic_delete*     normal  colorErrorContainer
    </LinearLayout>
    MaterialButton upload_button (unchanged, full-width, below)
</LinearLayout>
```

- All three FABs are normal size (56dp): equal-weight primary actions.
- Content descriptions come from the existing `Resume` / `Save` / `Discard` strings.
- Existing per-button visibility logic in DetailActivity is unchanged: in MODE_DETAILS the three
  FABs stay GONE and only Upload shows. An empty `action_row` collapses to ~24dp of padding.
- `*ic_check` and `*ic_delete` are new 24dp white-fill vector drawables from Google Material
  Icons (house style, matching `ic_pause.xml`/`ic_stop.xml`).

### Java (`app/src/main/org/runnerup/view/DetailActivity.java`)

- Field types `saveButton`, `discardButton`, `resumeButton` change from `Button` to
  `FloatingActionButton` (with import swap).
- Click handlers (`saveButtonClick`, `discardButtonClick`, `resumeButtonClick`) and the back-press
  handler that calls `resumeButtonClick.onClick(resumeButton)` are unchanged.

## Verification

- Gates: `./gradlew spotlessApply spotlessCheck test :app:lintLatestDebug :app:assembleLatestDebug`.
- Device: start a run, tap Stop, confirm three FABs render centered in save mode; verify
  Resume returns to the run screen, Discard shows the confirmation dialog and deletes, Save
  persists the activity and returns to history. Clean up the test activity from the device DB.
