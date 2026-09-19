package org.telegram.vr.quest.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The level meter is what tells a user in a headset that the microphone is hearing them, and it
 * is the one part of recording that can be checked without a microphone.
 */
public class VoiceRecorderLevelTest {

    private static byte[] tone(int samples, int amplitude) {
        byte[] out = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            int v = (i % 2 == 0) ? amplitude : -amplitude;
            out[i * 2] = (byte) (v & 0xff);
            out[i * 2 + 1] = (byte) (v >> 8);
        }
        return out;
    }

    @Test
    public void silenceReadsAsZero() {
        assertEquals(0f, VoiceRecorder.level(new byte[64], 64), 0.0001f);
    }

    @Test
    public void louderInputReadsHigher() {
        float quiet = VoiceRecorder.level(tone(64, 500), 128);
        float loud = VoiceRecorder.level(tone(64, 6000), 128);
        assertTrue(quiet + " should be below " + loud, quiet < loud);
    }

    @Test
    public void theMeterIsClampedSoAShoutDoesNotOverflowTheBar() {
        assertEquals(1f, VoiceRecorder.level(tone(64, 32000), 128), 0.0001f);
    }

    @Test
    public void shortOrAbsentBuffersDoNotThrow() {
        assertEquals(0f, VoiceRecorder.level(null, 0), 0.0001f);
        assertEquals(0f, VoiceRecorder.level(new byte[]{1}, 1), 0.0001f);
    }
}
