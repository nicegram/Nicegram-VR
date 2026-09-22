package org.telegram.vr.quest.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    // --- The address is the user's own, and until A-32 nothing checked it. Dictation posts a
    // recording of somebody's voice and sets an Authorization header; over plain http both
    // cross the network readable by anyone on it.

    @Test
    public void httpsIsFine() {
        assertEquals(SpeechSettings.EndpointProblem.NONE,
                SpeechSettings.endpointProblem("https://asr.example.com/v1/recognize"));
        assertEquals(SpeechSettings.EndpointProblem.NONE,
                SpeechSettings.endpointProblem("  https://asr.example.com/v1?model=ru  "));
        assertEquals(SpeechSettings.EndpointProblem.NONE,
                SpeechSettings.endpointProblem("HTTPS://ASR.EXAMPLE.COM/v1"));
    }

    @Test
    public void plainHttpToAnywhereElseIsRefused() {
        assertEquals(SpeechSettings.EndpointProblem.INSECURE,
                SpeechSettings.endpointProblem("http://asr.example.com/v1"));
        assertEquals(SpeechSettings.EndpointProblem.INSECURE,
                SpeechSettings.endpointProblem("http://192.168.1.5:8080/asr"));
    }

    @Test
    public void loopbackOverHttpIsAllowed() {
        // A recogniser on the headset itself never leaves the device, and refusing it would
        // remove the one configuration that needs no trust at all.
        for (String url : new String[]{
                "http://127.0.0.1:8080/asr",
                "http://127.1.2.3/asr",
                "http://localhost:9000/v1",
                "http://[::1]:9000/v1"}) {
            assertEquals(url, SpeechSettings.EndpointProblem.NONE,
                    SpeechSettings.endpointProblem(url));
        }
    }

    @Test
    public void somethingThatIsNotAnAddressSaysSo() {
        // Each of these used to reach the network layer and come back as NO_CONNECTION, which
        // sends a person to look at their Wi-Fi over a typo.
        for (String bad : new String[]{
                null, "", "   ", "asr.example.com/v1", "ftp://asr.example.com/v1",
                "https://", "just some words", "file:///etc/passwd"}) {
            assertEquals(String.valueOf(bad), SpeechSettings.EndpointProblem.NOT_A_URL,
                    SpeechSettings.endpointProblem(bad));
        }
    }

    /**
     * The check above is worth nothing if the transport does not call it — and "the rule is
     * right and the caller never asks it" is the defect this repository has now paid for three
     * times (A-26, A-29, A-31). The transport cannot be instantiated on the JVM, so what is
     * asserted is the call.
     */
    @Test
    public void theTransportRefusesBeforeItOpensAConnection() throws java.io.IOException {
        final String source = transport();
        final int check = source.indexOf("endpointProblem(");
        final int connect = source.indexOf("openConnection()");
        assertTrue("HttpSpeechToText no longer calls SpeechSettings.endpointProblem, so a plain "
                + "http address is sent again, carrying a recording of somebody's voice and "
                + "their token in the clear.", check >= 0);
        assertTrue("HttpSpeechToText checks the address AFTER opening the connection, which is "
                + "after the point the check exists to prevent.", check < connect);
        assertTrue("the insecure address no longer has its own failure",
                source.contains("Failure.INSECURE_ADDRESS"));
        assertTrue("a malformed address no longer has its own failure, so it reports itself as a "
                + "network problem and sends a person to look at their Wi-Fi over a typo",
                source.contains("Failure.BAD_ADDRESS"));
    }

    private static String transport() throws java.io.IOException {
        java.io.File dir = new java.io.File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final java.io.File f = new java.io.File(dir,
                    "TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/speech/HttpSpeechToText.java");
            if (f.isFile()) {
                return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new java.io.IOException("HttpSpeechToText.java not found above " + System.getProperty("user.dir"));
    }
}
