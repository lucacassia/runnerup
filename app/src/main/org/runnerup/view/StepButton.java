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

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.util.Formatter;
import org.runnerup.widget.NumberPicker;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Step;

public class StepButton extends LinearLayout {

  private final Context mContext;
  private final ViewGroup mLayout;
  private final TextView mIntensityBadge;
  private final TextView mDurationValue;
  private final TextView mGoalValue;
  private final Formatter formatter;

  public Step getStep() {
    return step;
  }

  private Step step;
  private Runnable mOnChangedListener = null;

  private static final boolean editRepeatCount = true;
  private static final boolean editStepButton = true;

  public StepButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;

    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.step_button, this);
    formatter = new Formatter(context);
    mLayout = findViewById(R.id.step_button_layout);
    mIntensityBadge = findViewById(R.id.step_intensity_badge);
    mDurationValue = findViewById(R.id.step_duration_value);
    mGoalValue = findViewById(R.id.step_goal_value);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    mLayout.setEnabled(enabled);
    for (int i = 0, j = mLayout.getChildCount(); i < j; i++) {
      mLayout.getChildAt(i).setEnabled(enabled);
    }
  }

  public void setOnChangedListener(Runnable runnable) {
    mOnChangedListener = runnable;
  }

  public static void styleIntensityBadge(TextView badge, Intensity intensity, Context context) {
    int badgeTextColorId;
    int badgeBgColorId;
    switch (intensity) {
      case ACTIVE:
        badgeTextColorId = R.color.stepActive;
        badgeBgColorId = R.color.stepActiveBg;
        break;
      case RESTING:
        badgeTextColorId = R.color.stepResting;
        badgeBgColorId = R.color.stepRestingBg;
        break;
      case REPEAT:
        badgeTextColorId = R.color.stepRepeat;
        badgeBgColorId = R.color.stepRestingBg;
        break;
      case WARMUP:
        badgeTextColorId = R.color.stepWarmup;
        badgeBgColorId = R.color.stepWarmupBg;
        break;
      case COOLDOWN:
        badgeTextColorId = R.color.stepCooldown;
        badgeBgColorId = R.color.stepCooldownBg;
        break;
      case RECOVERY:
        badgeTextColorId = R.color.stepRecovery;
        badgeBgColorId = R.color.stepRecoveryBg;
        break;
      default:
        badgeTextColorId = R.color.stepResting;
        badgeBgColorId = R.color.stepRestingBg;
    }
    badge.setTextColor(ContextCompat.getColor(context, badgeTextColorId));
    GradientDrawable badgeBg = new GradientDrawable();
    badgeBg.setShape(GradientDrawable.RECTANGLE);
    badgeBg.setCornerRadius(6 * context.getResources().getDisplayMetrics().density);
    badgeBg.setColor(ContextCompat.getColor(context, badgeBgColorId));
    badge.setBackground(badgeBg);
  }

  public void setStep(Step step) {
    this.step = step;

    styleIntensityBadge(mIntensityBadge, step.getIntensity(), mContext);
    mIntensityBadge.setText(step.getIntensity().getTextId());

    mDurationValue.setVisibility(VISIBLE);
    switch (step.getIntensity()) {
      case REPEAT:
        mDurationValue.setVisibility(GONE);
        mGoalValue.setVisibility(VISIBLE);
        mGoalValue.setText(
            String.format(
                Locale.getDefault(),
                getResources().getString(org.runnerup.common.R.string.repeat_times),
                step.getRepeatCount()));
        if (editRepeatCount) mLayout.setOnClickListener(onRepeatClickListener);
        return;
      default:
        break;
    }

    Dimension durationType = step.getDurationType();
    if (durationType == null) {
      mDurationValue.setText(org.runnerup.common.R.string.Until_press);
    } else {
      mDurationValue.setText(
          formatter.format(Formatter.Format.TXT_LONG, durationType, step.getDurationValue()));
    }

    Dimension goalType = step.getTargetType();
    if (goalType == null) {
      mGoalValue.setVisibility(View.GONE);
    } else {
      mGoalValue.setVisibility(View.VISIBLE);
      String prefix;
      if (goalType == Dimension.HR || goalType == Dimension.HRZ)
        prefix = "HR "; // todo should use a string
      else prefix = "";

      mGoalValue.setText(
          (String.format(
              Locale.getDefault(),
              "%s%s-%s",
              prefix,
              formatter.format(
                  Formatter.Format.TXT_SHORT, goalType, step.getTargetValue().minValue),
              formatter.format(
                  Formatter.Format.TXT_LONG, goalType, step.getTargetValue().maxValue))));
    }
    if (editStepButton) {
      mLayout.setOnClickListener(onStepClickListener);
    }
  }

  private final OnClickListener onRepeatClickListener =
      new OnClickListener() {

        @Override
        public void onClick(View v) {
          final NumberPicker numberPicker = new NumberPicker(mContext, null);
          numberPicker.setOrientation(VERTICAL);
          numberPicker.setDigits(4);
          numberPicker.setRange(0, 9999, true);
          numberPicker.setValue(step.getRepeatCount());

          final LinearLayout layout = new LinearLayout(mContext);
          layout.setLayoutParams(
              new LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
          layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
          layout.addView(numberPicker);

          new MaterialAlertDialogBuilder(mContext)
              .setTitle(org.runnerup.common.R.string.repeat)
              .setView(layout)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, whichButton) -> {
                    step.setRepeatCount(numberPicker.getValue());
                    dialog.dismiss();
                    setStep(step); // redraw
                    if (mOnChangedListener != null) {
                      mOnChangedListener.run();
                    }
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
              .show();
        }
      };

  private final OnClickListener onStepClickListener =
      new OnClickListener() {

        @Override
        public void onClick(View v) {
          StepEditor.showStepDialog(mContext, step, mOnChangedListener);
        }
      };
}
