package org.telegram.vr;

/**
 * Nicegram VR — where this application names itself.
 *
 * <h3>Why a registry and not an edit to strings.xml</h3>
 *
 * Editing {@code strings.xml} does not rename this app. {@code BuildVars.USE_CLOUD_STRINGS} is
 * true, so {@link org.telegram.messenger.LocaleController} asks the cloud language pack first
 * and only falls back to the resource — and the pack answers "Telegram" for {@code AppName} in
 * every language. That is the same mechanism finding A-19 turned up from the other side.
 *
 * So the rename happens after the pack has spoken, at the points every string in the client
 * passes through, and it is keyed by RESOURCE ID rather than by matching text.
 *
 * <h3>Why not replace the word "Telegram" everywhere</h3>
 *
 * Because most of the 508 strings that mention it are correct. "Telegram" names the SERVICE
 * this client connects to and the PROTOCOL it speaks; "Nicegram servers" would be a lie, and so would
 * pointing telegram.org/privacy or /tos at another domain — those are the terms governing the
 * user's account, which this project does not set. Upstream asks forks not to pass themselves
 * off as official, and that duty is discharged by naming the APPLICATION honestly, not by
 * scrubbing the service's name out of its own client.
 *
 * <h3>Why a delegate rather than a map</h3>
 *
 * It used to hold a {@code SparseArray<String>} of finished replacement sentences, which meant
 * every renamed string was frozen in the language it was written in — English — whatever
 * language the user had chosen (A-39). Deciding what a string becomes needs the value the pack
 * just returned, and that decision belongs to the module that owns the list. This class keeps
 * only the seam: one volatile read and a null check on every flavour that installs nothing.
 */
public final class VrBrand {

    /** Implemented by the flavour that has a brand. Called for EVERY string the client draws. */
    public interface Renamer {
        /**
         * @param resourceId the string being resolved
         * @param value      what the language pack or the resource just returned
         * @return the replacement, or null to leave {@code value} exactly as it is
         */
        CharSequence rename(int resourceId, CharSequence value);
    }

    private static volatile Renamer renamer;
    private static volatile String appName;
    private static volatile int logoRes;

    private VrBrand() {
    }

    /**
     * @param productName what the app calls itself in its own title, or null to leave it
     */
    public static void install(Renamer delegate, String productName, int mark) {
        renamer = delegate;
        appName = productName;
        logoRes = mark;
    }

    /**
     * The product's own mark, for the places upstream draws its logo as a picture: the intro
     * screen and the stories row. 0 on every flavour that has not installed one, where those
     * call sites are untouched.
     */
    public static int logoRes() {
        return logoRes;
    }

    /** The product's own name, or null on every flavour that has not installed one. */
    public static String appName() {
        return appName;
    }

    /**
     * Replaces a resolved string when this build has renamed it. Fails open in every direction:
     * no brand, no rule for this id, or anything unexpected leaves upstream's value untouched.
     */
    public static CharSequence rename(int resourceId, CharSequence value) {
        final Renamer delegate = renamer;
        if (delegate == null) {
            return value;
        }
        try {
            final CharSequence replacement = delegate.rename(resourceId, value);
            return replacement == null ? value : replacement;
        } catch (Throwable e) {
            return value;
        }
    }
}
