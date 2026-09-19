package org.telegram.vr.quest.speech;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Nicegram VR — where the recognition service is named, and by whom.
 *
 * The user enters both values and they live on the device. Nothing is compiled into the
 * application, and the reason is structural rather than cautious: this client is open source,
 * so a key built into it is a key published with it. A shared key would also be one rate limit
 * for every install.
 *
 * The token is a credential. It is never logged, never put in an exception message, and the
 * screen that shows it masks all but the last four characters.
 */
public final class SpeechSettings {

    private static final String FILE = "nicegram_vr_speech";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_LANGUAGE = "language";

    private final SharedPreferences prefs;

    public SpeechSettings(Context context) {
        this.prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String endpoint() {
        return prefs.getString(KEY_ENDPOINT, "").trim();
    }

    public String token() {
        return prefs.getString(KEY_TOKEN, "").trim();
    }

    /** BCP-47, or empty to let the service decide. */
    public String language() {
        return prefs.getString(KEY_LANGUAGE, "").trim();
    }

    public boolean isConfigured() {
        return !endpoint().isEmpty();
    }

    public void setEndpoint(String value) {
        prefs.edit().putString(KEY_ENDPOINT, value == null ? "" : value.trim()).apply();
    }

    public void setToken(String value) {
        prefs.edit().putString(KEY_TOKEN, value == null ? "" : value.trim()).apply();
    }

    public void setLanguage(String value) {
        prefs.edit().putString(KEY_LANGUAGE, value == null ? "" : value.trim()).apply();
    }

    /**
     * What the settings screen shows: enough to recognise which token is set, never enough to
     * read it back off a shoulder or out of a screen recording.
     */
    public static String maskedToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (token.length() <= 4) {
            return "····";
        }
        return "···· " + token.substring(token.length() - 4);
    }

    /**
     * The host a user is about to send their voice to, for the sentence that names the
     * recipient before the first recording. Never the full URL: a path can carry a token.
     */
    public static String endpointHost(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "";
        }
        try {
            final String host = new java.net.URI(endpoint).getHost();
            return host == null ? "" : host;
        } catch (Throwable e) {
            return "";
        }
    }
}
