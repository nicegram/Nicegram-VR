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
| `VrPolicy` | `NotificationsController.appendMessage:1374` | whether a message may interrupt anyone | returns every message untouched |
| `VrEntryPoints` | `NotificationsSettingsActivity`, `LaunchActivity:6970`, `DialogsActivity` | which headset-owned screens and controls appear in shared UI, and which chat folder a session opens on | null rows, `Integer.MIN_VALUE` folder, no first-run fragment |
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

Suppressed messages are counted per dialog with a period start, so what the user sees on coming
back is "three chats since 14:20" rather than thirty banners delivered late.

It lives in memory deliberately. After a restart the period begins at launch and anything
genuinely missed arrives through ordinary history sync — there is no second, staler copy of the
truth to keep consistent.

## Density and targets

The panel is 1440x900 dp at 1.3 m covering 52 degrees. That makes it 1.268 m wide, so one dp is
0.881 mm, or 2.33 arc-minutes. Upstream's type is calibrated for a screen at half a metre; at
1.3 m it subtends roughly half of what sustained reading needs. The whole scale is therefore
multiplied once, by 1.54, which turns 13 dp body text into 20 dp — about 47 arc-minutes.

Three density steps scale text and rows: 0.85, 1.0, 1.2. They do **not** scale the hit-target
floor. Ray jitter is 0.5–1.0 degrees and is a property of the hand, not of a preference, so the
minimum target stays 64 dp (56.4 mm, 2.49 degrees) at every step. `VrDensity.minTargetPx()` is
what enforces it. This is the only setting in the client that is deliberately clamped, and the
reason is worth repeating: choosing the compact step trades legibility for how much fits, which
is the user's to trade; a smaller target is a trade nobody asked for.

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
