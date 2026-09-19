package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Nicegram VR — where the exceptions live.
 *
 * Deliberately a private {@link SharedPreferences} file of this build and nothing else.
 *
 * It must never become a call to {@code account.updateNotifySettings}: that setting is
 * account-wide and synchronised, so writing it here would mute the user's phone as well.
 * Upstream's own {@code NotificationsController.muteUntil} does exactly that, which is why
 * this build does not use it and why the acceptance check for the feature is performed on a
 * second device.
 */
public final class SilenceStore {

    private static final String FILE = "nicegram_vr_silence";
    private static final String KEY_PEOPLE = "people";
    private static final String KEY_CHATS = "chats";
    private static final String KEY_WORDS = "words";

    /**
     * Bumped on every write. A reader that cached a profile compares this against what it saw
     * and re-reads when it moved. Without it the gate keeps deciding by a profile the user has
     * already changed, and an exception added in settings silently does nothing until the
     * process restarts — which is the kind of bug that gets reported as "it does not work".
     */
    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
            new java.util.concurrent.atomic.AtomicInteger();

    private final SharedPreferences prefs;

    public SilenceStore(Context context) {
        this.prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private String key(String base, int account) {
        return base + "_" + account;
    }

    /** Never null. A missing profile is silence for everyone, not an error. */
    public SilenceProfile load(int account) {
        return new SilenceProfile(
                readIds(key(KEY_PEOPLE, account)),
                readIds(key(KEY_CHATS, account)),
                readWords(key(KEY_WORDS, account)));
    }

    public static int generation() {
        return GENERATION.get();
    }

    /** Visible for the concurrency test; the counter is otherwise only moved by {@link #save}. */
    static void bumpGenerationForTest() {
        GENERATION.incrementAndGet();
    }

    public void save(int account, SilenceProfile profile) {
        GENERATION.incrementAndGet();
        prefs.edit()
                .putStringSet(key(KEY_PEOPLE, account), toStrings(profile.people))
                .putStringSet(key(KEY_CHATS, account), toStrings(profile.chats))
                .putStringSet(key(KEY_WORDS, account), new HashSet<>(profile.words))
                .apply();
    }

    private Set<Long> readIds(String key) {
        Set<String> raw = prefs.getStringSet(key, Collections.emptySet());
        Set<Long> out = new LinkedHashSet<>();
        for (String s : raw) {
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // A corrupt entry drops out rather than taking the whole profile with it.
            }
        }
        return out;
    }

    private Set<String> readWords(String key) {
        Set<String> raw = prefs.getStringSet(key, Collections.emptySet());
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (s != null && !s.trim().isEmpty()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private Set<String> toStrings(Set<Long> ids) {
        Set<String> out = new LinkedHashSet<>();
        for (Long id : ids) {
            out.add(String.valueOf(id));
        }
        return out;
    }
}
