package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * When the phone-app offer appears, stated as a test rather than as a comment.
 *
 * <p>The rule has two halves and both are easy to get wrong in the same direction: an offer
 * that arrives too early is an advert on a product the user has not yet chosen to keep, and an
 * offer that returns is an interruption in the one place people go to be left alone. So: not
 * before the third launch, and never twice.
 */
public class VrMobilePromoTest {

    @Test
    public void notOnTheFirstTwoLaunches() {
        assertFalse("nothing is offered on the launch that already shows the first-run screen",
                VrMobilePromo.due(1, false));
        assertFalse("the second launch is still too early to ask for anything",
                VrMobilePromo.due(2, false));
    }

    @Test
    public void onTheThirdLaunch() {
        assertTrue("two visits, then the offer", VrMobilePromo.due(3, false));
    }

    @Test
    public void everAfterUntilShown() {
        // A launch can pass without the offer being presented — the screen is consulted on
        // resume and an activated client is a precondition. It must not be lost because of it.
        assertTrue(VrMobilePromo.due(4, false));
        assertTrue(VrMobilePromo.due(40, false));
    }

    @Test
    public void neverTwice() {
        assertFalse("shown once per install, and there is no reminder",
                VrMobilePromo.due(3, true));
        assertFalse(VrMobilePromo.due(99, true));
    }

    @Test
    public void zeroSessionsIsNotDue() {
        // Defensive: a preferences file that has not been written yet reads 0, and 0 must not
        // satisfy a ">= 3" that was accidentally written as "!= 1".
        assertFalse(VrMobilePromo.due(0, false));
    }
}
