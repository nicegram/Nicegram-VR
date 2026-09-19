package org.telegram.vr.quest.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reading a recognition response is where this kind of code actually goes wrong, and none of
 * these cases needs a network or a headset.
 */
public class SpeechResponseTest {

    @Test
    public void oneAlternativeBecomesTheText() {
        SpeechToText.Result r = SpeechResponse.parse(
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"посмотрю релиз-ноуты\"}]}]}");
        assertTrue(r.ok());
        assertEquals("посмотрю релиз-ноуты", r.text);
    }

    @Test
    public void severalResultsAreJoinedInOrderAndOnlyTheBestAlternativeIsUsed() {
        SpeechToText.Result r = SpeechResponse.parse(
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"первая\"},{\"transcript\":\"ignored\"}]},"
                        + "{\"alternatives\":[{\"transcript\":\"вторая\"}]}]}");
        assertEquals("первая вторая", r.text);
    }

    @Test
    public void nothingHeardIsNotAnError() {
        for (String body : new String[]{
                "{\"results\":[]}",
                "{}",
                "{\"results\":[{\"alternatives\":[]}]}",
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"   \"}]}]}"}) {
            SpeechToText.Result r = SpeechResponse.parse(body);
            assertFalse(body, r.ok());
            assertEquals(body, SpeechToText.Failure.NOTHING_HEARD, r.failure);
        }
    }

    @Test
    public void aServiceThatReportsItsOwnErrorSendsTheUserToSettings() {
        SpeechToText.Result r = SpeechResponse.parse(
                "{\"error\":{\"code\":403,\"message\":\"API key not valid\"}}");
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, r.failure);
        assertEquals("API key not valid", r.detail);
    }

    @Test
    public void malformedAndEmptyBodiesAreServiceErrorsRatherThanSilence() {
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.parse("not json at all").failure);
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.parse("").failure);
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.parse(null).failure);
    }

    @Test
    public void statusMapsToSomethingTheUserCanActOn() {
        // Auth and client mistakes are settings problems; timeouts and overload are the network.
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.fromStatus(401));
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.fromStatus(403));
        assertEquals(SpeechToText.Failure.SERVICE_ERROR, SpeechResponse.fromStatus(400));
        assertEquals(SpeechToText.Failure.NO_CONNECTION, SpeechResponse.fromStatus(429));
        assertEquals(SpeechToText.Failure.NO_CONNECTION, SpeechResponse.fromStatus(500));
        assertEquals(SpeechToText.Failure.NO_CONNECTION, SpeechResponse.fromStatus(503));
    }

    @Test
    public void resultCarriesNoTextWhenItFailed() {
        SpeechToText.Result r = SpeechToText.Result.failed(SpeechToText.Failure.NO_PERMISSION);
        assertNull(r.text);
        assertFalse(r.ok());
    }
}
