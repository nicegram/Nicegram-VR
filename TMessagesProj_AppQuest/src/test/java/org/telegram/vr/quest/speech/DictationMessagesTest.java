package org.telegram.vr.quest.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Every failure gets its own sentence, and no two failures get the same one.
 *
 * <h3>Why the distinctness matters more than the individual mappings</h3>
 *
 * The point of seven failure causes is that each implies a different next move: configure the
 * service, speak again, check the connection, grant the microphone, fix the address. Two causes
 * that resolve to one sentence quietly undo that — the person is told something true and is
 * still left without the move. So the test that earns its place is the one asserting the map is
 * injective, not the seven asserting it is a map.
 *
 * <h3>Why it exists at all</h3>
 *
 * Two surfaces run dictation: the settings screen and the composer button (plan.md P-12). They
 * shared a seven-way switch by copy until 23 September 2026; now they share this class, and this
 * is what stops it drifting.
 */
public class DictationMessagesTest {

    @Test
    public void aSuccessHasNoSentence() {
        assertEquals("a result that worked has nothing to apologise for",
                0, DictationMessages.forResult(SpeechToText.Result.of("hello")));
    }

    @Test
    public void everyFailureHasOne() {
        for (SpeechToText.Failure failure : SpeechToText.Failure.values()) {
            assertNotEquals("no sentence for " + failure + ", so the person would be told nothing",
                    0, DictationMessages.forResult(SpeechToText.Result.failed(failure)));
        }
    }

    @Test
    public void noTwoFailuresShareASentence() {
        final Set<Integer> seen = new HashSet<>();
        for (SpeechToText.Failure failure : SpeechToText.Failure.values()) {
            final int id = DictationMessages.forResult(SpeechToText.Result.failed(failure));
            assertTrue(failure + " reuses another failure's sentence, so its own next step is "
                    + "lost", seen.add(id));
        }
    }

    @Test
    public void aNullResultIsStillAnswerable() {
        // Defensive: a recogniser that returned nothing at all must not leave a silent button.
        assertNotEquals(0, DictationMessages.forResult(null));
    }

    @Test
    public void theServiceDetailIsOnlyOfferedWhenThereIsOne() {
        assertFalse("a success has no detail to repeat",
                DictationMessages.hasServiceDetail(SpeechToText.Result.of("hello")));
        assertFalse("SERVICE_ERROR with no detail must not print a bare format string",
                DictationMessages.hasServiceDetail(
                        SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR)));
        assertFalse("an empty detail is not a detail",
                DictationMessages.hasServiceDetail(
                        SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR, "")));
        assertTrue(DictationMessages.hasServiceDetail(
                SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR, "quota exceeded")));
    }

    @Test
    public void aNamedFailureDoesNotBorrowTheServicesWords() {
        // NO_CONNECTION with a stray detail must still say "no connection", not repeat a string
        // the service never sent — the detail belongs to SERVICE_ERROR alone.
        assertFalse(DictationMessages.hasServiceDetail(
                SpeechToText.Result.failed(SpeechToText.Failure.NO_CONNECTION, "timeout")));
    }
}
