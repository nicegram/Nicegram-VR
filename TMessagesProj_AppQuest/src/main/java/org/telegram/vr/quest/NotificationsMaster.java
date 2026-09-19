package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nicegram VR — the one switch, above every rule.
 *
 * The client already decides message by message whether something may interrupt: a named
 * person, a named chat, a named word. That is the right shape for "mostly quiet, except these".
 * It is the wrong shape for "not now" — nobody wants to edit a rule list before a meeting.
 *
 * So this sits above the rules and answers a simpler question. OFF means nothing arrives, no
 * matter who sent it. ON means the rules decide, exactly as before. The rules are never edited
 * by this switch, so turning it back on restores the same exceptions the user had.
 *
 * <h3>What it does NOT do</h3>
 *
 * It never calls {@code account.updateNotifySettings}. That would push a mute to the Telegram
 * account, which is shared: silencing a headset would silence the phone in someone's pocket.
 * Everything here is local to this device, which is also why a second device shows unchanged
 * settings — the check the whole silence design exists to pass.
 *
 * <h3>Default</h3>
 *
 * OFF. The client's premise is that a headset is a place you went to be undisturbed, and the
 * first run screen says so. A user who wants interruptions turns them on and keeps them on.
 */
public final class NotificationsMaster {

    private static final String FILE = "nicegram_vr_silence";
    private static final String KEY_ON = "notifications_master_on";

    /** Bumped on every change so cached readers notice without anyone remembering to tell them. */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static volatile Boolean cached;

    private NotificationsMaster() {
    }

    public static int generation() {
        return GENERATION.get();
    }

    public static boolean isOn(Context context) {
        final Boolean value = cached;
        if (value != null) {
            return value;
        }
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        final boolean on = prefs.getBoolean(KEY_ON, false);
        cached = on;
        return on;
    }

    public static void set(Context context, boolean on) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ON, on).apply();
        cached = on;
        GENERATION.incrementAndGet();
    }

    public static boolean toggle(Context context) {
        final boolean next = !isOn(context);
        set(context, next);
        return next;
    }
}
