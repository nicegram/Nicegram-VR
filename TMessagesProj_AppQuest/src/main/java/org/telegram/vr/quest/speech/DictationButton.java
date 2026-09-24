package org.telegram.vr.quest.speech;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.vr.VrEntryPoints;
import org.telegram.vr.quest.VrDensity;
import org.telegram.vr.quest.VrStrings;

/**
 * Nicegram VR — speak into the composer (plan.md P-12).
 *
 * <h3>Why this exists at all</h3>
 *
 * Typing with a ray is the worst interaction in this product. The plan's answer used to be
 * signing in by a QR code the phone would scan, and that turned out to be impossible — the panel
 * is inside the headset, so no phone camera can reach it (A-41). Dictation is the answer that
 * survives, and it is the one the original request asked for in the first place: speak, see the
 * text, send it.
 *
 * <h3>What it is not allowed to do</h3>
 *
 * <b>It never sends.</b> The recognised text is placed in the field with the cursor at the end
 * and the person presses send, because a client that sent what it thought it heard would be
 * unusable the first time it was wrong — and in a headset the message is already gone before you
 * can read it.
 *
 * <h3>Where the first run goes</h3>
 *
 * A control the size of a button cannot own a permission dialogue, an explanation of where the
 * audio is sent, and a permission RESULT — a {@code View} receives no
 * {@code onRequestPermissionsResult}. So the first run, and every run with no microphone or no
 * service configured, opens {@link DictationActivity}, which already does all three properly and
 * names the recipient before the first recording. The button owns the path that people repeat;
 * the screen owns the path they take once.
 *
 * <h3>The indicator is not decoration</h3>
 *
 * A visible indicator for the whole recording is a store requirement, not a nicety: a microphone
 * that can be listening invisibly is the thing the rule exists to prevent. Here it is the icon's
 * own ring, drawn from the live level so it is obvious that the meter is live rather than a
 * static "recording" badge.
 */
public class DictationButton extends ImageView {

    private final SpeechSettings settings;
    private final VoiceRecorder recorder = new VoiceRecorder();
    private final VrEntryPoints.Composer composer;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float level;
    private boolean recognising;
    private volatile int generation;

    public DictationButton(Context context, int currentAccount, VrEntryPoints.Composer composer) {
        super(context);
        this.composer = composer;
        this.settings = new SpeechSettings(context);

        setImageResource(R.drawable.input_mic);
        setScaleType(ScaleType.CENTER);
        setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        setContentDescription(VrStrings.get(my.nicegram.vr.R.string.vr_dictation_title));
        // The ray jitters by about a degree; nothing in this client may be smaller than it can hit.
        setMinimumWidth(AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));
        setMinimumHeight(AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(AndroidUtilities.dp(2));
        ring.setColor(Theme.getColor(Theme.key_chat_recordedVoiceDot));

        setOnClickListener(v -> toggle());
    }

