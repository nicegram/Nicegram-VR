package org.telegram.vr.quest;

import android.content.Context;
import android.util.SparseArray;

import org.telegram.messenger.LocaleController;
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
 * The list is deliberately readable end to end. A regular expression over the 508 strings that
 * mention the word "Telegram" (measured: {@code grep -c Telegram values/strings.xml}) would have
 * been faster to write and impossible to review.
 *
 * <h3>Two kinds of entry, and why the difference matters</h3>
 *
 * <b>A rename</b> keeps upstream's sentence and puts this product's name where the service's
 * was — so "\u041E\u0431\u043D\u043E\u0432\u0438\u0442\u044C Telegram" becomes "\u041E\u0431\u043D\u043E\u0432\u0438\u0442\u044C Nicegram VR" and stays Russian. Thirty-five
 * entries. <b>A replacement</b> is one of this module's OWN sentences standing in for
 * upstream's, because the original said something this client must not say; twelve entries,
 * read from the pack by entry name so they are translatable too.
 *
 * <p>Everything here used to be the second kind, written out in finished English. That is why
 * a Russian interface turned English wherever the app named itself (A-39): thirty-five sentences
 * the pack had already translated were thrown away and replaced by ours.
 */
public final class VrBrandNames {

    /** What this application is called. Kept in one place so the name cannot drift. */
    public static final String PRODUCT = "Nicegram VR";

    /**
     * The names the service goes by inside a resolved string, in the languages this client is
     * translated into. Latin first: the official Russian pack leaves the product name in Latin,
     * and the Cyrillic form is here because a future translation may not.
     */
    private static final String[] SERVICE = {"Telegram", "\u0422\u0435\u043B\u0435\u0433\u0440\u0430\u043C"};

    /** id -&gt; the English sentence to fall back on when the substitution finds nothing. */
    private static volatile SparseArray<String> fallbacks;

    /** id -&gt; the entry NAME of this module's own resource, resolved through the pack. */
    private static volatile SparseArray<String> ownNames;

    /** id -&gt; that resource's compiled English, for when the pack does not carry the key. */
    private static volatile SparseArray<String> ownEnglish;

    private VrBrandNames() {
    }

    /**
     * The rename, decided per call rather than baked in at install.
     *
     * <p>It used to be a map of finished English sentences, which is why a Russian interface
     * turned English at every place this app names itself: "Update Nicegram VR" replaced
     * "\u041E\u0431\u043D\u043E\u0432\u0438\u0442\u044C Telegram" and the language was lost with the service's name (A-39).
     * The pack has already translated the sentence by the time this runs — all it is missing is
     * whose name belongs in it.
     */
    private static final VrBrand.Renamer RENAMER = new VrBrand.Renamer() {
        @Override
        public CharSequence rename(int resourceId, CharSequence value) {
            final SparseArray<String> own = ownNames;
            if (own != null) {
                final String name = own.get(resourceId);
                if (name != null) {
                    return ours(resourceId, name);
                }
            }
            final SparseArray<String> english = fallbacks;
            if (english == null) {
                return null;
            }
            final String fallback = english.get(resourceId);
            if (fallback == null) {
                return null;
            }
            return substitute(value == null ? null : value.toString(), fallback);
        }
    };

    /**
     * Put this product's name where the resolved string carries the service's, keeping whatever
     * language that string was in.
     *
     * <p>Pure, and separated from the map so the rule is a test rather than a claim.
     *
     * @param resolved what the pack or the resource just returned
     * @param fallback the English sentence to use when the name is not in there to replace — a
     *                 translation that transliterates it some third way, or an upstream edit
     *                 that dropped it. Returning the fallback keeps the sentence TRUE at the
     *                 cost of its language, which is the right way round.
     */
    static String substitute(String resolved, String fallback) {
        if (resolved != null && !resolved.isEmpty()) {
            for (String service : SERVICE) {
                if (resolved.contains(service)) {
                    return resolved.replace(service, PRODUCT);
                }
            }
        }
        return fallback;
    }

    /**
     * One of this module's own sentences, which replaces upstream's rather than renaming it.
     *
     * <p>Read by entry NAME through the cloud pack, exactly as {@link VrStrings} does, and NOT
     * through {@code LocaleController.getString(int)} — that would re-enter the seam this
     * method is called from. {@code getServerString} does not pass through the seam
     * ({@code LocaleController.java:1479}), so there is no recursion here.
     */
    private static String ours(int resourceId, String entryName) {
        try {
            final String fromPack = LocaleController.getServerString(entryName);
            if (fromPack != null && !fromPack.isEmpty() && !fromPack.startsWith("LOC_ERR")) {
                return fromPack;
            }
        } catch (Throwable ignored) {
            // No pack yet, or a renamed resource: the compiled English below still works.
        }
        final SparseArray<String> english = ownEnglish;
        return english == null ? null : english.get(resourceId);
    }

