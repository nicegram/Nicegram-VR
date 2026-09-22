package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The silence rule is the product. These run on the JVM, so they run on every push.
 */
public class SilenceDecisionTest {

    private static Set<Long> ids(long... values) {
        Set<Long> set = new HashSet<>();
        for (long v : values) {
            set.add(v);
        }
        return set;
    }

    private static SilenceProfile profile(Set<Long> people, Set<Long> chats, String... words) {
        return new SilenceProfile(people, chats, new HashSet<>(Arrays.asList(words)));
    }

    @Test
    public void emptyProfileIsSilentForEveryone() {
        assertFalse(SilenceDecision.allow(10L, 20L, false, "anything at all", SilenceProfile.SILENT));
    }

    @Test
    public void namedPersonGetsThrough() {
        SilenceProfile p = profile(ids(20L), Collections.emptySet());
        assertTrue(SilenceDecision.allow(10L, 20L, false, "hi", p));
        assertFalse(SilenceDecision.allow(10L, 21L, false, "hi", p));
    }

    @Test
    public void namedChatGetsThrough() {
        SilenceProfile p = profile(Collections.emptySet(), ids(10L));
        assertTrue(SilenceDecision.allow(10L, 99L, false, "hi", p));
        assertFalse(SilenceDecision.allow(11L, 99L, false, "hi", p));
    }

    @Test
    public void wordMatchIsCaseInsensitiveAndSubstring() {
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "срочно");
        assertTrue(SilenceDecision.allow(10L, 20L, false, "Это СРОЧНО, посмотри", p));
        assertTrue(SilenceDecision.allow(10L, 20L, false, "несрочно", p));
        assertFalse(SilenceDecision.allow(10L, 20L, false, "потом", p));
    }

    @Test
    public void ownMessagesAreNeverShown() {
        SilenceProfile p = profile(ids(20L), ids(10L), "срочно");
        assertFalse(SilenceDecision.allow(10L, 20L, true, "срочно", p));
    }

    @Test
    public void nullTextDoesNotMatchAWordAndDoesNotThrow() {
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "срочно");
        assertFalse(SilenceDecision.allow(10L, 20L, false, null, p));
    }

    @Test
    public void emptyWordNeverMatchesEverything() {
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "");
        assertFalse(SilenceDecision.allow(10L, 20L, false, "any text", p));
    }

    // --- Which text a word rule reads. The rule was right and the argument was wrong: it was
    // handed MessageObject.messageText, which for media is a description this CLIENT generates.

    @Test
    public void aWordInACaptionGetsThrough() {
        // A photo whose caption says "срочно". messageText is the generated word "Photo".
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "срочно");
        assertTrue(SilenceDecision.allow(10L, 20L, false,
                SilenceDecision.wordSource("срочно, посмотри", "Photo"), p));
    }

    @Test
    public void theClientsOwnWordsAreNotTheSenders() {
        // A photo with NO caption must not match a rule on the word "photo" — that word is this
        // client's description of the message, not anything anybody wrote.
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "photo");
        assertFalse(SilenceDecision.allow(10L, 20L, false,
                SilenceDecision.wordSource("", "Photo"), p));
        assertFalse(SilenceDecision.allow(10L, 20L, false,
                SilenceDecision.wordSource(null, "Photo"), p));
    }

    @Test
    public void plainTextIsUnaffected() {
        // For an ordinary message the two are the same value (MessageObject.java:6035), so the
        // change must be invisible here.
        SilenceProfile p = profile(Collections.emptySet(), Collections.emptySet(), "срочно");
        assertTrue(SilenceDecision.allow(10L, 20L, false,
                SilenceDecision.wordSource("это срочно", "это срочно"), p));
        assertFalse(SilenceDecision.allow(10L, 20L, false,
                SilenceDecision.wordSource("это подождёт", "это подождёт"), p));
    }
}
