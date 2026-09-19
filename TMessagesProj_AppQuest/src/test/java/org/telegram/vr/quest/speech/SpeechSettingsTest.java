package org.telegram.vr.quest.speech;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The two pure helpers that decide what a screen is allowed to show about a credential.
 */
public class SpeechSettingsTest {

    @Test
    public void tokenIsMaskedToTheLastFourAndNeverShownWhole() {
        assertEquals("···· cdef", SpeechSettings.maskedToken("0123456789abcdef"));
        assertEquals("····", SpeechSettings.maskedToken("abc"));
        assertEquals("", SpeechSettings.maskedToken(""));
        assertEquals("", SpeechSettings.maskedToken(null));
    }

    @Test
    public void onlyTheHostIsNamedToTheUserBecauseAPathCanCarryASecret() {
        assertEquals("speech.example.com",
                SpeechSettings.endpointHost("https://speech.example.com/v1/recognize?key=SECRET"));
        assertEquals("", SpeechSettings.endpointHost("not a url"));
        assertEquals("", SpeechSettings.endpointHost(""));
        assertEquals("", SpeechSettings.endpointHost(null));
    }
}
