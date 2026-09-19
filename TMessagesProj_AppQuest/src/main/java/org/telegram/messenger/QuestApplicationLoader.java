package org.telegram.messenger;

import org.telegram.vr.VrEntryPoints;
import org.telegram.vr.VrPolicy;
import org.telegram.vr.quest.SilenceGate;
import org.telegram.vr.quest.SilenceRulesActivity;
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
        VrDensity.apply(this);
        VrPolicy.install(new SilenceGate(new SilenceStore(this)));
        // The exceptions screen lives in this module, so shared settings can only reach it
        // through the registry. Installed here, it appears as one row in Notifications.
        VrEntryPoints.installSilenceRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return getString(app.nicegram.vr.R.string.vr_silence_title);
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new SilenceRulesActivity();
            }
        });
    }

    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPushProvider.INSTANCE;
    }
}
