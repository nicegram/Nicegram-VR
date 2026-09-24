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

    private static final class Account {
        final long owner;
        final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
        final LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        long since = System.currentTimeMillis();
        Account(long owner) { this.owner = owner; }
    }

    private final Map<Integer, Account> accounts = new java.util.HashMap<>();

    /** Account slots are reused after logout; never show the previous owner's previews. */
    public synchronized void ensureAccount(int account, long owner) {
        Account old = accounts.get(account);
        if (old == null || old.owner != owner) {
            accounts.put(account, new Account(owner));
        }
    }

    private Account account(int account) {
        return accounts.computeIfAbsent(account, ignored -> new Account(0));
    }

    public synchronized void add(int currentAccount, MessageObject message) {
        if (message == null) return;
        add(currentAccount, message.getDialogId(), message.getId(), message.messageText,
                message.messageOwner == null ? 0 : message.messageOwner.date);
    }

    // Primitive input lets the same storage path be exercised without Android MessageObject.
    synchronized void add(int currentAccount, long dialogId, int messageId,
                          CharSequence text, int date) {
        Account state = account(currentAccount);
        String key = dialogId + ":" + messageId;
        if (state.seen.put(key, Boolean.TRUE) != null) return;
        while (state.seen.size() > 10000) state.seen.remove(state.seen.keySet().iterator().next());
        Entry entry = state.entries.remove(dialogId);
        if (entry == null) entry = new Entry(dialogId);
        entry.count++;
        if (date >= entry.lastDate) {
            entry.lastText = preview(text);
            entry.lastDate = date;
        }
        state.entries.put(dialogId, entry);
        while (state.entries.size() > MAX_DIALOGS) {
            state.entries.remove(state.entries.keySet().iterator().next());
        }
    }

    static String preview(CharSequence text) {
        if (text == null) {
            return "";
        }
        final String s = text.toString().replace('\n', ' ').trim();
        return s.length() <= PREVIEW_CHARS ? s : s.substring(0, PREVIEW_CHARS);
    }

    /** Most recent first; copies cannot be changed by the notification queue after this read. */
    public synchronized List<Entry> snapshot(int currentAccount) {
        ArrayList<Entry> out = new ArrayList<>();
        for (Entry original : account(currentAccount).entries.values()) {
            Entry copy = new Entry(original.dialogId);
            copy.count = original.count;
            copy.lastText = original.lastText;
            copy.lastDate = original.lastDate;
            out.add(copy);
        }
        Collections.reverse(out);
        return Collections.unmodifiableList(out);
    }

    public synchronized int chatCount(int currentAccount) {
        return account(currentAccount).entries.size();
    }

    public synchronized int messageCount(int currentAccount) {
        int total = 0;
        for (Entry entry : account(currentAccount).entries.values()) total += entry.count;
        return total;
    }

    public synchronized long since(int currentAccount) {
        return account(currentAccount).since;
    }

    public synchronized void clear(int currentAccount) {
        Account state = account(currentAccount);
        state.entries.clear();
        // Keep recent IDs: reconnect must not resurrect a digest the user cleared.
        state.since = System.currentTimeMillis();
    }
}
