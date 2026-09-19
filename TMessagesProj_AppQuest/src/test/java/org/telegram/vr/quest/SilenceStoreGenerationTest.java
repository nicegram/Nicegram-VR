package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The gate caches the profile and re-reads when this moves. It used to be a non-atomic
 * increment on a volatile, which loses writes under concurrent saves and leaves the gate
 * deciding by a profile the user has already changed.
 */
public class SilenceStoreGenerationTest {

    @Test
    public void concurrentBumpsAreNotLost() throws Exception {
        final int before = SilenceStore.generation();
        final int threads = 8, each = 500;
        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            t[i] = new Thread(() -> {
                for (int n = 0; n < each; n++) {
                    SilenceStore.bumpGenerationForTest();
                }
            });
            t[i].start();
        }
        for (Thread x : t) {
            x.join();
        }
        assertEquals(before + threads * each, SilenceStore.generation());
    }
}
