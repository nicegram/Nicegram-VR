# Notice

## Origin

Nicegram VR is a fork of [Telegram for Android](https://github.com/DrKLO/Telegram), imported at
revision `9552e5541e1274b9557c9832b204dbfcaf44b3dc` (version 12.10.3, build 7089, 2026-09-17).
The first commit in this repository is that tree, unmodified. Every commit after it is ours and
says what it changed.

## Licence

GPL-2.0, inherited and retained. See [LICENSE](LICENSE). This applies to the whole work,
including the parts we added.

## Name

This is **not** an official Telegram application and is not endorsed by Telegram. Upstream asks
every fork for three things and we honour all three:

1. Use your own `api_id` — this build refuses to compile without one of its own.
2. Do not use the name Telegram for the app, or make sure users understand it is unofficial.
   The application is called Nicegram VR; "Telegram" appears only where it names the service
   or the protocol.
3. Do not use the official Telegram API keys, SafetyNet key or OAuth client id. All three were
   cleared in the first commit of our own work; see `BuildVars.java`.

## Third-party components

The build pulls thirteen submodules pinned to the exact revisions upstream pinned: FFmpeg,
BoringSSL, libvpx, dav1d, openh264, libyuv, Opus, Ogg, opusfile, tlottie, jlatexmath, TDLib and
a media3 fork. Each carries its own licence; none were modified by us.
