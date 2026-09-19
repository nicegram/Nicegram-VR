package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The scale is the feature this fork is named for, and it was silently lost once already.
 * These pin the arithmetic; the device check that it survives startup is in the plan.
 */
public class VrDensityTest {

    private static final float EPS = 0.0001f;

    @Test
    public void theThreeStepsScaleTheUpstreamRamp() {
        assertEquals(1.54f * 0.85f, VrDensity.factorForStep(VrDensity.STEP_COMPACT), EPS);
        assertEquals(1.54f, VrDensity.factorForStep(VrDensity.STEP_NORMAL), EPS);
        assertEquals(1.54f * 1.2f, VrDensity.factorForStep(VrDensity.STEP_LARGE), EPS);
    }

    @Test
    public void anUnknownStepFallsBackToNormalRatherThanBreakingTheInterface() {
        assertEquals(VrDensity.factorForStep(VrDensity.STEP_NORMAL), VrDensity.factorForStep(-1), EPS);
        assertEquals(VrDensity.factorForStep(VrDensity.STEP_NORMAL), VrDensity.factorForStep(99), EPS);
    }

    @Test
    public void everyStepStaysInsideTheBandVrDisplayWillAccept() {
        for (int step = 0; step <= 2; step++) {
            float f = VrDensity.factorForStep(step);
            assertTrue("step " + step + " factor " + f, f >= 0.5f && f <= 4f);
        }
    }

    @Test
    public void theHitTargetFloorIsNotAStepOfTheScale() {
        assertEquals(64, VrDensity.MIN_TARGET_DP);
    }
}
