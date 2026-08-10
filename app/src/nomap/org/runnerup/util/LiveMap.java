package org.runnerup.util;

import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

public class LiveMap {

  public LiveMap(MapViewWrapper mapView, View recenterButton) {}

  public void onCreate(Bundle savedInstanceState) {}

  public void onFirstShow(SQLiteDatabase mDB, long activityId) {}

  public void onLocationChanged(Location location) {}

  public void onResume() {}

  public void onPause() {}

  public void onDestroy() {}
}
