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
}
