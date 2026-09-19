package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The scale is the feature this fork is named for. It was silently lost once, and then set to a
 * number derived from a panel that did not exist. These pin both halves: the arithmetic, and
 * the count of chats that arithmetic produces on the panel actually measured on 19 September.
 */
public class VrDensityTest {

    private static final float EPS = 0.0001f;

    /**
     * The measured Quest 3 panel. It was 1280x800 while the app declared landscape and became
     * 500x800 when it declared portrait — the HEIGHT is 800 either way, which is the number
     * this arithmetic needs and the one the caller got wrong once already.
     */
    private static final int PANEL_H = 800;
    private static final float SYSTEM_DENSITY = 1.25f;

    @Test
    public void theStepsAreAbsoluteMultipliersOnTheSystemDensity() {
        assertEquals(0.85f, VrDensity.factorForStep(VrDensity.STEP_MOST_ROWS), EPS);
        assertEquals(1.0f, VrDensity.factorForStep(VrDensity.STEP_BALANCED), EPS);
        assertEquals(1.25f, VrDensity.factorForStep(VrDensity.STEP_LARGER), EPS);
        assertEquals(1.54f, VrDensity.factorForStep(VrDensity.STEP_LARGEST), EPS);
    }

    @Test
    public void theDefaultIsThePanelsOwnDensity() {
        assertEquals(1.0f, VrDensity.factorForStep(VrDensity.STEP_BALANCED), EPS);
    }

    @Test
    public void anUnknownStepFallsBackToBalancedRatherThanBreakingTheInterface() {
        assertEquals(VrDensity.factorForStep(VrDensity.STEP_BALANCED), VrDensity.factorForStep(-1), EPS);
        assertEquals(VrDensity.factorForStep(VrDensity.STEP_BALANCED), VrDensity.factorForStep(99), EPS);
    }

    @Test
    public void everyStepStaysInsideTheBandVrDisplayWillAccept() {
        for (int step = 0; step < VrDensity.STEP_COUNT; step++) {
            float f = VrDensity.factorForStep(step);
            assertTrue("step " + step + " factor " + f, f >= 0.5f && f <= 4f);
        }
    }

    /**
     * The regression this whole re-base exists for. The old scale put 1.54 in the middle and a
     * person wearing the headset saw four chats; anything that puts the default back there
     * fails here rather than in a headset a day later.
     */
    @Test
    public void theOldDefaultIsTheReasonThisWasRebased() {
        assertEquals(4, VrDensity.rowsForFactor(PANEL_H, SYSTEM_DENSITY, 1.54f));
    }

    @Test
    public void theNewDefaultShowsRoughlyTwiceAsManyChats() {
        assertEquals(7, VrDensity.rowsForStep(PANEL_H, SYSTEM_DENSITY, VrDensity.STEP_BALANCED));
        assertEquals(9, VrDensity.rowsForStep(PANEL_H, SYSTEM_DENSITY, VrDensity.STEP_MOST_ROWS));
        assertEquals(6, VrDensity.rowsForStep(PANEL_H, SYSTEM_DENSITY, VrDensity.STEP_LARGER));
    }

    @Test
    public void theRowCountIsMonotonic() {
        int previous = Integer.MAX_VALUE;
        for (int step = 0; step < VrDensity.STEP_COUNT; step++) {
            int rows = VrDensity.rowsForStep(PANEL_H, SYSTEM_DENSITY, step);
            assertTrue("step " + step + " gave " + rows + " after " + previous, rows <= previous);
            previous = rows;
        }
    }

    @Test
    public void nonsenseInputsReturnZeroRatherThanDividingByZero() {
        assertEquals(0, VrDensity.rowsForFactor(0, 1.25f, 1f));
        assertEquals(0, VrDensity.rowsForFactor(800, 0f, 1f));
        assertEquals(0, VrDensity.rowsForFactor(800, 1.25f, 0f));
        assertEquals(0, VrDensity.rowsForFactor(50, 1.25f, 1f));
    }

    @Test
    public void theHitTargetFloorIsNotAStepOfTheScale() {
        assertEquals(64, VrDensity.MIN_TARGET_DP);
    }
}
