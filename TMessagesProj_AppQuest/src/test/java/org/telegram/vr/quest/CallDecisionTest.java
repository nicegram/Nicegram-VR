package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * When an incoming call is allowed to ring (A-43).
 *
 * <h3>Why calls needed a rule of their own</h3>
 *
 * Until 23 September 2026 they had none. The silence gate sits in
 * {@code NotificationsController.appendMessage}, and a call does not go through it — so a client
 * whose first screen promises "nothing arrives until you say it may" rang for anyone who
 * dialled. That is not a missing detail; it is the central promise being false for the loudest
 * thing the device can do.
 *
 * <h3>The rule, and what it deliberately leaves out</h3>
 *
 * The same question as a message, minus the two parts that cannot apply. A call carries no text,
 * so the word rules have nothing to match. And "outgoing" is not a case: a call the user placed
 * does not ring at them.
 *
 * <h3>Why nothing is lost by not ringing</h3>
 *
 * Telegram delivers the missed call into the chat as a service message. That message arrives
 * through the ordinary path moments later, is gated like any other, and lands in the digest. So
 * a suppressed call is still visible — only the interruption is gone, which is what the user
 * asked for by silencing the device.
 */
public class CallDecisionTest {

    private static final long ALICE = 1001L;
    private static final long BOB = 1002L;

    /** The profile is immutable and built from three sets; these helpers keep the tests short. */
    private static SilenceProfile profile(Set<Long> people, Set<Long> chats, String... words) {
        return new SilenceProfile(people, chats, new HashSet<>(Arrays.asList(words)));
    }

    private static Set<Long> ids(Long... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @Test
    public void aNamedPersonRings() {
        assertTrue("the whole point of naming someone is that they can reach you",
                SilenceDecision.allowCall(ALICE, profile(ids(ALICE), Collections.emptySet())));
    }

    @Test
    public void anUnnamedPersonDoesNot() {
        assertFalse("a client that promises quiet and then rings for anyone has not promised "
                        + "anything", SilenceDecision.allowCall(BOB, profile(ids(ALICE), Collections.emptySet())));
    }

    @Test
    public void aNamedChatRingsBecauseItIsTheSamePerson() {
        // In a one-to-one conversation the dialog id IS the other person's user id, so someone
        // who marked the chat has marked its only other participant.
        assertFalse(SilenceDecision.allowCall(ALICE, SilenceProfile.SILENT));
        assertTrue(SilenceDecision.allowCall(ALICE, profile(Collections.emptySet(), ids(ALICE))));
    }

    @Test
    public void anEmptyProfileRingsForNobody() {
        assertFalse("quiet by default is the default, and a call is not an exception to it",
                SilenceDecision.allowCall(ALICE, SilenceProfile.SILENT));
    }

    @Test
    public void noProfileAtAllRingsForNobody() {
        // Distinct from the fail-open in VrPolicy.allowsCall: that covers a gate that THREW.
        // A profile that is simply absent is a profile that named nobody.
        assertFalse(SilenceDecision.allowCall(ALICE, null));
    }

    @Test
    public void wordsDoNotApplyToCalls() {
        final SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "urgent");
        assertFalse("a call has no text for a word rule to match, and matching on the caller's "
                + "name would be a different feature nobody asked for",
                SilenceDecision.allowCall(ALICE, p));
    }
}
