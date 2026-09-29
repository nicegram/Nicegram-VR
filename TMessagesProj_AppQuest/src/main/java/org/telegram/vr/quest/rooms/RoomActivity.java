package org.telegram.vr.quest.rooms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;
import org.telegram.vr.quest.VrStrings;
import my.nicegram.vr.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The room: its own Horizon OS panel. The group's Telegram chat on the left, the people in
 * the room and the Telegram call on the right.
 *
 * <p>Until 29 September this was an immersive Meta Spatial SDK scene. That SDK's licence
 * (§1.2.8) is not compatible with this GPL client, and all it contributed was a dark floor
 * around two panels; everything a room is — chat, call, presence — is ordinary Android UI. A
 * panel activity carries no proprietary dependency, so the APK is GPL throughout again.
 *
 * <p>The chat host follows upstream {@code BubbleActivity}, the one other host of a
 * single-dialog {@link ChatActivity}, minus its {@code closeOtherAppActivities} broadcast (it
 * would finish the 2D LaunchActivity) and its {@code closeChats} broadcast (it would close the
 * same group there). {@code checkDisplaySize} and {@code fillStatusBarHeight} are not called:
 * they write process-wide statics the messenger panel depends on.
 *
 * <p>Microphone: disarmed whenever this panel is paused (hidden, closed, headset off) or the
 * room link is lost; never armed by recovery. Using another panel of the same app while this
 * one stays visible keeps the call as it is — reading other chats during a call is the point. */
