/*
 * Copyright (C) 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

import android.app.Activity;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.widget.PopupMenu;

/**
 * Shows a platform popup menu below a compose overflow button.
 *
 * <p>The popup itself stays platform, which is what the toolbar used to open, so the menu renders
 * and behaves the way it always has. Compose only supplies the position, measured from where it
 * drew the button.
 */
public class ComposeOverflowMenu {

  /** Fills a freshly emptied menu with the items to show. */
  public interface MenuBuilder {
    void build(Menu menu);
  }

  private final Activity activity;
  private View anchor;
  private PopupMenu popup;

  public ComposeOverflowMenu(Activity activity) {
    this.activity = activity;
  }

  /**
   * Shows the menu.
   *
   * @param left the left edge of the compose overflow button in window coordinates
   * @param top the top edge of the compose overflow button in window coordinates
   * @param right the right edge of the compose overflow button in window coordinates
   * @param bottom the bottom edge of the compose overflow button in window coordinates
   * @param builder fills the menu with its items
   * @param listener receives the picked item
   */
  public void show(
      int left, int top, int right, int bottom, MenuBuilder builder, OnItemSelected listener) {
    if (popup == null) {
      // The popup needs a view to anchor against. Compose cannot hand it one, so an invisible
      // zero sized view marks the spot instead. Zero height is deliberate: the popup drops below
      // its anchor's top edge only when the anchor is that tall, and this is where the platform's
      // own overflow button puts the top of the menu.
      anchor = new View(activity);
      anchor.setVisibility(View.INVISIBLE);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
      params.gravity = Gravity.TOP | Gravity.START;
      // 8px in from the button's right edge, which is where the platform lands the menu's edge.
      int anchorLeft = right - 8;
      params.leftMargin = anchorLeft;
      params.topMargin = top;
      ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
      decor.addView(anchor, params);
      // Adding a view does not lay it out until the next pass and the popup reads the anchor's
      // rectangle the moment it is shown, so the rectangle is filled in here rather than left
      // empty for it to give up on.
      anchor.layout(anchorLeft, top, anchorLeft, top);

      popup = new PopupMenu(activity, anchor, Gravity.END);
    }
    popup.getMenu().clear();
    builder.build(popup.getMenu());
    popup.setOnMenuItemClickListener(
        item -> {
          listener.onItemSelected(item);
          return true;
        });
    popup.show();
  }

  /** Receives the item the user picked out of the popup menu. */
  public interface OnItemSelected {
    void onItemSelected(MenuItem item);
  }
}
