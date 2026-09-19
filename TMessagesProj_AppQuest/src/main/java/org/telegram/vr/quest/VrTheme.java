package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ActionBar.Theme;

/**
 * Nicegram VR — the brand, applied where a brand belongs and nowhere else.
 *
 * <h3>What this deliberately does not do</h3>
 *
 * It does not repaint the client. Telegram's dark palette is the product of years of work on
 * contrast, on unread states, on what a quoted message looks like inside a reply — and the
 * Nicegram Android client, the company's own flagship, ships Telegram's five themes untouched.
 * Inventing a purple messenger here would diverge from that flagship AND turn every upstream
 * merge into a colour negotiation.
 *
 * So exactly one value moves: the accent. Buttons, links, unread badges, the send button, the
 * selected tab — the places a user reads as "this app's colour" — become Nicegram's
 * {@code #8B5FF2}. Everything else stays the dark theme Telegram shipped.
 *
 * <h3>How, without fighting the theme engine</h3>
 *
 * The app already supports arbitrary accents: that is what the theme screen offers. This does
 * the same thing that screen does — take the Night theme, take one of its accents, set the
 * colour, select it, save — rather than adding a fourteenth entry to the eleven parallel arrays
 * in {@code Theme.java}, which would conflict on every merge for one colour's sake.
 *
 * The accent borrowed is id 10, upstream's violet ({@code 0xFF8D78E3}). It is overwritten
 * rather than appended because a user who prefers Telegram's own violet can still pick any
 * other accent in settings, and one occupied slot is a smaller price than a permanent diff.
 */
public final class VrTheme {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_APPLIED = "theme_default_applied";

    /** The Nicegram mark's purple, taken from the brand's own launcher background. */
    public static final int ACCENT = 0xFF8B5FF2;

    /** Outgoing bubbles: the same hue, stepped down so a wall of them is not a wall of purple. */
    public static final int MY_MESSAGES = 0xFF6F4BD8;

    /** Upstream's violet accent on the Night theme — the slot this build takes over. */
    private static final int NIGHT_VIOLET_ACCENT_ID = 10;

    private VrTheme() {
    }

    /**
     * Applied once per install — and the marker is written ONLY when the apply succeeded. The
     * theme engine loads its themes lazily, so an early call can find nothing to work with;
     * marking it done regardless would leave the client on Telegram blue forever with a
     * preference file claiming otherwise. Failing quietly and retrying next start is cheap.
     */
    public static void applyDefaultsOnce(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_APPLIED, false)) {
            return;
        }
        if (applyDefaults()) {
            prefs.edit().putBoolean(KEY_APPLIED, true).apply();
        }
    }

    /**
     * Repaints the slot, every start.
     *
     * This is separate from selecting it, and the separation was learned on a device. Upstream
     * defines accent 10's colour in a literal array in {@code Theme.java} and persists only the
     * *selected* id — after the first run, `themeconfig.xml` held
     * {@code accent_current_night.attheme=10} and no colour at all, so the next launch would
     * have rebuilt upstream's violet from the array and the brand colour would have lasted
     * exactly one session. The build could not show this; the headset did.
     *
     * So the colour is asserted on every start while the SELECTION is only a first-run default.
     * A user who picks another accent keeps it; whoever picks slot 10 always gets Nicegram's
     * purple rather than a slot that means one thing today and another tomorrow.
     *
     * @return true when the slot was repainted.
     */
    public static boolean enforceAccentColour() {
        try {
            final Theme.ThemeAccent accent = accentSlot();
            if (accent == null) {
                return false;
            }
            if (accent.accentColor == ACCENT && accent.myMessagesAccentColor == MY_MESSAGES) {
                return true;
            }
            accent.accentColor = ACCENT;
            accent.myMessagesAccentColor = MY_MESSAGES;
            // indexOnly=false rather than the true the first version passed. It is the right
            // call, but it is NOT what makes the colour hold: the blob upstream then writes to
            // `accents_night.attheme` was decoded on the device and is `09 00 00 00 00 00 00 00`
            // — an id record, with no palette in it. Nothing persists this colour. Re-asserting
            // it on every start is therefore the mechanism, not a safety net, which is why this
            // method is called from the application's onCreate and not only on first run.
            Theme.saveThemeAccents(Theme.getTheme("Night"), true, false, false, false);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static Theme.ThemeAccent accentSlot() {
        final Theme.ThemeInfo night = Theme.getTheme("Night");
        if (night == null || night.themeAccentsMap == null) {
            return null;
        }
        return night.themeAccentsMap.get(NIGHT_VIOLET_ACCENT_ID);
    }

    /**
     * @return true when the accent was actually applied. Also reachable from "reset to
     *         defaults" in the headset settings.
     */
    public static boolean applyDefaults() {
        try {
            final Theme.ThemeInfo night = Theme.getTheme("Night");
            if (night == null || accentSlot() == null) {
                return false;
            }
            if (!enforceAccentColour()) {
                return false;
            }
            night.setCurrentAccentId(NIGHT_VIOLET_ACCENT_ID);
            Theme.saveThemeAccents(night, true, false, false, false);
            Theme.applyTheme(night, true, true);
            return true;
        } catch (Throwable e) {
            // A brand colour is not worth a crash on startup. The client is perfectly usable in
            // Telegram's own dark theme, which is what a failure here leaves it in.
            return false;
        }
    }
}
