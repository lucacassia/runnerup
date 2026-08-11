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

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class Code128Barcode {
  private static final int QUIET_ZONE_MODULES = 4;

  private Code128Barcode() {}

  public static BitMatrix encode(String content) {
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("barcode content must not be null or empty");
    }
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES * 2);
    return new Code128Writer().encode(content, BarcodeFormat.CODE_128, 0, 1, hints);
  }

  public static Bitmap renderToBitmap(BitMatrix matrix, int widthPx, int heightPx) {
    if (widthPx <= 0 || heightPx <= 0) {
      throw new IllegalArgumentException("bitmap width and height must be positive");
    }
    int[] pixels = pixels(matrix, widthPx, heightPx);
    Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx);
    return bitmap;
  }

  static int[] pixels(BitMatrix matrix, int widthPx, int heightPx) {
    int[] pixels = new int[widthPx * heightPx];
    Arrays.fill(pixels, Color.TRANSPARENT);
    for (int x = 0; x < matrix.getWidth(); x++) {
      if (matrix.get(x, 0)) {
        int x0 = x * widthPx / matrix.getWidth();
        int x1 = (x + 1) * widthPx / matrix.getWidth();
        for (int px = x0; px < x1; px++) {
          for (int y = 0; y < heightPx; y++) {
            pixels[y * widthPx + px] = Color.BLACK;
          }
        }
      }
    }
    return pixels;
  }
}
