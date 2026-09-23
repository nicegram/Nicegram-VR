package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Nicegram VR — when to mention that Nicegram also runs on a phone, and when to stop.
 *
 * <h3>Why the third session and not the first</h3>
 *
 * The first run already spends its one screen on something the user must know: that a closed
 * client receives nothing. Putting a second ask beside it turns the first minute of the product
 * into two adverts, and the person has not yet seen anything worth having.
 *
 * <p>By the third launch they have chosen to come back twice. That is the earliest moment the
 * offer is an offer rather than an interruption.
 *
 * <h3>Once, and then never</h3>
 *
 * Shown exactly once per install. There is no second attempt, no reminder and no counter that
 * resets: a headset is a place people go to be left alone, and this client's whole premise is
 * that it does not interrupt. An offer that returns would contradict the product.
 */
public final class VrMobilePromo {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_SESSIONS = "session_count";
    private static final String KEY_SHOWN = "mobile_promo_shown";

    /** The launch it appears on. Two visits before an offer; the third carries it. */
    private static final int DUE_AT_SESSION = 3;

    private static volatile boolean countedThisProcess;

    private VrMobilePromo() {
    }

    /**
     * Counts this launch, once per process.
     *
     * <p>Once per PROCESS rather than per call, because a Horizon panel's activity is recreated
     * on a configuration change — resizing the panel is a configuration change — and counting
     * there would turn one session into several and fire the offer on the first day.
     */
    public static void countSession(Context context) {
        if (countedThisProcess || context == null) {
            return;
        }
        countedThisProcess = true;
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SESSIONS, prefs.getInt(KEY_SESSIONS, 0) + 1).apply();
    }

    /** The session this launch is, counting from one. */
    public static int sessionCount(Context context) {
        if (context == null) {
            return 0;
        }
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SESSIONS, 0);
    }

    /** True on the third launch, and only until it has been shown once. */
    public static boolean isDue(Context context) {
        if (context == null) {
            return false;
        }
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SHOWN, false)) {
            return false;
        }
        return prefs.getInt(KEY_SESSIONS, 0) >= DUE_AT_SESSION;
    }

    public static void markShown(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHOWN, true).apply();
    }

    /**
     * Pure, so the rule is a test rather than a claim: given a session number and whether the
     * offer has been shown, should it appear now?
     */
    public static boolean due(int sessions, boolean alreadyShown) {
        return !alreadyShown && sessions >= DUE_AT_SESSION;
    }
}
