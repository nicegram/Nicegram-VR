package org.telegram.vr.quest.rooms;

import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.vr.VrEntryPoints;
import org.telegram.vr.quest.VrStrings;
import my.nicegram.vr.R;

/** What shared Telegram screens may ask of rooms; installed by the headset application only. */
public final class RoomEntryPoint implements VrEntryPoints.RoomEntry {
    @Override public CharSequence title() { return VrStrings.get(R.string.vr_room_title); }

    @Override public BaseFragment create(int account, long chatId) { return new RoomLobbyActivity(account, chatId, null); }

    @Override public boolean openInvitation(BaseFragment from, int account, long chatId, String url) {
        if (from == null || !RoomApi.isInvitation(url)) return false;
        from.presentFragment(new RoomLobbyActivity(account, chatId, url));
        return true;
    }

    @Override public CharSequence recordingBlocked(int account) {
        RoomSession room = RoomSession.active;
        return room != null && !room.closed && VoIPService.getSharedInstance() != null ? VrStrings.get(R.string.vr_room_recording_blocked) : null;
    }
}