public final class RoomActivity extends BasePermissionsActivity implements INavigationLayout.INavigationLayoutDelegate {
    private static final long TICK = 500;
    private final ArrayList<BaseFragment> stack = new ArrayList<>();
    private final Map<Integer, String> shown = new HashMap<>();
    private INavigationLayout layout;
    private RoomSession room;
    private RoomCallBridge calls;
    private View panel;
    private boolean resumed;
    private long dialogId;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            RoomSession session = room;
            if (session == null) return;
            if (session.closed || !session.validAccount()) { leave(); return; }
            if (!session.connected()) calls.pause(); else if (resumed) calls.resume();
            render();
            if (resumed) AndroidUtilities.runOnUIThread(this, TICK);
        }
    };

    public static Intent intent(Context context, RoomSession room) {
        return new Intent(context, RoomActivity.class).putExtra("account", room.account).putExtra("chatId", room.chatId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        RoomSession session = RoomSession.active;
        int account = getIntent().getIntExtra("account", -1);
        long chatId = getIntent().getLongExtra("chatId", 0);
        // Only the active room's own group, on its own account, is ever shown here.
        if (session == null || session.closed || session.account != account || session.chatId != chatId || !session.validAccount()) { finish(); return; }
        if (!SharedConfig.passcodeHash.isEmpty() && (SharedConfig.appLocked || AndroidUtilities.needShowPasscode(true))) {
            // A locked app shows no messages here either; unlocking happens in the messenger panel.
            setContentView(notice(VrStrings.get(R.string.vr_room_chat_locked)));
            return;
        }
        room = session;
        calls = new RoomCallBridge(this, session);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(chat(session), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        panel = getLayoutInflater().inflate(R.layout.vr_room_panel, root, false);
        root.addView(panel, new LinearLayout.LayoutParams(AndroidUtilities.dp(PANEL_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        panel.findViewById(R.id.room_call).setOnClickListener(v -> { calls.connect(); render(); });
        panel.findViewById(R.id.room_mic).setOnClickListener(v -> { calls.toggleMicrophone(); render(); });
        panel.findViewById(R.id.room_exit).setOnClickListener(v -> leave());
        render();
    }

    /** Width of the room column; the chat takes the rest of the panel. */
    private static final int PANEL_DP = 360;

    private View chat(RoomSession session) {
        layout = INavigationLayout.newLayout(this, false);
        layout.setInBubbleMode(true);
        layout.setRemoveActionBarExtraHeight(true);
        DrawerLayoutContainer drawer = new DrawerLayoutContainer(this);
        FrameLayout container = new FrameLayout(this);
        drawer.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(layout.getView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        drawer.setParentActionBarLayout(layout);
        layout.setDrawerLayoutContainer(drawer);
        layout.setFragmentStack(stack);
        layout.setDelegate(this);
        Bundle args = new Bundle();
        args.putLong("chat_id", session.chatId);
        ChatActivity chat = new ChatActivity(args);
        chat.setInBubbleMode(true);
        chat.setCurrentAccount(session.account);
        dialogId = -session.chatId;
        layout.addFragmentToStack(chat);
        layout.showLastFragment();
        // The chat is on screen in the room: no notification for it while the room is open.
        AccountInstance.getInstance(session.account).getNotificationsController().setOpenedInBubble(dialogId, true);
        return drawer;
    }

    private TextView notice(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(22); view.setGravity(Gravity.CENTER);
        int pad = AndroidUtilities.dp(32); view.setPadding(pad, pad, pad, pad);
        view.setTextColor(0xFFE8ECF3); view.setBackgroundColor(0xFF161B24);
        return view;
    }

    private String label(int id) { return VrStrings.get(id); }

    /** Writes a view only when its text changed: the column is refreshed twice a second. */
    private void put(int id, String value) {
        if (value.equals(shown.get(id))) return;
        shown.put(id, value);
        ((TextView) panel.findViewById(id)).setText(value);
    }

    private void render() {
        if (panel == null || room == null) return;
        RoomSession session = room;
        put(R.id.room_title, label(R.string.vr_room_title));
        put(R.id.room_notice, label(R.string.vr_room_notice));
        RoomConnection.State state = session.state();
        put(R.id.room_status, state == RoomConnection.State.CONNECTED ? label(R.string.vr_room_connected)
                : state == RoomConnection.State.RECONNECTING ? label(R.string.vr_room_disconnected) : label(RoomMessages.failure(session.endCode())));
        JSONArray members = session.snapshot.optJSONArray("participants");
        int count = members == null ? 0 : members.length();
        put(R.id.room_people_title, String.format(label(R.string.vr_room_people_title), count));
        StringBuilder people = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) people.append('\n');
            people.append("• ").append(members.optJSONObject(i) == null ? "" : members.optJSONObject(i).optString("name"));
        }
        put(R.id.room_people, people.toString());
        VoIPService service = RoomCallBridge.call(session);
        List<RoomCallBridge.Person> inCall = calls.people();
        put(R.id.room_call_title, service == null ? label(R.string.vr_room_call_none) : String.format(label(R.string.vr_room_call_title), inCall.size()));
        StringBuilder call = new StringBuilder();
        for (int i = 0; i < inCall.size() && i < RoomCallBridge.LISTED; i++) {
            RoomCallBridge.Person p = inCall.get(i);
            if (i > 0) call.append('\n');
            call.append(p.speaking ? "🔊 " : p.muted ? "🔇 " : "🎙 ").append(p.name).append(p.self ? label(R.string.vr_room_you) : "");
        }
        if (inCall.size() > RoomCallBridge.LISTED) call.append('\n').append(String.format(label(R.string.vr_room_more), inCall.size() - RoomCallBridge.LISTED));
        put(R.id.room_call_people, call.toString());
        int status = calls.status();
        put(R.id.room_call_status, status == 0 ? "" : label(status));
        Button callButton = panel.findViewById(R.id.room_call);
        put(R.id.room_call, label(R.string.vr_room_call));
        callButton.setVisibility(service == null ? View.VISIBLE : View.GONE);
        callButton.setEnabled(session.connected() && !calls.busy());
        Button mic = panel.findViewById(R.id.room_mic);
        put(R.id.room_mic, label(service != null && !service.isMicMute() ? R.string.vr_room_mic_off : R.string.vr_room_mic_on));
        mic.setVisibility(service != null ? View.VISIBLE : View.GONE);
        mic.setEnabled(resumed && session.connected() && service != null && service.getCallState() == VoIPService.STATE_ESTABLISHED);
        put(R.id.room_exit, label(R.string.vr_room_exit));
    }

    /** The explicit leave: this person's call participation and room presence end, panel closes. */
    private void leave() {
        RoomSession session = room;
        if (session != null) { RoomCallBridge.leaveCall(session); session.close(); }
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (layout != null) {
            layout.onResume();
            ApplicationLoader.externalInterfacePaused = false;
            AccountInstance.getInstance(room.account).getConnectionsManager().setAppPaused(false, false);
        }
        if (calls != null) { calls.resume(); AndroidUtilities.cancelRunOnUIThread(tick); AndroidUtilities.runOnUIThread(tick); }
    }

    @Override protected void onPause() {
        resumed = false;
        AndroidUtilities.cancelRunOnUIThread(tick);
        if (calls != null) calls.pause();
        if (layout != null) { layout.onPause(); ApplicationLoader.externalInterfacePaused = true; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        AndroidUtilities.cancelRunOnUIThread(tick);
        if (calls != null) calls.dispose();
        if (room != null) {
            AccountInstance.getInstance(room.account).getNotificationsController().setOpenedInBubble(dialogId, false);
            // Closing the room panel is leaving the room; the lobby can start a new one.
            if (isFinishing()) { RoomCallBridge.leaveCall(room); room.close(); }
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BaseFragment top = top();
        if (top != null) top.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RoomCallBridge.PERMISSION_REQUEST) {
            if (calls != null) calls.onPermissionResult(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
            render();
            return;
        }
        if (!checkPermissionsResult(requestCode, permissions, grantResults)) return;
        BaseFragment top = top();
        if (top != null) top.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
    }

    @Override public void onBackPressed() {
        if (layout == null) { super.onBackPressed(); return; }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) { PhotoViewer.getInstance().closePhoto(true, false); return; }
        if (stack.size() > 1) { layout.onBackPressed(); return; }
        // Back at the group chat never ends the call by accident: the panel goes to the
        // background (the microphone is disarmed by onPause) and Leave stays the only exit.
        moveTaskToBack(true);
    }

    @Override public void onLowMemory() { super.onLowMemory(); if (layout != null) layout.onLowMemory(); }

    @Override public boolean needCloseLastFragment(INavigationLayout navigation) { return navigation.getFragmentStack().size() > 1; }

    private BaseFragment top() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
}
