package org.telegram.vr.quest.rooms;

/** Presence link state, fed by heartbeat results. Pure: time comes in, nothing is scheduled.
 *
 * <p>One lost heartbeat used to mark the room disconnected and disarm the microphone, and a room
 * the server had already ended was polled every three seconds for ever. A link now degrades only
 * after {@link #GRACE} without a success, and a terminal answer ends it. */
public final class RoomConnection {
    public enum State { CONNECTED, RECONNECTING, ENDED }

    /** Longer than one heartbeat interval plus one read timeout; well inside the 30 s lease. */
    public static final long GRACE = 12000;

    private long lastSuccess;
    private String endCode;

    public RoomConnection(long now) { lastSuccess = now; }

    /** Answers after which retrying the same session can never succeed. */
    public static boolean terminal(String code) {
        return "ROOM_EXPIRED".equals(code) || "NICEGRAM_AUTH_REQUIRED".equals(code)
                || "NICEGRAM_ACCOUNT_REQUIRED".equals(code) || "UNAUTHORIZED".equals(code)
                || "WRONG_CHAT".equals(code) || "ROOM_FULL".equals(code) || "ACCOUNT_CHANGED".equals(code)
                || "LEFT".equals(code);
    }

    /** The lease lapsed but the room may still exist: joining again with the invitation revives it. */
    public static boolean rejoinable(String code) { return "SESSION_EXPIRED".equals(code); }

    public synchronized void success(long now) { if (endCode == null) lastSuccess = Math.max(lastSuccess, now); }

    public synchronized void failure(String code, long now) { if (endCode == null && terminal(code)) endCode = code; }

    public synchronized void end(String code) { if (endCode == null) endCode = code; }

    public synchronized State state(long now) {
        if (endCode != null) return State.ENDED;
        return now - lastSuccess < GRACE ? State.CONNECTED : State.RECONNECTING;
    }

    public synchronized String endCode() { return endCode; }
}
