package org.runnerup.util;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.google.android.material.color.DynamicColors;
import org.runnerup.R;

public class ThemeUtil {

  private ThemeUtil() {}

  public static void applyThemeMode(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String mode = prefs.getString(context.getString(R.string.pref_theme_mode), "system");
    applyNightMode(context, mode);
    applyDynamicColor(context, prefs);
  }

  public static void applyDynamicColor(Context context, SharedPreferences prefs) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return;
    }
    boolean enabled = prefs.getBoolean(context.getString(R.string.pref_dynamic_color), false);
    if (enabled && context instanceof Application) {
      DynamicColors.applyToActivitiesIfAvailable((Application) context);
    }
  }

  public static void applyNightMode(Context context, String mode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
      if (uiModeManager != null) {
        uiModeManager.setApplicationNightMode(toUiModeNightMode(mode));
      }
    } else {
      AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }
  }

  public static int toNightMode(String mode) {
    return switch (mode) {
      case "light" -> AppCompatDelegate.MODE_NIGHT_NO;
      case "dark" -> AppCompatDelegate.MODE_NIGHT_YES;
      default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    };
  }

  private static int toUiModeNightMode(String mode) {
    return switch (mode) {
      case "light" -> UiModeManager.MODE_NIGHT_NO;
      case "dark" -> UiModeManager.MODE_NIGHT_YES;
      default -> UiModeManager.MODE_NIGHT_AUTO;
    };
  }
}
