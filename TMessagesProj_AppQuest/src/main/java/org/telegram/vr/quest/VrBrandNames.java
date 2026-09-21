package org.telegram.vr.quest;

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

    public static void install() {
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

        // Everything below is the SERVICE and its products, and stays Telegram's:
        //   TelegramFAQ, TelegramFeatures  - Telegram's own help pages
        //   TelegramPassport*              - a Telegram service feature
        //   TelegramContacts_*             - "N contacts on Telegram" is about the service
        //   TelegramTones                  - Telegram's notification sounds
        //   TelegramPremium*, TelegramBusiness*, TelegramStars*, AboutPremium*
        //                                  - products bought from Telegram, not from us
        //   Boosting* (65), Gift* (28), Voip*, Limit*, Privacy*, Revenue*
        //                                  - service features, named correctly
        // 486 strings mention Telegram. Nine are renamed. The other 477 are true.

        VrBrand.install(names, PRODUCT, my.nicegram.vr.R.drawable.nicegram_mark);
    }
}
