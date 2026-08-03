/*
 * Copyright (C) 2026
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

package org.runnerup.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * A Material 3 styled progress dialog replacing the deprecated {@link
 * android.app.ProgressDialog}. It shows an indeterminate progress indicator by default and switches
 * to a determinate (max/progress) indicator once {@link #setMax(int)} or {@link #setProgress(int)}
 * is called.
 */
@SuppressLint("SetTextI18n")
public class M3ProgressDialog extends AlertDialog {

  private final TextView messageView;
  private final LinearProgressIndicator progressView;

  public M3ProgressDialog(@NonNull Context context) {
    super(context);
    setCanceledOnTouchOutside(false);

    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);

    messageView = new TextView(context);
    messageView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    content.addView(
        messageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    progressView = new LinearProgressIndicator(context);
    progressView.setIndeterminate(true);
    LinearLayout.LayoutParams progressLp =
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    progressLp.topMargin = (int) (8 * density);
    content.addView(progressView, progressLp);

    setView(content);
  }

  @Override
  public void setMessage(CharSequence message) {
    messageView.setText(message);
  }

  /** Switches the indicator to determinate mode with the given maximum value. */
  public void setMax(int max) {
    progressView.setIndeterminate(false);
    progressView.setMax(max);
  }

  /** Sets the current progress in determinate mode. */
  public void setProgress(int progress) {
    progressView.setIndeterminate(false);
    progressView.setProgressCompat(progress, true);
  }
}
