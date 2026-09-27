package org.telegram.vr.quest.rooms;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Capability credentials stay in memory. Never logs URLs, bodies or tokens. */
public final class RoomApi {
    public static final String GATEWAY = "https://nicegram-vr-room-beta-dshuv.ondigitalocean.app";
    public static String endpoint(String value) throws Exception {
        URI uri = new URI(value.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new Exception("INVALID_ENDPOINT");
        return "https://" + uri.getRawAuthority();
    }
    public static String[] invitation(String value) throws Exception {
        URI uri = new URI(value.trim());
        String base = endpoint(new URI(uri.getScheme(), uri.getRawAuthority(), "", null, null).toString());
        if (!"/room".equals(uri.getPath()) || uri.getQuery() != null || uri.getRawFragment() == null) throw new Exception("INVALID_INVITE");
        String[] parts = uri.getRawFragment().split("\\.", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]{43}") || !parts[1].matches("[A-Za-z0-9_-]{43}")) throw new Exception("INVALID_INVITE");
        return new String[]{base, parts[0], parts[1]};
    }
    /** True only for a well-formed invitation to the pinned gateway; anything else stays a link. */
    public static boolean isInvitation(String value) {
        if (value == null) return false;
        try { return GATEWAY.equals(invitation(value)[0]); } catch (Exception ignored) { return false; }
    }
    /** Room ID of a well-formed invitation, or null. */
    public static String invitationRoom(String value) {
        try { return invitation(value)[1]; } catch (Exception ignored) { return null; }
    }
    public static JSONObject post(String endpoint, String path, String token, JSONObject data) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(endpoint(endpoint) + path).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        int status;
        byte[] response;
        try {
            byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new Exception("HTTP_" + status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = input) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 32768) throw new Exception("TOO_LARGE");
                    bytes.write(buffer, 0, count);
                }
            }
            response = bytes.toByteArray();
        } catch (java.io.IOException error) {
            // Timeouts, DNS, TLS and resets: the caller retries or explains, never shows a class name.
            throw new Exception("NETWORK");
        } finally { connection.disconnect(); }
        return result(status, new String(response, StandardCharsets.UTF_8));
    }
    /** Turns a response into data or into the server's own error code; an edge page becomes HTTP_n. */
    static JSONObject result(int status, String text) throws Exception {
        JSONObject result;
        try { result = new JSONObject(text); } catch (org.json.JSONException error) { throw new Exception("HTTP_" + status); }
        if (status < 200 || status >= 300) throw new Exception(result.optString("error", "HTTP_" + status));
        return result;
    }
    private RoomApi() {}
}
