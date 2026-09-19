package org.telegram.vr.quest;

import java.util.Collections;
import java.util.Set;

/**
 * Who and what may interrupt, on this device only.
 *
 * An empty profile means silence for everyone, and that is the default a fresh install gets.
 */
public final class SilenceProfile {

    public static final SilenceProfile SILENT =
            new SilenceProfile(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    public final Set<Long> people;
    public final Set<Long> chats;
    public final Set<String> words;

    public SilenceProfile(Set<Long> people, Set<Long> chats, Set<String> words) {
        this.people = people == null ? Collections.emptySet() : people;
        this.chats = chats == null ? Collections.emptySet() : chats;
        this.words = words == null ? Collections.emptySet() : words;
    }

    public boolean isSilentForEveryone() {
        return people.isEmpty() && chats.isEmpty() && words.isEmpty();
    }

    public int size() {
        return people.size() + chats.size() + words.size();
    }
}