    /**
     * Leaving the chat stops the recording, and it has to.
     *
     * <p>{@link VoiceRecorder} holds an {@code AudioRecord} and a thread for up to sixty
     * seconds. Without this, closing the chat mid-sentence leaves the microphone held by a view
     * that is no longer on screen — with the system's recording indicator lit, and nothing
     * anywhere to press to stop it. On a headset that is worse than on a phone: there is no
     * notification shade to go looking in.
     *
     * <p>The audio is discarded rather than recognised — {@code cancel()} rather than
     * {@code stop()}. Somebody who left the chat did not ask for text in it, and putting words
     * into a field they walked away from is its own surprise.
     *
     * <p><b>Off the main thread</b>, because {@code VoiceRecorder.stop} joins the capture thread
     * for up to two seconds. That wait is nothing in practice — the loop checks a volatile flag
     * — but a detach is a screen transition, and two seconds of frozen interface at the worst
     * possible moment is not a risk worth taking for a tidier call site.
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelPending();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) cancelPending();
    }

    private void cancelPending() {
        final int leaving = ++generation;
        recognising = true;
        recorder.requestCancel();
        level = 0f;
        Utilities.globalQueue.postRunnable(() -> {
            recorder.cancel();
            AndroidUtilities.runOnUIThread(() -> {
                if (generation == leaving) recognising = false;
            });
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!recorder.isRecording()) {
            return;
        }
        // A ring that breathes with the level. Its minimum is deliberately non-zero: silence
        // during a recording must still look like a recording.
        final float min = AndroidUtilities.dp(13);
        final float max = Math.min(getWidth(), getHeight()) / 2f - AndroidUtilities.dp(2);
        final float radius = min + (max - min) * Math.max(0f, Math.min(1f, level));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, ring);
    }

    private void toggle() {
        if (recognising) {
            return;
        }
        if (recorder.isRecording()) {
            stopAndRecognise();
            return;
        }
        // Both of these need a screen: one to explain where the audio goes and take the system's
        // answer, the other to enter an address. Neither fits on a button.
        if (!settings.isConfigured() || !hasMicrophone()) {
            openTheScreenThatOwnsThisPath();
            return;
        }
        start();
    }

    private boolean hasMicrophone() {
        final Activity activity = AndroidUtilities.findActivity(getContext());
        return activity != null && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openTheScreenThatOwnsThisPath() {
        final LaunchActivity launch = LaunchActivity.instance;
        if (launch != null && launch.getActionBarLayout() != null) {
            launch.getActionBarLayout().presentFragment(new DictationActivity());
        }
    }

    private void start() {
        final int recordingGeneration = ++generation;
        final boolean started = recorder.start(new VoiceRecorder.Listener() {
            @Override
            public void onLevel(float value) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != recordingGeneration) return;
                    level = value;
                    invalidate();
                });
            }

            @Override
            public void onLimitReached() {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation == recordingGeneration) stopAndRecognise();
                });
            }
        });
        if (!started) {
            say(my.nicegram.vr.R.string.vr_dictation_busy);
            return;
        }
        level = 0f;
        invalidate();
    }

    private void stopAndRecognise() {
        if (recognising || !recorder.isRecording()) return;
        recognising = true;
        final int request = generation;
        say(my.nicegram.vr.R.string.vr_dictation_recognizing);
        final SpeechToText service = new HttpSpeechToText(settings);
        final String language = settings.language();
        Utilities.globalQueue.postRunnable(() -> {
            final byte[] audio = recorder.stop();
            // Check on the UI thread after stopping: a hidden/cancelled view sends no audio.
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != request) return;
                level = 0f;
                invalidate();
                Utilities.globalQueue.postRunnable(() -> {
                    // A cancellation before this queued job starts must also prevent upload.
                    if (generation != request) return;
                    final SpeechToText.Result result = service.recognize(
                            audio, VoiceRecorder.MIME_TYPE, VoiceRecorder.SAMPLE_RATE, language);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (generation != request) return;
                        recognising = false;
                        deliver(result);
                    });
                });
            });
        });
    }

    private void deliver(SpeechToText.Result result) {
        if (result != null && result.ok()) {
            composer.insert(result.text);
            return;
        }
        // The same sentence the screen would have shown; the mapping lives in one place so the
        // two surfaces cannot drift apart.
        say(DictationMessages.forResult(result));
    }

    /**
     * One line, where the composer is. A bulletin rather than a dialogue: a modal over the field
     * you were about to type into is worse than the error it reports.
     */
    private void say(int resId) {
        if (resId == 0) {
            return;
        }
        final LaunchActivity launch = LaunchActivity.instance;
        if (launch == null || launch.getActionBarLayout() == null
                || launch.getActionBarLayout().getLastFragment() == null) {
            return;
        }
        org.telegram.ui.Components.BulletinFactory
                .of(launch.getActionBarLayout().getLastFragment())
                .createSimpleBulletin(R.raw.chats_infotip, VrStrings.get(resId))
                .show();
    }
}
