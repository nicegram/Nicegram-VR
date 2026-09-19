/*
 * Nicegram VR — where headset-only screens attach to shared Telegram UI.
 *
 * The library module cannot see the headset module: the dependency runs the other way. So a
 * screen that lives in the headset build cannot be named from shared code, and a string that
 * lives in its resources cannot be read there either. This registry is the seam. The headset
 * flavour fills it in at startup; every other flavour leaves it empty and the row does not
 * exist, which is why the shared edit that consumes it is three lines and a null check.
 */
package org.telegram.vr;

import org.telegram.ui.ActionBar.BaseFragment;

public final class VrEntryPoints {

    /** A row in shared settings that opens a screen owned by the headset build. */
    public interface SettingsRow {
        /** Already localised by whoever installed it; shared code only displays it. */
        CharSequence title();

        BaseFragment create();
    }

    private static volatile SettingsRow silenceRow;

    private VrEntryPoints() {
    }

    public static void installSilenceRow(SettingsRow row) {
        silenceRow = row;
    }

    /** Null on every build except the headset one. Callers hide the row when it is null. */
    public static SettingsRow silenceRow() {
        return silenceRow;
    }
}
