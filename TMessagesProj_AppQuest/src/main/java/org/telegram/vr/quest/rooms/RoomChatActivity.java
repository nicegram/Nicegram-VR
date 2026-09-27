package org.telegram.vr.quest.rooms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The room's chat: the group's own Telegram chat, hosted in a spatial panel next to the call.
 *
 * <p>Modelled on upstream {@code BubbleActivity}, the one host besides LaunchActivity that runs a
 * {@link ChatActivity} for a single dialog, minus its two broadcasts: {@code closeOtherAppActivities}
 * would finish the 2D LaunchActivity behind the room, and {@code closeChats} would close the same
 * group there. Messages load under the user's own Telegram account; the room service never sees
 * them. Composer, voice notes, round videos and the history are Telegram's own.
 *
 * <p>Nothing global is re-measured here: {@code checkDisplaySize} and {@code fillStatusBarHeight}
 * write process-wide statics the 2D interface depends on, so the panel inherits them instead. */
public final class RoomChatActivity extends BasePermissionsActivity implements INavigationLayout.INavigationLayoutDelegate {
    private static final Set<RoomChatActivity> open = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean focused;
    /** The chat panel holds window focus: the person is typing or reading in the room. */
    public static boolean focused() { return focused; }
    private final ArrayList<BaseFragment> stack = new ArrayList<>();
    private INavigationLayout layout;
    private int account = -1;
    private long dialogId;

    public static Intent intent(Context context, RoomSession room) {
        return new Intent(context, RoomChatActivity.class).putExtra("account", room.account).putExtra("chatId", room.chatId);
    }

    /** Leaving the room closes its chat panel too; nothing keeps showing the group afterwards. */
    public static void finishAll() {
        ArrayList<RoomChatActivity> copy;
        synchronized (open) { copy = new ArrayList<>(open); }
        for (RoomChatActivity activity : copy) activity.finish();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        open.add(this);
        account = getIntent().getIntExtra("account", -1);
        long chatId = getIntent().getLongExtra("chatId", 0);
        RoomSession room = RoomSession.active;
        // Only the active room's own group, on its own account, is ever shown here.
        if (!UserConfig.isValidAccount(account) || room == null || room.closed || room.account != account
                || room.chatId != chatId || !room.validAccount()) { account = -1; finish(); return; }
        if (!SharedConfig.passcodeHash.isEmpty() && (SharedConfig.appLocked || AndroidUtilities.needShowPasscode(true))) {
            // A locked app shows no messages in the headset either; unlocking happens in 2D.
            setContentView(notice(VrStrings.get(R.string.vr_room_chat_locked)));
            account = -1;
            return;
        }
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        layout = INavigationLayout.newLayout(this, false);
        layout.setInBubbleMode(true);
        layout.setRemoveActionBarExtraHeight(true);
        DrawerLayoutContainer drawer = new DrawerLayoutContainer(this);
        setContentView(drawer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        RelativeLayout container = new RelativeLayout(this);
        drawer.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(layout.getView(), LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        drawer.setParentActionBarLayout(layout);
        layout.setDrawerLayoutContainer(drawer);
        layout.setFragmentStack(stack);
        layout.setDelegate(this);
        Bundle args = new Bundle();
        args.putLong("chat_id", chatId);
        ChatActivity chat = new ChatActivity(args);
        chat.setInBubbleMode(true);
        chat.setCurrentAccount(account);
        dialogId = -chatId;
        layout.addFragmentToStack(chat);
        layout.showLastFragment();
        // The chat is on screen in the headset: no notification for it while the panel is open.
        AccountInstance.getInstance(account).getNotificationsController().setOpenedInBubble(dialogId, true);
    }

    private TextView notice(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(22); view.setGravity(Gravity.CENTER);
        int pad = AndroidUtilities.dp(32); view.setPadding(pad, pad, pad, pad);
        view.setTextColor(0xFFE8ECF3); view.setBackgroundColor(0xFF161B24);
        return view;
    }

    @Override protected void onResume() {
        super.onResume();
        if (layout == null) return;
        layout.onResume();
        ApplicationLoader.externalInterfacePaused = false;
        AccountInstance.getInstance(account).getConnectionsManager().setAppPaused(false, false);
    }

    @Override protected void onPause() {
        super.onPause();
        if (layout == null) return;
        layout.onPause();
        ApplicationLoader.externalInterfacePaused = true;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focused = hasFocus;
    }

    @Override protected void onDestroy() {
        focused = false;
        open.remove(this);
        if (account != -1) AccountInstance.getInstance(account).getNotificationsController().setOpenedInBubble(dialogId, false);
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BaseFragment top = top();
        if (top != null) top.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!checkPermissionsResult(requestCode, permissions, grantResults)) return;
        BaseFragment top = top();
        if (top != null) top.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
    }

    @Override public void onBackPressed() {
        if (layout == null) { super.onBackPressed(); return; }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) { PhotoViewer.getInstance().closePhoto(true, false); return; }
        // The group chat is the panel itself; back never closes it, only what was opened on top.
        if (stack.size() > 1) layout.onBackPressed();
    }

    @Override public void onLowMemory() { super.onLowMemory(); if (layout != null) layout.onLowMemory(); }

    @Override public boolean needCloseLastFragment(INavigationLayout navigation) { return navigation.getFragmentStack().size() > 1; }

    private BaseFragment top() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
}
