package org.telegram.vr.quest.speech;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Nicegram VR — reading a recognition response.
 *
 * Separated from the transport so it can be tested on the JVM against real payloads, which is
 * where this kind of code actually goes wrong: an empty results array, an alternatives array
 * that exists but is empty, a transcript that is present and blank.
 *
 * The shape is {@code results[].alternatives[].transcript} — the contract the existing Nicegram
 * Android client already consumes, so a backend the company already runs works unchanged.
 */
public final class SpeechResponse {

    private SpeechResponse() {
    }

    public static SpeechToText.Result parse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR, "empty response");
        }
        final JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Throwable e) {
            return SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR, "not JSON");
        }
        // A service that reports its own error is not a service that heard nothing; saying so
        // sends the user to settings rather than to repeat themselves at a broken endpoint.
        final JSONObject error = root.optJSONObject("error");
        if (error != null) {
            final String message = error.optString("message", "");
            return SpeechToText.Result.failed(SpeechToText.Failure.SERVICE_ERROR,
                    message.isEmpty() ? null : message);
        }
        final JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return SpeechToText.Result.failed(SpeechToText.Failure.NOTHING_HEARD);
        }
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < results.length(); i++) {
            final JSONObject result = results.optJSONObject(i);
            if (result == null) {
                continue;
            }
            final JSONArray alternatives = result.optJSONArray("alternatives");
            if (alternatives == null || alternatives.length() == 0) {
                continue;
            }
            // The first alternative is the service's own best guess; showing several would make
            // the user do the ranking the service already did.
            final JSONObject best = alternatives.optJSONObject(0);
            if (best == null) {
                continue;
            }
            final String transcript = best.optString("transcript", "").trim();
            if (transcript.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(transcript);
        }
        final String out = text.toString().trim();
        return out.isEmpty()
                ? SpeechToText.Result.failed(SpeechToText.Failure.NOTHING_HEARD)
                : SpeechToText.Result.of(out);
    }

    /** HTTP status to failure, so the transport and the tests agree on one table. */
    public static SpeechToText.Failure fromStatus(int status) {
        if (status == 401 || status == 403) {
            return SpeechToText.Failure.SERVICE_ERROR;
        }
        if (status == 408 || status == 429 || status >= 500) {
            return SpeechToText.Failure.NO_CONNECTION;
        }
        return SpeechToText.Failure.SERVICE_ERROR;
    }
}
