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

    private volatile boolean recording;
    private Thread thread;
    private ByteArrayOutputStream buffer;

    public boolean isRecording() {
        return recording;
    }

    /** Requires {@link Manifest.permission#RECORD_AUDIO}; the caller checks it and says why. */
    public synchronized boolean start(Listener listener) {
        if (recording) {
            return true;
        }
        final int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            return false;
        }
        final AudioRecord record;
        try {
            record = new AudioRecord(SOURCE, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4);
        } catch (Throwable e) {
            return false;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            return false;
        }
        buffer = new ByteArrayOutputStream();
        recording = true;
        thread = new Thread(() -> {
            final byte[] chunk = new byte[minBuffer];
            final int maxBytes = SAMPLE_RATE * 2 * MAX_SECONDS;
            try {
                record.startRecording();
                while (recording) {
                    final int read = record.read(chunk, 0, chunk.length);
                    if (read <= 0) {
                        continue;
                    }
                    synchronized (VoiceRecorder.this) {
                        if (buffer != null) {
                            buffer.write(chunk, 0, read);
                        }
                    }
                    if (listener != null) {
                        listener.onLevel(level(chunk, read));
                    }
                    if (buffer != null && buffer.size() >= maxBytes) {
                        recording = false;
                        if (listener != null) {
                            listener.onLimitReached();
                        }
                    }
                }
            } catch (Throwable ignored) {
                // A recorder that dies mid-phrase yields whatever it captured; the caller sees
                // a short recording rather than a crash.
            } finally {
                try {
                    record.stop();
                } catch (Throwable ignored) {
                }
                record.release();
            }
        }, "vr-dictation");
        thread.start();
        return true;
    }

    /** @return what was captured, never null; empty when nothing was. */
    public synchronized byte[] stop() {
        recording = false;
        final Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        final byte[] out = buffer == null ? new byte[0] : buffer.toByteArray();
        buffer = null;
        return out;
    }

    /** Discards the capture; used when the user cancels, so nothing is sent anywhere. */
    public synchronized void cancel() {
        stop();
    }

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
