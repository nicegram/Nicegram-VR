package org.telegram.messenger;

/**
 * Nicegram VR — the push provider for a device that has none.
 *
 * Horizon OS ships without Google Play services, so there is no FCM. Upstream already handles
 * that honestly: {@link ApplicationLoader#initPushServices()} records
 * {@code __NO_GOOGLE_PLAY_SERVICES__} and registers an empty token when a provider reports no
 * services. This class exists so that path is taken deliberately rather than by the absence of
 * a class.
 *
 * The consequence is a product decision, not an accident, and it is stated on the first screen
 * of the app: messages arrive while the client is running. A sleeping headset delivers nothing
 * in real time, and what accumulated is shown on the next launch.
 */
public class NoPushProvider implements PushListenerController.IPushListenerServiceProvider {

    public static final NoPushProvider INSTANCE = new NoPushProvider();

    private NoPushProvider() {
    }

    @Override
    public boolean hasServices() {
        return false;
    }

    @Override
    public String getLogTitle() {
        return "Horizon OS (no push service)";
    }

    @Override
    public void onRequestPushToken() {
        // Nothing to request. Deliberately empty: see the class comment.
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_FIREBASE;
    }
}