    /**
     * Records one of this module's own strings against the upstream id it replaces.
     *
     * <p>The entry name is read HERE, at install, with the Context we are handed — a
     * {@code Resources} lookup on the path every drawn string takes would be exactly the cost
     * this seam was built to avoid.
     */
    private static void own(Context context, SparseArray<String> names, SparseArray<String> english,
                            int upstreamId, int ourId) {
        try {
            names.put(upstreamId, context.getResources().getResourceEntryName(ourId));
            english.put(upstreamId, context.getString(ourId));
        } catch (Throwable ignored) {
            // A resource that will not resolve leaves upstream's string in place, which is the
            // one outcome that cannot make the client worse.
        }
    }

    public static void install(Context context) {
        final SparseArray<String> english = new SparseArray<>();
        final SparseArray<String> ourKeys = new SparseArray<>();
        final SparseArray<String> ourEnglish = new SparseArray<>();

        // The app's own name, wherever it introduces itself.
        english.put(R.string.AppName, PRODUCT);
        english.put(R.string.AppNameBeta, PRODUCT);

        // What a notification says when its content is hidden. It is the app speaking as
        // itself on a lock screen, so it is the app's name — not the service's.
        english.put(R.string.NotificationHiddenName, PRODUCT);
        english.put(R.string.NotificationHiddenChatName, PRODUCT);

        // Where the app speaks about ITSELF and upstream wrote its own name.
        // "Update Telegram" updates THIS app; the passcode screen locks THIS app; the version
        // and the cache on this device are THIS app's.
        english.put(R.string.AppUpdate, "Update " + PRODUCT);
        english.put(R.string.AppUpdateBeta, "Update " + PRODUCT + " Beta");
        english.put(R.string.AppLocked, PRODUCT + " Locked");
        // Upstream is "Telegram for Android %1$s". Substituting the name into it would
        // give "Nicegram VR for Android" - true of the package, odd on a headset - so
        // this one is replaced outright rather than renamed.
        own(context, ourKeys, ourEnglish, R.string.TelegramVersion, my.nicegram.vr.R.string.vr_app_version);
        english.put(R.string.TelegramCacheSize, "%s " + PRODUCT + " Cache");

        // --- Storage, cache and database: all of it is THIS app's, on this device. ---
        english.put(R.string.ClearTelegramCache, "Clear " + PRODUCT + " Cache");
        english.put(R.string.LocalDatabaseSize, "%s " + PRODUCT + " Local Database");
        english.put(R.string.StorageUsageTelegram, PRODUCT + " uses %s of your device storage.");
        english.put(R.string.StorageUsageTelegramLess, PRODUCT + " uses %s of your device storage.");
        english.put(R.string.OptimizingTelegram, "Optimizing " + PRODUCT + "\u2026");

        // --- The lock screen. This app is what is locked and unlocked. ---
        english.put(R.string.EnterYourTelegramPasscode, "Enter your " + PRODUCT + " passcode");
        english.put(R.string.UnlockToUse, "Unlock to use " + PRODUCT);

        // --- Updating. "Update Telegram" on a headset means update THIS. ---
        english.put(R.string.UpdateTelegram, "Update " + PRODUCT);
        english.put(R.string.UnsupportedMessageMessage, "Update " + PRODUCT + " to view this message");

        // --- The built-in browser, which belongs to this app. ---
        english.put(R.string.OpenInTelegramBrowser, "Open in " + PRODUCT);
        english.put(R.string.BrowserSettingsCustomTabs, "New Pages within " + PRODUCT);

        // --- The empty state. The first sentence a new user reads. ---
        english.put(R.string.NoChats, "Welcome to " + PRODUCT);

        // --- Call branding. This is the label on the call the user is looking at, in this
        //     app, on their headset - not a description of Telegram's calling service. ---
        english.put(R.string.CallViaTelegram, PRODUCT + " Call");
        english.put(R.string.VoiceCallViaTelegram, PRODUCT + " Voice Call");
        english.put(R.string.VideoCallViaTelegram, PRODUCT + " Video Call");
        english.put(R.string.VoipInCallBranding, PRODUCT + " Call");
        english.put(R.string.VoipInCallBrandingWithName, PRODUCT + " Call to %s");
        english.put(R.string.VoipInVideoCallBranding, PRODUCT + " Video Call");
        english.put(R.string.VoipInVideoCallBrandingWithName, PRODUCT + " Video Call to %s");
        english.put(R.string.VoipInConferenceCallBranding, PRODUCT + " Group Call");
        english.put(R.string.VoipOutgoingCall, "Ongoing " + PRODUCT + " call");

        // --- Permission prompts. This app is the one asking. (Mostly unreachable now that
        //     the telephony permissions are stripped, but a dead string that lies is still
        //     a string that lies.) ---
        english.put(R.string.AllowFillNumber, "Please allow " + PRODUCT + " to receive calls so that we can automatically confirm your phone number.");
        english.put(R.string.AllowReadCall, "Please allow " + PRODUCT + " to receive calls so that we can automatically enter your code for you.");
        english.put(R.string.AllowReadCallAndLog, "Please allow " + PRODUCT + " to receive calls and read the call log so that we can automatically enter your code for you.");
        english.put(R.string.AllowReadCallLog, "Please allow " + PRODUCT + " to read the call log so that we can automatically enter your code for you.");

        // "Sorry, your Telegram app is out of date" - the out-of-date app is THIS one.
        english.put(R.string.UpdateAppAlert, "Sorry, your " + PRODUCT + " app is out of date and can\'t handle this request. Please update " + PRODUCT + ".");

        // Everything below is the SERVICE and its products, and stays Telegram's:
        //   TelegramFAQ, TelegramFeatures  - Telegram's own help pages
        //   TelegramPassport*              - a Telegram service feature
        //   TelegramContacts_*             - "N contacts on Telegram" is about the service
        //   TelegramTones                  - Telegram's notification sounds
        //   TelegramPremium*, TelegramBusiness*, TelegramStars*, AboutPremium*
        //                                  - products bought from Telegram, not from us
        //   Boosting* (65), Gift* (28), Voip*, Limit*, Privacy*, Revenue*
        //                                  - service features, named correctly
        // 508 strings mention Telegram. FORTY-SEVEN are touched - every place the app
        // speaks about ITSELF: thirty-five renamed by substitution, twelve replaced outright.
        // The rest are true: they name the service, its products, its terms, or another
        // client.

        // The first intro page introduced the app and then advertised the service:
        // "Telegram" over "The world's fastest messaging app. It is free and secure."
        // In an UNOFFICIAL client that is someone else's marketing on our first screen, and
        // the brand's own voice pack forbids marketing claims outright. Replaced with a
        // sentence that says what this actually is.
        //
        // Read from OUR resource BY ENTRY NAME, through getServerString - which does not pass
        // back through the rename seam, so there is no recursion - and so this sentence is
        // translatable the moment P-18 loads the keys into the pack. Until then it falls back
        // to the compiled English captured at install.
        english.put(R.string.Page1Title, PRODUCT);
        own(context, ourKeys, ourEnglish, R.string.Page1Message, my.nicegram.vr.R.string.vr_intro_message);

        // Pages 2-6 said "**Telegram** delivers messages faster than any other application",
        // "provides free unlimited cloud storage", "lets you access your messages from multiple
        // devices". Each sentence is true OF THE SERVICE, and each is a claim this client does
        // not get to make - it is someone else's marketing, on the second screen a new user
        // sees, in an unofficial client. Replaced with five that describe THIS app on a headset
        // and assert nothing about Telegram. Measured on the simulator 2026-09-21: pages 2 and 3
        // read "Telegram delivers..." and "Telegram provides..." verbatim.
        own(context, ourKeys, ourEnglish, R.string.Page2Title, my.nicegram.vr.R.string.vr_page2_title);
        own(context, ourKeys, ourEnglish, R.string.Page2Message, my.nicegram.vr.R.string.vr_page2_message);
        own(context, ourKeys, ourEnglish, R.string.Page3Title, my.nicegram.vr.R.string.vr_page3_title);
        own(context, ourKeys, ourEnglish, R.string.Page3Message, my.nicegram.vr.R.string.vr_page3_message);
        own(context, ourKeys, ourEnglish, R.string.Page4Title, my.nicegram.vr.R.string.vr_page4_title);
        own(context, ourKeys, ourEnglish, R.string.Page4Message, my.nicegram.vr.R.string.vr_page4_message);
        own(context, ourKeys, ourEnglish, R.string.Page5Title, my.nicegram.vr.R.string.vr_page5_title);
        own(context, ourKeys, ourEnglish, R.string.Page5Message, my.nicegram.vr.R.string.vr_page5_message);
        own(context, ourKeys, ourEnglish, R.string.Page6Title, my.nicegram.vr.R.string.vr_page6_title);
        own(context, ourKeys, ourEnglish, R.string.Page6Message, my.nicegram.vr.R.string.vr_page6_message);

        fallbacks = english;
        ownNames = ourKeys;
        ownEnglish = ourEnglish;
        VrBrand.install(RENAMER, PRODUCT, my.nicegram.vr.R.drawable.nicegram_mark);
    }
}
