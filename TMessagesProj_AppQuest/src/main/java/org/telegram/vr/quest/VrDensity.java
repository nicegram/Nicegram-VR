package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;

/**
 * Nicegram VR — interface scale for a panel, not a phone.
 *
 * <h3>What this file used to say, and why it is gone</h3>
 *
 * The first version of this class derived a single multiplier of 1.54 from an assumed panel:
 * "1440x900 dp at 1.3 m covering 52 degrees, so one dp is 0.881 mm, which is 2.33 arc-minutes".
 * A Quest 3 was measured on 19 September 2026 and the panel is **1280x800 px at 200 dpi** —
 * 1024x640 dp before any scaling of ours. Multiplying by 1.54 leaves the app 415 dp of height
 * to work with, which is less than a phone has, and the consequence was reported from inside
 * the headset before it was noticed here: **four chats on a screen wide enough for far more**.
 *
 * (415.6 dp of panel, minus 48 dp of action bar and 44 dp of folder strip, over a 70 dp
 * {@code DialogCell} — 4.6 rows. The complaint and the arithmetic agree to within a row.)
 *
 * So the scale is re-based. The steps below are absolute multipliers on the system density
 * rather than on an assumed viewing distance, the default is **1.0** — the panel's own density,
 * which the platform already chose for a panel — and the old 1.54 survives as the largest step
 * for anyone who sits far from it. A headset panel is virtual: its angular size is the wearer's
 * to change by moving it, and that is a better lever than a number compiled into an app.
 *
 * <h3>The floor that does not move</h3>
 *
 * The scale changes text and rows together. What it must never do is shrink a hit target below
 * the floor: ray jitter is a property of the hand, not of a preference, so the minimum target
 * stays 64 dp at every step. That is the one setting in this client that is deliberately
 * clamped, and {@link #minTargetPx} is what enforces it.
 */
public final class VrDensity {

    private static final String FILE = "nicegram_vr_display";

    /**
     * Version 2 of the key. The old {@code density_step} held an index into a different scale
     * whose middle was 1.54, so reusing it would silently give a saved "normal" the largest
     * step. A new name re-defaults everyone once, deliberately.
     */
    private static final String KEY_STEP = "density_step_v2";

    public static final int STEP_MOST_ROWS = 0;
    public static final int STEP_BALANCED = 1;
    public static final int STEP_LARGER = 2;
    public static final int STEP_LARGEST = 3;

    /** Absolute multipliers on the system density. 1.0 means "the panel as the platform sized it". */
    private static final float[] STEP_SCALE = {0.85f, 1.0f, 1.25f, 1.54f};

    public static final int STEP_COUNT = 4;

    /** 64 dp, and it is not multiplied by the step below. See {@link #minTargetPx}. */
    public static final int MIN_TARGET_DP = 64;

    /** {@code DialogCell.heightDefault} — the two-line row this client shows by default. */
    public static final int ROW_DP = 70;

    /**
     * Action bar plus the folder strip. Folders are one of the reasons this client exists, so
     * the strip is the common case and pretending otherwise would overstate every count by one.
     */
    public static final int CHROME_DP = 92;

    /**
     * Read once and held. checkDisplaySize calls this on every configuration change, and the
     * step is documented as taking effect at the next start, so re-reading preferences per call
     * would buy nothing and cost a disk touch on the layout path.
     */
    private static volatile float cachedFactor = -1f;

    private VrDensity() {
    }

    /** Pure, so the scale can be tested without Android. */
    public static float factorForStep(int step) {
        if (step < 0 || step >= STEP_SCALE.length) {
            step = STEP_BALANCED;
        }
        return STEP_SCALE[step];
    }

    /**
     * How many chat rows fit on this panel at this step — pure, and the honest way to present a
     * density setting. "Compact" and "Large" describe the control; a number of chats describes
     * what the person actually gets, and it is the thing they complained about.
     *
     * @param panelHeightPx the panel's height in real pixels
     * @param systemDensity what the platform reports, before any step of ours
     */
    public static int rowsForFactor(int panelHeightPx, float systemDensity, float factor) {
        if (panelHeightPx <= 0 || systemDensity <= 0f || factor <= 0f) {
            return 0;
        }
        final float panelDp = panelHeightPx / (systemDensity * factor);
        final float listDp = panelDp - CHROME_DP;
        return listDp <= 0f ? 0 : (int) (listDp / ROW_DP);
    }

    public static int rowsForStep(int panelHeightPx, float systemDensity, int step) {
        return rowsForFactor(panelHeightPx, systemDensity, factorForStep(step));
    }

    public static float factor(Context context) {
        float f = cachedFactor;
        if (f < 0f) {
            f = factorForStep(step(context));
            cachedFactor = f;
        }
        return f;
    }

    public static int step(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        int step = prefs.getInt(KEY_STEP, STEP_BALANCED);
        return step < 0 || step >= STEP_SCALE.length ? STEP_BALANCED : step;
    }

    /**
     * Stores the chosen step. It takes effect on the next process start: rescaling a running
     * Telegram UI mid-session is the kind of change that looks cheap and is not.
     */
    public static void setStep(Context context, int step) {
        if (step < 0 || step >= STEP_SCALE.length) {
            step = STEP_BALANCED;
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_STEP, step).apply();
        cachedFactor = -1f;
    }

    /**
     * The hit-target floor in pixels, for layout code that places an interactive element.
     *
     * Note what it does NOT do: it never multiplies by the density step. A user choosing more
     * rows is trading legibility for how much fits, which is theirs to trade. A smaller target
     * is a trade nobody asked for.
     */
    public static int minTargetPx() {
        return AndroidUtilities.dp(MIN_TARGET_DP);
    }
}
