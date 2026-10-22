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
package org.runnerup.util;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import org.junit.Test;

public class FormatterTest {

  private static Date date(int year, int month, int day) {
    return Date.from(
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  @Test
  public void datesAreDayFirstWithFourDigitYear() {
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    try {
      assertEquals("24/09/2026", Formatter.newDateFormat().format(date(2026, 9, 24)));
      assertEquals("05/01/2026", Formatter.newDateFormat().format(date(2026, 1, 5)));
      assertEquals("31/12/1999", Formatter.newDateFormat().format(date(1999, 12, 31)));
    } finally {
      Locale.setDefault(original);
    }
  }
}
