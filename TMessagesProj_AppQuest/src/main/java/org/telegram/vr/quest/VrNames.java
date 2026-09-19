package org.telegram.vr.quest;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Nicegram VR — naming a dialog that may not be loaded yet.
 *
 * {@link DialogObject#getName} returns an empty string for a user or chat the client has not
 * seen in this session. On the exceptions screen that produced a blank row the user could
 * neither identify nor confidently remove, which is worse than a placeholder: it looks like
 * corruption rather than like something still arriving.
 */
public final class VrNames {

    private VrNames() {
    }

    public static boolean isPerson(long dialogId) {
        return dialogId >= 0;
    }

    /** The loaded object, or null. Callers pass it to a cell that draws its own avatar. */
    public static Object dialogObject(int currentAccount, long dialogId) {
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        if (isPerson(dialogId)) {
            return controller.getUser(dialogId);
        }
        return controller.getChat(-dialogId);
    }

    /**
     * A name that is never empty: the real one when it is known, the supplied placeholder when
     * the dialog has not arrived yet.
     */
    public static CharSequence name(int currentAccount, long dialogId, CharSequence whileLoading) {
        final Object object = dialogObject(currentAccount, dialogId);
        if (object == null) {
            return whileLoading;
        }
        final String name = DialogObject.getName(currentAccount, dialogId);
        return name == null || name.trim().isEmpty() ? whileLoading : name;
    }

    /**
     * Loads from local storage anything the exceptions reference but the controller does not
     * hold in memory yet, then hands it back on the main thread and calls {@code onLoaded}.
     *
     * The same shape upstream uses for notification exceptions
     * (NotificationsSettingsActivity.loadExceptions): read on the storage queue with
     * getUsersInternal / getChatsInternal, publish with putUsers / putChats. Nothing is fetched
     * from the network — a dialog the user added as an exception was, by definition, visible in
     * their own list a moment earlier.
     */
    public static void loadMissing(int currentAccount, Collection<Long> dialogIds, Runnable onLoaded) {
        final ArrayList<Long> users = new ArrayList<>();
        final ArrayList<String> chats = new ArrayList<>();
        for (Long id : dialogIds) {
            if (id == null || dialogObject(currentAccount, id) != null) {
                continue;
            }
            if (isPerson(id)) {
                users.add(id);
            } else {
                chats.add(String.valueOf(-id));
            }
        }
        if (users.isEmpty() && chats.isEmpty()) {
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
            final ArrayList<TLRPC.Chat> loadedChats = new ArrayList<>();
            try {
                if (!users.isEmpty()) {
                    MessagesStorage.getInstance(currentAccount).getUsersInternal(users, loadedUsers);
                }
                if (!chats.isEmpty()) {
                    MessagesStorage.getInstance(currentAccount)
                            .getChatsInternal(TextUtils.join(",", chats), loadedChats);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (!loadedUsers.isEmpty()) {
                    MessagesController.getInstance(currentAccount).putUsers(loadedUsers, true);
                }
                if (!loadedChats.isEmpty()) {
                    MessagesController.getInstance(currentAccount).putChats(loadedChats, true);
                }
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
        });
    }
}
