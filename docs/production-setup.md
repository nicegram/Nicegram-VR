# Production setup

What separates a build you can sideload from a build you can ship, and what each part costs.

## The two credentials, and why a fork needs its own

`TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` identify the **application** to Telegram, not the
user. They are obtained at [my.telegram.org/apps](https://my.telegram.org/apps), which requires
signing in to a Telegram account and confirming a code — a person has to do it, and no build
tool or agent can do it for them.

**Register a pair for this client rather than borrowing one.** The temptation is obvious: a
sibling app already has a working `api_id`, it is right there, and the build would start
working this afternoon. The reason not to is that the `api_id` is the unit Telegram acts on. If
this client's traffic pattern draws a rate limit or a block — a new client, sideloaded, logging
people in for the first time, is exactly the shape that does — the consequence lands on every
application sharing that id, including whichever one has real users today. A test build should
not be able to take a shipping product down with it.

The same argument rules out borrowing a bot's credentials. A bot or userbot `api_id` was
registered for a different kind of application, is frequently shared across several small tools
already, and rotating it later breaks all of them at once.

Upstream asks for the same thing in its own README, and this build enforces it: there is no
default, and a missing value stops the build by name.

## Where the values live

Never in the repository. `local.properties` is in `.gitignore`; CI passes the same two names as
environment variables. Both paths are read by the same helper, environment first.

```sh
cp local.properties.example local.properties   # then fill in the two values
./gradlew :TMessagesProj_AppQuest:assembleQuestDebug
```

A value typed into a chat, a ticket or a commit message has left your control, because those
outlive the key. If your machine has a secret store, put it there and let it inject the
environment for the build; the repository does not care where the environment came from.

## Signing

Two configurations, and the difference matters more than it looks.

**Debug** is signed with `TMessagesProj/config/release.keystore`, which is checked in and whose
password is the word `android`, in `gradle.properties`, in the open. That is fine for a build
you push to your own headset over ADB.

**Release** refuses it. `assembleQuestStandalone` stops unless four values are supplied:

| Name | What it is |
|---|---|
| `NICEGRAM_VR_KEYSTORE` | path to a keystore file you generated and kept |
| `NICEGRAM_VR_KEYSTORE_PASSWORD` | its password |
| `NICEGRAM_VR_KEY_ALIAS` | the key inside it |
| `NICEGRAM_VR_KEY_PASSWORD` | that key's password |

```sh
keytool -genkey -v -keystore nicegram-vr.keystore -alias nicegram-vr \
        -keyalg RSA -keysize 4096 -validity 10000
```

The signing key is the one part of this setup that cannot be replaced later: whatever signs the
first published build is the only key that can ever update it, on any store. Back it up
somewhere that survives the machine, and do not put it in the repository.

## What "production" does not mean yet

A production application identity and signing key establish package identity, not device
readiness. Earlier revisions have launched on Quest 3; dictation is implemented in both
its own screen and the composer. This run still owes headset checks of the exact candidate.
Use [the release review](release-review-2026-09-24.md) for the current artifact and gates,
and [the device agenda](device-session.md) for runtime acceptance.
