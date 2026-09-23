package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.telegram.vr.VrDisplay;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Round video messages must be sized from the PANEL, not from the display behind it.
 *
 * <h3>The defect this pins (P-20, reported from a headset on 21 September)</h3>
 *
 * Upstream computes {@code roundMessageSize} and its two playing variants once, guarded by
 * {@code roundMessageSize == 0}, on the assumption that the first configuration a process sees
 * is the one it will live in. On a phone that holds. On Horizon OS it does not — the client's
 * own log, measured on the simulator:
 *
 * <pre>
 *   density = 1.25 display size = 2064 2208   &lt;- application context, the whole display
 *   density = 1.25 display size = 525 900     &lt;- the activity, the actual panel
 * </pre>
 *
 * A value frozen on the first line is 1238 px of video drawn into 525 px of panel. That is the
 * clipping that was reported, and the fix is one clause: recompute whenever the window is not
 * the display.
 *
 * <h3>Why the plan's own hypothesis is not what this tests</h3>
 *
 * {@code plan.md} P-20 proposed that the three sizes are in raw pixels while everything around
 * them is in dp, so a larger interface step breaks the relationship. Worked through, that
 * predicts the circle becomes relatively <i>smaller</i> at a larger step, not clipped — the
 * arithmetic never reproduced the report. The cause was staleness, not scale.
 *
 * <h3>Why the source check</h3>
 *
 * The clause lives in {@code AndroidUtilities.checkDisplaySize}, a method upstream rewrites. If
 * a merge restores {@code roundMessageSize == 0} on its own, the clipping returns and nothing
 * else in this project would notice — no compiler, no unit test of ours, and nobody without a
 * headset in front of them.
 */
public class RoundVideoSizingTest {

    @After
    public void clearTheRegistry() {
        // Static and global: a scale left installed would leak into every other test.
        VrDisplay.install(null);
    }

    @Test
    public void nothingInstalledMeansNotWindowed() {
        VrDisplay.install(null);
        assertFalse("with no scale installed this must read as an ordinary phone, so upstream's "
                + "compute-once behaviour is untouched on every other flavour", VrDisplay.windowed());
        assertEquals(1f, VrDisplay.factor(), 0.0001f);
    }

    @Test
    public void aScaleInstalledMeansWindowed() {
        VrDisplay.install(() -> 1.25f);
        assertTrue("the headset flavour installs a scale at application start, and that is what "
                + "marks this process as drawing into a panel rather than a display",
                VrDisplay.windowed());
    }

    @Test
    public void theRecomputeClauseIsStillInUpstreamsMethod() throws IOException {
        final String body = read("TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java");

        // Matched on the two things that must both be true rather than on one exact spelling:
        // that the round sizes are still computed here, and that `windowed()` still has a say.
        // An earlier draft asserted the whole condition verbatim, which would have failed on a
        // harmless reordering of the two operands — a guard that cries wolf is a guard people
        // learn to silence.
        final int computed = body.indexOf("roundMessageSize =");
        assertTrue("checkDisplaySize no longer computes roundMessageSize at all; this guard is "
                + "pointing at code that has moved", computed > 0);

        final int guard = body.indexOf("roundMessageSize == 0");
        assertTrue("the compute-once guard is gone from AndroidUtilities", guard > 0);

        final String around = body.substring(guard, Math.min(body.length(), guard + 160));
        assertTrue("checkDisplaySize no longer recomputes the round-video sizes when the window "
                        + "is not the display — round videos will be sized from 2064x2208 and drawn "
                        + "into a 525 px panel again (P-20). Found instead: " + around.split("\n")[0],
                around.contains("VrDisplay.windowed()"));
    }

    /**
     * The sizes upstream computes, reproduced here so the arithmetic is on the record.
     *
     * <p>Not a call into upstream: {@code checkDisplaySize} needs a {@code Context} and a
     * {@code Configuration}. This is the same three formulas against the measured panel, and its
     * job is to say what the correct answer looks like — all three inside the panel — so that a
     * future change to them has something to fail against.
     */
    @Test
    public void everySizeFitsTheMeasuredPanel() {
        final int panelX = 525, panelY = 900;
        final float density = 1.25f;              // the default interface step
        final int dp28 = Math.round(28 * density);
        final int dp64 = Math.round(64 * density);

        final int idle = (int) (Math.min(panelX, panelY) * 0.6f);
        final int playing = Math.min(panelX, panelY) - dp28;
        final int besideAvatar = Math.min(panelX - dp64, panelY) - dp28;

        assertTrue("idle round video " + idle + " px does not fit a " + panelX + " px panel",
                idle <= panelX);
        assertTrue("playing round video " + playing + " px does not fit", playing <= panelX);
        assertTrue("round video beside an avatar " + besideAvatar + " px does not fit",
                besideAvatar <= panelX);

        // And the number the bug actually produced, for contrast: the display is 2064 wide.
        final int fromTheDisplay = (int) (Math.min(2064, 2208) * 0.6f);
        assertTrue("sized from the display it is " + fromTheDisplay + " px, which is what 525 px "
                + "of panel could not hold", fromTheDisplay > panelX);
    }

    private static String read(String relative) throws IOException {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, relative);
            if (candidate.isFile()) {
                return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("could not find " + relative);
    }
}
