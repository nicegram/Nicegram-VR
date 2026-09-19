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
    private static volatile FirstRun firstRun;

    private VrEntryPoints() {
    }

    public static void installSilenceRow(SettingsRow row) {
        silenceRow = row;
    }

    public static void installDigestRow(SettingsRow row) {
        digestRow = row;
    }

    public static void installFirstRun(FirstRun run) {
        firstRun = run;
    }

    /** Null on every build except the headset one. Callers hide the row when it is null. */
    public static SettingsRow silenceRow() {
        return silenceRow;
    }

    public static SettingsRow digestRow() {
        return digestRow;
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
