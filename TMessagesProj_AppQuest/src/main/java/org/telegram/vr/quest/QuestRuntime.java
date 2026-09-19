package org.telegram.vr.quest;

/**
 * Nicegram VR — the one place the headset build's live objects can be found from a screen.
 *
 * The gate is created once, at application start, and the digest lives inside it. Screens need
 * to read that digest; making them construct their own would give them a second, empty one, and
 * the bug would look like "the digest is always empty" rather than like a wiring mistake.
 */
public final class QuestRuntime {

    private static volatile SilenceGate gate;

    private QuestRuntime() {
    }

    public static void setGate(SilenceGate value) {
        gate = value;
    }

    /** Null only before the application has finished starting, which no screen outlives. */
    public static SilenceGate gate() {
        return gate;
    }

    public static Digest digest() {
        final SilenceGate g = gate;
        return g == null ? null : g.digest();
    }
}
