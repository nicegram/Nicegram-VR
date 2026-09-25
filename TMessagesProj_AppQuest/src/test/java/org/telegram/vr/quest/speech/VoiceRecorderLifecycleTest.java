package org.telegram.vr.quest.speech;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class VoiceRecorderLifecycleTest {
    static class Input implements VoiceRecorder.Input {
        volatile boolean stopped;
        final AtomicInteger releases = new AtomicInteger();
        public void start() { }
        public int read(byte[] b) { b[0] = 1; b[1] = 2; return 2; }
        public void stop() { stopped = true; }
        public void release() { releases.incrementAndGet(); }
    }

    @Test public void limitKeepsAudioAvailableForTheUiCallbackExactlyOnce() throws Exception {
        Input input = new Input();
        VoiceRecorder r = new VoiceRecorder(() -> input, 4);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        assertTrue(r.start(new VoiceRecorder.Listener() {
            public void onLevel(float n) { }
            public void onLimitReached() { callbacks.incrementAndGet(); done.countDown(); }
        }));
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue("UI's isRecording guard must accept the completed phrase", r.isRecording());
        assertFalse("cannot overwrite unconsumed audio", r.start(null));
        assertArrayEquals(new byte[]{1,2,1,2}, r.stop());
        assertEquals(0, r.stop().length);
        assertEquals(1, callbacks.get());
        assertEquals(1, input.releases.get());
    }

    @Test public void readFailureEndsCaptureAndReleasesMicrophone() throws Exception {
        Input input = new Input() { public int read(byte[] b) { return -3; } };
        VoiceRecorder r = new VoiceRecorder(() -> input, 100);
        CountDownLatch done = new CountDownLatch(1);
        assertTrue(r.start(new VoiceRecorder.Listener() {
            public void onLevel(float n) { }
            public void onLimitReached() { done.countDown(); }
        }));
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(0, r.stop().length);
        assertEquals(1, input.releases.get());
        assertFalse(r.isRecording());
    }

    @Test public void stopDoesNotJoinWhileHoldingTheRecorderMonitor() throws Exception {
        final VoiceRecorder[] holder = new VoiceRecorder[1];
        CountDownLatch reading = new CountDownLatch(1);
        Input input = new Input() {
            public int read(byte[] bytes) {
                reading.countDown();
                while (!stopped) Thread.yield();
                // Models the worker needing the recorder monitor before it can terminate.
                synchronized (holder[0]) { return -3; }
            }
        };
        VoiceRecorder r = holder[0] = new VoiceRecorder(() -> input, 100);
        assertTrue(r.start(null));
        assertTrue(reading.await(2, TimeUnit.SECONDS));
        long begin = System.nanoTime();
        r.stop();
        assertTrue("stop waited for its own lock", System.nanoTime()-begin < 1_000_000_000L);
        assertEquals(1, input.releases.get());
    }

    @Test public void startFailureReleasesInputAndAllowsRetry() {
        Input input = new Input() { public void start() { throw new IllegalStateException(); } };
        VoiceRecorder r = new VoiceRecorder(() -> input, 100);
        assertFalse(r.start(null));
        assertFalse(r.isRecording());
        assertEquals(1, input.releases.get());
    }
}
