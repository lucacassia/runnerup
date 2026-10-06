package org.runnerup.view;

import android.os.Bundle;
import org.runnerup.R;

public class SettingsGraphFragment extends ComposePreferenceFragment {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_graph, rootKey);
  }
}
