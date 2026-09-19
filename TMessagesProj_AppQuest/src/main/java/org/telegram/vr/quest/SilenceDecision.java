package org.telegram.vr.quest;

import java.util.Locale;

/**
 * Nicegram VR — the silence rule, with nothing Android in it.
 *
 * Separated from {@link SilenceGate} for one reason: this is the decision the whole feature
 * rests on, and a decision that cannot be tested without a headset is a decision nobody checks.
 * Here it is a pure function of its arguments and runs on the JVM in milliseconds.
 */
public final class SilenceDecision {

    private SilenceDecision() {
    }

    /**
     * @param outgoing the user's own message; never shown, in any profile
     * @param text     message text, may be null
     * @return true when this message may raise a notification on this device
     */
    public static boolean allow(long dialogId, long senderId, boolean outgoing,
                                CharSequence text, SilenceProfile profile) {
        if (outgoing || profile == null || profile.isSilentForEveryone()) {
            return false;
        }
        if (profile.chats.contains(dialogId) || profile.people.contains(senderId)) {
            return true;
        }
        if (!profile.words.isEmpty() && text != null && text.length() > 0) {
            final String haystack = text.toString().toLowerCase(Locale.ROOT);
            for (String word : profile.words) {
                if (!word.isEmpty() && haystack.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }
}
