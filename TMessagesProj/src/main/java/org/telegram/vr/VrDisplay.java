/*
 * Nicegram VR — the interface scale, attached where density is actually decided.
 *
 * The first attempt multiplied AndroidUtilities.density in Application.onCreate. That value is
 * overwritten a moment later by checkDisplaySize, which reads it back from the display metrics
 * and runs before the first screen exists — so the scale the fork is named for was lost every
 * single launch and nobody could see it happen. This registry exists so the factor is applied
 * at the one place the assignment happens, and nowhere else.
 *
 * Inert on every other flavour: with nothing installed the factor is exactly 1.
 */
package org.telegram.vr;

public final class VrDisplay {

    /** Installed by the headset flavour at application start. */
    public interface Scale {
        float factor();
    }

    private static volatile Scale scale;

    private VrDisplay() {
    }

    public static void install(Scale newScale) {
        scale = newScale;
    }

    /**
     * The multiplier for {@code AndroidUtilities.density}, never zero and never absurd.
     *
     * Fails safe to 1: a scale that throws, or returns something outside a sane band, would
     * otherwise produce an interface measured in nothing at all, on a device where the user
     * cannot reach a settings screen to undo it.
     */
    public static float factor() {
        final Scale s = scale;
        if (s == null) {
            return 1f;
        }
        try {
            final float f = s.factor();
            return (f >= 0.5f && f <= 4f) ? f : 1f;
        } catch (Throwable e) {
            return 1f;
        }
    }
}
