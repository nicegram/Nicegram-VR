package org.telegram.vr.quest.speech;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;

/**
 * Nicegram VR — capturing a phrase, with a level the screen can show.
 *
 * Sixteen kilohertz mono PCM: what recognition services want, and small enough that a phrase is
 * a few hundred kilobytes rather than a few megabytes over a headset's Wi-Fi.
 *
 * The audio source is {@link MediaRecorder.AudioSource#VOICE_RECOGNITION} rather than DEFAULT.
 * On Horizon OS the microphone is an array and DEFAULT can pick a member with processing meant
 * for something else; VOICE_RECOGNITION is the source whose contract is "a person talking to a
 * machine". Which of the two is actually better here is a measurement on a device, and this
 * class keeps the choice in one constant so that measurement can change it.
 */
public final class VoiceRecorder {

    public static final int SAMPLE_RATE = 16_000;
    public static final String MIME_TYPE = "audio/l16";
    private static final int SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    /** Above this many seconds the recording stops itself; a dictation is not a monologue. */
    private static final int MAX_SECONDS = 60;

    public interface Listener {
        /** 0..1, for the meter. Called on the recording thread. */
        void onLevel(float level);

        /** Called on the recording thread when the recorder stopped on its own. */
        void onLimitReached();
    }

    interface Input {
        void start();
        int read(byte[] bytes);
        void stop();
        void release();
    }
    interface Factory { Input open(); }

    private final Factory factory;
    private final int maxBytes;
    private Capture active;
    private int stopping;

    private static final class Capture {
        final Input input;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        volatile boolean running = true;
        Thread thread;
        Capture(Input input) { this.input = input; }
    }

    public VoiceRecorder() {
        this(VoiceRecorder::openMicrophone, SAMPLE_RATE * 2 * MAX_SECONDS);
    }

    VoiceRecorder(Factory factory, int maxBytes) {
        this.factory = factory;
        this.maxBytes = maxBytes;
    }

    private static Input openMicrophone() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("Microphone unavailable");
        final AudioRecord record = new AudioRecord(SOURCE, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            throw new IllegalStateException("Microphone unavailable");
        }
        return new Input() {
            public void start() { record.startRecording(); }
            public int read(byte[] bytes) { return record.read(bytes, 0, bytes.length); }
            public void stop() { record.stop(); }
            public void release() { record.release(); }
        };
    }

    /** True until captured audio is consumed, including automatic completion awaiting the UI. */
    public synchronized boolean isRecording() { return active != null; }

    /** Requires RECORD_AUDIO. A second start cannot replace an unconsumed capture. */
    public synchronized boolean start(Listener listener) {
        if (active != null || stopping != 0) return false;
        Input input = null;
        try {
            input = factory.open();
            input.start();
        } catch (Throwable error) {
            if (input != null) input.release();
            return false;
        }
        final Capture capture = new Capture(input);
        active = capture;
        capture.thread = new Thread(() -> {
            boolean automatic = false;
            try {
                byte[] chunk = new byte[3200];
                while (capture.running) {
                    int read = capture.input.read(chunk);
                    if (!capture.running) break;
                    // AudioRecord error values must terminate, not spin forever holding the mic.
                    if (read <= 0) { automatic = true; break; }
                    synchronized (capture) {
                        int remaining = maxBytes - capture.buffer.size();
                        capture.buffer.write(chunk, 0, Math.min(read, remaining));
                    }
                    if (listener != null) listener.onLevel(level(chunk, read));
                    if (capture.buffer.size() >= maxBytes) { automatic = true; break; }
                }
            } catch (Throwable error) {
                automatic = true;
            } finally {
                capture.running = false;
                try { capture.input.stop(); } catch (Throwable ignored) { }
                capture.input.release();
                // Keep active until stop() consumes the audio. Previously clearing 'recording'
                // here made both UI callbacks return before recognition, losing the whole phrase.
                if (automatic && listener != null) listener.onLimitReached();
            }
        }, "vr-dictation");
        capture.thread.start();
        return true;
    }

    /** Stops a blocking read and joins without holding the monitor needed by the worker. */
    public byte[] stop() {
        final Capture capture;
        synchronized (this) {
            capture = active;
            if (capture == null) return new byte[0];
            active = null;
            stopping++;
            capture.running = false;
        }
        try {
            try { capture.input.stop(); } catch (Throwable ignored) { }
            if (capture.thread != Thread.currentThread()) {
                try { capture.thread.join(2000); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            synchronized (capture) { return capture.buffer.toByteArray(); }
        } finally {
            synchronized (this) { stopping--; }
        }
    }

    /** Stop signal is immediate; cleanup may run on the caller's background queue. */
    public synchronized void requestCancel() {
        if (active != null) active.running = false;
    }

    public void cancel() { stop(); }

    /** Root mean square over 16-bit samples, normalised. Pure, so the meter can be tested. */
    static float level(byte[] pcm, int length) {
        if (pcm == null || length < 2) {
            return 0f;
        }
        long sum = 0;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            final int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (long) sample * sample;
            samples++;
        }
        if (samples == 0) {
            return 0f;
        }
        final double rms = Math.sqrt((double) sum / samples);
        return (float) Math.min(1.0, rms / 8000.0);
    }
}
