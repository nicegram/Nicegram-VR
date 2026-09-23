package org.telegram.vr.quest.speech;

/**
 * Nicegram VR — which sentence a failed recognition gets.
 *
 * <h3>Why this is a class and not a switch inside a screen</h3>
 *
 * There are two surfaces that run dictation now — the settings screen that exists to prove the
 * service works, and the composer button that people will actually use (plan.md P-12). Both have
 * to say the same thing about the same failure, and a second copy of a seven-way switch is a
 * second copy that will drift the first time a cause is added.
 *
 * <h3>Why every cause gets its own sentence</h3>
 *
 * "Something went wrong" tells a person wearing a headset nothing they can act on, and they
 * cannot open a log. Each failure here names what happened and implies the next move: configure
 * the service, speak again, check the connection, grant the microphone, fix the address.
 *
 * <p>Pure, so the mapping is a test rather than a claim.
 */
public final class DictationMessages {

    private DictationMessages() {
    }

    /**
     * @return the string resource for this failure, or 0 when the result was a success and
     *         therefore has no sentence to show
     */
    public static int forResult(SpeechToText.Result result) {
        if (result == null) {
            return my.nicegram.vr.R.string.vr_dictation_service_error;
        }
        if (result.ok()) {
            return 0;
        }
        if (result.failure == null) {
            return my.nicegram.vr.R.string.vr_dictation_service_error;
        }
        switch (result.failure) {
            case NOT_CONFIGURED:
                return my.nicegram.vr.R.string.vr_dictation_not_configured;
            case NOTHING_HEARD:
                return my.nicegram.vr.R.string.vr_dictation_empty;
            case NO_CONNECTION:
                return my.nicegram.vr.R.string.vr_dictation_network;
            case NO_PERMISSION:
                return my.nicegram.vr.R.string.vr_dictation_denied;
            case BAD_ADDRESS:
                return my.nicegram.vr.R.string.vr_dictation_bad_address;
            case INSECURE_ADDRESS:
                return my.nicegram.vr.R.string.vr_dictation_insecure;
            default:
                return my.nicegram.vr.R.string.vr_dictation_service_error;
        }
    }

    /**
     * True when the service answered with a detail worth repeating verbatim.
     *
     * <p>Separate from {@link #forResult} because only one branch takes an argument, and a
     * caller that forgets to check it would print "%s" at a person.
     */
    public static boolean hasServiceDetail(SpeechToText.Result result) {
        return result != null
                && !result.ok()
                && (result.failure == null || result.failure == SpeechToText.Failure.SERVICE_ERROR)
                && result.detail != null
                && !result.detail.isEmpty();
    }
}
