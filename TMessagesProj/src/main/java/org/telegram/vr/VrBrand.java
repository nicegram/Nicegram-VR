package org.telegram.vr;

import android.util.SparseArray;

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
 * So the rename happens after the pack has spoken, at the one point every string in the client
 * passes through, and it is keyed by RESOURCE ID rather than by matching text.
 *
 * <h3>Why not replace the word "Telegram" everywhere</h3>
 *
 * Because most of its 508 occurrences are correct. "Telegram" names the SERVICE this client
 * connects to and the PROTOCOL it speaks; "Nicegram servers" would be a lie, and so would
 * pointing telegram.org/privacy or /tos at another domain — those are the terms governing the
 * user's account, which this project does not set. Upstream asks forks not to pass themselves
 * off as official, and that duty is discharged by naming the APPLICATION honestly, not by
 * scrubbing the service's name out of its own client.
 *
 * <h3>The hot path</h3>
 *
 * Every string the client draws goes through {@link #rename}. It must therefore cost nothing
 * when no brand is installed — one null check — and a SparseArray lookup when one is. Resolving
 * a resource entry NAME per call would put a Resources lookup on the layout path of a client
 * held to 60 fps, which is why the map is built once, at install, and keyed by id.
 */
public final class VrBrand {

    private static volatile SparseArray<String> renames;
    private static volatile String appName;
    private static volatile int logoRes;

    private VrBrand() {
    }

    /**
     * @param byResourceId resolved once by the caller, so this class never touches Resources
     * @param productName  what the app calls itself in its own title, or null to leave it
     */
    public static void install(SparseArray<String> byResourceId, String productName, int mark) {
        renames = byResourceId;
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
     * no brand, no map entry, or anything unexpected leaves upstream's value untouched.
     */
    public static CharSequence rename(int resourceId, CharSequence value) {
        final SparseArray<String> map = renames;
        if (map == null) {
            return value;
        }
        try {
            final String replacement = map.get(resourceId);
            return replacement == null ? value : replacement;
        } catch (Throwable e) {
            return value;
        }
    }
}
