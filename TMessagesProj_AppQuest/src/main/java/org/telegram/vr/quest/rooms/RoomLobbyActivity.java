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
    private EditText invite;
    private String identityToken, challengeToken, authEndpoint;
    private long authUserId;
    private Button authStart, authComplete;
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
        authStart = button(body, R.string.vr_room_auth_start, () -> authenticate(false));
        authComplete = button(body, R.string.vr_room_auth_complete, () -> authenticate(true));
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
        create.setEnabled(!busy && !has && identityToken != null); join.setEnabled(!busy && !has && identityToken != null);
        authStart.setEnabled(!busy && !has); authComplete.setEnabled(!busy && !has && challengeToken != null);
        copy.setVisibility(has ? View.VISIBLE : View.GONE); enter.setVisibility(has ? View.VISIBLE : View.GONE);
    }
    private void connect(boolean creating) {
        if (busy) return;
        if (identityToken == null || UserConfig.getInstance(currentAccount).getClientUserId() != authUserId) { status.setText(text(R.string.vr_room_auth_required)); return; }
        if (RoomSession.active != null || !UserConfig.getInstance(currentAccount).isClientActivated()) { status.setText(text(R.string.vr_room_account)); return; }
        org.telegram.tgnet.TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null || ChatObject.isNotInChat(chat)) { status.setText(text(R.string.vr_room_wrong_chat)); return; }
        final String chatKey = (ChatObject.isChannel(chat) ? "channel:" : "chat:") + chatId;
        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        final String name = UserObject.getFirstName(UserConfig.getInstance(currentAccount).getCurrentUser());
        final String service = authEndpoint, key = identityToken, invitation = invite.getText().toString();
        busy = true; status.setText(text(R.string.vr_room_wait)); updateButtons();
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
                    if (!base.equals(authEndpoint)) throw new Exception("AUTH_ENDPOINT_MISMATCH");
                    result = RoomApi.post(base, "/v1/rooms/" + parsed[1] + "/join", key, body.put("inviteToken", inviteToken));
                }
                if (!chatKey.equals(result.getString("chatKey"))) throw new Exception("WRONG_CHAT");
                session = new RoomSession(currentAccount, chatId, base, result, inviteToken);
            } catch (Exception error) { failure = error.getMessage(); }
            RoomSession ready = session; String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (disposed || UserConfig.getInstance(currentAccount).getClientUserId() != userId || RoomSession.active != null) { if (ready != null) ready.close(); return; }
                if (ready != null) { RoomSession.active = ready; ready.start(); invite.setText(""); status.setText(text(R.string.vr_room_connected)); }
                else { if ("NICEGRAM_AUTH_REQUIRED".equals(code)) identityToken = null;
                    int message = "NICEGRAM_AUTH_REQUIRED".equals(code) || "AUTH_ENDPOINT_MISMATCH".equals(code) ? R.string.vr_room_auth_required : "WRONG_CHAT".equals(code) ? R.string.vr_room_wrong_chat : "ROOM_FULL".equals(code) ? R.string.vr_room_full : "ROOM_EXPIRED".equals(code) || "SESSION_EXPIRED".equals(code) ? R.string.vr_room_expired : R.string.vr_room_error; status.setText(text(message)); }
                updateButtons();
            });
        }, "vr-room-connect").start();
    }
    private void authenticate(boolean completing) {
        if (busy || RoomSession.active != null) return;
        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (userId == 0) return;
        final String base;
        try {
            base = completing ? authEndpoint : RoomApi.GATEWAY;
            if (base == null || (completing && (challengeToken == null || authUserId != userId))) throw new Exception();
        } catch (Exception error) { status.setText(text(R.string.vr_room_auth_required)); return; }
        final String challenge = challengeToken;
        busy = true; identityToken = null; status.setText(text(R.string.vr_room_wait)); updateButtons();
        new Thread(() -> {
            JSONObject result = null; String failure = null;
            try {
                result = RoomApi.post(base, completing ? "/v1/auth/complete" : "/v1/auth/start", completing ? challenge : "",
                        completing ? new JSONObject() : new JSONObject().put("telegramId", Long.toString(userId)));
                if (!RoomAuthProtocol.sameAccount(userId, result.getString("telegramId"))) throw new Exception("ACCOUNT_MISMATCH");
                String credential = result.getString(completing ? "identityToken" : "challengeToken");
                if (!credential.matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_AUTH");
                if (!completing) RoomAuthProtocol.loginUrl(result.getString("loginUrl"));
            } catch (Exception error) { failure = error.getMessage(); }
            JSONObject ready = result; String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (disposed || UserConfig.getInstance(currentAccount).getClientUserId() != userId) return;
                if (code == null && ready != null) {
                    authEndpoint = base; authUserId = userId;
                    if (completing) { identityToken = ready.optString("identityToken"); challengeToken = null; status.setText(text(R.string.vr_room_auth_verified)); }
                    else {
                        challengeToken = ready.optString("challengeToken"); status.setText(text(R.string.vr_room_auth_confirm));
                        org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), ready.optString("loginUrl"));
                    }
                } else {
                    int message = "NICEGRAM_CONFIRM_REQUIRED".equals(code) ? R.string.vr_room_auth_confirm
                            : "NICEGRAM_NOT_CONFIGURED".equals(code) || "NICEGRAM_UNAVAILABLE".equals(code) ? R.string.vr_room_auth_unavailable
                            : "NICEGRAM_ACCOUNT_REQUIRED".equals(code) ? R.string.vr_room_auth_account_missing : R.string.vr_room_auth_required;
                    status.setText(text(message));
                }
                updateButtons();
            });
        }, "vr-nicegram-auth").start();
    }
    @Override public void onResume() { super.onResume(); updateButtons(); }
    @Override public void onFragmentDestroy() {
        disposed = true;
        if (!launched) { RoomSession s = ownSession(); if (s != null) s.close(); }
        super.onFragmentDestroy();
    }
}
