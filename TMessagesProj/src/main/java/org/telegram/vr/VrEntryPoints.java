/*
 * Nicegram VR — where headset-only screens attach to shared Telegram UI.
 *
 * The library module cannot see the headset module: the dependency runs the other way. So a
 * screen that lives in the headset build cannot be named from shared code, and a string that
 * lives in its resources cannot be read there either. This registry is the seam. The headset
 * flavour fills it in at startup; every other flavour leaves it empty, the rows do not exist,
 * and the shared edits that consume it are a null check each.
 */
package org.telegram.vr;

import org.telegram.ui.ActionBar.BaseFragment;

public final class VrEntryPoints {

    /** A row in shared settings that opens a screen owned by the headset build. */
    public interface SettingsRow {
        /** Already localised by whoever installed it; shared code only displays it. */
        CharSequence title();

        /** The right-hand value, or null for a row with none. */
        CharSequence value();

        BaseFragment create();
    }

    /**
     * A screen shown at most once in the life of an install — the headset build owns the
     * "once", because only it knows what it has already shown.
     */
    public interface FirstRun {
        /** @return the fragment to show now, or null if it is not due. */
        BaseFragment takeIfDue(int currentAccount);
    }

    private static volatile SettingsRow silenceRow;
    private static volatile SettingsRow digestRow;
    private static volatile SettingsRow headsetRow;
    private static volatile FirstRun firstRun;
    private static volatile StartupFilter startupFilter;

    private VrEntryPoints() {
    }

    public static void installSilenceRow(SettingsRow row) {
        silenceRow = row;
    }

    public static void installDigestRow(SettingsRow row) {
        digestRow = row;
    }

    public static void installHeadsetRow(SettingsRow row) {
        headsetRow = row;
    }

    /**
     * Which chat folder the client should open on. "Show me only these chats" is a folder with
     * an include-only rule, and folders already exist upstream — what was missing is opening in
     * one, so a headset session starts in the narrow view rather than in everything.
     */
    public interface StartupFilter {
        /**
         * @return the PERSISTENT {@code DialogFilter.id} to select once per session, or
         *         {@code Integer.MIN_VALUE} for none. Never {@code localId}, which is a
         *         process counter and means a different folder on the next launch.
         */
        int filterId();
    }

    private static volatile boolean startupFilterPending;

    public static void installStartupFilter(StartupFilter filter) {
        startupFilter = filter;
        startupFilterPending = filter != null;
    }

    /**
     * True until the startup folder has been applied once in this process.
     *
     * The selection is a one-shot rather than a rule: after it, the user's own tab taps must
     * stand, and a chat-list rebuild — of which there are many — must not snap them back to the
     * folder they deliberately left.
     */
    public static boolean startupFilterPending() {
        return startupFilterPending && startupFilter != null;
    }

    /** Disarms the one-shot. Called whether or not the folder was found. */
    public static void markStartupFilterApplied() {
        startupFilterPending = false;
    }

    /** Integer.MIN_VALUE on every build except a headset one that has the setting set. */
    public static int startupFilterId() {
        final StartupFilter f = startupFilter;
        if (f == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return f.filterId();
        } catch (Throwable e) {
            return Integer.MIN_VALUE;
        }
    }

    public static void installFirstRun(FirstRun run) {
        firstRun = run;
    }

    /** Null on every build except the headset one. Callers hide the row when it is null. */
    /**
     * A switch the headset build puts in the chat-list header.
     *
     * It exists as a registry entry rather than a flag because the thing it switches lives in
     * the headset module — the master notification state — and shared code must not know about
     * it. Null on every other flavour, where the header is unchanged.
     */
    public interface HeaderToggle {
        /** Drawable for the current state. */
        int icon(boolean on);

        boolean isOn();

        /** @return the new state. */
        boolean toggle();

        /** Read aloud by accessibility services, and the text of the confirmation. */
        CharSequence description(boolean on);
    }

    private static volatile HeaderToggle headerToggle;

    public static void installHeaderToggle(HeaderToggle toggle) {
        headerToggle = toggle;
    }

    public static HeaderToggle headerToggle() {
        return headerToggle;
    }

    public static SettingsRow silenceRow() {
        return silenceRow;
    }

    public static SettingsRow digestRow() {
        return digestRow;
    }

    /**
     * Display and motion, which are not notification settings. It sits on the notifications
     * screen all the same, because that is the one headset-specific place a user of this build
     * already knows; a row of its own in the main settings list is the better home and is
     * tracked as such.
     */
    public static SettingsRow headsetRow() {
        return headsetRow;
    }

    /**
     * Consulted on resume. Fails quiet: a first-run screen that throws must not be able to stop
     * the client from opening, which is the one failure a user cannot work around.
     */
    public static BaseFragment takeFirstRunFragment(int currentAccount) {
        final FirstRun run = firstRun;
        if (run == null) {
            return null;
        }
        try {
            return run.takeIfDue(currentAccount);
        } catch (Throwable e) {
            return null;
        }
    }
}
