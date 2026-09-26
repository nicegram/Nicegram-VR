package org.telegram.vr.quest.rooms;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoomContractsTest {
    @Test public void invitationRequiresHttpsAndExactFragmentCapability() throws Exception {
        String token = "a".repeat(43);
        String[] parsed = RoomApi.invitation("https://rooms.example/room#" + token + "." + token);
        assertArrayEquals(new String[]{"https://rooms.example", token, token}, parsed);
        for (String invalid : new String[]{
            "http://rooms.example/room#" + token + "." + token,
            "https://user:password@rooms.example/room#" + token + "." + token,
            "https://rooms.example/room?token=secret#" + token + "." + token,
            "https://rooms.example/room#short.short",
            "https://rooms.example/other#" + token + "." + token}) {
            try { RoomApi.invitation(invalid); fail("accepted invalid invitation"); } catch (Exception expected) {}
        }
    }
    @Test public void endpointCannotRedirectCredentialsViaUrlComponents() throws Exception {
        assertEquals("https://rooms.example", RoomApi.endpoint("https://rooms.example/"));
        for (String invalid : new String[]{"http://rooms.example", "https://rooms.example/path", "https://rooms.example/?x=y", "https://rooms.example/#token", "https://user@rooms.example"}) {
            try { RoomApi.endpoint(invalid); fail("accepted invalid endpoint"); } catch (Exception expected) {}
        }
    }
    @Test public void authLinkCannotSendTheUserToAnotherHostOrAccount() throws Exception {
        String session = "a".repeat(64);
        assertEquals("https://t.me/TestBot?start=" + session, RoomAuthProtocol.loginUrl("https://t.me/TestBot?start=" + session));
        for (String invalid : new String[]{"https://evil.example/TestBot?start=" + session,
                "https://t.me/TestBot?start=short", "https://t.me/TestBot?start=" + session + "&redirect=evil",
                "https://t.me.evil.example/TestBot?start=" + session}) {
            try { RoomAuthProtocol.loginUrl(invalid); fail("accepted unsafe auth link"); } catch (Exception expected) {}
        }
        assertTrue(RoomAuthProtocol.sameAccount(123L, "123"));
        assertFalse(RoomAuthProtocol.sameAccount(123L, "456"));
    }
    @Test public void ordinaryMemberCanJoinButCannotCreate() {
        assertEquals(RoomCallPolicy.Action.JOIN, RoomCallPolicy.decide(true, false, true, false, false, true));
        assertEquals(RoomCallPolicy.Action.ADMIN_REQUIRED, RoomCallPolicy.decide(true, false, false, false, false, true));
    }
    @Test public void administratorReusesExistingCallAndNeverCreatesFromMissingCallData() {
        assertEquals(RoomCallPolicy.Action.CREATE, RoomCallPolicy.decide(true, false, false, true, false, true));
        assertEquals(RoomCallPolicy.Action.JOIN, RoomCallPolicy.decide(true, false, true, true, false, true));
        assertEquals(RoomCallPolicy.Action.WAIT, RoomCallPolicy.decide(true, false, true, true, false, false));
    }
    @Test public void membershipAndExistingCallTakePrecedenceOverAdminRights() {
        assertEquals(RoomCallPolicy.Action.NOT_MEMBER, RoomCallPolicy.decide(false, false, false, true, false, true));
        assertEquals(RoomCallPolicy.Action.OTHER_CALL, RoomCallPolicy.decide(true, true, false, true, false, true));
        assertEquals(RoomCallPolicy.Action.NATIVE_UI, RoomCallPolicy.decide(true, false, true, true, true, true));
    }
}
