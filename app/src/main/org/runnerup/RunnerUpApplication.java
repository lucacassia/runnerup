package org.runnerup;

import android.app.Application;
import org.runnerup.util.ThemeUtil;

public class RunnerUpApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    ThemeUtil.applyThemeMode(this);
  }
}
