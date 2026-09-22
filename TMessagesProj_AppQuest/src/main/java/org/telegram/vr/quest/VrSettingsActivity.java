package org.telegram.vr.quest;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.LocaleController;
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
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.vr.quest.speech.DictationActivity;
import org.telegram.vr.quest.speech.SpeechSettings;

import java.util.ArrayList;

/**
 * Nicegram VR — the settings a headset needs and a phone does not.
 *
 * Four things live here and nowhere else: how large the interface is drawn, how much motion
 * the client is allowed to spend, the way back into the silence profile and the digest, and the
 * recognition service dictation speaks to — which is the user's own, because none is built in.
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
    private static final int ID_DICT_ENDPOINT = 7;
    private static final int ID_DICT_TOKEN = 8;
    private static final int ID_DICT_LANGUAGE = 9;
    private static final int ID_DICT_TEST = 10;
    private static final int ID_LAYOUT = 11;
    private static final int ID_ABOUT_SOURCE = 12;
    private static final int ID_ABOUT_SITE = 13;
    private static final int ID_ABOUT_TERMS = 14;
    private static final int ID_ABOUT_PRIVACY = 15;
    private static final int ID_START_FOLDER = 16;

    /**
     * The two links that resolve for anyone. Checked rather than assumed: the repository is
     * public, and nicegram.me answers 200. The project's Data Room is deliberately NOT here —
     * `nicegram/dataroom` is a private repository and okr.nicegram.me redirects to /login, so a
     * row pointing at either would hand a user a login wall from inside a messenger.
     */
    private static final String SOURCE_URL = "https://github.com/nicegram/Nicegram-VR";
    private static final String SITE_URL = "https://nicegram.me";

    /**
     * Nicegram's OWN terms and privacy policy, which are not Telegram's and do not replace
     * them. Telegram's govern the account, the messages and the service; these govern this
     * client and what Nicegram itself provides. Both exist, both are linked, each where it
     * belongs — the service references elsewhere in the app still point at telegram.org, which
     * is also what the Nicegram Android client does.
     *
     * Verified rather than guessed: /terms-of-use and /privacy-policy both answer 200, and the
     * first is titled "Nicegram Terms Of Use". /terms, /tos and /legal are 404s.
     */
    private static final String TERMS_URL = "https://nicegram.me/terms-of-use";
    private static final String PRIVACY_URL = "https://nicegram.me/privacy-policy";

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<Item> items = new ArrayList<>();
    private SpeechSettings speech;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_title));
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

        speech = new SpeechSettings(ApplicationLoader.applicationContext);
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
            case ID_LAYOUT:
                chooseLayout(context);
                break;
            case ID_START_FOLDER:
                chooseStartFolder(context);
                break;
            case ID_ABOUT_SOURCE:
                Browser.openUrl(context, SOURCE_URL);
                break;
            case ID_ABOUT_SITE:
                Browser.openUrl(context, SITE_URL);
                break;
            case ID_ABOUT_TERMS:
                Browser.openUrl(context, TERMS_URL);
                break;
            case ID_ABOUT_PRIVACY:
                Browser.openUrl(context, PRIVACY_URL);
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
            case ID_DICT_ENDPOINT:
                askEndpoint(context);
                break;
            case ID_DICT_TOKEN:
                askToken(context);
                break;
            case ID_DICT_LANGUAGE:
                askLanguage(context);
                break;
            case ID_DICT_TEST:
                presentFragment(new DictationActivity());
                break;
            default:
                break;
        }
    }

    private boolean isAutoplayOn() {
        return LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS);
    }

    private static final int[] DENSITY_STEP_NAMES = {
            my.nicegram.vr.R.string.vr_density_most_rows,
            my.nicegram.vr.R.string.vr_density_balanced,
            my.nicegram.vr.R.string.vr_density_larger,
            my.nicegram.vr.R.string.vr_density_largest,
    };

    /**
     * The panel's height in real pixels and the density the platform chose for it, before any
     * step of ours. Taken from the display rather than assumed, because the whole reason this
     * scale was re-based is that the assumed panel was not the real one.
     */
    private static int panelHeightPx() {
        // displaySize.y IS the height in the current orientation. This used to take the smaller
        // side, which was right only by accident while the panel was locked to landscape: the
        // moment it became portrait (500x800) the smaller side was the WIDTH, and the row count
        // shown next to every density step would have been four instead of seven. Guarded
        // because displaySize is zero until checkDisplaySize has run at least once.
        final int y = AndroidUtilities.displaySize.y;
        return y > 0 ? y : 0;
    }

    /**
     * The platform's own density, untouched by this build's step: {@code checkDisplaySize}
     * multiplies {@link AndroidUtilities#density}, while the Resources it read from keep
     * reporting what the system decided. Reading it here rather than dividing our own value
     * back out avoids compounding the two if the seam ever changes.
     */
    private static float systemDensity() {
        return ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
    }

    /**
     * "Balanced — about 7 chats". A density control named only by size asks a person to imagine
     * the result; this one states it. The count comes from the panel in front of them.
     */
    private static CharSequence densityChoiceLabel(int step) {
        final String name = LocaleController.getString(DENSITY_STEP_NAMES[step]);
        final int rows = VrDensity.rowsForStep(panelHeightPx(), systemDensity(), step);
        return rows <= 0 ? name : String.format(
                LocaleController.getString(my.nicegram.vr.R.string.vr_density_rows), name, rows);
    }

    private void chooseDensity(Context context) {
        final CharSequence[] labels = new CharSequence[VrDensity.STEP_COUNT];
        for (int step = 0; step < VrDensity.STEP_COUNT; step++) {
            labels[step] = densityChoiceLabel(step);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_density));
        builder.setItems(labels, (dialog, which) -> {
            VrDensity.setStep(context, which);
            rebuild();
            // Honest rather than convenient: rescaling a running Telegram UI mid-session is the
            // kind of change that looks cheap and is not, so the screen says when it applies.
            showRestartNote(context);
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void showRestartNote(Context context) {
        android.widget.Toast.makeText(context,
                LocaleController.getString(my.nicegram.vr.R.string.vr_density_applies_next_start),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void confirmReset(Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_reset));
        builder.setMessage(LocaleController.getString(my.nicegram.vr.R.string.vr_reset_text));
        builder.setPositiveButton(LocaleController.getString(my.nicegram.vr.R.string.vr_reset_do), (dialog, which) -> {
            VrDensity.setStep(context, VrDensity.STEP_BALANCED);
            VrPerformance.applyDefaults();
            VrLayout.applyDefaults();
            VrTheme.applyDefaults();
            rebuild();
            showRestartNote(context);
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void rebuild() {
        final Context context = ApplicationLoader.applicationContext;
        items.clear();
        items.add(Item.header(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_display)));
        items.add(Item.setting(ID_DENSITY, LocaleController.getString(my.nicegram.vr.R.string.vr_density),
                densityLabel(context)));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_density_info)));
        items.add(Item.setting(ID_LAYOUT, LocaleController.getString(my.nicegram.vr.R.string.vr_layout),
                layoutLabel()));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_layout_info)));
        items.add(Item.setting(ID_START_FOLDER,
                LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder),
                startFolderLabel(context)));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder_info)));

        items.add(Item.header(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_motion)));
        items.add(Item.check(ID_AUTOPLAY, LocaleController.getString(my.nicegram.vr.R.string.vr_autoplay), isAutoplayOn()));
        items.add(Item.check(ID_STICKERS, LocaleController.getString(my.nicegram.vr.R.string.vr_stickers),
                LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_STICKERS_CHAT)));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_motion_info)));

        items.add(Item.header(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_quiet)));
        items.add(Item.setting(ID_SILENCE, LocaleController.getString(my.nicegram.vr.R.string.vr_silence_title), null));
        items.add(Item.setting(ID_DIGEST, LocaleController.getString(my.nicegram.vr.R.string.vr_digest_title), null));
        items.add(Item.setting(ID_RESET, LocaleController.getString(my.nicegram.vr.R.string.vr_reset), null));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_silence_note)));

        items.add(Item.header(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_dictation)));
        items.add(Item.setting(ID_DICT_ENDPOINT,
                LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_endpoint),
                // The host, never the whole URL: a path can carry a token and this row is on
                // screen whenever anyone opens settings.
                orNotSet(context, SpeechSettings.endpointHost(speech.endpoint()))));
        items.add(Item.setting(ID_DICT_TOKEN,
                LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_token),
                orNotSet(context, SpeechSettings.maskedToken(speech.token()))));
        items.add(Item.setting(ID_DICT_LANGUAGE,
                LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_language),
                orNotSet(context, speech.language())));
        items.add(Item.setting(ID_DICT_TEST,
                LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_test), null));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_info)));

        items.add(Item.header(LocaleController.getString(my.nicegram.vr.R.string.vr_settings_about)));
        items.add(Item.setting(ID_ABOUT_SOURCE,
                LocaleController.getString(my.nicegram.vr.R.string.vr_about_source), null));
        items.add(Item.setting(ID_ABOUT_SITE,
                LocaleController.getString(my.nicegram.vr.R.string.vr_about_site), null));
        items.add(Item.setting(ID_ABOUT_TERMS,
                LocaleController.getString(my.nicegram.vr.R.string.vr_about_terms), null));
        items.add(Item.setting(ID_ABOUT_PRIVACY,
                LocaleController.getString(my.nicegram.vr.R.string.vr_about_privacy), null));
        items.add(Item.info(LocaleController.getString(my.nicegram.vr.R.string.vr_about_info)));

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** One-argument sink for {@link #askText}; the platform's own is not guaranteed here. */
    private interface Saver {
        void accept(String value);
    }

    private static String orNotSet(Context context, String value) {
        return TextUtils.isEmpty(value)
                ? LocaleController.getString(my.nicegram.vr.R.string.vr_dictation_not_set)
                : value;
    }

    /**
     * One text dialog for the three dictation rows. The token's prefill is deliberately empty
     * while the other two carry their current value: a token on screen is a token in a
     * screenshot, in a recording, and in whatever the headset streams to a TV.
     */
    private void askText(Context context, int titleRes, int hintRes, String prefill,
                         Saver onSave) {
        final EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHintText(LocaleController.getString(hintRes));
        input.setSingleLine(true);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        if (!TextUtils.isEmpty(prefill)) {
            input.setText(prefill);
            input.setSelection(prefill.length());
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(titleRes));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(my.nicegram.vr.R.string.vr_save), (dialog, which) -> {
            onSave.accept(input.getText() == null ? "" : input.getText().toString().trim());
            rebuild();
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    private void askEndpoint(Context context) {
        askText(context, my.nicegram.vr.R.string.vr_dictation_endpoint,
                my.nicegram.vr.R.string.vr_dictation_endpoint_hint,
                speech.endpoint(), value -> speech.setEndpoint(value));
    }

    private void askToken(Context context) {
        askText(context, my.nicegram.vr.R.string.vr_dictation_token,
                my.nicegram.vr.R.string.vr_dictation_token_hint,
                "", value -> {
                    // Blank means "leave it alone", which is why the field starts empty. Clearing
                    // a token is the reset row's job, not an accidental empty save.
                    if (!TextUtils.isEmpty(value)) {
                        speech.setToken(value);
                    }
                });
    }

    private void askLanguage(Context context) {
        askText(context, my.nicegram.vr.R.string.vr_dictation_language,
                my.nicegram.vr.R.string.vr_dictation_language_hint,
                speech.language(), value -> speech.setLanguage(value));
    }

    private static String layoutLabel() {
        return LocaleController.getString(VrLayout.isSingleColumn()
                ? my.nicegram.vr.R.string.vr_layout_single
                : my.nicegram.vr.R.string.vr_layout_split);
    }

    /**
     * Two choices, not a switch, because neither is "on". A headset panel is wide in pixels and
     * narrow in the dp this build draws with, and which of the two reads better is a matter for
     * the person wearing it.
     */
    private void chooseLayout(Context context) {
        final CharSequence[] labels = {
                LocaleController.getString(my.nicegram.vr.R.string.vr_layout_single),
                LocaleController.getString(my.nicegram.vr.R.string.vr_layout_split),
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_layout));
        builder.setItems(labels, (dialog, which) -> {
            VrLayout.setSingleColumn(which == 0);
            rebuild();
            showRestartNote(context);
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    /**
     * The folders of the CURRENT account, plus "All chats" for none.
     *
     * The list is read live rather than cached: a folder can be created, renamed or deleted from
     * the phone in the same account, and a settings screen showing a folder that no longer
     * exists is how a user learns not to trust the screen. If the chosen folder disappears, the
     * label falls back to "All chats" and the client opens where upstream would — the selection
     * in DialogsActivity simply finds nothing to match.
     */
    private void chooseStartFolder(Context context) {
        final ArrayList<MessagesController.DialogFilter> filters = folders();
        if (filters.isEmpty()) {
            final AlertDialog.Builder empty = new AlertDialog.Builder(context);
            empty.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder));
            empty.setMessage(LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder_empty));
            empty.setPositiveButton(LocaleController.getString(my.nicegram.vr.R.string.vr_intro_accept), null);
            showDialog(empty.create());
            return;
        }
        final CharSequence[] labels = new CharSequence[filters.size() + 1];
        labels[0] = LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder_none);
        for (int i = 0; i < filters.size(); i++) {
            labels[i + 1] = filters.get(i).name;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder));
        builder.setItems(labels, (dialog, which) -> {
            // DialogFilter.id, never localId: the second is a process counter and would mean a
            // different folder on the next launch. See VrStartFolder.
            VrStartFolder.set(ApplicationLoader.applicationContext,
                    which == 0 ? VrStartFolder.NONE : filters.get(which - 1).id);
            rebuild();
            showRestartNote(context);
        });
        builder.setNegativeButton(LocaleController.getString(my.nicegram.vr.R.string.vr_cancel), null);
        showDialog(builder.create());
    }

    /** Every real folder of the selected account; the "All chats" pseudo-filter is not one. */
    private ArrayList<MessagesController.DialogFilter> folders() {
        final ArrayList<MessagesController.DialogFilter> out = new ArrayList<>();
        try {
            for (MessagesController.DialogFilter f :
                    MessagesController.getInstance(UserConfig.selectedAccount).getDialogFilters()) {
                if (f != null && !f.isDefault()) {
                    out.add(f);
                }
            }
        } catch (Throwable e) {
            // A settings row is not worth a crash; an empty list reads as "no folders yet".
            FileLog.e(e);
        }
        return out;
    }

    private CharSequence startFolderLabel(Context context) {
        final int wanted = VrStartFolder.filterId(context);
        if (wanted != VrStartFolder.NONE) {
            for (MessagesController.DialogFilter f : folders()) {
                if (f.id == wanted) {
                    return f.name;
                }
            }
        }
        return LocaleController.getString(my.nicegram.vr.R.string.vr_start_folder_none);
    }

    private String densityLabel(Context context) {
        return densityChoiceLabel(VrDensity.step(context)).toString();
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
