package org.telegram.vr.quest;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Nicegram VR — the settings a headset needs and a phone does not.
 *
 * Three things live here and nowhere else: how large the interface is drawn, how much motion
 * the client is allowed to spend, and the way back into the silence profile and the digest.
 * Everything else a user might look for is upstream's own settings, unchanged.
 */
public class VrSettingsActivity extends BaseFragment {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_SETTING = 1;
    private static final int VIEW_TYPE_CHECK = 2;
    private static final int VIEW_TYPE_INFO = 3;

    private static final int ID_DENSITY = 1;
    private static final int ID_AUTOPLAY = 2;
    private static final int ID_STICKERS = 3;
    private static final int ID_SILENCE = 4;
    private static final int ID_DIGEST = 5;
    private static final int ID_RESET = 6;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<Item> items = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(context.getString(app.nicegram.vr.R.string.vr_settings_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter());
        frameLayout.addView(listView, LayoutHelper.createFrameMatchParent());
        listView.setOnItemClickListener((view, position) -> onClick(position, view));

        rebuild();
        return fragmentView;
    }

    private void onClick(int position, View view) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        switch (items.get(position).id) {
            case ID_DENSITY:
                chooseDensity(context);
                break;
            case ID_AUTOPLAY: {
                final boolean on = !isAutoplayOn();
                // Both flags move together: a user who says "do not play media by itself" does
                // not mean "except the animated ones".
                LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, on);
                LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, on);
                ((TextCheckCell) view).setChecked(on);
                break;
            }
            case ID_STICKERS: {
                final boolean on = !LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_STICKERS_CHAT);
                LiteMode.toggleFlag(LiteMode.FLAG_ANIMATED_STICKERS_CHAT, on);
                ((TextCheckCell) view).setChecked(on);
                break;
            }
            case ID_SILENCE:
                presentFragment(new SilenceRulesActivity());
                break;
            case ID_DIGEST:
                presentFragment(new DigestActivity());
                break;
            case ID_RESET:
                confirmReset(context);
                break;
            default:
                break;
        }
    }

    private boolean isAutoplayOn() {
        return LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS);
    }

    private void chooseDensity(Context context) {
        final CharSequence[] labels = {
                context.getString(app.nicegram.vr.R.string.vr_density_compact),
                context.getString(app.nicegram.vr.R.string.vr_density_normal),
                context.getString(app.nicegram.vr.R.string.vr_density_large),
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(app.nicegram.vr.R.string.vr_density));
        builder.setItems(labels, (dialog, which) -> {
            VrDensity.setStep(context, which);
            rebuild();
            // Honest rather than convenient: rescaling a running Telegram UI mid-session is the
            // kind of change that looks cheap and is not, so the screen says when it applies.
            showRestartNote(context);
        });
        builder.setNegativeButton(context.getString(app.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void showRestartNote(Context context) {
        android.widget.Toast.makeText(context,
                context.getString(app.nicegram.vr.R.string.vr_density_applies_next_start),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void confirmReset(Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(app.nicegram.vr.R.string.vr_reset));
        builder.setMessage(context.getString(app.nicegram.vr.R.string.vr_reset_text));
        builder.setPositiveButton(context.getString(app.nicegram.vr.R.string.vr_reset_do), (dialog, which) -> {
            VrDensity.setStep(context, VrDensity.STEP_NORMAL);
            VrPerformance.applyDefaults();
            rebuild();
            showRestartNote(context);
        });
        builder.setNegativeButton(context.getString(app.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void rebuild() {
        final Context context = ApplicationLoader.applicationContext;
        items.clear();
        items.add(Item.header(context.getString(app.nicegram.vr.R.string.vr_settings_display)));
        items.add(Item.setting(ID_DENSITY, context.getString(app.nicegram.vr.R.string.vr_density),
                densityLabel(context)));
        items.add(Item.info(context.getString(app.nicegram.vr.R.string.vr_density_info)));

        items.add(Item.header(context.getString(app.nicegram.vr.R.string.vr_settings_motion)));
        items.add(Item.check(ID_AUTOPLAY, context.getString(app.nicegram.vr.R.string.vr_autoplay), isAutoplayOn()));
        items.add(Item.check(ID_STICKERS, context.getString(app.nicegram.vr.R.string.vr_stickers),
                LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_STICKERS_CHAT)));
        items.add(Item.info(context.getString(app.nicegram.vr.R.string.vr_motion_info)));

        items.add(Item.header(context.getString(app.nicegram.vr.R.string.vr_settings_quiet)));
        items.add(Item.setting(ID_SILENCE, context.getString(app.nicegram.vr.R.string.vr_silence_title), null));
        items.add(Item.setting(ID_DIGEST, context.getString(app.nicegram.vr.R.string.vr_digest_title), null));
        items.add(Item.setting(ID_RESET, context.getString(app.nicegram.vr.R.string.vr_reset), null));
        items.add(Item.info(context.getString(app.nicegram.vr.R.string.vr_silence_note)));

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String densityLabel(Context context) {
        switch (VrDensity.step(context)) {
            case VrDensity.STEP_COMPACT:
                return context.getString(app.nicegram.vr.R.string.vr_density_compact);
            case VrDensity.STEP_LARGE:
                return context.getString(app.nicegram.vr.R.string.vr_density_large);
            default:
                return context.getString(app.nicegram.vr.R.string.vr_density_normal);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> out = new ArrayList<>();
        out.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        out.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        out.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        out.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        return out;
    }

    private static final class Item {
        final int viewType;
        final int id;
        final CharSequence text;
        CharSequence value;
        boolean checked;

        private Item(int viewType, int id, CharSequence text) {
            this.viewType = viewType;
            this.id = id;
            this.text = text;
        }

        static Item header(CharSequence t) {
            return new Item(VIEW_TYPE_HEADER, 0, t);
        }

        static Item info(CharSequence t) {
            return new Item(VIEW_TYPE_INFO, 0, t);
        }

        static Item setting(int id, CharSequence t, CharSequence value) {
            Item i = new Item(VIEW_TYPE_SETTING, id, t);
            i.value = value;
            return i;
        }

        static Item check(int id, CharSequence t, boolean checked) {
            Item i = new Item(VIEW_TYPE_CHECK, id, t);
            i.checked = checked;
            return i;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            return type == VIEW_TYPE_SETTING || type == VIEW_TYPE_CHECK;
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
            } else if (viewType == VIEW_TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else {
                view = new TextSettingsCell(context);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Item item = items.get(position);
            final boolean divider = position + 1 < items.size()
                    && items.get(position + 1).viewType != VIEW_TYPE_INFO
                    && items.get(position + 1).viewType != VIEW_TYPE_HEADER;
            switch (item.viewType) {
                case VIEW_TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(item.text);
                    break;
                case VIEW_TYPE_INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText(item.text);
                    break;
                case VIEW_TYPE_CHECK:
                    ((TextCheckCell) holder.itemView).setTextAndCheck(item.text.toString(), item.checked, divider);
                    break;
                default:
                    final TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (item.value == null) {
                        cell.setText(item.text, divider);
                    } else {
                        cell.setTextAndValue(item.text, item.value, divider);
                    }
                    break;
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
