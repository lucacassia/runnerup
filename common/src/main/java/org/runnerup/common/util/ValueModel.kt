/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.common.util

import java.util.ArrayList

class ValueModel<T> @JvmOverloads constructor(value: T? = null) {
  private var value: T? = value

  private val listeners = ArrayList<ChangeListener<T>>()

  fun interface ChangeListener<T> {
    fun onValueChanged(instance: ValueModel<T>?, oldValue: T?, newValue: T?)
  }

  fun set(newValue: T?) {
    if (value == null && newValue == null) {
      return
    } else if (value != null && newValue != null) {
      if (value == newValue) return
    }

    val oldValue = value
    value = newValue

    val copy = ArrayList(listeners)
    for (l in copy) {
      l.onValueChanged(this, oldValue, newValue)
    }
  }

  fun get(): T? = value

  fun registerChangeListener(listener: ChangeListener<T>?) {
    if (listener == null) throw IllegalArgumentException("listener is null")
    listeners.add(listener)
  }

  fun unregisterChangeListener(listener: ChangeListener<T>?) {
    if (listener == null) throw IllegalArgumentException("listener is null")
    listeners.remove(listener)
  }

  fun clearListeners() {
    listeners.clear()
  }
}
