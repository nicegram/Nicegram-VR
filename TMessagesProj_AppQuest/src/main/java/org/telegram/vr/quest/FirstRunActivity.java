package org.telegram.vr.quest;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Nicegram VR — the screen that tells the truth before the user discovers it.
 *
 * Three sentences, and the third is the one that cannot be dropped: while the app is closed,
 * nothing arrives. Horizon OS has no Play services and therefore no push; a client that let
 * someone find that out by missing something would have earned the complaint. Saying it on the
 * first screen costs one screen.
 */
public class FirstRunActivity extends BaseFragment {

    private static final String FILE = "nicegram_vr_display";
    private static final String KEY_SHOWN = "first_run_shown";

    /** @return true at most once per install, for the account that gets there first. */
    public static boolean isDue(Context context) {
        return !context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false);
    }

    private static void markShown(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHOWN, true).apply();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(my.nicegram.vr.R.string.vr_intro_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    accept();
                }
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        column.addView(line(context, my.nicegram.vr.R.string.vr_intro_line1, true));
        column.addView(line(context, my.nicegram.vr.R.string.vr_intro_line2, false));
        column.addView(line(context, my.nicegram.vr.R.string.vr_intro_line3, false));
        column.addView(line(context, my.nicegram.vr.R.string.vr_silence_note, false));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button(context, my.nicegram.vr.R.string.vr_intro_accept, v -> accept()));
        buttons.addView(button(context, my.nicegram.vr.R.string.vr_intro_exceptions, v -> {
            markShown(getParentActivity() == null ? org.telegram.messenger.ApplicationLoader.applicationContext : getParentActivity());
            presentFragment(new SilenceRulesActivity(), true);
        }));
        column.addView(buttons);

        return fragmentView;
    }

    private void accept() {
        markShown(getParentActivity() == null
                ? org.telegram.messenger.ApplicationLoader.applicationContext
                : getParentActivity());
        finishFragment();
    }

    private TextView line(Context context, int resId, boolean big) {
        final TextView view = new TextView(context);
        // Through LocaleController, not Context: the cloud language pack answers by resource
        // ENTRY NAME, and a Context read never asks it. This is the first screen a user sees
        // and it would have stayed English whatever language they chose (A-31).
        view.setText(LocaleController.getString(resId));
        view.setTextColor(Theme.getColor(big ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, big ? 18 : 16);
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return view;
    }

    private TextView button(Context context, int resId, View.OnClickListener onClick) {
        final TextView view = new TextView(context);
        view.setText(LocaleController.getString(resId));
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        view.setGravity(Gravity.CENTER);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        // The hit-target floor: a ray jitters by about a degree, and a control the user cannot
        // hit is a control that is not there.
        view.setMinimumHeight(AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));
        view.setMinimumWidth(AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));
        view.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));
        lp.rightMargin = AndroidUtilities.dp(12);
        lp.topMargin = AndroidUtilities.dp(16);
        view.setLayoutParams(lp);
        view.setOnClickListener(onClick);
        return view;
    }
}
