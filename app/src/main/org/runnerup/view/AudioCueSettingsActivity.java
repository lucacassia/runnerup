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

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import org.runnerup.R;
import org.runnerup.util.ViewUtil;

public class AudioCueSettingsActivity extends AppCompatActivity {
  public static final String SUFFIX = "_audio_cues";

  private final ComposeOverflowMenu overflow = new ComposeOverflowMenu(this);
  private SettingsToolbarHolder toolbar;

  /**
   * The overflow menu the fragment contributes its items to. Compose draws the button that opens
   * it, but the menu is still the platform popup.
   */
  public ComposeOverflowMenu getOverflowMenu() {
    return overflow;
  }

  /** The app bar, so the fragment can add its items to the overflow. */
  public SettingsToolbarHolder getToolbar() {
    return toolbar;
  }

  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);

    toolbar = new SettingsToolbarHolder(org.runnerup.common.R.string.Audio_cue_settings, true);
    toolbar.bind((ComposeView) findViewById(R.id.actionbar));
    toolbar.setOnBackClickListener(() -> getOnBackPressedDispatcher().onBackPressed());

    Intent intent = getIntent();
    String settingsName = intent.getStringExtra("name");

    // Ensure that the fragment is added only once
    if (savedInstanceState == null) {
      Bundle bundle = new Bundle();
      if (settingsName != null) {
        // If the Intent that started this Activity has an audio cue name, pass it to the Fragment.
        bundle.putString("name", settingsName);
      }

      getSupportFragmentManager()
          .beginTransaction()
          .setReorderingAllowed(true)
          .replace(R.id.settings_fragment_container, AudioCueSettingsFragment.class, bundle)
          .commit();
    }

    ViewUtil.Insets(findViewById(R.id.settings_root_view), true);
  }
}
