package org.telegram.vr.quest;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

/**
 * Nicegram VR — how a string of THIS module reaches the screen.
 *
 * <h3>The bug this exists to fix</h3>
 *
 * Every headset string was read with {@code LocaleController.getString(my.nicegram.vr.R.string.X)}
 * and every one of them came out as the literal text <b>{@code LOC_ERR:null}</b> — the app's own
 * marker for a string it could not resolve. Seventy-eight call sites: the first-run screen, the
 * whole headset settings screen, the silence rules, the digest.
 *
 * <p>It is not a missing translation, and the reason is one step further in than it looks.
 * {@code LocaleController} resolves a resource id through {@code Localization.getByResId}, which
 * is two lookups, not one:
 *
 * <pre>
 *   final int hash = rawResBindings.get(resId);   // id -> hash of the resource's NAME
 *   return get(hash);                             // hash -> the string itself
 * </pre>
 *
 * <p>The first lookup succeeds. {@code string_resource_ids.bin} is generated at build time by
 * {@code GenerateStringResourceIdsAssetTask} from the merged symbol list, so this module's ids
 * are in it — that was the obvious explanation and it is wrong.
 *
 * <p>The second lookup is the one that fails. Those strings come from the app's packaged
 * localization asset ({@code checkLocalizationInternal} → {@code addResLocalization}), which is
 * built from <i>upstream's</i> {@code strings.xml}. This module's strings were never in it. So
 * the id maps to a name hash the localization has no entry for, {@code get} returns null, the
 * fallback returns null, and {@code getStringInternal} finishes with {@code "LOC_ERR:" + key}
 * where the key was null too.
 *
 * <p>It stayed invisible because every screen that draws these strings is behind sign-in, and
 * until 23 September nobody had signed in.
 *
 * <h3>What actually reaches the language pack</h3>
 *
 * The cloud pack is keyed by resource <b>entry name</b>, not by id —
 * {@code Localization.getByResName} hashes the name. So the working path is: take the entry name
 * from OUR resources, ask the pack for that name, and fall back to our own compiled string.
 *
 * <p>That is what {@link #get} does, and it means the Russian upload in {@code language-pack/}
 * does reach these screens once loaded — which the previous code could not have done at all.
 *
 * <p>Note what is NOT used here: {@code Context.getString} alone. It works, and it was what the
 * code did before finding A-31, but it never asks the pack, so the interface would be English
 * for ever. Both halves are needed, in this order.
 */
public final class VrStrings {

    private VrStrings() {
    }

    /** A headset string: the language pack if it carries it, otherwise this module's own. */
    public static String get(int res) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return "";
        }
        try {
            final String name = context.getResources().getResourceEntryName(res);
            final String fromPack = LocaleController.getServerString(name);
            // getServerString answers null when the pack has no such key, and can answer its own
            // error marker when something else went wrong. Neither is a string to show anyone.
            if (fromPack != null && !fromPack.isEmpty() && !fromPack.startsWith("LOC_ERR")) {
                return fromPack;
            }
        } catch (Throwable ignored) {
            // A missing pack, a renamed resource, anything at all: the compiled string still works.
        }
        try {
            return context.getString(res);
        } catch (Throwable e) {
            return "";
        }
    }

    /** The same, with format arguments. */
    public static String format(int res, Object... args) {
        final String template = get(res);
        if (template.isEmpty()) {
            return "";
        }
        try {
            return String.format(LocaleController.getInstance().getCurrentLocale(), template, args);
        } catch (Throwable e) {
            // A pack whose translation dropped or reordered a placeholder must not crash a screen.
            return template;
        }
    }
}
