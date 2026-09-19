package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.LiteMode;

/**
 * Nicegram VR — the frame budget, set once as a default rather than enforced.
 *
 * Not a preference: 60 fps is a condition of publishing on the platform, and media that plays
 * by itself is the cheapest way to lose it. So the headset build starts with autoplay off. It
 * is a DEFAULT and not a lock — the user can turn it back on in the headset settings, and the
 * measurement that decides whether that was wise is on a device, not in this file.
 */
public final class VrPerformance {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_APPLIED = "performance_defaults_applied";

    private VrPerformance() {
    }

    /**
     * Applied once per install. Running it on every start would silently undo the user's own
     * choice every time they opened the app, which is worse than never setting a default.
     */
    public static void applyDefaultsOnce(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_APPLIED, false)) {
            return;
        }
        applyDefaults();
        prefs.edit().putBoolean(KEY_APPLIED, true).apply();
    }

    /** Also reachable from "reset to defaults" in the headset settings. */
    public static void applyDefaults() {
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, false);
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, false);
    }
}
