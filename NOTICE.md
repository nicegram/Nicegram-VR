# Notice

## Origin

Nicegram VR is a fork of [Telegram for Android](https://github.com/DrKLO/Telegram), imported at
revision `9552e5541e1274b9557c9832b204dbfcaf44b3dc` (version 12.10.3, build 7089, 2026-09-17).
The first commit in this repository is that tree, unmodified. Every commit after it is ours and
says what it changed.

## Licence

GPL-2.0, inherited and retained. See [LICENSE](LICENSE). This applies to the source inherited from Telegram and the source we added. Third-party
SDK binaries retain their own licenses; this statement does not relicense them.

## Name

This is **not** an official Telegram application and is not endorsed by Telegram. Upstream asks
every fork for three things and we honour all three:

1. Use your own `api_id` — this build refuses to compile without one of its own.
2. Do not use the name Telegram for the app, or make sure users understand it is unofficial.
   The application is called Nicegram VR; "Telegram" appears only where it names the service
   or the protocol.
3. Do not use the official Telegram API keys, SafetyNet key or OAuth client id. All three were
   cleared in the first commit of our own work; see `BuildVars.java`.

## Artwork

The launcher icon is **Nicegram's own default icon** — the monogram on black — copied from the
Nicegram Android client's `nicegram-features` module (`res/mipmap-*/ic_launcher_default*`, its
adaptive-icon XML and the `#000000` background colour). It is the icon that repository itself
calls `default`, so this client wears the same face as its sibling rather than a variant chosen
here. Names are kept unchanged so the files trace back byte for byte. Nicegram has no
paper-plane mark; its brand is the monogram, and the other variants in that module (gradient,
filled, mono, modern) are not shipped. Both applications belong to the same owner, which is
why the mark is reused rather than redrawn. It is not Telegram artwork: upstream's own `ic_launcher` is the
blue paper plane and is deliberately not used here, because point 2 above applies to the icon as
much as to the name.

## Third-party components

The build pulls thirteen submodules pinned to the exact revisions upstream pinned: FFmpeg,
BoringSSL, libvpx, dav1d, openh264, libyuv, Opus, Ogg, opusfile, tlottie, jlatexmath, TDLib and
a media3 fork. Each carries its own licence; none were modified by us.

## Experimental spatial dependency

The room-alpha branch links Meta Spatial SDK 0.14.0 (`meta-spatial-sdk`, toolkit, VR and
transitive artifacts). Their Maven POMs name the
[Meta Platform Technologies SDK License Agreement](https://developers.meta.com/horizon/licenses/oculussdk/).
Copyright © Meta Platform Technologies, LLC and its affiliates. All rights reserved.
These dependencies are fetched by Gradle; no SDK binaries or sample assets are committed.

The agreement includes an open-source licensing restriction in section 1.2.8. Compatibility
with this GPL client has NOT been established. This is a concrete distribution gate for the
experimental combined APK: do not upload it to a public release or Store as a licensed final
product until that compatibility is resolved. A GPL-compatible renderer/IPC separation or
appropriate permission must be assessed before distribution; source publication alone does
not resolve binary licensing. Read 2026-09-26; this is a recorded dependency issue, not a legal
conclusion about an approved exception.
