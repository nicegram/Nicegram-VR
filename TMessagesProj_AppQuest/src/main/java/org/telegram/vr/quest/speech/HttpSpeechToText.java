package org.telegram.vr.quest.speech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Nicegram VR — the one transport, over plain HTTP, with no new dependency.
 *
 * It posts the audio and reads {@code results[].alternatives[].transcript}. Everything that can
 * be decided without a network lives in {@link SpeechResponse}, which is why the tests can be
 * honest about the cases that matter.
 */
public final class HttpSpeechToText implements SpeechToText {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Longer than connect on purpose: recognition takes as long as the phrase did. */
    private static final int READ_TIMEOUT_MS = 30_000;

    private final String endpoint;
    private final String token;
    private final String defaultLanguage;

    public HttpSpeechToText(SpeechSettings settings) {
        this(settings.endpoint(), settings.token(), settings.language());
    }

    HttpSpeechToText(String endpoint, String token, String language) {
        this.endpoint = endpoint;
        this.token = token;
        this.defaultLanguage = language;
    }

    @Override
    public Result recognize(byte[] audio, String mimeType, int sampleRate, String language) {
        if (endpoint.isEmpty()) {
            return Result.failed(Failure.NOT_CONFIGURED);
        }
        // Before the audio exists on any wire. A bad address used to surface as NO_CONNECTION,
        // which tells a person to check their Wi-Fi over a typo; a plain-http address used to
        // be sent, taking a recording of their voice and their token with it.
        switch (SpeechSettings.endpointProblem(endpoint)) {
            case NOT_A_URL:
                return Result.failed(Failure.BAD_ADDRESS);
            case INSECURE:
                return Result.failed(Failure.INSECURE_ADDRESS);
            default:
                break;
        }
        if (audio == null || audio.length == 0) {
            return Result.failed(Failure.NOTHING_HEARD);
        }
        HttpURLConnection connection = null;
        try {
            final String lang = language != null && !language.isEmpty() ? language : defaultLanguage;
            final StringBuilder url = new StringBuilder(endpoint);
            url.append(endpoint.contains("?") ? '&' : '?')
                    .append("sampleRate=").append(sampleRate);
            if (!lang.isEmpty()) {
                url.append("&language=").append(java.net.URLEncoder.encode(lang, "UTF-8"));
            }
            connection = (HttpURLConnection) new URL(url.toString()).openConnection();
            connection.setRequestMethod("POST");
            // The user approved this endpoint, not a redirected recipient.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", mimeType);
            final String token = this.token;
            if (!token.isEmpty()) {
                // A header, never the query string: a URL reaches logs, proxies and process
                // listings; a header does not.
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(audio);
            }
            final int status = connection.getResponseCode();
            if (status >= 300 && status < 400) return Result.failed(Failure.SERVICE_ERROR);
            final InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            final String body = read(stream);
            if (status >= 400) {
                final Result parsed = SpeechResponse.parse(body);
                // A service that explained itself is worth quoting; otherwise the status decides.
                if (parsed.detail != null) {
                    return parsed;
                }
                return Result.failed(SpeechResponse.fromStatus(status));
            }
            return SpeechResponse.parse(body);
        } catch (IOException e) {
            // Deliberately not logged with the exception's message: a connection error can carry
            // the URL, and the URL is configuration a user typed.
            return Result.failed(Failure.NO_CONNECTION);
        } catch (Throwable e) {
            return Result.failed(Failure.SERVICE_ERROR);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > 1_000_000) {
                    break;
                }
            }
            return out.toString("UTF-8");
        }
    }
}
