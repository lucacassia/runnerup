package org.runnerup.view;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.R;
import org.runnerup.util.ThemeUtil;

public class SettingsUserInterfaceFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_user_interface, rootKey);

    ListPreference themeMode = findPreference(getString(R.string.pref_theme_mode));
    if (themeMode != null) {
      themeMode.setOnPreferenceChangeListener(this::onThemeModeChanged);
    }
  }

  private boolean onThemeModeChanged(@NonNull Preference preference, Object newValue) {
    ThemeUtil.applyNightMode(requireContext(), newValue.toString());
    return true;
  }
}
