package org.telegram.vr.quest.speech;

/**
 * Nicegram VR — turning speech into text a person can check before it is sent.
 *
 * Deliberately an interface with one implementation, because the recognition service is the
 * user's choice and not the client's. Nothing here is compiled in: no endpoint, no token.
 *
 * Telegram's own messages.transcribeAudio cannot serve this, and the reason is worth carrying
 * next to the code so nobody rediscovers it: it takes a peer and a msg_id, so it transcribes a
 * message that has already been SENT, and TranscribeButton gates it on Premium. Dictation
 * happens before anything is sent and has to work for everyone.
 */
public interface SpeechToText {

    /** Why a recognition did not produce text. Each maps to a sentence and a way out. */
    enum Failure {
        /** The microphone was never granted; the way out is the system prompt. */
        NO_PERMISSION,
        /** The service answered, and heard nothing. The way out is to say it again. */
        NOTHING_HEARD,
        /** The request never arrived. The way out is a voice message instead. */
        NO_CONNECTION,
        /** No endpoint or token has been entered yet. The way out is the settings screen. */
        NOT_CONFIGURED,
        /** The service answered with an error of its own. The way out is to look at settings. */
        SERVICE_ERROR
    }

    final class Result {
        public final String text;
        public final Failure failure;
        /** Present on SERVICE_ERROR when the service said something worth showing. */
        public final String detail;

        private Result(String text, Failure failure, String detail) {
            this.text = text;
            this.failure = failure;
            this.detail = detail;
        }

        public static Result of(String text) {
            return new Result(text, null, null);
        }

        public static Result failed(Failure failure) {
            return new Result(null, failure, null);
        }

        public static Result failed(Failure failure, String detail) {
            return new Result(null, failure, detail);
        }

        public boolean ok() {
            return text != null;
        }
    }

    /**
     * Blocking; callers run it off the main thread.
     *
     * @param audio      encoded audio bytes
     * @param mimeType   what the bytes are, for the request
     * @param sampleRate samples per second, which most services need told rather than sniffed
     * @param language   BCP-47 tag, or null to let the service decide
     */
    Result recognize(byte[] audio, String mimeType, int sampleRate, String language);
}
