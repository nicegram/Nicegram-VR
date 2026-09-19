package org.telegram.vr.quest;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Nicegram VR — what accumulated while the client stayed quiet.
 *
 * This is the other half of the silence: suppressing a message is only defensible if the person
 * can see, in one place and at one glance, what was suppressed. The screen shows the period it
 * covers, one row per chat with a count and the last line, and an empty state that says nobody
 * wrote rather than showing zeroes.
 */
public class DigestActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_CHAT = 0;
    private static final int VIEW_TYPE_INFO = 1;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<Digest.Entry> rows = new ArrayList<>();
    private CharSequence infoText;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(context.getString(app.nicegram.vr.R.string.vr_digest_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter());
        frameLayout.addView(listView, LayoutHelper.createFrameMatchParent());
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            openChat(rows.get(position).dialogId);
        });

        rebuild();
        return fragmentView;
    }

    private void openChat(long dialogId) {
        final android.os.Bundle args = new android.os.Bundle();
        if (VrNames.isPerson(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        presentFragment(new ChatActivity(args));
    }

    private void rebuild() {
        rows.clear();
        final Digest digest = QuestRuntime.digest();
        final Context context = ApplicationLoader.applicationContext;
        if (digest != null) {
            rows.addAll(digest.snapshot());
            final ArrayList<Long> ids = new ArrayList<>();
            for (Digest.Entry e : rows) {
                ids.add(e.dialogId);
            }
            VrNames.loadMissing(currentAccount, ids, () -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
        if (rows.isEmpty()) {
            // A sentence, never a zero: "0 messages" reads as a broken counter, "nobody wrote"
            // reads as the truth it is.
            infoText = context.getString(app.nicegram.vr.R.string.vr_digest_empty);
        } else {
            // Plain Android formatting rather than Telegram's own: this string is ours, it
            // lives in our resources, and borrowing upstream's formatter would tie the screen
            // to a key we do not own.
            final CharSequence clock = android.text.format.DateFormat.getTimeFormat(context)
                    .format(new java.util.Date(digest == null ? System.currentTimeMillis() : digest.since()));
            infoText = String.format(context.getString(app.nicegram.vr.R.string.vr_digest_since), clock);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** Clears the period and returns; the caller sees the empty state next time. */
    public void markAllSeen() {
        final Digest digest = QuestRuntime.digest();
        if (digest != null) {
            digest.clear();
        }
        rebuild();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> out = new ArrayList<>();
        out.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        out.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        out.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        out.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        out.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        return out;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_CHAT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            final View view;
            if (viewType == VIEW_TYPE_CHAT) {
                view = new UserCell(context, 6, 0, false);
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(infoText);
                return;
            }
            final Digest.Entry entry = rows.get(position);
            final Context context = ApplicationLoader.applicationContext;
            final UserCell cell = (UserCell) holder.itemView;
            final CharSequence name = VrNames.name(currentAccount, entry.dialogId,
                    context.getString(app.nicegram.vr.R.string.vr_loading));
            final String count = context.getResources()
                    .getQuantityString(app.nicegram.vr.R.plurals.vr_digest_messages, entry.count, entry.count);
            final String status = count + " · " + (entry.lastText == null ? "" : entry.lastText);
            cell.setData(VrNames.dialogObject(currentAccount, entry.dialogId), name, status, 0);
        }

        @Override
        public int getItemViewType(int position) {
            return position < rows.size() ? VIEW_TYPE_CHAT : VIEW_TYPE_INFO;
        }

        @Override
        public int getItemCount() {
            return rows.size() + 1;
        }
    }
}
