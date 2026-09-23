package org.telegram.vr;

import android.graphics.Bitmap;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.TelegramQRCodeWriter;

import java.util.HashMap;
import java.util.Map;

/**
 * Nicegram VR — a QR code from a URL, for screens that live in the headset module.
 *
 * <h3>Why it is here and not there</h3>
 *
 * zxing is declared {@code implementation 'com.google.zxing:core:3.5.4'} in this module's
 * {@code build.gradle:51}. An {@code implementation} dependency is on this module's compile
 * classpath and on nobody else's, so the headset module — which depends on this one — cannot
 * name {@code EncodeHintType} at all. Promoting it to {@code api} would put zxing on the
 * classpath of every flavour that has no use for it; a six-line function on this side does
 * the same job and leaks nothing.
 *
 * <h3>The hints</h3>
 *
 * The same pair {@code QRCodeBottomSheet} uses: error correction <b>M</b>, which survives a
 * camera held at an angle across a room, and margin <b>0</b>, because the quiet zone is drawn
 * by the view's own padding rather than inside the bitmap.
 */
public final class VrQrCode {

    private VrQrCode() {
    }

    /**
     * @return a square black-on-white code, or null. Null rather than an exception: every
     *         screen that shows one of these also shows the link as text or as a button, and
     *         a download offer is not worth crashing a client for.
     */
    public static Bitmap render(String contents, int sizePx) {
        if (contents == null || contents.isEmpty() || sizePx <= 0) {
            return null;
        }
        try {
            final Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            return new TelegramQRCodeWriter().encode(contents, sizePx, sizePx, hints, null);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
