package org.telegram.vr.quest.rooms;

import android.os.SystemClock;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One local room, one account; network work is serial and never blocks the UI.
 *
 * <p>The session outlives the lobby screen: a creator who goes back to the group to post the
 * invitation stays in the room. It ends on an explicit leave, an account change, or a terminal
 * server answer, and a lapsed lease is revived once by joining again with the same invitation. */
public final class RoomSession {
    /** UI-thread listeners, told whenever the snapshot or the link state of any session changes. */
    private static final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void listen(Runnable listener) { listeners.add(listener); }
    public static void unlisten(Runnable listener) { listeners.remove(listener); }
    public static final long INTERVAL = 3000;

    public static volatile RoomSession active;
    public final int account;
    public final long chatId, userId;
    public final String endpoint, roomId, invitation, chatKey;
    private final String inviteToken, identityToken;
    private volatile String sessionId, secret;
    public volatile JSONObject snapshot;
    public volatile boolean closed;
    private final RoomConnection link = new RoomConnection(SystemClock.elapsedRealtime());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Runnable pulse = this::heartbeat;
    private long lastRejoin;

    public RoomSession(int account, long chatId, String endpoint, JSONObject result, String invite, String identityToken) throws Exception {
        this.account = account; this.chatId = chatId; this.endpoint = endpoint; this.identityToken = identityToken;
        userId = UserConfig.getInstance(account).getClientUserId();
        roomId = result.getString("roomId"); chatKey = result.getString("chatKey");
        adopt(result);
        if (!roomId.matches("[A-Za-z0-9_-]{43}") || invite == null || !invite.matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_SESSION");
        inviteToken = invite;
        invitation = endpoint + "/room#" + roomId + "." + invite;
    }
    private void adopt(JSONObject result) throws Exception {
        String id = result.getString("sessionId"), token = result.getString("sessionToken");
        if (!id.matches("[A-Za-z0-9_-]{43}") || !token.matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_SESSION");
        sessionId = id; secret = token; snapshot = result;
    }
    public boolean validAccount() {
        return UserConfig.getInstance(account).isClientActivated() && UserConfig.getInstance(account).getClientUserId() == userId;
    }
    /** Presence is live: the microphone may be armed only in this state. */
    public boolean connected() { return !closed && link.state(SystemClock.elapsedRealtime()) == RoomConnection.State.CONNECTED; }
    public RoomConnection.State state() { return link.state(SystemClock.elapsedRealtime()); }
    /** Why the room ended, as a server or local code; null while it is running. */
    public String endCode() { return link.endCode(); }
    public void start() { heartbeat(); }
    private void heartbeat() {
        if (closed) return;
        if (!validAccount()) { end("ACCOUNT_CHANGED"); return; }
        network.execute(() -> {
            // A beat queued before close() must not run: its SESSION_EXPIRED would rejoin a room
            // the person just left.
            if (closed) return;
            String failure = null;
            try {
                JSONObject next = RoomApi.post(endpoint, "/v1/rooms/" + roomId + "/heartbeat", secret, new JSONObject().put("sessionId", sessionId));
                if (!closed) { snapshot = next; link.success(SystemClock.elapsedRealtime()); }
            } catch (Exception error) { failure = error.getMessage(); }
            if (closed) return;
            if (failure != null && RoomConnection.rejoinable(failure)) failure = rejoin();
            if (failure != null) link.failure(failure, SystemClock.elapsedRealtime());
            final boolean ended = link.endCode() != null;
            AndroidUtilities.runOnUIThread(() -> {
                if (ended) { if (!closed) end(link.endCode()); return; }
                changed();
                if (!closed) AndroidUtilities.runOnUIThread(pulse, INTERVAL);
            });
        });
    }
    /** Runs on the network thread. Returns null on success, else the code that stopped it. */
    private String rejoin() {
        long now = SystemClock.elapsedRealtime();
        if (closed || now - lastRejoin < 10000) return null;
        lastRejoin = now;
        try {
            JSONObject result = RoomApi.post(endpoint, "/v1/rooms/" + roomId + "/join", identityToken,
                    new JSONObject().put("chatKey", chatKey).put("inviteToken", inviteToken));
            if (!chatKey.equals(result.optString("chatKey"))) return "WRONG_CHAT";
            if (!closed) { adopt(result); link.success(SystemClock.elapsedRealtime()); }
            return null;
        } catch (Exception error) {
            String code = error.getMessage();
            if ("NICEGRAM_AUTH_REQUIRED".equals(code) || "NICEGRAM_ACCOUNT_REQUIRED".equals(code)) RoomIdentityStore.forget(account);
            return code;
        }
    }
    private void changed() { for (Runnable listener : listeners) listener.run(); }
    private void end(String code) {
        link.end(code);
        close();
    }
    /** Explicit leave: tells the server, stops the heartbeat. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        link.end("LEFT");
        AndroidUtilities.cancelRunOnUIThread(pulse);
        if (active == this) active = null;
        final String id = sessionId, token = secret;
        network.execute(() -> { try { RoomApi.post(endpoint, "/v1/rooms/" + roomId + "/leave", token, new JSONObject().put("sessionId", id)); } catch (Exception ignored) {} });
        network.shutdown();
        changed();
    }
}
