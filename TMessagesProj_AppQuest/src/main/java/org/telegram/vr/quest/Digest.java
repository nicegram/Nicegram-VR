package org.telegram.vr.quest;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nicegram VR — what accumulated while nobody was interrupted.
 *
 * Counts per dialog with the period start, so what a returning user sees is "three chats since
 * 14:20" rather than a stack of thirty banners delivered late.
 *
 * In memory on purpose: after a restart the period begins at launch and anything genuinely
 * missed arrives through ordinary history sync, so there is no second, staler copy of the truth
 * to keep consistent.
 */
public final class Digest {

    /** Beyond this many dialogs the oldest is dropped: a digest nobody can read is not a digest. */
    private static final int MAX_DIALOGS = 200;
    /** The preview is a preview. Holding a whole message keeps its spans alive for the session. */
    private static final int PREVIEW_CHARS = 200;

    public static final class Entry {
        public final long dialogId;
        public int count;
        /**
         * Deliberately a String, not the CharSequence it came from. Telegram's message text is
         * frequently a Spannable whose spans reference other objects; keeping one per dialog for
         * the length of a session is a retention leak that nothing would ever point at.
         */
        public String lastText;
        public int lastDate;

        Entry(long dialogId) {
            this.dialogId = dialogId;
        }
    }

    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
    private long since = System.currentTimeMillis();

    public synchronized void add(int currentAccount, MessageObject message) {
        if (message == null) {
            return;
        }
        final long dialogId = message.getDialogId();
        Entry entry = entries.remove(dialogId);
        if (entry == null) {
            entry = new Entry(dialogId);
        }
        entry.count++;
        entry.lastText = preview(message.messageText);
        if (message.messageOwner != null) {
            entry.lastDate = message.messageOwner.date;
        }
        // Re-inserted so iteration order is most-recent-last; the screen reverses it.
        entries.put(dialogId, entry);
        while (entries.size() > MAX_DIALOGS) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    static String preview(CharSequence text) {
        if (text == null) {
            return "";
        }
        final String s = text.toString().replace('\n', ' ').trim();
        return s.length() <= PREVIEW_CHARS ? s : s.substring(0, PREVIEW_CHARS);
    }

    /** Most recent first. */
    public synchronized List<Entry> snapshot() {
        final ArrayList<Entry> out = new ArrayList<>(entries.values());
        Collections.reverse(out);
        return Collections.unmodifiableList(out);
    }

    public synchronized int chatCount() {
        return entries.size();
    }

    public synchronized int messageCount() {
        int total = 0;
        for (Map.Entry<Long, Entry> e : entries.entrySet()) {
            total += e.getValue().count;
        }
        return total;
    }

    public synchronized long since() {
        return since;
    }

    public synchronized void clear() {
        entries.clear();
        since = System.currentTimeMillis();
    }
}
