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
     * True when this build draws into a WINDOW smaller than the display it sits on — a 2D panel
     * inside a spatial shell.
     *
     * It matters because upstream computes several sizes from {@code displaySize} exactly once,
     * guarded by {@code == 0}, on the assumption that the first configuration a process sees is
     * the one it will live in. On a phone that holds. On Horizon OS it does not: the first call
     * carries the DISPLAY's configuration and every later one carries the PANEL's. Measured on
     * the simulator, from the client's own log:
     *
     * <pre>
     *   density = 1.25 display size = 2064 2208   &lt;- application context, the whole display
     *   density = 1.25 display size = 525 900     &lt;- the activity, the actual panel
     * </pre>
     *
     * A value frozen on the first line is four times too large and never recovers.
     */
    public static boolean windowed() {
        return scale != null;
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
