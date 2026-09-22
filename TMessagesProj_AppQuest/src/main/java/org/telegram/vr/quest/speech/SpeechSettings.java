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

    /** What is wrong with an endpoint, decided before anything is sent to it. */
    public enum EndpointProblem {
        NONE,
        /** No scheme, no host, or a scheme that is not http(s). */
        NOT_A_URL,
        /** Plain http to somewhere that is not this device. */
        INSECURE
    }

    /**
     * Checked because the address is the user's own and nothing else checks it.
     *
     * <p>Dictation posts a recording of somebody's voice and sets an {@code Authorization}
     * header. Over plain {@code http} both cross the network readable by anyone on it — on a
     * headset that is usually a home or office Wi-Fi, and the screen that collects the address
     * promises the recording "goes nowhere else", which is true of the destination and says
     * nothing about the wire. The file already refuses to put the token in a query string for
     * the same class of reason; this is the other half of that decision.
     *
     * <p>Loopback is allowed, and deliberately: a recogniser running on the headset itself over
     * {@code http://127.0.0.1} never leaves the device, and refusing it would remove the one
     * configuration that needs no trust at all.
     *
     * <p>Pure and static so it is tested on the JVM rather than reasoned about.
     */
    public static EndpointProblem endpointProblem(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return EndpointProblem.NOT_A_URL;
        }
        final java.net.URI uri;
        try {
            uri = new java.net.URI(endpoint.trim());
        } catch (Throwable e) {
            return EndpointProblem.NOT_A_URL;
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.isEmpty() || !("http".equals(scheme) || "https".equals(scheme))) {
            return EndpointProblem.NOT_A_URL;
        }
        if ("https".equals(scheme) || isLoopback(host)) {
            return EndpointProblem.NONE;
        }
        return EndpointProblem.INSECURE;
    }

    /** `localhost`, the whole 127.0.0.0/8 block, and IPv6 `::1` however it was bracketed. */
    private static boolean isLoopback(String host) {
        final String bare = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        return "localhost".equals(bare)
                || "::1".equals(bare)
                || "0:0:0:0:0:0:0:1".equals(bare)
                || bare.startsWith("127.");
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
