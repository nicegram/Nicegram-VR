package org.telegram.vr.quest.rooms;
import java.net.URI;
/** Only an explicit Nicegram bot confirmation can bind the room identity to this account. */
public final class RoomAuthProtocol {
    public static String loginUrl(String raw) throws Exception {
        URI uri = new URI(raw);
        if (!"https".equals(uri.getScheme()) || !"t.me".equals(uri.getHost()) || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getFragment() != null || !"/nicegram_auth_bot".equals(uri.getPath())
                || uri.getRawQuery() == null || !uri.getRawQuery().matches("start=[a-f0-9]{64}")) throw new Exception("INVALID_AUTH_LINK");
        return raw;
    }
    public static boolean sameAccount(long expected, String actual) { return Long.toString(expected).equals(actual); }
    private RoomAuthProtocol() {}
}
