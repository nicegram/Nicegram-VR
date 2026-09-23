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

public final class VrPolicy {

    /** Installed by the headset flavour at application start. */
    public interface Gate {
        /** @return true when this message may raise a notification on this device. */
        boolean allowNotification(int currentAccount, MessageObject message);

        /** Called for every message the gate held back, so it can be offered as a digest later. */
        void onSuppressed(int currentAccount, MessageObject message);

        /**
         * @param callerId the user placing the call
         * @return true when this call may ring on this device
         */
        boolean allowIncomingCall(int currentAccount, long callerId);
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
     * May this message raise a notification on this device?
     *
     * Fails OPEN on any error, and the direction is chosen rather than inherited: a missed
     * message is worse than an extra one, and a client that went silent because its filter threw
     * is indistinguishable from a client that is simply broken.
     *
     * A message held back is handed to {@link Gate#onSuppressed} so it can be offered as a
     * digest later — suppressed is not the same as dropped.
     */
    /**
     * May this incoming call ring on this device?
     *
     * <p>Calls were outside this gate until 23 September 2026, and that was a hole in the
     * product's central promise rather than an omission of detail: a client that says "nothing
     * arrives until you say it may" rang for anyone who dialled (A-43). They are asked the same
     * question as messages now — the master switch first, then the named people and chats.
     *
     * <p><b>Nothing is lost by not ringing.</b> Telegram delivers a missed call into the chat as
     * a service message, which reaches {@code appendMessage} and is gated and remembered like
     * any other, so a suppressed call still appears in the digest. That is why the gate does not
     * need a second suppression channel for calls.
     *
     * <p>Fails OPEN, in the same direction and for the same reason as {@link #allows}: a client
     * that went silent because its filter threw is indistinguishable from one that is broken.
     */
    public static boolean allowsCall(int currentAccount, long callerId) {
        final Gate g = gate;
        if (g == null) {
            return true;
        }
        try {
            return g.allowIncomingCall(currentAccount, callerId);
        } catch (Throwable e) {
            FileLog.e(e);
            return true;
        }
    }

    public static boolean allows(int currentAccount, MessageObject message) {
        final Gate g = gate;
        if (g == null || message == null) {
            return true;
        }
        try {
            if (g.allowNotification(currentAccount, message)) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return true;
        }
        try {
            g.onSuppressed(currentAccount, message);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }
}
