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

package org.runnerup.workout;

import android.content.Context;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** User-defined display order for local workouts, stored in app_workouts/.order. */
public class WorkoutOrder {

  private static final String ORDER_FILE = ".order";
  private static final String JSON_SUFFIX = ".json";

  public static File orderFile(Context ctx) {
    return new File(ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0), ORDER_FILE);
  }

  public static List<String> load(File orderFile) {
    List<String> order = new ArrayList<>();
    if (orderFile == null || !orderFile.isFile()) return order;
    try (Reader in = new FileReader(orderFile)) {
      JSONArray arr = new JSONArray(readAll(in));
      for (int i = 0; i < arr.length(); i++) order.add(arr.getString(i));
    } catch (IOException | JSONException e) {
      return new ArrayList<>();
    }
    return order;
  }

  public static List<String> apply(List<String> filenames, File orderFile) {
    List<String> order = load(orderFile);
    List<String> result = new ArrayList<>(filenames.size());
    for (String name : order) {
      for (String f : filenames) {
        if (name.contentEquals(stripSuffix(f)) && !result.contains(f)) result.add(f);
      }
    }
    for (String f : filenames) {
      if (!result.contains(f)) result.add(f);
    }
    return result;
  }

  public static void write(File orderFile, List<String> names) throws IOException {
    File tmp = new File(orderFile.getParentFile(), ORDER_FILE + ".tmp");
    try (Writer out = new FileWriter(tmp)) {
      out.write(new JSONArray(names).toString());
      out.flush();
    }
    if (!tmp.renameTo(orderFile)) {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      throw new IOException("Failed to write " + orderFile);
    }
  }

  public static void replace(File orderFile, String oldName, String newName) throws IOException {
    List<String> order = load(orderFile);
    boolean changed = false;
    for (int i = 0; i < order.size(); i++) {
      if (oldName.contentEquals(order.get(i))) {
        order.set(i, newName);
        changed = true;
        break;
      }
    }
    if (changed) write(orderFile, order);
  }

  public static void remove(File orderFile, String name) throws IOException {
    List<String> order = load(orderFile);
    if (order.remove(name)) write(orderFile, order);
  }

  private static String stripSuffix(String filename) {
    if (filename.endsWith(JSON_SUFFIX)) {
      return filename.substring(0, filename.length() - JSON_SUFFIX.length());
    }
    return filename;
  }

  private static String readAll(Reader in) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[1024];
    int n;
    while ((n = in.read(buf)) > 0) sb.append(buf, 0, n);
    return sb.toString();
  }
}
