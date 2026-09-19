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
 * Counts per dialog, with the period start, so the user is shown "three chats since 14:20"
 * rather than a stack of thirty banners delivered late. It lives in memory on purpose: after a
 * restart the period begins at launch and anything genuinely missed arrives through the normal
 * history sync, so there is no second, staler copy of the truth to keep consistent.
 */
public final class Digest {

    public static final class Entry {
        public final long dialogId;
        public int count;
        public CharSequence lastText;
        public int lastDate;

        Entry(long dialogId) {
            this.dialogId = dialogId;
        }
    }

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private long since = System.currentTimeMillis();

    public synchronized void add(int currentAccount, MessageObject message) {
        if (message == null) {
            return;
        }
        final long dialogId = message.getDialogId();
        Entry entry = entries.get(dialogId);
        if (entry == null) {
            entry = new Entry(dialogId);
            entries.put(dialogId, entry);
        }
        entry.count++;
        entry.lastText = message.messageText;
        if (message.messageOwner != null) {
            entry.lastDate = message.messageOwner.date;
        }
    }

    public synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public synchronized int chatCount() {
        return entries.size();
    }

    public synchronized int messageCount() {
        int total = 0;
        for (Entry e : entries.values()) {
            total += e.count;
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
