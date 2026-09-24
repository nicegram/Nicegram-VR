# The VR layer

What this fork adds to Telegram for Android, why each piece exists, and where it lives. Read
[CONTRIBUTING.md](../CONTRIBUTING.md) first for the constraints these follow from.

## The shape of the fork

Upstream is a working messenger. Folders, filters, per-chat notification settings, media, video,
documents and Opus voice messages already exist and are not reimplemented here. What a headset
needs and a phone does not is a short list, and it lives in `TMessagesProj_AppQuest`.

<a id="seams"></a>

**Four registries** sit inside shared code, all in `org.telegram.vr`, and every one of them is
inert unless the headset build fills it in — so every other flavour behaves exactly as upstream.
They exist because the dependency runs one way: the library module cannot name a class, a screen
or a string that lives in `TMessagesProj_AppQuest`.

| Registry | Read from | What it decides | Inert as |
|---|---|---|---|
| `VrPolicy` | `NotificationsController.appendMessage:1374`, `VoIPService.startRinging:4501` | whether a message may interrupt anyone, and whether a call may ring | returns every message untouched, rings every call |
| `VrEntryPoints` | `NotificationsSettingsActivity`, `LaunchActivity:6970`, `DialogsActivity`, `ChatActivityEnterView` | which headset-owned screens and controls appear in shared UI, which chat folder a session opens on, and what sits in the composer | null rows, `Integer.MIN_VALUE` folder, no first-run fragment, no composer control |
| `VrDisplay` | `AndroidUtilities.checkDisplaySize:2755` | the density multiplier, clamped to 0.5–4 | factor 1.0 |
| `VrBrand` | `LocaleController.getStringInternal:1472` and two `formatString` paths | what this application calls itself, and its mark | one volatile read and a null check |

**Keeping that count low is a goal rather than an accident** — each edit is a merge conflict
every time upstream moves. Two of the four were added only after a defect proved a registry was
the smaller change: `VrDisplay` because the density assignment happens inside upstream's own
method (A-01), `VrBrand` because the cloud language pack answers "Telegram" for `AppName` in
every language whatever `strings.xml` says (A-19, A-39).

**Every one of those call sites carries a comment naming this section.** A shared-code edit with
no explanation beside it is the one an upstream merge deletes without anybody noticing.

## Silence

The rule, in full: show a message when its sender is named, or its chat is named, or its text
carries a named word. Otherwise stay quiet and remember it.

An empty profile means silence for everyone, and that is what a fresh install has. This is the
product, not a default someone can flip: a headset is a place you went to concentrate.

**The boundary that matters.** The profile is a private `SharedPreferences` file of this build.
It is never written through `account.updateNotifySettings`, because that setting is account-wide
and synchronised — upstream's own `NotificationsController.muteUntil` calls it, which is exactly
why this build does not use it. Silencing a headset must not silence the phone in your pocket,
and the acceptance test for this feature is performed on a second device signed into the same
account.

Unread counters and dialog state are untouched. Silence is about what is *shown*.

`SilenceGate` fails **open**: any exception inside it lets the message through. A missed message
is worse than an extra one, and a client that went quiet because its filter crashed is
indistinguishable from a client that is simply broken.

## Digest

Suppressed messages are counted per account and dialog with a period start, so what the user sees on coming
back is "three chats since 14:20" rather than thirty banners delivered late.

A bounded recent-message ID cache prevents reconnect replays from increasing the count.
Changing the signed-in user in an account slot clears the previous user's previews.
Snapshots are copies; clearing one account leaves the other accounts untouched.

It lives in memory deliberately. After a restart the period begins at launch and anything
genuinely missed arrives through ordinary history sync — there is no second, staler copy of the
truth to keep consistent.

## Density and targets

The original design assumed a 1440×900 dp landscape panel. Device measurements contradicted
that assumption; it is not the current layout. The manifest requests a portrait 420×720 dp
panel with a 360×480 dp minimum; Horizon OS and the wearer control its actual size.

`VrDensity.factorForStep` uses four multipliers on system density: 0.85, 1.0, 1.25 and 1.54.
The default is 1.0. `VrDensityTest` checks the scale; runtime readings belong in
[running-on-a-headset.md](running-on-a-headset.md). The 64 dp floor applies where headset
controls request it, not automatically to all inherited Telegram controls. The composer's
fixed layout and full input comfort still need device review.

## No push

Horizon OS has no Google Play services, so there is no FCM. `NoPushProvider` reports no services,
which takes upstream's existing and honest path: it records `__NO_GOOGLE_PLAY_SERVICES__` and
registers an empty token.

Delivery therefore lasts as long as the client runs. A sleeping headset delivers nothing in real
time. The app states this on its first screen; a client that let a user discover it by missing
something would have earned the complaint.

## Keys

`TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` reach the code through `BuildConfig`, sourced from the
environment first and `local.properties` second. Missing values stop the build by name rather
than defaulting, because an empty `api_id` fails later, in a user's hands, and far more quietly.

Upstream's SafetyNet key and Google OAuth client id were cleared rather than inherited: they
belong to the official application, they are inert without Play services, and a key in an open
repository is a published key.
