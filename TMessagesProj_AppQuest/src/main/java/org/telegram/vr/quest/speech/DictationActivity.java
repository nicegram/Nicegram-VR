package org.telegram.vr.quest.speech;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.vr.quest.VrDensity;
import org.telegram.vr.quest.VrStrings;

/**
 * Nicegram VR — dictation, end to end, on a screen of its own.
 *
 * Everything the composer will eventually need — permission, recording, a level meter,
 * recognition, the text, and one sentence per way it can fail — lives here, where it can be run
 * and measured on a headset without touching the message composer. The composer is the most
 * crowded view in the app and its layout cannot be changed responsibly without a device in front
 * of you; this screen is what makes that last step small instead of speculative.
 *
 * Two rules are in the code rather than in a document, because they are the product: the level
 * meter is visible for the whole of every recording, and recognised text goes nowhere by itself.
 * Here there is nowhere for it to go at all.
 */
public class DictationActivity extends BaseFragment {

    private static final int REQUEST_MIC = 4201;

    private SpeechSettings settings;
    private VoiceRecorder recorder;
    private TextView status;
    private TextView transcript;
    private TextView recordButton;
    private View levelBar;
    private boolean recognising;

    @Override
    public boolean onFragmentCreate() {
        settings = new SpeechSettings(ApplicationLoader.applicationContext);
        recorder = new VoiceRecorder();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (recorder != null && recorder.isRecording()) {
            recorder.cancel();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(string(my.nicegram.vr.R.string.vr_dictation_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        column.addView(status);

        // The indicator. The platform's privacy rules want it; a person wearing a headset needs
        // it more, because they cannot glance at the device to see whether it is listening.
        levelBar = new View(context);
        levelBar.setBackgroundColor(Theme.getColor(Theme.key_text_RedRegular));
        final LinearLayout.LayoutParams levelParams =
                new LinearLayout.LayoutParams(AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        levelParams.topMargin = AndroidUtilities.dp(12);
        levelBar.setLayoutParams(levelParams);
        levelBar.setVisibility(View.INVISIBLE);
        column.addView(levelBar);

        transcript = new TextView(context);
        transcript.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        transcript.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        transcript.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20));
        transcript.setMinHeight(AndroidUtilities.dp(96));
        transcript.setTextIsSelectable(true);
        column.addView(transcript);

        recordButton = new TextView(context);
        recordButton.setGravity(Gravity.CENTER);
        recordButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        recordButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        recordButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        // VrDensity.MIN_TARGET_DP, not a number chosen by eye: at 1.3 m a smaller target is
        // inside the jitter of a hand ray.
        recordButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(VrDensity.MIN_TARGET_DP)));
        recordButton.setOnClickListener(v -> toggle());
        column.addView(recordButton);

        idle();
        return fragmentView;
    }

    /**
     * Through {@link LocaleController}, not {@link Context#getString}. The app strips every
     * Android locale from the package (`localeFilters += ["zz"]`, TMessagesProj_AppQuest/
     * build.gradle:124) because Telegram serves its own language packs; a string read straight
     * from resources is therefore English forever, whatever language the user picked.
     */
    private static String string(int resId) {
        return VrStrings.get(resId);
    }

    private void idle() {
        recordButton.setText(string(my.nicegram.vr.R.string.vr_dictation_start));
        levelBar.setVisibility(View.INVISIBLE);
        if (settings.isConfigured()) {
            // Only the host. A path can carry a token, and this line is on screen by default.
            status.setText(String.format(string(my.nicegram.vr.R.string.vr_dictation_recipient),
                    SpeechSettings.endpointHost(settings.endpoint())));
        } else {
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_not_configured));
        }
    }

    private void toggle() {
        if (recognising) {
            return;
        }
        if (recorder.isRecording()) {
            stopAndRecognise();
            return;
        }
        if (!settings.isConfigured()) {
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_not_configured));
            return;
        }
        final android.app.Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            explainThenAsk(activity);
            return;
        }
        start();
    }

    /**
     * The recipient is named BEFORE the first recording, not in a settings screen nobody opens.
     * The system's own permission sheet says "microphone"; it cannot say where the audio goes.
     */
    private void explainThenAsk(android.app.Activity activity) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(string(my.nicegram.vr.R.string.vr_dictation_title));
        builder.setMessage(String.format(string(my.nicegram.vr.R.string.vr_dictation_disclosure),
                SpeechSettings.endpointHost(settings.endpoint())));
        builder.setPositiveButton(string(my.nicegram.vr.R.string.vr_dictation_allow), (dialog, which) ->
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC));
        builder.setNegativeButton(string(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_MIC) {
            return;
        }
        if (grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_denied));
        }
    }

    private void start() {
        transcript.setText("");
        final boolean started = recorder.start(new VoiceRecorder.Listener() {
            @Override
            public void onLevel(float level) {
                AndroidUtilities.runOnUIThread(() -> showLevel(level));
            }

            @Override
            public void onLimitReached() {
                AndroidUtilities.runOnUIThread(DictationActivity.this::stopAndRecognise);
            }
        });
        if (!started) {
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_busy));
            return;
        }
        levelBar.setVisibility(View.VISIBLE);
        status.setText(string(my.nicegram.vr.R.string.vr_dictation_listening));
        recordButton.setText(string(my.nicegram.vr.R.string.vr_dictation_stop));
    }

    private void showLevel(float level) {
        if (levelBar == null || fragmentView == null) {
            return;
        }
        final float clamped = Math.max(0f, Math.min(1f, level));
        final int full = Math.max(AndroidUtilities.dp(4),
                fragmentView.getWidth() - AndroidUtilities.dp(48));
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) levelBar.getLayoutParams();
        params.width = AndroidUtilities.dp(4) + (int) ((full - AndroidUtilities.dp(4)) * clamped);
        levelBar.setLayoutParams(params);
    }

    private void stopAndRecognise() {
        if (!recorder.isRecording()) {
            return;
        }
        final byte[] audio = recorder.stop();
        levelBar.setVisibility(View.INVISIBLE);
        recordButton.setText(string(my.nicegram.vr.R.string.vr_dictation_start));
        if (audio.length == 0) {
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_empty));
            return;
        }
        recognising = true;
        status.setText(string(my.nicegram.vr.R.string.vr_dictation_recognizing));
        final SpeechToText service = new HttpSpeechToText(settings);
        final String language = settings.language();
        Utilities.globalQueue.postRunnable(() -> {
            final SpeechToText.Result result = service.recognize(
                    audio, VoiceRecorder.MIME_TYPE, VoiceRecorder.SAMPLE_RATE, language);
            AndroidUtilities.runOnUIThread(() -> {
                recognising = false;
                show(result);
            });
        });
    }

    private void show(SpeechToText.Result result) {
        if (result.ok()) {
            transcript.setText(result.text);
            status.setText(string(my.nicegram.vr.R.string.vr_dictation_check));
            return;
        }
        // Every cause gets its own sentence. There is no "something went wrong" on this screen,
        // because that sentence tells a person in a headset nothing they can act on.
        //
        // The mapping itself lives in DictationMessages, shared with the composer button
        // (plan.md P-12): two surfaces must say the same thing about the same failure, and a
        // second copy of this switch would drift the first time a cause was added.
        if (DictationMessages.hasServiceDetail(result)) {
            status.setText(String.format(string(my.nicegram.vr.R.string.vr_dictation_service_said),
                    result.detail));
            return;
        }
        status.setText(string(DictationMessages.forResult(result)));
    }
}
