package org.telegram.vr.quest;

import android.content.Context;
import android.util.SparseArray;

import org.telegram.messenger.R;
import org.telegram.vr.VrBrand;

/**
 * Nicegram VR — the short, explicit list of strings that name this application.
 *
 * Every entry here is a place the client speaks about ITSELF. Nothing here touches the many
 * strings that name the Telegram service, its protocol, its terms or its privacy policy: those
 * are correct, and rewriting them would make the client lie about what it connects to and whose
 * rules the account is under.
 *
 * The list is deliberately readable end to end. A regular expression over 508 occurrences of
 * the word "Telegram" would have been faster to write and impossible to review.
 */
public final class VrBrandNames {

    /** What this application is called. Kept in one place so the name cannot drift. */
    public static final String PRODUCT = "Nicegram VR";

    private VrBrandNames() {
    }

    public static void install(Context context) {
        final SparseArray<String> names = new SparseArray<>();

        // The app's own name, wherever it introduces itself.
        names.put(R.string.AppName, PRODUCT);
        names.put(R.string.AppNameBeta, PRODUCT);

        // What a notification says when its content is hidden. It is the app speaking as
        // itself on a lock screen, so it is the app's name — not the service's.
        names.put(R.string.NotificationHiddenName, PRODUCT);
        names.put(R.string.NotificationHiddenChatName, PRODUCT);

        // Where the app speaks about ITSELF and upstream wrote its own name.
        // "Update Telegram" updates THIS app; the passcode screen locks THIS app; the version
        // and the cache on this device are THIS app's.
        names.put(R.string.AppUpdate, "Update " + PRODUCT);
        names.put(R.string.AppUpdateBeta, "Update " + PRODUCT + " Beta");
        names.put(R.string.AppLocked, PRODUCT + " Locked");
        names.put(R.string.TelegramVersion, PRODUCT + " %1$s");
        names.put(R.string.TelegramCacheSize, "%s " + PRODUCT + " Cache");

        // --- Storage, cache and database: all of it is THIS app's, on this device. ---
        names.put(R.string.ClearTelegramCache, "Clear " + PRODUCT + " Cache");
        names.put(R.string.LocalDatabaseSize, "%s " + PRODUCT + " Local Database");
        names.put(R.string.StorageUsageTelegram, PRODUCT + " uses %s of your device storage.");
        names.put(R.string.StorageUsageTelegramLess, PRODUCT + " uses %s of your device storage.");
        names.put(R.string.OptimizingTelegram, "Optimizing " + PRODUCT + "\u2026");

        // --- The lock screen. This app is what is locked and unlocked. ---
        names.put(R.string.EnterYourTelegramPasscode, "Enter your " + PRODUCT + " passcode");
        names.put(R.string.UnlockToUse, "Unlock to use " + PRODUCT);

        // --- Updating. "Update Telegram" on a headset means update THIS. ---
        names.put(R.string.UpdateTelegram, "Update " + PRODUCT);
        names.put(R.string.UnsupportedMessageMessage, "Update " + PRODUCT + " to view this message");

        // --- The built-in browser, which belongs to this app. ---
        names.put(R.string.OpenInTelegramBrowser, "Open in " + PRODUCT);
        names.put(R.string.BrowserSettingsCustomTabs, "New Pages within " + PRODUCT);

        // --- The empty state. The first sentence a new user reads. ---
        names.put(R.string.NoChats, "Welcome to " + PRODUCT);

        // --- Call branding. This is the label on the call the user is looking at, in this
        //     app, on their headset - not a description of Telegram's calling service. ---
        names.put(R.string.CallViaTelegram, PRODUCT + " Call");
        names.put(R.string.VoiceCallViaTelegram, PRODUCT + " Voice Call");
        names.put(R.string.VideoCallViaTelegram, PRODUCT + " Video Call");
        names.put(R.string.VoipInCallBranding, PRODUCT + " Call");
        names.put(R.string.VoipInCallBrandingWithName, PRODUCT + " Call to %s");
        names.put(R.string.VoipInVideoCallBranding, PRODUCT + " Video Call");
        names.put(R.string.VoipInVideoCallBrandingWithName, PRODUCT + " Video Call to %s");
        names.put(R.string.VoipInConferenceCallBranding, PRODUCT + " Group Call");
        names.put(R.string.VoipOutgoingCall, "Ongoing " + PRODUCT + " call");

        // --- Permission prompts. This app is the one asking. (Mostly unreachable now that
        //     the telephony permissions are stripped, but a dead string that lies is still
        //     a string that lies.) ---
        names.put(R.string.AllowFillNumber, "Please allow " + PRODUCT + " to receive calls so that we can automatically confirm your phone number.");
        names.put(R.string.AllowReadCall, "Please allow " + PRODUCT + " to receive calls so that we can automatically enter your code for you.");

        // Everything below is the SERVICE and its products, and stays Telegram's:
        //   TelegramFAQ, TelegramFeatures  - Telegram's own help pages
        //   TelegramPassport*              - a Telegram service feature
        //   TelegramContacts_*             - "N contacts on Telegram" is about the service
        //   TelegramTones                  - Telegram's notification sounds
        //   TelegramPremium*, TelegramBusiness*, TelegramStars*, AboutPremium*
        //                                  - products bought from Telegram, not from us
        //   Boosting* (65), Gift* (28), Voip*, Limit*, Privacy*, Revenue*
        //                                  - service features, named correctly
        // 486 strings mention Telegram. THIRTY-ONE are renamed - every place the app
        // speaks about ITSELF. The rest are true: they name the service, its products,
        // its terms, or another client.

        // The first intro page introduced the app and then advertised the service:
        // "Telegram" over "The world's fastest messaging app. It is free and secure."
        // In an UNOFFICIAL client that is someone else's marketing on our first screen, and
        // the brand's own voice pack forbids marketing claims outright. Replaced with a
        // sentence that says what this actually is.
        //
        // Read from OUR resource rather than through LocaleController, deliberately: the
        // language pack has no entry for it, and asking the pack from inside the rename seam
        // would recurse. That makes it English today - like every other string this module
        // owns - and P-18 fixes all of them together by loading the keys into the pack.
        names.put(R.string.Page1Title, PRODUCT);
        names.put(R.string.Page1Message, context.getString(my.nicegram.vr.R.string.vr_intro_message));

        // Pages 2-6 are NOT touched. "Telegram delivers messages faster than any other
        // application", "provides free unlimited cloud storage", "lets you access your
        // messages from multiple devices" - all true, and all about the SERVICE, which this
        // client does not provide and must not claim to.

        VrBrand.install(names, PRODUCT, my.nicegram.vr.R.drawable.nicegram_mark);
    }
}
