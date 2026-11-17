package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.junit.Test;

// leanview: the QR the TV draws must be one a phone can read. The drawing itself (a PNG) is covered by QrLinkTest; here the module
// matrix behind it is decoded with zxing's own reader, so a blank or wrongly sized picture cannot pass.
public class QrCodeTest {
    private static final String LINK = "https://example.org/activate/ABCD-EFGH?code=1234";

    private static int[] pixels(BitMatrix matrix, int size) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        return pixels;
    }

    @Test
    public void theMatrixDecodesBackToTheLink() throws Exception {
        int size = 512;
        BitMatrix matrix = QrCode.matrix(LINK, size);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size, size, pixels(matrix, size))));
        assertEquals(LINK, new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    public void theCodeKeepsTheQuietZoneTheQrSpecificationAsksFor() throws Exception {
        int size = 512;
        BitMatrix matrix = QrCode.matrix(LINK, size);
        int firstBlackX = -1;
        int firstBlackY = -1;
        for (int y = 0; y < size && firstBlackY < 0; y++) {
            for (int x = 0; x < size; x++) {
                if (matrix.get(x, y)) {
                    firstBlackX = x;
                    firstBlackY = y;
                    break;
                }
            }
        }
        int moduleWidth = 0;
        for (int x = firstBlackX; x < size && matrix.get(x, firstBlackY); x++) {
            moduleWidth++;
        }
        // the first black run of a finder pattern is 7 modules wide
        moduleWidth /= 7;
        assertTrue("module " + moduleWidth + "px, white border " + firstBlackX + "px", moduleWidth > 0 && firstBlackX >= 4 * moduleWidth);
    }
}
