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
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
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
public class SilenceRulesActivity extends BaseFragment {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ENTRY = 1;
    private static final int VIEW_TYPE_ACTION = 2;
    private static final int VIEW_TYPE_INFO = 3;

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
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(context.getString(app.nicegram.vr.R.string.vr_silence_title));
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
        if (item.viewType == VIEW_TYPE_ENTRY) {
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
        input.setHintText(context.getString(app.nicegram.vr.R.string.vr_word_hint));
        input.setSingleLine(true);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(app.nicegram.vr.R.string.vr_word_title));
        builder.setView(input);
        builder.setPositiveButton(context.getString(app.nicegram.vr.R.string.vr_save), (dialog, which) -> {
            final String word = input.getText() == null ? "" : input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (TextUtils.isEmpty(word)) {
                toast(context.getString(app.nicegram.vr.R.string.vr_word_empty));
                return;
            }
            if (profile.words.contains(word)) {
                // Said out loud rather than swallowed: a duplicate absorbed in silence looks
                // exactly like a save that did not work.
                toast(context.getString(app.nicegram.vr.R.string.vr_word_duplicate));
                return;
            }
            final Set<String> words = new LinkedHashSet<>(profile.words);
            words.add(word);
            save(new SilenceProfile(profile.people, profile.chats, words));
        });
        builder.setNegativeButton(context.getString(app.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void confirmRemoval(Item item) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(app.nicegram.vr.R.string.vr_silence_remove_title));
        builder.setMessage(AndroidUtilities.replaceTags(
                String.format(context.getString(app.nicegram.vr.R.string.vr_silence_remove_text), item.text)));
        builder.setPositiveButton(context.getString(app.nicegram.vr.R.string.vr_silence_remove), (dialog, which) -> {
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
        builder.setNegativeButton(context.getString(app.nicegram.vr.R.string.vr_cancel), null);
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
        items.add(new Item(VIEW_TYPE_HEADER, 0, getStringSafe(app.nicegram.vr.R.string.vr_silence_people)));
        for (Long id : profile.people) {
            items.add(Item.entry(id, DialogObject.getName(currentAccount, id)));
        }
        for (Long id : profile.chats) {
            items.add(Item.entry(id, DialogObject.getName(currentAccount, id)));
        }
        items.add(Item.action(ID_ADD_CHAT, getStringSafe(app.nicegram.vr.R.string.vr_silence_add_chat)));

        items.add(new Item(VIEW_TYPE_HEADER, 1, getStringSafe(app.nicegram.vr.R.string.vr_silence_words)));
        for (String word : profile.words) {
            items.add(Item.word(word));
        }
        items.add(Item.action(ID_ADD_WORD, getStringSafe(app.nicegram.vr.R.string.vr_silence_add_word)));

        final String note = getStringSafe(app.nicegram.vr.R.string.vr_silence_note);
        items.add(new Item(VIEW_TYPE_INFO, 2,
                profile.isSilentForEveryone()
                        ? getStringSafe(app.nicegram.vr.R.string.vr_silence_empty) + "\n\n" + note
                        : note));

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String getStringSafe(int resId) {
        final Context context = getParentActivity() != null
                ? getParentActivity()
                : org.telegram.messenger.ApplicationLoader.applicationContext;
        return context.getString(resId);
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

        static Item entry(long dialogId, CharSequence name) {
            Item item = new Item(VIEW_TYPE_ENTRY, 0, name);
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
            return type == VIEW_TYPE_ENTRY || type == VIEW_TYPE_ACTION;
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
            if (item.viewType == VIEW_TYPE_HEADER) {
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
