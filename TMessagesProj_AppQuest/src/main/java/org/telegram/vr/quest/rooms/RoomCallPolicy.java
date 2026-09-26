package org.telegram.vr.quest.rooms;

/** Decisions depend on a freshly loaded Telegram group; presence is never authority. */
public final class RoomCallPolicy {
    public enum Action { CREATE, JOIN, NOT_MEMBER, ADMIN_REQUIRED, NATIVE_UI, OTHER_CALL, WAIT }
    public static Action decide(boolean member, boolean otherCall, boolean pendingCall, boolean hasCall,
                                boolean canManage, boolean nativeOnly, boolean callLoaded) {
        if (!member) return Action.NOT_MEMBER;
        // An incoming call may be pending before VoIPService has an instance.
        if (otherCall || pendingCall) return Action.OTHER_CALL;
        if (nativeOnly) return Action.NATIVE_UI;
        if (hasCall) return callLoaded ? Action.JOIN : Action.WAIT;
        return canManage ? Action.CREATE : Action.ADMIN_REQUIRED;
    }
    private RoomCallPolicy() {}
}
