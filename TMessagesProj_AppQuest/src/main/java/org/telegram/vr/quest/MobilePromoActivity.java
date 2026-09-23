package org.telegram.vr.quest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.vr.VrQrCode;

/**
 * Nicegram VR — Nicegram on the phone, offered once, on the third launch.
 *
 * <h3>How a link actually gets out of a headset</h3>
 *
 * By the account that is already signed in. The primary action sends both store links to
 * <b>Saved Messages</b>, where the phone this offer is about picks them up a second later. That
 * is the whole reason this screen can exist in a messenger and would not work in anything else.
 *
 * <p>A QR code cannot do that job, and this is worth writing down because the first version of
 * this screen was built on the assumption that it could. <b>A code drawn on a headset panel is
 * inside the headset.</b> There is no external screen for a phone camera to point at, and the
 * person wearing it cannot see the panel and their phone at the same time. The code below is
 * kept because it was asked for and because a headset that is CASTING to a phone or a television
 * does put it on a real screen — but it is the third path on this screen, not the first.
 *
 * <p>The two store buttons open the headset's own browser, which is the path for someone who
 * would rather finish here.
 */
public class MobilePromoActivity extends BaseFragment {

    /** Verified 200 on 23 September 2026, all three. */
    private static final String IOS_URL =
            "https://apps.apple.com/app/apple-store/id1608870673";
    private static final String ANDROID_URL =
            "https://play.google.com/store/apps/details?id=app.nicegram";

    /**
     * The short one, and the only address on this screen a person could retype from memory.
     * It routes by platform on the other side, so one code and one line of text cover both.
     *
     * <p>Two forms on purpose: the code carries the scheme, because a scanner handed a bare
     * host may offer a web search instead of the page; the label drops it, because a person
     * reading an address off a panel should not have to read "https://" as well.
     */
    private static final String SHORT_URL = "https://nicegram.me/download";
    private static final String SHORT_URL_LABEL = "nicegram.me/download";

    private static final int QR_PX = 512;

    /**
     * The drawn size of the code, in dp.
     *
     * <p>Sized for the NARROWEST panel this build can produce, not for the widest. The Quest 3
     * panel is 500x800 px at 200 dpi, and {@code VrDensity} multiplies density by up to 1.54
     * ({@code STEP_SCALE}), so the interface can be as narrow as 500 / (1.25 x 1.54) = 260 dp.
     * Minus the 24 dp column padding on each side that leaves 212 dp, and a 160 dp square with
     * its quiet zone fits inside it at every one of the four steps. The first version drew two
     * 164 dp blocks side by side — 328 dp — which overflowed the panel at every step but one.
     */
    private static final int QR_DP = 160;

    /**
     * The quiet zone, drawn by the view because the encoder is asked for {@code MARGIN = 0}.
     *
     * <p>A decoder is entitled to four clear modules. This URL encodes to a 33-module code, so
     * the 120 dp of code area left inside the square below puts a module at about 3.6 dp, and
     * 20 dp is five and a half of them — over the line rather than on it, because the panel is
     * being read through a lens.
     */
    private static final int QUIET_ZONE_DP = 20;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(VrStrings.get(my.nicegram.vr.R.string.vr_mobile_title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        // A ScrollView rather than a centred column: at the largest interface step the panel is
        // about 415 dp tall, and this screen's content is taller than that. Without it the
        // buttons are simply off the bottom of the panel with no way to reach them.
        final ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT));

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_lead, 18, true,
                Theme.key_windowBackgroundWhiteBlackText));
        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_body, 15, false,
                Theme.key_windowBackgroundWhiteGrayText));

        // The path that works: the account in this headset is the account on that phone.
        column.addView(button(context, my.nicegram.vr.R.string.vr_mobile_send_saved,
                true, v -> sendToSavedMessages()));

        column.addView(button(context, my.nicegram.vr.R.string.vr_mobile_open_ios,
                false, v -> open(IOS_URL)));
        column.addView(button(context, my.nicegram.vr.R.string.vr_mobile_open_android,
                false, v -> open(ANDROID_URL)));

        column.addView(code(context));
        final TextView address = text(context, 0, 16, true,
                Theme.key_windowBackgroundWhiteBlackText);
        address.setText(SHORT_URL_LABEL);
        column.addView(address);
        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_scan, 13, false,
                Theme.key_windowBackgroundWhiteGrayText));

        column.addView(text(context, my.nicegram.vr.R.string.vr_mobile_dismiss_hint, 13, false,
                Theme.key_windowBackgroundWhiteGrayText));

        VrMobilePromo.markShown(ApplicationLoader.applicationContext);
        return fragmentView;
    }

    /**
     * Both links, into the one chat that is guaranteed to exist and guaranteed to be on the
     * other device within seconds.
     */
    private void sendToSavedMessages() {
        try {
            final long self = getUserConfig().getClientUserId();
            getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(
                    "Nicegram\n" + IOS_URL + "\n" + ANDROID_URL, self));
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.contact_check,
                            VrStrings.get(my.nicegram.vr.R.string.vr_mobile_sent))
                    .show();
        } catch (Throwable e) {
            // Say so rather than look like it worked: the store buttons below are still there.
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.error,
                            VrStrings.get(my.nicegram.vr.R.string.vr_mobile_send_failed))
                    .show();
        }
    }

    private void open(String url) {
        if (getParentActivity() != null) {
            Browser.openUrl(getParentActivity(), url);
        }
    }

    /** The code, on white with its quiet zone drawn by the view. Absent if it will not render. */
    private View code(Context context) {
        final FrameLayout holder = new FrameLayout(context);
        final Bitmap qr = VrQrCode.render(SHORT_URL, QR_PX);
        if (qr != null) {
            final ImageView image = new ImageView(context);
            image.setImageBitmap(qr);
            image.setBackgroundColor(Color.WHITE);
            image.setPadding(AndroidUtilities.dp(QUIET_ZONE_DP), AndroidUtilities.dp(QUIET_ZONE_DP),
                    AndroidUtilities.dp(QUIET_ZONE_DP), AndroidUtilities.dp(QUIET_ZONE_DP));
            holder.addView(image, LayoutHelper.createFrame(QR_DP, QR_DP, Gravity.CENTER));
        }
        final LinearLayout.LayoutParams lp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, qr == null ? 0 : QR_DP);
        lp.topMargin = AndroidUtilities.dp(16);
        holder.setLayoutParams(lp);
        return holder;
    }

    /** @param res 0 for a view whose text the caller sets itself. */
    private TextView text(Context context, int res, int sizeDp, boolean bold, int colourKey) {
        final TextView view = new TextView(context);
        if (res != 0) {
            view.setText(VrStrings.get(res));
        }
        view.setTextColor(Theme.getColor(colourKey));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        return view;
    }

    private TextView button(Context context, int res, boolean primary, View.OnClickListener onClick) {
        final TextView view = new TextView(context);
        view.setText(VrStrings.get(res));
        view.setTextColor(Theme.getColor(primary
                ? Theme.key_featuredStickers_buttonText
                : Theme.key_featuredStickers_addButton));
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(primary
                        ? Theme.key_featuredStickers_addButton
                        : Theme.key_windowBackgroundGray),
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
