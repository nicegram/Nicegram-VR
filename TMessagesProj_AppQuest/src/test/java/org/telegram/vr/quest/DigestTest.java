package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The preview is the part with a defect history: it used to hold the message's own CharSequence,
 * which on Telegram is usually a Spannable, for the whole session.
 */
public class DigestTest {

    /**
     * A CharSequence that is deliberately not a String — the shape Telegram's message text
     * actually has. Android's own Spannable cannot be used here: it is not mocked in a JVM
     * test, which is how this test failed the first time it ran.
     */
    private static final class NotAString implements CharSequence {
        private final String value;

        NotAString(String value) {
            this.value = value;
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new NotAString(value.substring(start, end));
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Test
    public void previewIsAPlainStringAndNotTheSequenceItCameFrom() {
        CharSequence text = new NotAString("hello");
        String preview = Digest.preview(text);
        assertEquals("hello", preview);
        assertTrue("preview must not retain the original sequence", preview.getClass() == String.class);
    }

    @Test
    public void previewIsTruncatedAndSingleLine() {
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            long_.append("0123456789");
        }
        assertEquals(200, Digest.preview(long_).length());
        assertEquals("a b", Digest.preview("a\nb"));
    }

    @Test
    public void nullTextDoesNotThrow() {
        assertEquals("", Digest.preview(null));
    }

    @Test public void accountsWithTheSameChatStaySeparate() {
        Digest d = new Digest();
        d.add(0, 77, 1, "account zero", 10);
        d.add(1, 77, 1, "account one", 11);
        assertEquals("account zero", d.snapshot(0).get(0).lastText);
        assertEquals("account one", d.snapshot(1).get(0).lastText);
        d.clear(0);
        assertEquals(0, d.chatCount(0));
        assertEquals(1, d.messageCount(1));
    }

    @Test public void reconnectDoesNotCountTheSameMessageTwice() {
        Digest d = new Digest();
        d.add(0, 77, 1, "one", 10);
        d.add(0, 77, 1, "one", 10);
        assertEquals(1, d.messageCount(0));
        d.clear(0);
        d.add(0, 77, 1, "one", 10);
        assertEquals(0, d.chatCount(0));
    }

    @Test public void reusedAccountSlotDropsPreviousOwnersMessages() {
        Digest d = new Digest();
        d.ensureAccount(0, 100);
        d.add(0, 77, 1, "private", 10);
        d.ensureAccount(0, 101);
        assertEquals(0, d.chatCount(0));
    }

    @Test public void snapshotsStayStableAndOlderMessagesDoNotReplacePreview() {
        Digest d = new Digest();
        d.add(0, 77, 2, "new", 20);
        java.util.List<Digest.Entry> before = d.snapshot(0);
        d.add(0, 77, 1, "old", 10);
        assertEquals(1, before.get(0).count);
        assertEquals(2, d.messageCount(0));
        assertEquals("new", d.snapshot(0).get(0).lastText);
    }
}
