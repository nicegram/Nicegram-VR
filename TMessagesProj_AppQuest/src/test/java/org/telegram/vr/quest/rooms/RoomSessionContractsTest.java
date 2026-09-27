package org.telegram.vr.quest.rooms;

import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import my.nicegram.vr.R;
import static org.junit.Assert.*;

/** Review of 27 September: the link, identity and error contracts the room screens rely on. */
public class RoomSessionContractsTest {
    @Before public void reset() { RoomIdentityStore.clearForTests(); }

    @Test public void oneLostHeartbeatKeepsTheRoomButALongSilenceDoesNot() {
        RoomConnection link = new RoomConnection(1000);
        link.failure("NETWORK", 4000);
        assertEquals(RoomConnection.State.CONNECTED, link.state(4000));
        assertEquals(RoomConnection.State.CONNECTED, link.state(1000 + RoomConnection.GRACE - 1));
        assertEquals(RoomConnection.State.RECONNECTING, link.state(1000 + RoomConnection.GRACE));
        link.success(20000);
        assertEquals(RoomConnection.State.CONNECTED, link.state(20001));
    }

    @Test public void terminalAnswersEndTheRoomAndNothingRevivesIt() {
        RoomConnection link = new RoomConnection(0);
        link.failure("ROOM_EXPIRED", 10);
        assertEquals(RoomConnection.State.ENDED, link.state(11));
        assertEquals("ROOM_EXPIRED", link.endCode());
        link.success(12);
        assertEquals(RoomConnection.State.ENDED, link.state(13));
        link.end("LEFT");
        assertEquals("the first reason is the one shown", "ROOM_EXPIRED", link.endCode());
    }

    @Test public void onlyALapsedLeaseIsRejoinable() {
        assertTrue(RoomConnection.rejoinable("SESSION_EXPIRED"));
        for (String code : new String[]{"ROOM_EXPIRED", "NETWORK", "NICEGRAM_AUTH_REQUIRED", "HTTP_502", null}) assertFalse(code, RoomConnection.rejoinable(code));
        assertFalse("a transient failure is not terminal", RoomConnection.terminal("NICEGRAM_UNAVAILABLE"));
        assertFalse(RoomConnection.terminal("NETWORK"));
    }

    @Test public void verificationIsRememberedPerAccountAndNotPastItsExpiry() {
        RoomIdentityStore.putIdentity(0, 42, RoomApi.GATEWAY, "t", 1_000_000);
        assertNotNull(RoomIdentityStore.identity(0, 42, RoomApi.GATEWAY, 10));
        assertNull("another account slot sees nothing", RoomIdentityStore.identity(1, 42, RoomApi.GATEWAY, 10));
        assertNull("a token about to expire is not handed out", RoomIdentityStore.identity(0, 42, RoomApi.GATEWAY, 1_000_000 - RoomIdentityStore.MARGIN));
        RoomIdentityStore.putIdentity(0, 42, RoomApi.GATEWAY, "t", 1_000_000);
        assertNull("a different Telegram user on the same slot is refused and the row dropped", RoomIdentityStore.identity(0, 43, RoomApi.GATEWAY, 10));
        assertNull(RoomIdentityStore.identity(0, 42, RoomApi.GATEWAY, 10));
        RoomIdentityStore.putIdentity(0, 42, RoomApi.GATEWAY, "t", 1_000_000);
        assertNull("another gateway never receives this token", RoomIdentityStore.identity(0, 42, "https://other.example", 10));
    }

    @Test public void confirmedIdentityReplacesThePendingChallenge() {
        RoomIdentityStore.putChallenge(0, 42, RoomApi.GATEWAY, "c", "https://t.me/nicegram_auth_bot?start=x", 5000);
        assertNotNull(RoomIdentityStore.challenge(0, 42, RoomApi.GATEWAY, 10));
        assertNull("expired", RoomIdentityStore.challenge(0, 42, RoomApi.GATEWAY, 5000));
        RoomIdentityStore.putChallenge(0, 42, RoomApi.GATEWAY, "c", "u", 5000);
        RoomIdentityStore.putIdentity(0, 42, RoomApi.GATEWAY, "t", 1_000_000);
        assertNull(RoomIdentityStore.challenge(0, 42, RoomApi.GATEWAY, 10));
        RoomIdentityStore.forget(0);
        assertNull(RoomIdentityStore.identity(0, 42, RoomApi.GATEWAY, 10));
    }

    @Test public void onlyInvitationsToThePinnedGatewayOpenTheRoom() {
        String token = "a".repeat(43), other = "b".repeat(43);
        String link = RoomApi.GATEWAY + "/room#" + token + "." + other;
        assertTrue(RoomApi.isInvitation(link));
        assertEquals(token, RoomApi.invitationRoom(link));
        assertFalse(RoomApi.isInvitation("https://evil.example/room#" + token + "." + other));
        assertFalse(RoomApi.isInvitation(RoomApi.GATEWAY + "/room"));
        assertFalse(RoomApi.isInvitation("https://t.me/nicegram"));
        assertFalse(RoomApi.isInvitation(null));
        assertNull(RoomApi.invitationRoom("not a url"));
    }

    @Test public void anEdgeErrorPageBecomesAStatusCodeNotAParserMessage() throws Exception {
        try { RoomApi.result(502, "<html>Bad gateway</html>"); fail(); } catch (Exception e) { assertEquals("HTTP_502", e.getMessage()); }
        try { RoomApi.result(409, "{\"error\":\"ROOM_FULL\"}"); fail(); } catch (Exception e) { assertEquals("ROOM_FULL", e.getMessage()); }
        try { RoomApi.result(500, "{}"); fail(); } catch (Exception e) { assertEquals("HTTP_500", e.getMessage()); }
        assertEquals("r", RoomApi.result(200, "{\"roomId\":\"r\"}").getString("roomId"));
    }

    /** Seam with room-service: every code the server can answer has its own sentence. */
    @Test public void everyServerErrorCodeHasItsOwnMessage() throws Exception {
        Set<String> codes = new TreeSet<>();
        Pattern failure = Pattern.compile("(?:Failure|AuthFailure)\\(\\d{3}, '([A-Z_]+)'\\)");
        for (String file : new String[]{"room-service/server.mjs", "room-service/identity.mjs"}) {
            Matcher m = failure.matcher(new String(Files.readAllBytes(new File(root(), file).toPath()), StandardCharsets.UTF_8));
            while (m.find()) codes.add(m.group(1));
        }
        assertTrue("parsed the server's codes", codes.size() >= 15);
        // Protocol-level refusals a correct client never triggers; they read as a generic failure.
        Set<String> generic = new TreeSet<>(java.util.Arrays.asList("NOT_FOUND", "JSON_REQUIRED", "TOO_LARGE", "INVALID_JSON",
                "INVALID_CHAT", "INVALID_NAME", "INVALID_ACCOUNT", "AUTH_IN_PROGRESS"));
        for (String code : codes) {
            if (generic.contains(code)) continue;
            assertNotEquals("no specific message for server code " + code, R.string.vr_room_error, RoomMessages.failure(code));
        }
    }

    private static File root() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) if (new File(dir, "room-service/server.mjs").isFile()) return dir;
        throw new IllegalStateException("project root not found");
    }
}
