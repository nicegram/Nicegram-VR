package org.telegram.vr.quest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.vr.VrQrCode;

/**
 * Nicegram VR — Nicegram on the phone, offered once, on the third launch.
 *
 * <h3>Why two codes rather than one</h3>
 *
 * A single code pointing at a chooser page would be correct and one step too many: whoever
 * scans it is holding the phone they own, and already knows which one it is. Two codes, one per
 * platform, means the scan lands on the store.
 *
 * <h3>Why a code at all</h3>
 *
 * Because there is no other way out of a headset. A link is not tappable from someone else's
 * device, typing a URL on a virtual keyboard with a ray is the worst interaction this product
 * has, and the phone that would install the app is already in the room. The buttons below the
 * codes are for the person who wants to open it here and send it to themselves.
 */
public class MobilePromoActivity extends BaseFragment {

    /** Verified 200 on 23 September 2026, both of them. */
    private static final String IOS_URL =
            "https://apps.apple.com/app/apple-store/id1608870673?pt=119567154&mt=8";
    private static final String ANDROID_URL =
            "https://play.google.com/store/apps/details?id=app.nicegram&utm_source=nicegram-vr";

    private static final int QR_PX = 512;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(VrStrings.get(my.nicegram.vr.R.string.vr_mobile_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                }
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_lead, 18,
                Theme.key_windowBackgroundWhiteBlackText));
        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_body, 15,
                Theme.key_windowBackgroundWhiteGrayText));

        final LinearLayout codes = new LinearLayout(context);
        codes.setOrientation(LinearLayout.HORIZONTAL);
        codes.setGravity(Gravity.CENTER);
        column.addView(codes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 8));
        codes.addView(codeBlock(context, my.nicegram.vr.R.string.vr_mobile_ios, IOS_URL));
        codes.addView(codeBlock(context, my.nicegram.vr.R.string.vr_mobile_android, ANDROID_URL));

        column.addView(button(context, my.nicegram.vr.R.string.vr_mobile_open_ios,
                v -> open(IOS_URL)));
        column.addView(button(context, my.nicegram.vr.R.string.vr_mobile_open_android,
                v -> open(ANDROID_URL)));
        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_dismiss_hint, 13,
                Theme.key_windowBackgroundWhiteGrayText));

        VrMobilePromo.markShown(ApplicationLoader.applicationContext);
        return fragmentView;
    }

    private void open(String url) {
        if (getParentActivity() != null) {
            Browser.openUrl(getParentActivity(), url);
        }
    }

    private void dismiss() {
        finishFragment();
    }

    /** One labelled QR. A code that fails to render leaves its label and the button below it. */
    private View codeBlock(Context context, int labelRes, String url) {
        final LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        block.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);

        final Bitmap qr = VrQrCode.render(url, QR_PX);
        if (qr != null) {
            final ImageView image = new ImageView(context);
            image.setImageBitmap(qr);
            image.setBackgroundColor(Color.WHITE);
            // The quiet zone, drawn by the view because MARGIN=0 leaves none in the bitmap.
            // The spec wants four modules clear; a ~33-module code across 140 dp makes that
            // about 17 dp, so 16 is the floor rather than a spacing choice.
            image.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                    AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            block.addView(image, LayoutHelper.createLinear(140, 140));
        }
        final TextView label = new TextView(context);
        label.setText(VrStrings.get(labelRes));
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        block.addView(label);
        return block;
    }

    private TextView text(Context context, int res, int sizeDp, int colourKey) {
        final TextView view = new TextView(context);
        view.setText(VrStrings.get(res));
        view.setTextColor(Theme.getColor(colourKey));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        return view;
    }

    private TextView button(Context context, int res, View.OnClickListener onClick) {
        final TextView view = new TextView(context);
        view.setText(VrStrings.get(res));
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        view.setGravity(Gravity.CENTER);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        // The hit-target floor, the same one the first-run screen uses: a ray jitters by about
        // a degree, and a control the user cannot hit is a control that is not there.
        view.setMinimumHeight(AndroidUtilities.dp(VrDensity.MIN_TARGET_DP));
        view.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        view.setOnClickListener(onClick);
        final LinearLayout.LayoutParams lp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, VrDensity.MIN_TARGET_DP);
        lp.topMargin = AndroidUtilities.dp(8);
        view.setLayoutParams(lp);
        return view;
    }
}
