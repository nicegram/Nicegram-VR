package org.telegram.vr.quest;

import org.telegram.messenger.MessageObject;
import org.telegram.vr.VrPolicy;

/**
 * Nicegram VR — the decision itself.
 *
 * Deliberately a pure function of (message, profile) with one cached read, so it can be tested
 * on the JVM without Android and so a reader can check the rule by reading it:
 *
 *   show it when the sender is named, or the chat is named, or the text carries a named word.
 *   otherwise keep quiet and remember it for the digest.
 *
 * Outgoing messages are never shown — a client that notifies you about your own message is a
 * bug in every context, and in a headset it is a bug you cannot look away from.
 */
public final class SilenceGate implements VrPolicy.Gate {

    private final SilenceStore store;
    private final Digest digest = new Digest();

    private int cachedAccount = -1;
    private int cachedGeneration = -1;
    private SilenceProfile cachedProfile;

    public SilenceGate(SilenceStore store) {
        this.store = store;
        QuestRuntime.setGate(this);
    }

    public Digest digest() {
        return digest;
    }

    /** Drops the cached profile so the next decision re-reads it. */
    public void invalidate() {
        cachedAccount = -1;
        cachedGeneration = -1;
        cachedProfile = null;
    }

    /**
     * Cached, because this runs on every incoming message; re-read whenever the store was
     * written, so an exception added in settings takes effect on the next message rather than
     * on the next process start. Nobody has to remember to call invalidate().
     */
    private SilenceProfile profile(int account) {
        final int generation = SilenceStore.generation();
        if (cachedAccount != account || cachedProfile == null || cachedGeneration != generation) {
            cachedProfile = store.load(account);
            cachedAccount = account;
            cachedGeneration = generation;
        }
        return cachedProfile;
    }

    @Override
    public boolean allowNotification(int currentAccount, MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        // The master switch sits above the rules, and it is checked first because when it is
        // off the rules are not a question anyone is asking. Turning it back on restores the
        // same exceptions: nothing here edits them.
        if (!NotificationsMaster.isOn(org.telegram.messenger.ApplicationLoader.applicationContext)) {
            return false;
        }
        return SilenceDecision.allow(
                message.getDialogId(),
                message.getSenderId(),
                message.isOutOwner(),
                SilenceDecision.wordSource(message.messageOwner.message, message.messageText),
                profile(currentAccount));
    }

    @Override
    public void onSuppressed(int currentAccount, MessageObject message) {
        digest.add(currentAccount, message);
    }

    /**
     * The same question for a call, and the master switch is checked first for the same reason.
     *
     * <p>A suppressed call is not added to the digest here, and that is deliberate rather than
     * an omission: Telegram delivers a missed call into the chat as a service message, which
     * arrives through {@code appendMessage} moments later and is gated and remembered like any
     * other message. Adding it twice would show it twice.
     */
    @Override
    public boolean allowIncomingCall(int currentAccount, long callerId) {
        if (!NotificationsMaster.isOn(org.telegram.messenger.ApplicationLoader.applicationContext)) {
            return false;
        }
        return SilenceDecision.allowCall(callerId, profile(currentAccount));
    }
}
