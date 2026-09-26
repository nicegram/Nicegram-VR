package org.telegram.vr.quest.rooms;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.vr.quest.VrStrings;
import my.nicegram.vr.R;

/** The invitation is explicitly entered in the selected Telegram group, never auto-sent. */
public final class RoomLobbyActivity extends BaseFragment {
    private final long chatId;
    private EditText endpoint, creationKey, invite;
    private TextView status;
    private Button create, join, enter, copy;
    private boolean disposed, busy, launched;
    public RoomLobbyActivity(int account, long chatId) { setCurrentAccount(account); this.chatId = chatId; }
    private String text(int id) { return VrStrings.get(id); }
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.vr_room_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20); body.setPadding(pad, pad, pad, pad); scroll.addView(body);
        TextView notice = new TextView(context); notice.setText(text(R.string.vr_room_notice) + "\n\n" + text(R.string.vr_room_limit));
        notice.setTextSize(18); notice.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); body.addView(notice);
        endpoint = input(body, R.string.vr_room_endpoint, false);
        creationKey = input(body, R.string.vr_room_key, true);
        create = button(body, R.string.vr_room_create, () -> connect(true));
        invite = input(body, R.string.vr_room_invite, true);
        join = button(body, R.string.vr_room_join, () -> connect(false));
        status = new TextView(context); status.setTextSize(18); status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); body.addView(status);
        copy = button(body, R.string.vr_room_copy, () -> {
            RoomSession session = ownSession(); if (session == null) return;
            ClipData clip = ClipData.newPlainText(text(R.string.vr_room_invite), session.invitation);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
            status.setText(text(R.string.vr_room_copied));
        });
        enter = button(body, R.string.vr_room_enter, () -> {
            RoomSession session = ownSession(); if (session == null || !session.connected || !session.validAccount()) return;
            try { context.startActivity(new Intent(context, RoomSpatialActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); launched = true; }
            catch (RuntimeException error) { status.setText(text(R.string.vr_room_error)); }
        });
        button(body, R.string.vr_room_exit, () -> { RoomSession s = ownSession(); if (s != null) { RoomCallBridge.leaveCall(s); s.close(); } finishFragment(); });
        updateButtons(); fragmentView = scroll; return scroll;
    }
    private EditText input(LinearLayout body, int hint, boolean secret) {
        EditText field = new EditText(body.getContext()); field.setHint(text(hint)); field.setTextSize(18);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setSingleLine(true); field.setSaveEnabled(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        body.addView(field, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(64))); return field;
    }
    private Button button(LinearLayout body, int label, Runnable action) {
        Button button = new Button(body.getContext()); button.setText(text(label)); button.setAllCaps(false); button.setTextSize(18);
        button.setOnClickListener(view -> action.run()); body.addView(button, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(64))); return button;
    }
    private RoomSession ownSession() { RoomSession s = RoomSession.active; return s != null && !s.closed && s.account == currentAccount && s.chatId == chatId ? s : null; }
    private void updateButtons() {
        if (create == null) return;
        boolean has = ownSession() != null;
        create.setEnabled(!busy && !has); join.setEnabled(!busy && !has);
        copy.setVisibility(has ? View.VISIBLE : View.GONE); enter.setVisibility(has ? View.VISIBLE : View.GONE);
    }
    private void connect(boolean creating) {
        if (busy) return;
        if (RoomSession.active != null || !UserConfig.getInstance(currentAccount).isClientActivated()) { status.setText(text(R.string.vr_room_account)); return; }
        org.telegram.tgnet.TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null || ChatObject.isNotInChat(chat)) { status.setText(text(R.string.vr_room_wrong_chat)); return; }
        final String chatKey = (ChatObject.isChannel(chat) ? "channel:" : "chat:") + chatId;
        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        final String name = UserObject.getFirstName(UserConfig.getInstance(currentAccount).getCurrentUser());
        final String service = endpoint.getText().toString(), key = creationKey.getText().toString(), invitation = invite.getText().toString();
        creationKey.setText(""); busy = true; status.setText(text(R.string.vr_room_wait)); updateButtons();
        new Thread(() -> {
            RoomSession session = null; String failure = null;
            try {
                String base, inviteToken;
                JSONObject result;
                JSONObject body = new JSONObject().put("chatKey", chatKey).put("name", name);
                if (creating) {
                    base = RoomApi.endpoint(service); result = RoomApi.post(base, "/v1/rooms", key, body); inviteToken = result.getString("inviteToken");
                } else {
                    String[] parsed = RoomApi.invitation(invitation); base = parsed[0]; inviteToken = parsed[2];
                    result = RoomApi.post(base, "/v1/rooms/" + parsed[1] + "/join", "", body.put("inviteToken", inviteToken));
                }
                if (!chatKey.equals(result.getString("chatKey"))) throw new Exception("WRONG_CHAT");
                session = new RoomSession(currentAccount, chatId, base, result, inviteToken);
            } catch (Exception error) { failure = error.getMessage(); }
            RoomSession ready = session; String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (disposed || UserConfig.getInstance(currentAccount).getClientUserId() != userId || RoomSession.active != null) { if (ready != null) ready.close(); return; }
                if (ready != null) { RoomSession.active = ready; ready.start(); invite.setText(""); status.setText(text(R.string.vr_room_connected)); }
                else { int message = "WRONG_CHAT".equals(code) ? R.string.vr_room_wrong_chat : "ROOM_FULL".equals(code) ? R.string.vr_room_full : "ROOM_EXPIRED".equals(code) || "SESSION_EXPIRED".equals(code) ? R.string.vr_room_expired : R.string.vr_room_error; status.setText(text(message)); }
                updateButtons();
            });
        }, "vr-room-connect").start();
    }
    @Override public void onResume() { super.onResume(); updateButtons(); }
    @Override public void onFragmentDestroy() {
        disposed = true;
        if (!launched) { RoomSession s = ownSession(); if (s != null) s.close(); }
        super.onFragmentDestroy();
    }
}
