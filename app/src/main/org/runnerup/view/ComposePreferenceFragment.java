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
package org.runnerup.view;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import java.util.function.Consumer;

/**
 * A preferences screen that keeps {@link PreferenceFragmentCompat} for the preference model and
 * replaces only its rendering with compose.
 *
 * <p>The fragment still inflates the preference XML, so the sorting, the dependencies, the enabled
 * state and every click and change listener keep working exactly as they did. Only the rows are
 * drawn by compose, which leaves the library's own recycler view in the hierarchy, unused, as the
 * place where that model lives.
 */
public abstract class ComposePreferenceFragment extends PreferenceFragmentCompat {

  private final PreferenceScreenHolder holder = new PreferenceScreenHolder();

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    // The library wires itself to this recycler view, so it has to stay, but compose draws over
    // it. It goes where the recycler view sits rather than over the whole fragment, because a
    // screen such as the audio cues one wraps the list in a layout of its own.
    View recyclerView = view.findViewById(androidx.preference.R.id.recycler_view);
    if (recyclerView == null) {
      return;
    }
    recyclerView.setVisibility(View.GONE);
    ViewGroup parent = (ViewGroup) recyclerView.getParent();
    ComposeView compose = new ComposeView(requireContext());
    compose.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    parent.addView(compose, parent.indexOfChild(recyclerView) + 1);
    holder.bind(compose, (Consumer<Preference>) this::onPreferenceChosen);
    bindPreferences();
  }

  @Override
  public void onResume() {
    super.onResume();
    // A preference may have been changed from another screen, so the rows are rebound every time.
    bindPreferences();
  }

  @Override
  public void onDestroyView() {
    holder.setRows(java.util.Collections.emptyList());
    super.onDestroyView();
  }

  private void bindPreferences() {
    androidx.preference.PreferenceScreen screen = getPreferenceScreen();
    if (screen == null) {
      return;
    }
    java.util.List<Preference> rows = new java.util.ArrayList<>();
    collect(screen, rows);
    holder.setRows(rows);
  }

  /**
   * Collects the preferences in the order the library draws them.
   *
   * <p>A settings screen nests its preferences inside its categories, so the rows hang off the
   * category rather than off the screen. The library draws a category and then the preferences it
   * contains, which is the order they are collected in here.
   */
  private static void collect(
      androidx.preference.PreferenceGroup group, java.util.List<Preference> out) {
    for (int i = 0; i < group.getPreferenceCount(); i++) {
      Preference preference = group.getPreference(i);
      if (!preference.isVisible()) {
        continue;
      }
      out.add(preference);
      if (preference instanceof androidx.preference.PreferenceGroup) {
        collect((androidx.preference.PreferenceGroup) preference, out);
      }
    }
  }

  /**
   * Handles a tap the way the library's own row does.
   *
   * <p>{@link Preference#performClick()} is what the row's view calls, and it is what dispatches a
   * click listener, an intent, the fragment a preference names and its dialog. Going through {@link
   * PreferenceFragmentCompat#onPreferenceTreeClick} directly would only ever navigate, because that
   * is all the fragment's half of the dispatch does.
   */
  @SuppressLint("RestrictedApi") // the row this replaces is the library's own caller of it
  private void onPreferenceChosen(Preference preference) {
    preference.performClick();
    // A tap changes the preference, and the rows read their state straight off the preference
    // objects, so they have to be handed over again for the change to show.
    bindPreferences();
  }
}
