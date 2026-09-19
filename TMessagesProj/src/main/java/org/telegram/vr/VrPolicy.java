/*
 * Nicegram VR — device-local display policy.
 *
 * This is the ONLY hook the headset build needs inside shared Telegram code, and it is
 * deliberately inert everywhere else: with no gate installed it returns every message
 * untouched, so the phone builds behave exactly as upstream.
 *
 * It decides what is SHOWN, never what is read. Unread counters, dialog state and the
 * server's own notification settings are not its business — a headset that goes quiet must
 * not silence the phone in the user's pocket.
 */
package org.telegram.vr;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.List;

public final class VrPolicy {

    /** Installed by the headset flavour at application start. */
    public interface Gate {
        /** @return true when this message may raise a notification on this device. */
        boolean allowNotification(int currentAccount, MessageObject message);

        /** Called for every message the gate held back, so it can be offered as a digest later. */
        void onSuppressed(int currentAccount, MessageObject message);
    }

    private static volatile Gate gate;

    private VrPolicy() {
    }

    public static void install(Gate newGate) {
        gate = newGate;
    }

    public static boolean isInstalled() {
        return gate != null;
    }

    /**
     * Removes from {@code messages} everything the installed gate holds back.
     *
     * Fails OPEN on any error: a gate that throws must not be able to silence the client.
     * That direction is chosen deliberately — a missed message is worse than an extra one,
     * and a silent client with a broken gate is indistinguishable from a broken client.
     */
    public static void filterForDisplay(int currentAccount, List<MessageObject> messages) {
        final Gate g = gate;
        if (g == null || messages == null || messages.isEmpty()) {
            return;
        }
        ArrayList<MessageObject> suppressed = null;
        try {
            for (int i = 0; i < messages.size(); ++i) {
                final MessageObject message = messages.get(i);
                if (message == null) {
                    continue;
                }
                if (!g.allowNotification(currentAccount, message)) {
                    if (suppressed == null) {
                        suppressed = new ArrayList<>();
                    }
                    suppressed.add(message);
                    messages.remove(i);
                    i--;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return;
        }
        if (suppressed != null) {
            for (int i = 0; i < suppressed.size(); ++i) {
                try {
                    g.onSuppressed(currentAccount, suppressed.get(i));
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }
}
