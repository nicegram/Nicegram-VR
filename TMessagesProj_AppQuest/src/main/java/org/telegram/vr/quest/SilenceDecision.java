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
     * Which of a message's two texts a word rule may read — and the signature takes BOTH so the
     * call site cannot quietly pass the wrong one, which is exactly what it did.
     *
     * <p>{@code MessageObject.messageText} is not the sender's text. For media it holds a
     * description this client generated: literally {@code getString(R.string.AttachPhoto)},
     * the word "Photo" ({@code MessageObject.java:6007}). What the sender typed — the caption —
     * lives in {@code messageOwner.message}, which is also where {@code generateCaption} reads
     * it from ({@code :7598}); for an ordinary message the two are the same value
     * ({@code :6035}). So a rule built on the rendered text misses every captioned photo, video
     * and file, and fires on the word "photo" for every photo that carries no caption at all.
     *
     * <p>It lives here rather than beside its call site for one reason: this class imports
     * nothing but {@code java.util.Locale}, so a test can load it on the JVM. {@code SilenceGate}
     * pulls {@code MessageObject} into its loading graph and a test through it would be hostage to
     * whether the JVM happened to resolve an Android class.
     *
     * @param sentText     {@code messageOwner.message} — what the sender wrote
     * @param renderedText {@code messageText} — what this client drew; never returned
     */
    public static CharSequence wordSource(CharSequence sentText, CharSequence renderedText) {
        return sentText == null ? "" : sentText;
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
