package org.telegram.vr.quest.rooms;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import my.nicegram.vr.R;
import java.util.ArrayList;
import java.util.List;

/** Uses Telegram's native VoIPService, including its native muted-on-join behaviour.
 * No new media transport and no message-send method. Account and group are immutable. */
public final class RoomCallBridge implements NotificationCenter.NotificationCenterDelegate {
    public static final int PERMISSION_REQUEST = 206;
    /** How many call participants the room panel lists; the rest are counted. */
    public static final int LISTED = 8;

    /** One line of the call list: display name, and whether the person is heard right now. */
    public static final class Person {
        public final String name;
        public final boolean speaking, muted, self;
        Person(String name, boolean speaking, boolean muted, boolean self) { this.name = name; this.speaking = speaking; this.muted = muted; this.self = self; }
    }

    private final Activity activity;
    private final RoomSession room;
    private boolean waiting, disposed, foreground;
    private boolean microphoneArmed;
    /** The permission dialog takes focus, so its answer may arrive before focus returns. */
    private boolean connectOnResume;
    /** Full-chat data held while the group call itself is still loading. */
    private TLRPC.ChatFull awaitingCall;
    private final Runnable microphoneGuard = new Runnable() {
        public void run() {
            if (disposed) return;
            if (!foreground || !room.connected() || !room.validAccount()) microphoneArmed = false;
            // setMicMute may temporarily decline during native micSwitching, and is a no-op when the
            // state already matches. Retry while disarmed; recovery never arms the microphone.
            if (!microphoneArmed) mute(room);
            AndroidUtilities.runOnUIThread(this, 250);
        }
    };
    private int status;
    private final Runnable timeout = () -> { waiting = false; awaitingCall = null; status = R.string.vr_room_call_error; };
    public RoomCallBridge(Activity activity, RoomSession room) {
        this.activity = activity; this.room = room;
        NotificationCenter.getInstance(room.account).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(room.account).addObserver(this, NotificationCenter.groupCallUpdated);
        AndroidUtilities.runOnUIThread(microphoneGuard);
    }
    public static VoIPService call(RoomSession room) {
        VoIPService service = VoIPService.getSharedInstance();
        return service != null && service.getAccount() == room.account && service.getChat() != null && service.getChat().id == room.chatId ? service : null;
    }
    public int status() {
        VoIPService service = call(room);
        if (service != null && status == R.string.vr_room_listener) return status;
        if (service != null) return service.getCallState() == VoIPService.STATE_ESTABLISHED ? R.string.vr_room_call_ready : R.string.vr_room_call_connecting;
        return status;
    }
    public boolean busy() { return waiting; }
    public void connect() {
        if (waiting || disposed || !foreground || !room.connected()) return;
        if (!room.validAccount()) { status = R.string.vr_room_account; return; }
        if (call(room) != null) return;
        if (VoIPService.getSharedInstance() != null || VoIPService.callIShouldHavePutIntoIntent != null) { status = R.string.vr_room_other_call; return; }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = R.string.vr_room_mic_permission; activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST); return;
        }
        if (ConnectionsManager.getInstance(room.account).getConnectionState() != ConnectionsManager.ConnectionStateConnected) { status = R.string.vr_room_telegram_offline; return; }
        waiting = true; awaitingCall = null; status = R.string.vr_room_call_wait;
        AndroidUtilities.runOnUIThread(timeout, 15000);
        MessagesController.getInstance(room.account).loadFullChat(room.chatId, 0, true);
    }
    /** The permission dialog answered: continue the same press instead of asking for another. */
    public void onPermissionResult(boolean granted) {
        if (disposed) return;
        if (!granted) { connectOnResume = false; status = R.string.vr_room_mic_denied; return; }
        status = 0;
        if (foreground) connect(); else connectOnResume = true;
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!waiting || disposed || account != room.account) return;
        if (id == NotificationCenter.groupCallUpdated) {
            if (awaitingCall != null && args.length > 0 && args[0] instanceof Long && (Long) args[0] == room.chatId) decide(awaitingCall);
            return;
        }
        if (args.length == 0 || !(args[0] instanceof TLRPC.ChatFull)) return;
        TLRPC.ChatFull full = (TLRPC.ChatFull) args[0];
        if (full.id == room.chatId) decide(full);
    }
    private void decide(TLRPC.ChatFull full) {
        if (!room.connected() || !room.validAccount() || room.closed) { finishWaiting(); return; }
        MessagesController controller = MessagesController.getInstance(room.account);
        TLRPC.Chat chat = controller.getChat(room.chatId);
        ChatObject.Call known = controller.getGroupCall(room.chatId, false);
        boolean member = chat != null && !ChatObject.isNotInChat(chat) && (!ChatObject.isChannel(chat) || chat.megagroup);
        boolean nativeOnly = chat != null && (ChatObject.shouldSendAnonymously(chat)
                || (full.groupcall_default_join_as != null && full.groupcall_default_join_as.user_id != room.userId)
                || (known != null && (known.isScheduled() || known.call.rtmp_stream)));
        RoomCallPolicy.Action action = RoomCallPolicy.decide(member, VoIPService.getSharedInstance() != null,
                VoIPService.callIShouldHavePutIntoIntent != null,
                full.call != null, chat != null && ChatObject.canManageCalls(chat), nativeOnly, known != null && full.call != null && known.call.id == full.call.id);
        if (action == RoomCallPolicy.Action.WAIT) {
            // The call exists but its participants are not loaded yet: ask for it and keep waiting
            // for groupCallUpdated, inside the same timeout, instead of failing the press.
            if (awaitingCall == null) { awaitingCall = full; controller.getGroupCall(room.chatId, true); }
            return;
        }
        finishWaiting();
        switch (action) {
            case NOT_MEMBER: status = R.string.vr_room_wrong_chat; return;
            case OTHER_CALL: status = R.string.vr_room_other_call; return;
            case ADMIN_REQUIRED: status = R.string.vr_room_permissions; return;
            case NATIVE_UI: status = R.string.vr_room_native; return;
            default: break;
        }
        boolean creating = action == RoomCallPolicy.Action.CREATE;
        Intent intent = new Intent(activity, VoIPService.class);
        intent.putExtra("account", room.account); intent.putExtra("chat_id", room.chatId);
        intent.putExtra("createGroupCall", creating); intent.putExtra("is_outgoing", true);
        intent.putExtra("start_incall_activity", false); intent.putExtra("video_call", false);
        try { activity.startService(intent); status = R.string.vr_room_call_connecting; }
        catch (RuntimeException error) { status = R.string.vr_room_call_error; }
    }
    private void finishWaiting() { waiting = false; awaitingCall = null; AndroidUtilities.cancelRunOnUIThread(timeout); }
    public void toggleMicrophone() {
        VoIPService service = call(room);
        if (!foreground || service == null || service.getCallState() != VoIPService.STATE_ESTABLISHED || !room.connected() || !room.validAccount()) return;
        if (!service.isMicMute()) { microphoneArmed = false; status = 0; service.setMicMute(true, false, true); return; }
        TLRPC.GroupCallParticipant self = service.groupCall == null ? null : service.groupCall.participants.get(service.getSelfId());
        if (self == null || (self.muted && !self.can_self_unmute)) { status = R.string.vr_room_listener; return; }
        microphoneArmed = true; status = 0;
        service.setMicMute(false, false, true);
    }
    /** Who is in the Telegram call, speakers first; empty when this room has no call. */
    public List<Person> people() {
        List<Person> result = new ArrayList<>();
        VoIPService service = call(room);
        ChatObject.Call groupCall = service == null ? null : service.groupCall;
        if (groupCall == null) return result;
        MessagesController controller = MessagesController.getInstance(room.account);
        long self = service.getSelfId();
        for (TLRPC.GroupCallParticipant participant : groupCall.sortedParticipants) {
            long peer = MessageObject.getPeerId(participant.peer);
            String name;
            if (peer > 0) { TLRPC.User user = controller.getUser(peer); name = user == null ? null : UserObject.getUserName(user); }
            else { TLRPC.Chat chat = controller.getChat(-peer); name = chat == null ? null : chat.title; }
            if (name == null || name.trim().isEmpty()) continue;
            boolean speaking = groupCall.currentSpeakingPeers.get(peer, null) != null;
            boolean muted = peer == self ? service.isMicMute() : participant.muted;
            result.add(new Person(name.trim(), speaking && !muted, muted, peer == self));
        }
        java.util.Collections.sort(result, (a, b) -> Boolean.compare(b.speaking, a.speaking));
        return result;
    }
    public void resume() {
        foreground = true;
        if (connectOnResume) { connectOnResume = false; connect(); }
    }
    public void pause() { foreground = false; microphoneArmed = false; finishWaiting(); mute(room); }
    public static void mute(RoomSession room) { VoIPService service = call(room); if (service != null) service.setMicMute(true, false, true); }
    public static void leaveCall(RoomSession room) { VoIPService service = call(room); if (service != null) { service.setMicMute(true, false, true); service.hangUp(); } }
    public void dispose() {
        disposed = true; AndroidUtilities.cancelRunOnUIThread(microphoneGuard); pause();
        NotificationCenter.getInstance(room.account).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(room.account).removeObserver(this, NotificationCenter.groupCallUpdated);
    }
}
