package org.telegram.vr.quest;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Nicegram VR — who and what may interrupt, on this device only.
 *
 * The sentence about the phone is on the screen rather than in help, because it answers the
 * question every user of this feature actually has, and answering it late is answering it after
 * they have already worried.
 */
public class SilenceRulesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ENTRY = 1;
    private static final int VIEW_TYPE_ACTION = 2;
    private static final int VIEW_TYPE_INFO = 3;
    private static final int VIEW_TYPE_DIALOG = 4;

    private static final int ID_ADD_CHAT = -1;
    private static final int ID_ADD_WORD = -2;

    private final SilenceStore store;
    private SilenceProfile profile;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<Item> items = new ArrayList<>();

    public SilenceRulesActivity() {
        this.store = new SilenceStore(org.telegram.messenger.ApplicationLoader.applicationContext);
    }

    @Override
    public boolean onFragmentCreate() {
        profile = store.load(currentAccount);
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
        // A name that arrived after the screen was built; redraw rather than leave a placeholder.
        if (id == NotificationCenter.updateInterfaces && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_silence_title));
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
        listView.setOnItemClickListener((view, position) -> onItemClick(position));

        rebuild();
        return fragmentView;
    }

    private void onItemClick(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        final Item item = items.get(position);
        if (item.viewType == VIEW_TYPE_ACTION) {
            if (item.id == ID_ADD_CHAT) {
                pickChat();
            } else if (item.id == ID_ADD_WORD) {
                askForWord();
            }
            return;
        }
        if (item.viewType == VIEW_TYPE_ENTRY || item.viewType == VIEW_TYPE_DIALOG) {
            confirmRemoval(item);
        }
    }

    private void pickChat() {
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        final DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) {
                return true;
            }
            final long dialogId = dids.get(0).dialogId;
            final Set<Long> people = new LinkedHashSet<>(profile.people);
            final Set<Long> chats = new LinkedHashSet<>(profile.chats);
            // A positive dialog id is a person, a negative one is a group or channel. Keeping the
            // two apart is what lets the screen name them separately later.
            if (dialogId >= 0) {
                people.add(dialogId);
            } else {
                chats.add(dialogId);
            }
            save(new SilenceProfile(people, chats, profile.words));
            fragment.finishFragment();
            return true;
        });
        presentFragment(picker);
    }

    private void askForWord() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHintText(LocaleController.getString(my.nicegram.vr.R.string.vr_word_hint));
        input.setSingleLine(true);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_word_title));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(my.nicegram.vr.R.string.vr_save), (dialog, which) -> {
            final String word = input.getText() == null ? "" : input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (TextUtils.isEmpty(word)) {
                toast(LocaleController.getString(my.nicegram.vr.R.string.vr_word_empty));
                return;
            }
            if (profile.words.contains(word)) {
                // Said out loud rather than swallowed: a duplicate absorbed in silence looks
                // exactly like a save that did not work.
                toast(LocaleController.getString(my.nicegram.vr.R.string.vr_word_duplicate));
                return;
            }
            final Set<String> words = new LinkedHashSet<>(profile.words);
            words.add(word);
            save(new SilenceProfile(profile.people, profile.chats, words));
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void confirmRemoval(Item item) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_silence_remove_title));
        final CharSequence subject = item.word != null
                ? item.word
                : VrNames.name(currentAccount, item.dialogId,
                        getStringSafe(my.nicegram.vr.R.string.vr_loading));
        builder.setMessage(AndroidUtilities.replaceTags(
                String.format(LocaleController.getString(my.nicegram.vr.R.string.vr_silence_remove_text), subject)));
        builder.setPositiveButton(LocaleController.getString(my.nicegram.vr.R.string.vr_silence_remove), (dialog, which) -> {
            final Set<Long> people = new LinkedHashSet<>(profile.people);
            final Set<Long> chats = new LinkedHashSet<>(profile.chats);
            final Set<String> words = new LinkedHashSet<>(profile.words);
            if (item.word != null) {
                words.remove(item.word);
            } else {
                people.remove(item.dialogId);
                chats.remove(item.dialogId);
            }
            save(new SilenceProfile(people, chats, words));
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void toast(String text) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), text, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Writes the profile and rebuilds the list. Note what it does NOT do: it never calls
     * account.updateNotifySettings. That setting is account-wide, so writing it here would
     * silence the user's phone as well — the whole reason this profile exists separately.
     */
    private void save(SilenceProfile updated) {
        profile = updated;
        store.save(currentAccount, updated);
        rebuild();
    }

    private void rebuild() {
        items.clear();
        // People and chats are separated because they are different questions: "who may reach
        // me" and "which room may reach me". Merged, a list of twenty is unreadable.
        items.add(new Item(VIEW_TYPE_HEADER, 0, getStringSafe(my.nicegram.vr.R.string.vr_silence_people)));
        for (Long id : profile.people) {
            items.add(Item.dialog(id));
        }
        items.add(Item.action(ID_ADD_CHAT, getStringSafe(my.nicegram.vr.R.string.vr_silence_add_chat)));

        if (!profile.chats.isEmpty()) {
            items.add(new Item(VIEW_TYPE_HEADER, 1, getStringSafe(my.nicegram.vr.R.string.vr_silence_chats_header)));
            for (Long id : profile.chats) {
                items.add(Item.dialog(id));
            }
        }

        items.add(new Item(VIEW_TYPE_HEADER, 2, getStringSafe(my.nicegram.vr.R.string.vr_silence_words)));
        for (String word : profile.words) {
            items.add(Item.word(word));
        }
        items.add(Item.action(ID_ADD_WORD, getStringSafe(my.nicegram.vr.R.string.vr_silence_add_word)));

        final String note = getStringSafe(my.nicegram.vr.R.string.vr_silence_note);
        items.add(new Item(VIEW_TYPE_INFO, 3,
                profile.isSilentForEveryone()
                        ? getStringSafe(my.nicegram.vr.R.string.vr_silence_empty) + "\n\n" + note
                        : note));

        // Anything referenced but not loaded is fetched from local storage; the rows show a
        // placeholder until it lands rather than an empty line the user cannot identify.
        final java.util.ArrayList<Long> referenced = new java.util.ArrayList<>(profile.people);
        referenced.addAll(profile.chats);
        VrNames.loadMissing(currentAccount, referenced, () -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** How many exceptions exist, for the row in shared settings. */
    public static int count(int currentAccount) {
        final SilenceProfile p = new SilenceStore(
                org.telegram.messenger.ApplicationLoader.applicationContext).load(currentAccount);
        return p.size();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> out = new ArrayList<>();
        out.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        out.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        out.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        out.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        out.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        return out;
    }

    /**
     * Through LocaleController, which needs no Context at all — that is why the null-safety this
     * helper was built for is gone with it. A Context read never asks the cloud language pack,
     * which answers by resource ENTRY NAME, so every string read this way was English forever
     * however the pack was loaded (A-31).
     */
    private String getStringSafe(int resId) {
        return LocaleController.getString(resId);
    }

    private static class Item {
        final int viewType;
        final int id;
        final CharSequence text;
        long dialogId;
        String word;

        Item(int viewType, int id, CharSequence text) {
            this.viewType = viewType;
            this.id = id;
            this.text = text;
        }

        static Item dialog(long dialogId) {
            // The name is resolved when the row is drawn, not when the list is built: it may
            // still be loading, and a row built too early would keep the placeholder forever.
            Item item = new Item(VIEW_TYPE_DIALOG, 0, null);
            item.dialogId = dialogId;
            return item;
        }

        static Item word(String word) {
            Item item = new Item(VIEW_TYPE_ENTRY, 0, word);
            item.word = word;
            return item;
        }

        static Item action(int id, CharSequence text) {
            return new Item(VIEW_TYPE_ACTION, id, text);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            return type == VIEW_TYPE_ENTRY || type == VIEW_TYPE_ACTION || type == VIEW_TYPE_DIALOG;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            final View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == VIEW_TYPE_INFO) {
                view = new TextInfoPrivacyCell(context);
            } else if (viewType == VIEW_TYPE_DIALOG) {
                view = new UserCell(context, 6, 0, false);
            } else {
                view = new TextSettingsCell(context);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            final Item item = items.get(position);
            final boolean divider = position + 1 < items.size()
                    && items.get(position + 1).viewType != VIEW_TYPE_INFO
                    && items.get(position + 1).viewType != VIEW_TYPE_HEADER;
            if (item.viewType == VIEW_TYPE_DIALOG) {
                final UserCell cell = (UserCell) holder.itemView;
                final CharSequence name = VrNames.name(currentAccount, item.dialogId,
                        getStringSafe(my.nicegram.vr.R.string.vr_loading));
                cell.setData(VrNames.dialogObject(currentAccount, item.dialogId), name, null, 0);
            } else if (item.viewType == VIEW_TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (item.viewType == VIEW_TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(item.text);
            } else {
                final TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(item.text, divider);
                cell.setTextColor(Theme.getColor(item.viewType == VIEW_TYPE_ACTION
                        ? Theme.key_windowBackgroundWhiteBlueText4
                        : Theme.key_windowBackgroundWhiteBlackText));
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
