/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB.DIMENSION;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Range;
import org.runnerup.workout.Step;

/** The dialog for editing a single workout step, shared by the workout plan views. */
public class StepEditor {

  /**
   * Shows the edit dialog for {@code step}. The step is only modified when the user confirms, and
   * {@code onChanged} is run afterwards.
   */
  public static void showStepDialog(Context context, Step step, Runnable onChanged) {
    final LayoutInflater inflater = LayoutInflater.from(context);
    @SuppressLint("InflateParams")
    final View layout = inflater.inflate(R.layout.step_dialog, null);

    final Runnable save = setupEditStep(context, inflater, layout, step);

    new MaterialAlertDialogBuilder(context)
        .setTitle(org.runnerup.common.R.string.Edit_step)
        .setView(layout)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, whichButton) -> {
              save.run();
              dialog.dismiss();
              if (onChanged != null) {
                onChanged.run();
              }
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
        .show();
  }

  private static Runnable setupEditStep(
      Context context, LayoutInflater inflator, View layout, Step step) {
    final Formatter formatter = new Formatter(context);

    final TitleSpinner stepType = layout.findViewById(R.id.step_dialog_intensity);
    stepType.setValue(step.getIntensity().getValue());

    final HRZonesListAdapter hrZonesAdapter = new HRZonesListAdapter(context, inflator);
    final TitleSpinner durationType = layout.findViewById(R.id.step_dialog_duration_type);
    final TitleSpinner durationTime = layout.findViewById(R.id.step_dialog_duration_time);
    final TitleSpinner durationDistance = layout.findViewById(R.id.step_dialog_duration_distance);
    durationType.setOnSetValueListener(
        new TitleSpinner.OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            return null;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            switch (newValue) {
              case DIMENSION.TIME:
                durationTime.setEnabled(true);
                durationTime.setVisibility(View.VISIBLE);
                durationTime.setValue(
                    formatter.formatElapsedTime(
                        Formatter.Format.TXT, (long) step.getDurationValue()));
                durationDistance.setVisibility(View.GONE);
                break;
              case DIMENSION.DISTANCE:
                durationTime.setVisibility(View.GONE);
                durationDistance.setEnabled(true);
                durationDistance.setVisibility(View.VISIBLE);
                durationDistance.setValue(Long.toString((long) step.getDurationValue()));
                break;
              default:
                durationTime.setEnabled(false);
                durationDistance.setEnabled(false);
                break;
            }
            return newValue;
          }
        });
    if (step.getDurationType() == null) {
      durationType.setValue(-1);
    } else {
      durationType.setValue(step.getDurationType().getValue());
    }

    final TitleSpinner targetType = layout.findViewById(R.id.step_dialog_target_type);
    final TitleSpinner targetPaceLo = layout.findViewById(R.id.step_dialog_target_pace_lo);
    final TitleSpinner targetPaceHi = layout.findViewById(R.id.step_dialog_target_pace_hi);
    final TitleSpinner targetHrz = layout.findViewById(R.id.step_dialog_target_hrz);

    if (!hrZonesAdapter.hrZones.isConfigured()) {
      targetType.addDisabledValue(DIMENSION.HRZ);
    } else {
      targetHrz.setAdapter(hrZonesAdapter);
    }

    targetType.setOnSetValueListener(
        new TitleSpinner.OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            return null;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            Range target = step.getTargetValue();
            switch (newValue) {
              case DIMENSION.PACE:
                targetPaceLo.setEnabled(true);
                targetPaceHi.setEnabled(true);
                targetPaceLo.setVisibility(View.VISIBLE);
                targetPaceHi.setVisibility(View.VISIBLE);
                if (target != null) {
                  targetPaceLo.setValue(
                      formatter.formatPace(Formatter.Format.TXT_SHORT, target.minValue));
                  targetPaceHi.setValue(
                      formatter.formatPace(Formatter.Format.TXT_SHORT, target.maxValue));
                }
                targetHrz.setVisibility(View.GONE);
                break;
              case DIMENSION.HR:
              case DIMENSION.HRZ:
                targetPaceLo.setVisibility(View.GONE);
                targetPaceHi.setVisibility(View.GONE);
                targetHrz.setEnabled(true);
                targetHrz.setVisibility(View.VISIBLE);
                if (target != null) {
                  // the zones are off (i) due to 0-base in java arrays
                  // to 1 base in user HR zones numbering, and (b) there are 2 values
                  // per zone (min and max values)
                  int matchedZone =
                      hrZonesAdapter.hrZones.match(target.minValue, target.maxValue) - 2;
                  if (matchedZone < 0) {
                    matchedZone = 0;
                  }
                  targetHrz.setValue(matchedZone);
                } else {
                  targetHrz.setValue(0);
                }
                break;
              default:
                targetPaceLo.setEnabled(false);
                targetPaceHi.setEnabled(false);
                targetHrz.setEnabled(false);
                break;
            }
            return newValue;
          }
        });
    if (step.getTargetType() == null) {
      targetType.setValue(-1);
    } else if (step.getTargetType().getValue() == DIMENSION.HR) {
      targetType.setValue(DIMENSION.HRZ);
    } else {
      targetType.setValue(step.getTargetType().getValue());
    }

    return () -> {
      step.setIntensity(Intensity.valueOf(stepType.getValueInt()));
      step.setDurationType(Dimension.valueOf(durationType.getValueInt()));
      switch (durationType.getValueInt()) {
        case DIMENSION.DISTANCE:
          step.setDurationValue(
              SafeParse.parseDouble(durationDistance.getValue().toString(), 1000));
          break;
        case DIMENSION.TIME:
          step.setDurationValue(SafeParse.parseSeconds(durationTime.getValue().toString(), 60));
          break;
      }
      step.setTargetType(Dimension.valueOf(targetType.getValueInt()));
      switch (targetType.getValueInt()) {
        case DIMENSION.PACE:
          {
            double unitMeters = Formatter.getUnitMeters(context);
            double paceLo =
                (double) SafeParse.parseSeconds(targetPaceLo.getValue().toString(), 5 * 60);
            double paceHi =
                (double) SafeParse.parseSeconds(targetPaceHi.getValue().toString(), 5 * 60);
            step.setTargetValue(paceLo / unitMeters, paceHi / unitMeters);
            break;
          }
        case DIMENSION.HRZ:
          step.setTargetType(Dimension.HR);
          int[] range = hrZonesAdapter.hrZones.getHRValues(targetHrz.getValueInt() + 1);
          step.setTargetValue(range[0], range[1]);
      }
    };
  }

  private StepEditor() {}
}
