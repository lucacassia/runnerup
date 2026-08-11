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

  @Test
  public void renderDimsMatchRequestedSize() {
    assertEquals(
        200 * 64, Code128Barcode.pixels(Code128Barcode.encode("C1234567"), 200, 64).length);
  }

  @Test
  public void renderQuietZonesAreTransparent() {
    BitMatrix matrix = Code128Barcode.encode("C1234567");
    int widthPx = 200;
    int[] pixels = Code128Barcode.pixels(matrix, widthPx, DECODE_HEIGHT);
    int matrixWidth = matrix.getWidth();
    for (int x = 0; x < 4; x++) {
      assertColumnsTransparent(
          pixels, widthPx, x * widthPx / matrixWidth, (x + 1) * widthPx / matrixWidth);
      assertColumnsTransparent(
          pixels,
          widthPx,
          (matrixWidth - 1 - x) * widthPx / matrixWidth,
          (matrixWidth - x) * widthPx / matrixWidth);
    }
  }

  @Test
  public void renderDecodesBack() throws ReaderException {
    String value = "C1234567";
    assertEquals(
        value,
        decode(
            overLightBackdrop(
                Code128Barcode.pixels(Code128Barcode.encode(value), 200, DECODE_HEIGHT)),
            200));
  }

  @Test(expected = IllegalArgumentException.class)
  public void renderGuardRejectsNonPositive() {
    Code128Barcode.renderToBitmap(Code128Barcode.encode("C1234567"), 0, DECODE_HEIGHT);
    Code128Barcode.renderToBitmap(Code128Barcode.encode("C1234567"), 200, 0);
  }

  private static String decode(int[] pixels, int width) throws ReaderException {
    BinaryBitmap image =
        new BinaryBitmap(
            new GlobalHistogramBinarizer(new RGBLuminanceSource(width, DECODE_HEIGHT, pixels)));
    Result result = new Code128Reader().decode(image);
    return result.getText();
  }

  private static void assertColumnsTransparent(int[] pixels, int widthPx, int x0, int x1) {
    for (int px = x0; px < x1; px++) {
      for (int y = 0; y < DECODE_HEIGHT; y++) {
        assertEquals(0x00000000, pixels[y * widthPx + px]);
      }
    }
  }

  private static int[] overLightBackdrop(int[] pixels) {
    int[] out = new int[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      out[i] = (pixels[i] & 0xFF000000) == 0 ? 0xFFF5F8FD : pixels[i];
    }
    return out;
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
