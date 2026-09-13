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

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Pinned-workout persistence: a JSON array of favorite display names plus a "most recently started"
 * name, both in SharedPreferences.
 */
public class FavoritesStore {

  private final SharedPreferences prefs;
  private final String favoriteKey;
  private final String lastUsedKey;

  public FavoritesStore(SharedPreferences prefs, String favoriteKey, String lastUsedKey) {
    this.prefs = prefs;
    this.favoriteKey = favoriteKey;
    this.lastUsedKey = lastUsedKey;
  }

  private List<String> readList(String key) {
    List<String> out = new ArrayList<>();
    String raw = prefs.getString(key, "[]");
    try {
      JSONArray arr = new JSONArray(raw);
      for (int i = 0; i < arr.length(); i++) {
        out.add(arr.getString(i));
      }
    } catch (JSONException e) {
      // malformed value: fall back to empty
    }
    return out;
  }

  private void writeList(String key, List<String> list) {
    prefs.edit().putString(key, new JSONArray(list).toString()).apply();
  }

  public List<String> getFavorites() {
    return readList(favoriteKey);
  }

  public boolean isFavorite(String name) {
    return getFavorites().contains(name);
  }

  public void addFavorite(String name) {
    List<String> list = getFavorites();
    if (list.contains(name)) {
      return;
    }
    list.add(name);
    writeList(favoriteKey, list);
  }

  public void removeFavorite(String name) {
    List<String> list = getFavorites();
    if (list.remove(name)) {
      writeList(favoriteKey, list);
    }
  }

  /**
   * @return true if name is now a favorite
   */
  public boolean toggleFavorite(String name) {
    if (isFavorite(name)) {
      removeFavorite(name);
      return false;
    }
    addFavorite(name);
    return true;
  }

  public String getLastUsed() {
    String name = prefs.getString(lastUsedKey, "");
    return name.isEmpty() ? null : name;
  }

  public void setLastUsed(String name) {
    if (name == null || name.isEmpty()) {
      return;
    }
    prefs.edit().putString(lastUsedKey, name).apply();
  }

  /** Resume pins: most recently started first, then favorites (deduped). */
  public List<String> getPins() {
    List<String> pins = new ArrayList<>();
    String last = getLastUsed();
    if (last != null) {
      pins.add(last);
    }
    for (String fav : getFavorites()) {
      if (!pins.contains(fav)) {
        pins.add(fav);
      }
    }
    return pins;
  }

  /** Drop name from last-used when the workout file is deleted (favorites stay). */
  public void cleanupDeleted(String name) {
    if (name == null || name.isEmpty() || isFavorite(name)) {
      return;
    }
    String last = prefs.getString(lastUsedKey, "");
    if (name.equals(last)) {
      prefs.edit().remove(lastUsedKey).apply();
    }
  }

  public void rename(String oldName, String newName) {
    if (oldName == null || oldName.isEmpty() || newName == null || newName.isEmpty()) {
      return;
    }
    if (oldName.equals(newName)) {
      return;
    }
    List<String> favs = getFavorites();
    boolean changedFavs = false;
    for (int i = 0; i < favs.size(); i++) {
      if (oldName.equals(favs.get(i))) {
        favs.set(i, newName);
        changedFavs = true;
      }
    }
    if (changedFavs) {
      writeList(favoriteKey, favs);
    }
    String last = prefs.getString(lastUsedKey, "");
    if (oldName.equals(last)) {
      prefs.edit().putString(lastUsedKey, newName).apply();
    }
  }
}
