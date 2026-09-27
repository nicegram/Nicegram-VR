package org.telegram.vr.quest.rooms;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
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
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.vr.quest.VrStrings;
import my.nicegram.vr.R;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A group's VR room: verify Nicegram once, create or join, share the invitation, enter.
 *
 * <p>The invitation is sent to the group only by an explicit press. Verification is remembered
 * for the process ({@link RoomIdentityStore}); the bot confirmation is picked up automatically
 * when the person comes back from the bot. A tapped invitation link opens this screen with the
 * invitation filled in and joins as soon as the account is verified. */
public final class RoomLobbyActivity extends BaseFragment {
    private static final ExecutorService network = Executors.newSingleThreadExecutor();
    private static final long POLL = 5000;
    private final long chatId;
    private String pendingInvite;
    private EditText invite;
    private TextView state, status;
    private Button verify, openBot, check, create, join, send, copy, enter, leave;
    private boolean disposed, busy, resumed, polling;
    private final Runnable listener = () -> { if (!disposed) render(); };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (disposed || !resumed) return;
            if (!busy && challenge() != null) confirm(true);
            AndroidUtilities.runOnUIThread(this, POLL);
        }
    };

    public RoomLobbyActivity(int account, long chatId, String invitation) {
        setCurrentAccount(account); this.chatId = chatId;
        pendingInvite = RoomApi.isInvitation(invitation) ? invitation : null;
    }
    private String text(int id) { return VrStrings.get(id); }
    private long userId() { return UserConfig.getInstance(currentAccount).getClientUserId(); }
    private RoomIdentityStore.Identity identity() { return RoomIdentityStore.identity(currentAccount, userId(), RoomApi.GATEWAY, System.currentTimeMillis()); }
    private RoomIdentityStore.Challenge challenge() { return RoomIdentityStore.challenge(currentAccount, userId(), RoomApi.GATEWAY, System.currentTimeMillis()); }
    private RoomSession ownSession() { RoomSession s = RoomSession.active; return s != null && !s.closed && s.account == currentAccount && s.chatId == chatId ? s : null; }

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
        TextView notice = label(body, text(R.string.vr_room_notice) + "\n\n" + text(R.string.vr_room_limit));
        notice.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        state = label(body, ""); state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status = label(body, ""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        verify = button(body, R.string.vr_room_auth_start, this::verify);
        openBot = button(body, R.string.vr_room_auth_open_bot, () -> { RoomIdentityStore.Challenge c = challenge(); if (c != null) openUrl(c.loginUrl); });
        check = button(body, R.string.vr_room_auth_complete, () -> confirm(false));
        create = button(body, R.string.vr_room_create, this::create);
        invite = input(body, R.string.vr_room_invite);
        if (pendingInvite != null) invite.setText(pendingInvite);
        join = button(body, R.string.vr_room_join, () -> join(invite.getText().toString().trim()));
        enter = button(body, R.string.vr_room_enter, () -> enter(context));
        send = button(body, R.string.vr_room_send, this::sendInvitation);
        copy = button(body, R.string.vr_room_copy, () -> copy(context));
        leave = button(body, R.string.vr_room_exit, () -> {
            RoomSession s = ownSession(); if (s == null) return;
            RoomCallBridge.leaveCall(s); RoomChatActivity.finishAll(); s.close();
            status.setText(text(R.string.vr_room_left)); render();
        });
        RoomSession.unlisten(listener);
        RoomSession.listen(listener);
        render();
        fragmentView = scroll; return scroll;
    }
    private TextView label(LinearLayout body, String value) {
        TextView view = new TextView(body.getContext()); view.setText(value); view.setTextSize(18);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        body.addView(view); return view;
    }
    private EditText input(LinearLayout body, int hint) {
        EditText field = new EditText(body.getContext()); field.setHint(text(hint)); field.setTextSize(18);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setSingleLine(true); field.setSaveEnabled(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(field, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(64))); return field;
    }
    private Button button(LinearLayout body, int label, Runnable action) {
        Button button = new Button(body.getContext()); button.setText(text(label)); button.setAllCaps(false); button.setTextSize(18);
        button.setOnClickListener(view -> action.run()); body.addView(button, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(64))); return button;
    }
    private static void show(View view, boolean visible) { view.setVisibility(visible ? View.VISIBLE : View.GONE); }

    /** Every control's visibility and the state line follow from the stores, never from history. */
    private void render() {
        if (state == null) return;
        boolean activated = UserConfig.getInstance(currentAccount).isClientActivated();
        RoomSession session = ownSession();
        RoomSession other = RoomSession.active != null && !RoomSession.active.closed && session == null ? RoomSession.active : null;
        RoomIdentityStore.Identity identity = activated ? identity() : null;
        RoomIdentityStore.Challenge challenge = activated && identity == null ? challenge() : null;
        String line;
        if (!activated) line = text(R.string.vr_room_account);
        else if (session != null) {
            RoomConnection.State link = session.state();
            int count = session.snapshot.optJSONArray("participants") == null ? 0 : session.snapshot.optJSONArray("participants").length();
            line = link == RoomConnection.State.CONNECTED ? String.format(text(R.string.vr_room_connected_count), count) : text(R.string.vr_room_reconnecting);
        } else if (other != null) line = text(R.string.vr_room_already_active);
        else if (identity != null) line = text(R.string.vr_room_auth_verified);
        else if (challenge != null) line = text(R.string.vr_room_auth_confirm);
        else line = text(pendingInvite != null ? R.string.vr_room_invited : R.string.vr_room_auth_needed);
        state.setText(line);
        boolean idle = !busy && activated && other == null;
        show(verify, idle && session == null && identity == null && challenge == null);
        show(openBot, idle && session == null && challenge != null);
        show(check, idle && session == null && challenge != null);
        show(create, idle && session == null && identity != null);
        show(invite, idle && session == null && identity != null);
        show(join, idle && session == null && identity != null);
        show(enter, session != null && !busy);
        show(send, session != null && !busy);
        show(copy, session != null && !busy);
        show(leave, session != null);
        enter.setEnabled(session != null && session.connected());
    }

    private void verify() {
        if (busy) return;
        final long userId = userId();
        if (userId == 0) return;
        busy = true; status.setText(text(R.string.vr_room_wait)); render();
        network.execute(() -> {
            JSONObject result = null; String failure = null;
            try {
                result = RoomApi.post(RoomApi.GATEWAY, "/v1/auth/start", "", new JSONObject().put("telegramId", Long.toString(userId)));
                if (!RoomAuthProtocol.sameAccount(userId, result.getString("telegramId"))) throw new Exception("ACCOUNT_MISMATCH");
                if (!result.getString("challengeToken").matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_AUTH");
                RoomAuthProtocol.loginUrl(result.getString("loginUrl"));
            } catch (Exception error) { failure = error.getMessage(); }
            final JSONObject ready = result; final String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (disposed || userId() != userId) return;
                if (code == null) {
                    RoomIdentityStore.putChallenge(currentAccount, userId, RoomApi.GATEWAY, ready.optString("challengeToken"),
                            ready.optString("loginUrl"), ready.optLong("expiresAt", System.currentTimeMillis() + 300000));
                    status.setText(text(R.string.vr_room_auth_confirm));
                    openUrl(ready.optString("loginUrl"));
                } else status.setText(text(RoomMessages.failure(code)));
                render();
            });
        });
    }

    /** Exchanges a bot-confirmed challenge for a room identity. Silent polling keeps quiet while
     *  the person simply has not pressed Start yet. */
    private void confirm(boolean silent) {
        RoomIdentityStore.Challenge challenge = challenge();
        if (busy || challenge == null || (silent && polling)) return;
        final long userId = userId();
        if (silent) polling = true;
        else { busy = true; status.setText(text(R.string.vr_room_wait)); render(); }
        network.execute(() -> {
            JSONObject result = null; String failure = null;
            try {
                result = RoomApi.post(challenge.gateway, "/v1/auth/complete", challenge.token, new JSONObject());
                if (!RoomAuthProtocol.sameAccount(userId, result.getString("telegramId"))) throw new Exception("ACCOUNT_MISMATCH");
                if (!result.getString("identityToken").matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_AUTH");
            } catch (Exception error) { failure = error.getMessage(); }
            final JSONObject ready = result; final String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                if (silent) polling = false; else busy = false;
                if (disposed || userId() != userId) return;
                if (code == null) {
                    RoomIdentityStore.putIdentity(currentAccount, userId, challenge.gateway, ready.optString("identityToken"),
                            ready.optLong("expiresAt", System.currentTimeMillis() + 7200000));
                    status.setText(text(R.string.vr_room_auth_verified));
                    if (pendingInvite != null && ownSession() == null) join(pendingInvite);
                } else if ("NICEGRAM_CONFIRM_REQUIRED".equals(code) || "AUTH_IN_PROGRESS".equals(code)) {
                    if (!silent) status.setText(text(R.string.vr_room_auth_confirm));
                } else if ("NETWORK".equals(code) && silent) {
                    // A poll that lost the network tries again on the next tick.
                } else {
                    if ("AUTH_EXPIRED".equals(code) || "ACCOUNT_MISMATCH".equals(code)) RoomIdentityStore.forgetChallenge(currentAccount);
                    status.setText(text(RoomMessages.failure(code)));
                }
                render();
            });
        });
    }

    private void create() { connect(null); }

    private void join(String invitation) {
        if (!RoomApi.isInvitation(invitation)) { status.setText(text(R.string.vr_room_expired)); return; }
        connect(invitation);
    }

    /** Creates a room when invitation is null, otherwise joins the invited one. */
    private void connect(String invitation) {
        if (busy) return;
        RoomIdentityStore.Identity identity = identity();
        if (identity == null) { status.setText(text(R.string.vr_room_auth_required)); render(); return; }
        if (RoomSession.active != null && !RoomSession.active.closed) {
            RoomSession active = RoomSession.active;
            if (invitation != null && active == ownSession() && active.roomId.equals(RoomApi.invitationRoom(invitation))) { pendingInvite = null; render(); return; }
            status.setText(text(RoomMessages.failure("ALREADY_IN_ROOM"))); return;
        }
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null || ChatObject.isNotInChat(chat)) { status.setText(text(R.string.vr_room_wrong_chat)); return; }
        final String chatKey = (ChatObject.isChannel(chat) ? "channel:" : "chat:") + chatId;
        final long userId = userId();
        busy = true; status.setText(text(R.string.vr_room_wait)); render();
        network.execute(() -> {
            RoomSession session = null; String failure = null;
            try {
                JSONObject body = new JSONObject().put("chatKey", chatKey);
                JSONObject result; String inviteToken;
                if (invitation == null) {
                    result = RoomApi.post(identity.gateway, "/v1/rooms", identity.token, body);
                    inviteToken = result.getString("inviteToken");
                } else {
                    String[] parsed = RoomApi.invitation(invitation);
                    if (!parsed[0].equals(identity.gateway)) throw new Exception("AUTH_ENDPOINT_MISMATCH");
                    inviteToken = parsed[2];
                    result = RoomApi.post(parsed[0], "/v1/rooms/" + parsed[1] + "/join", identity.token, body.put("inviteToken", inviteToken));
                }
                if (!chatKey.equals(result.getString("chatKey"))) throw new Exception("WRONG_CHAT");
                session = new RoomSession(currentAccount, chatId, identity.gateway, result, inviteToken, identity.token);
            } catch (Exception error) { failure = error.getMessage(); }
            final RoomSession ready = session; final String code = failure;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                boolean stale = UserConfig.getInstance(currentAccount).getClientUserId() != userId
                        || (RoomSession.active != null && !RoomSession.active.closed);
                if (ready != null && stale) { ready.close(); render(); return; }
                if (ready != null) {
                    RoomSession.active = ready; ready.start(); pendingInvite = null;
                    if (invite != null) invite.setText("");
                    if (!disposed) status.setText(text(invitation == null ? R.string.vr_room_created : R.string.vr_room_joined));
                } else if (!disposed) {
                    if ("NICEGRAM_AUTH_REQUIRED".equals(code) || "NICEGRAM_ACCOUNT_REQUIRED".equals(code)) RoomIdentityStore.forget(currentAccount);
                    status.setText(text(RoomMessages.failure(code)));
                }
                render();
            });
        });
    }

    private void sendInvitation() {
        RoomSession session = ownSession(); if (session == null) return;
        // An explicit press only: the link goes to this group, whose members may join with it.
        String message = String.format(text(R.string.vr_room_invite_message), session.invitation);
        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(message, -chatId));
        status.setText(text(R.string.vr_room_sent));
    }

    private void copy(Context context) {
        RoomSession session = ownSession(); if (session == null) return;
        ClipData clip = ClipData.newPlainText(text(R.string.vr_room_invite), session.invitation);
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        status.setText(text(R.string.vr_room_copied));
    }

    private void enter(Context context) {
        RoomSession session = ownSession(); if (session == null || !session.connected() || !session.validAccount()) return;
        try { context.startActivity(new Intent(context, RoomSpatialActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException error) { status.setText(text(R.string.vr_room_error)); }
    }

    private void openUrl(String url) {
        try { RoomAuthProtocol.loginUrl(url); } catch (Exception invalid) { return; }
        org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), url);
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        render();
        // Coming back from the bot is the moment the confirmation exists: check at once.
        AndroidUtilities.cancelRunOnUIThread(poll);
        if (challenge() != null && identity() == null) confirm(true);
        AndroidUtilities.runOnUIThread(poll, POLL);
    }
    @Override public void onPause() { super.onPause(); resumed = false; AndroidUtilities.cancelRunOnUIThread(poll); }
    @Override public void onFragmentDestroy() {
        // The room stays: leaving this screen is not leaving the room. Leave is its own button.
        disposed = true; resumed = false;
        AndroidUtilities.cancelRunOnUIThread(poll);
        RoomSession.unlisten(listener);
        super.onFragmentDestroy();
    }
}
