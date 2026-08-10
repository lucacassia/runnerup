package org.runnerup.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.oned.Code128Reader;
import org.junit.Test;

public class Code128BarcodeTest {
  private static final int DECODE_HEIGHT = 64;

  @Test
  public void roundTripShortValue() throws ReaderException {
    String value = "C1234567";
    assertEquals(value, decode(Code128Barcode.encode(value)));
  }

  @Test
  public void roundTripLongValue() throws ReaderException {
    String value = "A12345678901234";
    assertEquals(value, decode(Code128Barcode.encode(value)));
  }

  @Test
  public void deterministicEncoding() {
    assertEquals(Code128Barcode.encode("C1234567"), Code128Barcode.encode("C1234567"));
  }

  @Test
  public void quietZonesAreWhite() {
    BitMatrix matrix = Code128Barcode.encode("C1234567");
    for (int x = 0; x < 4; x++) {
      assertFalse(matrix.get(x, 0));
      assertFalse(matrix.get(matrix.getWidth() - 1 - x, 0));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullContentRejected() {
    Code128Barcode.encode(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyContentRejected() {
    Code128Barcode.encode("");
  }

  private static String decode(BitMatrix matrix) throws ReaderException {
    BinaryBitmap image =
        new BinaryBitmap(new GlobalHistogramBinarizer(toLuminanceSource(matrix, DECODE_HEIGHT)));
    Result result = new Code128Reader().decode(image);
    return result.getText();
  }

  private static LuminanceSource toLuminanceSource(BitMatrix matrix, int height) {
    int width = matrix.getWidth();
    int[] pixels = new int[width * height];
    for (int x = 0; x < width; x++) {
      boolean dark = matrix.get(x, 0);
      for (int y = 0; y < height; y++) {
        pixels[y * width + x] = dark ? 0xFF000000 : 0xFFFFFFFF;
      }
    }
    return new RGBLuminanceSource(width, height, pixels);
  }
}
