# Nicegram VR

A Telegram client you can actually use inside a headset.

Nicegram VR is an open-source, unofficial Telegram client for **Meta Quest 3 and Quest 3S**,
built as a 2D Horizon OS application. It is a fork of
[Telegram for Android](https://github.com/DrKLO/Telegram) and, like every other Nicegram
client, its source is open — see [NOTICE.md](NOTICE.md) for the provenance and the licence.

> **Release candidate review, 24 September 2026.** The signed candidate, checks and
> remaining publication steps are tracked in [the release review](docs/release-review-2026-09-24.md).
> Published alpha.3 predates important fixes; do not use it for this review.
> The current JVM suite has 97 tests across 21 classes. Automated tests do not establish
> headset readiness: this run has no connected Quest, and calls, dictation quality and
> sustained performance still require the [device session](docs/device-session.md).
> Nothing was submitted to Horizon Store in this run.

## Team and contact

| | Who | How to reach |
|---|---|---|
| Maintainer, product decisions | **Sergey S** — [@sshlg](https://github.com/sshlg) | [sshlg.me](https://sshlg.me) |
| Anything about this client | the Nicegram team | [t.me/nicegramchat](https://t.me/nicegramchat) |
| Nicegram itself, downloads, help | — | [nicegram.me](https://nicegram.me) · [wiki.nicegram.me](https://wiki.nicegram.me) |
| A security issue | — | [SECURITY.md](SECURITY.md) — please do not open a public issue first |

All four links above were fetched and answer 200. Nicegram is built by Appvillis; this repository is
one client of several, and the rest of the team's areas — Android, backend, design, localisation,
web and SEO — are coordinated through the community chat rather than through individual
addresses.

## Why it exists

People who spend hours in a headset take it off to answer one message. That is the whole
problem. Everything in this fork follows from it:

- **It is quiet.** Nothing is shown until you name who may interrupt you — a person, a chat, or
  a word. An empty list means silence for everyone, and that is what a fresh install is.
- **You answer by speaking.** Typing with a ray on a virtual keyboard is the worst part of any
  headset app. Dictation fills the input field; you check the text and press send.
- **You can hit things.** Headset settings use enlarged controls and selectable interface sizes. Some
  inherited Telegram controls remain smaller; the headset action bar is still planned.

## What it does not promise

This build excludes Firebase and provides **no push transport**. Messages arrive while the
client is running. A sleeping headset delivers nothing in real time, and what accumulated is
shown on the next launch. The app explains this limitation on its first screen.

## Build

You need two credentials of your own and nothing else.

```sh
git clone --recurse-submodules https://github.com/nicegram/Nicegram-VR.git
cd Nicegram-VR
cp local.properties.example local.properties
# fill in TELEGRAM_APP_ID and TELEGRAM_APP_HASH — https://core.telegram.org/api/obtaining_api_id
./gradlew -PquestAbiOnly :TMessagesProj_AppQuest:assembleQuestDebug
adb install -r TMessagesProj_AppQuest/build/outputs/apk/quest/debug/nicegram-vr.apk
```

Requirements: JDK 21, Android SDK 36, NDK `27.2.12479018`, CMake `3.22.1`. The clone is large —
thirteen submodules including FFmpeg and BoringSSL, pinned to the revisions upstream pinned. The
native build targets `arm64-v8a` only, because that is what a Quest is.

Missing keys stop the build at the first compile task with a message naming them.
`local.properties` is in `.gitignore` and must stay there. Registering your own `api_id`
rather than borrowing one, and signing a release with a key that is not the development
keystore in this repository: [production setup](docs/production-setup.md).

## Layout

| Path | What it is |
|---|---|
| `TMessagesProj/` | upstream Telegram library, kept as close to upstream as possible |
| `TMessagesProj_AppQuest/` | the headset build: everything of ours that can live apart, lives here |
| `TMessagesProj/src/main/java/org/telegram/vr/` | shared registries for notification policy, display, branding and UI entry points |
| `TMessagesProj_App*/` | upstream's other application flavours, untouched |

Our edits to shared Telegram code are deliberately few, each marked with a `Nicegram VR:`
comment saying why. The registries and their callers are documented in [the VR layer](docs/vr-layer.md).
Messages, calls, display sizing, branding and composer entry points now have separate hooks.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) — it is short, and the constraints in it are the product
rather than house style. Security and credential handling: [SECURITY.md](SECURITY.md).

## Licence

GPL-2.0, inherited from upstream and retained. Not an official Telegram application; not
endorsed by Telegram.
