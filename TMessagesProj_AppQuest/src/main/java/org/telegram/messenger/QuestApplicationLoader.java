package org.telegram.messenger;

import org.telegram.vr.VrDisplay;
import org.telegram.vr.VrEntryPoints;
import org.telegram.vr.VrPolicy;
import org.telegram.vr.quest.SilenceGate;
import org.telegram.vr.quest.VrStrings;
import org.telegram.vr.quest.DigestActivity;
import org.telegram.vr.quest.FirstRunActivity;
import org.telegram.vr.quest.MobilePromoActivity;
import org.telegram.vr.quest.VrMobilePromo;
import org.telegram.vr.quest.QuestRuntime;
import org.telegram.vr.quest.SilenceRulesActivity;
import org.telegram.vr.quest.VrLayout;
import org.telegram.vr.quest.VrStartFolder;
import org.telegram.vr.quest.VrPerformance;
import org.telegram.vr.quest.VrTheme;
import org.telegram.vr.quest.VrSettingsActivity;
import org.telegram.vr.quest.SilenceStore;
import org.telegram.vr.quest.NotificationsMaster;
import org.telegram.vr.quest.VrBrandNames;
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
        // Before super: the brand map must be in place before any string is drawn, and it costs
        // one SparseArray. It needs no Context and nothing from the loader's own start-up.
        VrBrandNames.install(this);
        super.onCreate();
        // Not applied here: checkDisplaySize reassigns density before the first screen and
        // would erase it. Installed instead, and read where the assignment happens.
        VrDisplay.install(() -> VrDensity.factor(this));
        VrPolicy.install(new SilenceGate(new SilenceStore(this)));
        // The master switch, in the chat-list header. Everything it touches is local to this
        // device: it never writes account notification settings, so a phone in a pocket keeps
        // whatever it had.
        VrEntryPoints.installHeaderToggle(new VrEntryPoints.HeaderToggle() {
            @Override
            public int icon(boolean on) {
                return on ? org.telegram.messenger.R.drawable.msg_unmute
                          : org.telegram.messenger.R.drawable.msg_mute;
            }

            @Override
            public boolean isOn() {
                return NotificationsMaster.isOn(QuestApplicationLoader.this);
            }

            @Override
            public boolean toggle() {
                return NotificationsMaster.toggle(QuestApplicationLoader.this);
            }

            @Override
            public CharSequence description(boolean on) {
                return VrStrings.get(on
                        ? my.nicegram.vr.R.string.vr_master_on
                        : my.nicegram.vr.R.string.vr_master_off);
            }
        });
        // A default, not a lock: 60 fps is a condition of publishing here and media that
        // plays by itself is the cheapest way to lose it. The user can turn it back on.
        VrPerformance.applyDefaultsOnce(this);
        // One column by default. The number in this comment used to be sw640dp, which was true
        // of the LANDSCAPE panel this build asked for until A-21 removed that request; the
        // portrait panel a Quest 3 hands us now measures 500x800 px at 200 dpi — sw400dp
        // (`adb shell dumpsys activity a my.nicegram.vr`, 21 September). Either way a split
        // leaves the conversation squeezed, and it looked exactly as bad as that arithmetic says.
        VrLayout.applyDefaultsOnce(this);
        // The brand, and only the accent of it. Retries next start if the theme engine has not
        // loaded its themes yet — see VrTheme for why the marker is not written on failure.
        VrTheme.applyDefaultsOnce(this);
        // And the colour again on EVERY start: upstream keeps accent 10's colour in a literal
        // array and persists only which accent is selected, so a slot repainted once reverts on
        // the next launch. Measured on a headset — themeconfig.xml held the id and no colour.
        VrTheme.enforceAccentColour();
        // The exceptions screen lives in this module, so shared settings can only reach it
        // through the registry. Installed here, it appears as one row in Notifications.
        VrEntryPoints.installSilenceRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return VrStrings.get(my.nicegram.vr.R.string.vr_silence_title);
            }

            @Override
            public CharSequence value() {
                final int n = SilenceRulesActivity.count(org.telegram.messenger.UserConfig.selectedAccount);
                return n == 0 ? null : VrStrings.format(my.nicegram.vr.R.string.vr_silence_count, n);
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new SilenceRulesActivity();
            }
        });
        VrEntryPoints.installDigestRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return VrStrings.get(my.nicegram.vr.R.string.vr_digest_title);
            }

            @Override
            public CharSequence value() {
                final int account = org.telegram.messenger.UserConfig.selectedAccount;
                final org.telegram.vr.quest.Digest digest = QuestRuntime.digest(account);
                final int chats = digest == null ? 0 : digest.chatCount(account);
                return chats == 0 ? null : VrStrings.format(my.nicegram.vr.R.string.vr_digest_row_value, chats);
            }

            @Override
            public org.telegram.ui.ActionBar.BaseFragment create() {
                return new DigestActivity();
            }
        });
        VrEntryPoints.installHeadsetRow(new VrEntryPoints.SettingsRow() {
            @Override
            public CharSequence title() {
                return VrStrings.get(my.nicegram.vr.R.string.vr_settings_title);
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
        // Which folder a session opens on. The setting holds the SERVER's filter id, so it
        // survives a restart; DialogsActivity translates it to a tab once, after the folders
        // have loaded, and never fights a tab the user taps afterwards.
        VrEntryPoints.installStartupFilter(() -> VrStartFolder.filterId(QuestApplicationLoader.this));
        // Speak into the composer. Typing with a ray is the worst interaction in this product,
        // and since A-41 removed the QR sign-in this is the answer that survives — as well as
        // the one the original request asked for. See plan.md P-12.
        VrEntryPoints.installComposerControl(
                (context, account, composer) ->
                        new org.telegram.vr.quest.speech.DictationButton(context, account, composer));

        // Counts this launch, once per process. The offer below is due on the third one, and
        // an activity recreated by a panel resize must not spend a session doing it.
        VrMobilePromo.countSession(this);
        // Two one-time screens, in a fixed order. The first-run screen wins whenever it is due,
        // because a client that says nothing about its own silence has misled someone; only
        // after it has been seen can the third launch carry the offer of the phone app.
        VrEntryPoints.installFirstRun(currentAccount -> {
            if (FirstRunActivity.isDue(QuestApplicationLoader.this)) {
                return new FirstRunActivity();
            }
            if (VrMobilePromo.isDue(QuestApplicationLoader.this)) {
                return new MobilePromoActivity();
            }
            return null;
        });
    }

    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPushProvider.INSTANCE;
    }
}
