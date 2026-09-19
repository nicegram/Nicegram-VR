package org.telegram.messenger;

import org.telegram.vr.VrDisplay;
import org.telegram.vr.VrEntryPoints;
import org.telegram.vr.VrPolicy;
import org.telegram.vr.quest.SilenceGate;
import org.telegram.vr.quest.DigestActivity;
import org.telegram.vr.quest.FirstRunActivity;
import org.telegram.vr.quest.QuestRuntime;
import org.telegram.vr.quest.SilenceRulesActivity;
import org.telegram.vr.quest.VrLayout;
import org.telegram.vr.quest.VrPerformance;
import org.telegram.vr.quest.VrSettingsActivity;
import org.telegram.vr.quest.SilenceStore;
import org.telegram.vr.quest.VrDensity;

/**
 * Nicegram VR — the application entry point for the headset build.
 *
 * Two things happen here that happen nowhere else, and both happen before the first screen:
 * the display policy is installed, so the client is quiet from its very first message rather
 * than from the moment a user finds a setting; and the interface density is scaled for a panel
 * read at arm's length and pointed at with a ray.
 */
public class QuestApplicationLoader extends ApplicationLoader {

    @Override
    public void onCreate() {
        super.onCreate();
        // Not applied here: checkDisplaySize reassigns density before the first screen and
        // would erase it. Installed instead, and read where the assignment happens.
        VrDisplay.install(() -> VrDensity.factor(this));
        VrPolicy.install(new SilenceGate(new SilenceStore(this)));
        // A default, not a lock: 60 fps is a condition of publishing here and media that
        // plays by itself is the cheapest way to lose it. The user can turn it back on.
        VrPerformance.applyDefaultsOnce(this);
        // One column by default. A Quest 3 panel reports sw640dp, so Android hands this app the
        // tablet layout and splits a surface that is only ~665 dp wide once the headset scale is
        // applied — measured on a device, and it looked exactly as bad as that arithmetic says.
        VrLayout.applyDefaultsOnce(this);
        // The exceptions screen lives in this module, so shared settings can only reach it
        // through the registry. Installed here, it appears as one row in Notifications.
        VrEntryPoints.installSilenceRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return LocaleController.getString(app.nicegram.vr.R.string.vr_silence_title);
            }

            @Override
            public CharSequence value() {
                final int n = SilenceRulesActivity.count(org.telegram.messenger.UserConfig.selectedAccount);
                return n == 0 ? null : LocaleController.formatString(app.nicegram.vr.R.string.vr_silence_count, n);
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new SilenceRulesActivity();
            }
        });
        VrEntryPoints.installDigestRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return LocaleController.getString(app.nicegram.vr.R.string.vr_digest_title);
            }

            @Override
            public CharSequence value() {
                final org.telegram.vr.quest.Digest digest = QuestRuntime.digest();
                final int chats = digest == null ? 0 : digest.chatCount();
                return chats == 0 ? null : LocaleController.formatString(app.nicegram.vr.R.string.vr_digest_row_value, chats);
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new DigestActivity();
            }
        });
        VrEntryPoints.installHeadsetRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return LocaleController.getString(app.nicegram.vr.R.string.vr_settings_title);
            }

            @Override
            public CharSequence value() {
                return null;
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new VrSettingsActivity();
            }
        });
        // Shown at most once per install, and it is the app's only chance to say that a closed
        // client receives nothing before the user finds it out by missing something.
        VrEntryPoints.installFirstRun(currentAccount ->
                FirstRunActivity.isDue(QuestApplicationLoader.this) ? new FirstRunActivity() : null);
    }

    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPushProvider.INSTANCE;
    }
}
