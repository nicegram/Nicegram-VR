package org.telegram.vr.quest.rooms;

import my.nicegram.vr.R;

/** Server and local failure codes to the sentence a person reads. One table, tested, so a new
 * code cannot silently fall through to "could not connect" in one screen and not another. */
public final class RoomMessages {
    public static int failure(String code) {
        if (code == null) return R.string.vr_room_error;
        switch (code) {
            case "NICEGRAM_AUTH_REQUIRED": case "AUTH_ENDPOINT_MISMATCH": return R.string.vr_room_auth_required;
            case "NICEGRAM_CONFIRM_REQUIRED": return R.string.vr_room_auth_confirm;
            case "AUTH_EXPIRED": return R.string.vr_room_auth_expired;
            case "ACCOUNT_MISMATCH": return R.string.vr_room_auth_mismatch;
            case "NICEGRAM_NOT_CONFIGURED": case "NICEGRAM_UNAVAILABLE": return R.string.vr_room_auth_unavailable;
            case "NICEGRAM_ACCOUNT_REQUIRED": return R.string.vr_room_auth_account_missing;
            case "WRONG_CHAT": return R.string.vr_room_wrong_chat;
            case "ROOM_FULL": return R.string.vr_room_full;
            case "ROOM_EXPIRED": case "SESSION_EXPIRED": case "UNAUTHORIZED": case "INVALID_INVITE": return R.string.vr_room_expired;
            case "ROOM_LIMIT": case "USER_ROOM_LIMIT": return R.string.vr_room_limit_reached;
            case "RATE_LIMITED": return R.string.vr_room_rate_limited;
            case "NETWORK": return R.string.vr_room_network;
            case "ACCOUNT_CHANGED": return R.string.vr_room_account;
            case "ALREADY_IN_ROOM": return R.string.vr_room_already_active;
            case "LEFT": return R.string.vr_room_left;
            default: return R.string.vr_room_error;
        }
    }
    private RoomMessages() {}
}
