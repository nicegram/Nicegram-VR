package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Nicegram VR — which chat folder a headset session opens on.
 *
 * <h3>Why this exists</h3>
 *
 * "Show me only these chats" is a folder with an include-only rule, and folders already work
 * upstream. What was missing is opening in one: a headset session that starts in everything is
 * a session that starts by scrolling. The narrow view is the point of the device.
 *
 * <h3>What is stored, and why it is not the obvious thing</h3>
 *
 * {@link org.telegram.messenger.MessagesController.DialogFilter} carries two numbers and only
 * one of them survives a restart:
 *
 * <pre>
 *   public int id;                                 // the server's filter id — persistent
 *   public int localId = dialogFilterPointer++;    // a process counter (MessagesController:1295)
 * </pre>
 *
 * {@code localId} is what {@code FilterTabsView} calls a "stable id", and it is stable within a
 * session and meaningless across one. A setting that stored it would open a different folder
 * every launch, and would do it convincingly enough that the bug would be blamed on the folder
 * list. So this stores {@code id}, and the translation to a tab index happens at the moment of
 * selection, where the live filter list is in hand.
 *
 * <h3>Local, like everything else here</h3>
 *
 * A private preferences file of this build. It is not an account setting, so a phone signed
 * into the same account keeps opening wherever it opened before.
 */
public final class VrStartFolder {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY = "start_folder_filter_id";

    /** No folder chosen: the client opens where upstream would. */
    public static final int NONE = Integer.MIN_VALUE;

    private VrStartFolder() {
    }

    /** The persistent {@code DialogFilter.id} to open on, or {@link #NONE}. */
    public static int filterId(Context context) {
        if (context == null) {
            return NONE;
        }
        final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return prefs.getInt(KEY, NONE);
    }

    public static void set(Context context, int filterId) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY, filterId).apply();
    }

    public static void clear(Context context) {
        set(context, NONE);
    }

    public static boolean isSet(Context context) {
        return filterId(context) != NONE;
    }
}
