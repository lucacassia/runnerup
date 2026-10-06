package org.runnerup.view;

import android.os.Bundle;
import org.runnerup.R;

public class SettingsMapFragment extends ComposePreferenceFragment {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_map, rootKey);
  }
}
