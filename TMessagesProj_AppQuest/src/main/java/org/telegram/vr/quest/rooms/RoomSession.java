package org.telegram.vr.quest.rooms;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One local room, one account; network work is serial and never blocks the UI. */
public final class RoomSession {
    public static volatile RoomSession active;
    public final int account;
    public final long chatId, userId;
    public final String endpoint, roomId, sessionId, secret, invitation;
    public volatile JSONObject snapshot;
    public volatile boolean connected = true, closed;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Runnable pulse = () -> heartbeat();
    public RoomSession(int account, long chatId, String endpoint, JSONObject result, String invite) throws Exception {
        this.account = account; this.chatId = chatId; this.endpoint = endpoint;
        userId = UserConfig.getInstance(account).getClientUserId();
        roomId = result.getString("roomId"); sessionId = result.getString("sessionId"); secret = result.getString("sessionToken");
        if (!roomId.matches("[A-Za-z0-9_-]{43}") || !sessionId.matches("[A-Za-z0-9_-]{43}") || !secret.matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_SESSION");
        invitation = endpoint + "/room#" + roomId + "." + invite;
        snapshot = result;
    }
    public boolean validAccount() {
        return UserConfig.getInstance(account).isClientActivated() && UserConfig.getInstance(account).getClientUserId() == userId;
    }
    public void start() { heartbeat(); }
    private void heartbeat() {
        if (closed) return;
        if (!validAccount()) { close(); return; }
        network.execute(() -> {
            try {
                JSONObject next = RoomApi.post(endpoint, "/v1/rooms/" + roomId + "/heartbeat", secret, new JSONObject().put("sessionId", sessionId));
                if (!closed) { snapshot = next; connected = true; }
            } catch (Exception error) { connected = false; }
            AndroidUtilities.runOnUIThread(() -> { if (!closed) AndroidUtilities.runOnUIThread(pulse, 3000); });
        });
    }
    public void close() {
        if (closed) return;
        closed = true; connected = false;
        AndroidUtilities.cancelRunOnUIThread(pulse);
        if (active == this) active = null;
        network.execute(() -> { try { RoomApi.post(endpoint, "/v1/rooms/" + roomId + "/leave", secret, new JSONObject().put("sessionId", sessionId)); } catch (Exception ignored) {} });
        network.shutdown();
    }
}
