package org.telegram.vr.quest.rooms;

import java.util.HashMap;
import java.util.Map;

/** Nicegram room identity for this process, keyed by Telegram account slot and user.
 * Memory only: the token is a bearer capability and dies with the process. Before this store
 * the token lived in the lobby screen, so every reopening sent the user back to the bot. */
public final class RoomIdentityStore {
    /** Kept back from the server expiry so a request never leaves with a token about to die. */
    static final long MARGIN = 60000;

    public static final class Identity {
        public final String gateway, token;
        public final long userId, expiresAt;
        Identity(String gateway, String token, long userId, long expiresAt) {
            this.gateway = gateway; this.token = token; this.userId = userId; this.expiresAt = expiresAt;
        }
    }

    public static final class Challenge {
        public final String gateway, token, loginUrl;
        public final long userId, expiresAt;
        Challenge(String gateway, String token, String loginUrl, long userId, long expiresAt) {
            this.gateway = gateway; this.token = token; this.loginUrl = loginUrl; this.userId = userId; this.expiresAt = expiresAt;
        }
    }

    private static final Map<Integer, Identity> identities = new HashMap<>();
    private static final Map<Integer, Challenge> challenges = new HashMap<>();

    public static synchronized Identity identity(int account, long userId, String gateway, long now) {
        Identity row = identities.get(account);
        if (row == null) return null;
        if (row.userId != userId || !row.gateway.equals(gateway) || now >= row.expiresAt - MARGIN) {
            identities.remove(account);
            return null;
        }
        return row;
    }

    public static synchronized void putIdentity(int account, long userId, String gateway, String token, long expiresAt) {
        identities.put(account, new Identity(gateway, token, userId, expiresAt));
        challenges.remove(account);
    }

    public static synchronized Challenge challenge(int account, long userId, String gateway, long now) {
        Challenge row = challenges.get(account);
        if (row == null) return null;
        if (row.userId != userId || !row.gateway.equals(gateway) || now >= row.expiresAt) {
            challenges.remove(account);
            return null;
        }
        return row;
    }

    public static synchronized void putChallenge(int account, long userId, String gateway, String token, String loginUrl, long expiresAt) {
        challenges.put(account, new Challenge(gateway, token, loginUrl, userId, expiresAt));
    }

    /** The server rejected this identity: forget it so the next action starts a fresh check. */
    public static synchronized void forget(int account) {
        identities.remove(account);
        challenges.remove(account);
    }

    public static synchronized void forgetChallenge(int account) { challenges.remove(account); }

    static synchronized void clearForTests() { identities.clear(); challenges.clear(); }

    private RoomIdentityStore() {}
}
