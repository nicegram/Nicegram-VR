package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;

/**
 * Nicegram VR — interface scale for a panel, not a phone.
 *
 * The arithmetic, in full, because every number below is derived rather than chosen:
 * the panel is 1440x900 dp at 1.3 m covering 52 degrees, so it is 1.268 m wide and one dp is
 * 0.881 mm, which is 2.33 arc-minutes. Upstream's type is calibrated for a screen held at half
 * a metre; at 1.3 m the same text subtends about half of what it needs, so the whole scale is
 * multiplied once.
 *
 * The scale changes text and rows together. What it must never do is shrink a hit target below
 * the floor: ray jitter is a property of the hand, not of a preference, so the minimum target
 * stays 64 dp at every step. That is the one setting in this client that is deliberately
 * clamped, and {@link #minTargetPx} is what enforces it.
 */
public final class VrDensity {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_STEP = "density_step";

    public static final int STEP_COMPACT = 0;
    public static final int STEP_NORMAL = 1;
    public static final int STEP_LARGE = 2;

    /** Multiplied onto upstream's scale so 13 dp body text becomes 20 dp at the normal step. */
    private static final float BASE_MULTIPLIER = 1.54f;
    private static final float[] STEP_SCALE = {0.85f, 1.0f, 1.2f};

    /** 64 dp = 56.4 mm = 2.49 degrees at 1.3 m; ray jitter is 0.5-1.0 degrees. */
    public static final int MIN_TARGET_DP = 64;

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
            step = STEP_NORMAL;
        }
        return BASE_MULTIPLIER * STEP_SCALE[step];
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
        int step = prefs.getInt(KEY_STEP, STEP_NORMAL);
        return step < 0 || step >= STEP_SCALE.length ? STEP_NORMAL : step;
    }

    /**
     * Stores the chosen step. It takes effect on the next process start: rescaling a running
     * Telegram UI mid-session is the kind of change that looks cheap and is not.
     */
    public static void setStep(Context context, int step) {
        if (step < 0 || step >= STEP_SCALE.length) {
            step = STEP_NORMAL;
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_STEP, step).apply();
        cachedFactor = -1f;
    }

    /**
     * The hit-target floor in pixels, for layout code that places an interactive element.
     *
     * Note what it does NOT do: it never multiplies by the density step. A user choosing the
     * compact step is trading legibility for how much fits, which is theirs to trade. A smaller
     * target is a trade nobody asked for.
     */
    public static int minTargetPx() {
        return AndroidUtilities.dp(MIN_TARGET_DP);
    }
}
