package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * leanview: QR codes drawn on the TV. Upstream asks a QR web service for its sign-in QR; a Quick
 * Connect link carries a session code, so it is not sent to a third party.
 */
public final class QrCode {
    private QrCode() {
    }

    /** The code as black and white points, size x size, with the 4-module quiet zone the QR specification asks for (a phone camera needs it on a dark screen). */
    static BitMatrix matrix(String text, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 4);
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
    }

    /** A data: URI (PNG) Glide can load like any image URL. */
    public static String dataUri(String text, int size) {
        try {
            BitMatrix matrix = matrix(text, size);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            return "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}
