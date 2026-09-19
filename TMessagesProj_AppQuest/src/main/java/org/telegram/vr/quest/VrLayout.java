package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

/**
 * Nicegram VR — one column, because a headset panel is not a tablet.
 *
 * Android decides the tablet layout from `smallestScreenWidthDp`, and a Quest 3 panel reports
 * **sw640dp** — above the 600 threshold, so upstream splits the screen in two. Measured on a
 * device on 19 September 2026, that is the wrong answer twice over. The panel is 1280 px wide
 * and this build draws at 1.925 px/dp, so the whole surface is about 665 dp — narrower in
 * usable terms than a large phone. Splitting it leaves the chat list eating most of the width
 * and the conversation squeezed into what is left, which is what the panel actually looked
 * like before this file existed.
 *
 * The switch is upstream's own — `SharedConfig.forceDisableTabletMode` — so nothing here
 * reimplements a layout. It is set as a DEFAULT, once per install, exactly like
 * {@link VrPerformance}: a person who prefers the two-column layout can turn it back on in the
 * headset settings and keep it. Forcing it on every start would quietly undo their choice,
 * which is worse than never having a default.
 */
public final class VrLayout {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_APPLIED = "layout_default_applied";

    private VrLayout() {
    }

    /** True when the panel shows one thing at a time: the chat list, or a chat. */
    public static boolean isSingleColumn() {
        return SharedConfig.forceDisableTabletMode;
    }

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
        if (!SharedConfig.forceDisableTabletMode) {
            setSingleColumn(true);
        }
    }

    /**
     * Upstream's toggle writes both the field and the preference, so this stays a call rather
     * than a second copy of the same two lines. {@link AndroidUtilities#resetTabletFlag()} is
     * what makes a running app notice; a restart is still the honest advice, because fragments
     * already built keep the geometry they were built with.
     */
    public static void setSingleColumn(boolean single) {
        if (SharedConfig.forceDisableTabletMode != single) {
            SharedConfig.toggleForceDisableTabletMode();
            AndroidUtilities.resetTabletFlag();
        }
    }
}
