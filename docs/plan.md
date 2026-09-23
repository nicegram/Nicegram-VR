# Plan — remaining work, decomposed

> **Status, 19 September 2026, later the same day.** Done and pushed: P-01 … P-08, **P-11**
> (dictation's provider layer and its own screen) and P-17. P-15 has its seam and not its
> wiring. The headset build now also starts in **one column** and carries the Nicegram name and
> icon — both from what the panel actually looked like, not from a plan.
>
> Open: **P-09** (device protocol — the first run happened; what is left is behind sign-in),
> **P-10** (sign in by code), **P-12** (the composer trigger, the second half of dictation),
> **P-13** (message action bar), **P-14** (gallery shortcuts), **P-16** (store metadata, blocked
> on VRQ-001), **P-19** (the rail of chat avatars), **P-21** (notifications and calls on a
> platform with no push) and **P-22** (draw in the air and send it).
>
> **Closed since, 21–22 September:** **P-20** (round videos — the size was frozen from the
> DISPLAY's configuration and never recomputed for the PANEL; A-27), **P-15** (the
> `DialogsActivity` half, and the setting beside it), and **P-18** as far as code can take it —
> all five strings that could never be translated are fixed (A-31) and the Russian is an upload
> artifact in `language-pack/`, so what remains of it is one upload by whoever owns the pack.
>
> **This build has now run on a Quest 3**, 19 September 2026: installed in 12 s, started without
> a crash, and the density fix proved itself in the running interface — a button declared 56 dp
> measured 108 px, against the 1.925 px/dp this build intends. Readings in
> [running-on-a-headset.md](running-on-a-headset.md). Every acceptance *behind sign-in* is still
> owed, and that is most of them.

**Where this backlog lives, for an agent or a manager changing it.** This file is the
engineering plan. The *task registry* — statuses, owners, acceptance — is
`nicegram-product-workspace/public/projects/nicegram-vr/` (private), where this project is
`WORK-018` and its tasks are `VRQ-001…020`; the OKR mirror is
`dataroom/docs/okr/backlog/nicegram-vr-quest-client.md`. **Status is changed in the workspace,
never in the mirror**, and `npm run check` there computes the OKR totals from those files, so
progress is never sent anywhere separately. How to add or edit a task:
`nicegram-product-workspace/docs/MANAGER-GUIDE.md`.

Written for an agent with no prior context. Each task says what to do, where exactly, what
"done" means in a form that can be checked, and what has already gone wrong in that area so it
is not repeated. Findings referenced as `A-nn` are in [audit-2026-09-19.md](audit-2026-09-19.md).
Task IDs `VRQ-nnn` are the workspace tasks this plan maps onto.

**Read before touching anything:**

- [CONTRIBUTING.md](../CONTRIBUTING.md) — the constraints are the product, not house style.
- [vr-layer.md](vr-layer.md) — what the headset layer is and where it lives.
- The two seams into shared code: `org.telegram.vr.VrPolicy` and `org.telegram.vr.VrEntryPoints`.
  Every new headset behaviour that shared UI must reach goes through a registry like these,
  filled in `QuestApplicationLoader.onCreate`, never through a direct reference from
  `TMessagesProj` to `TMessagesProj_AppQuest` — the dependency runs the other way and the
  build will tell you so.
- Shared-code edits carry a `Nicegram VR:` comment saying why. Count them; keep the count low.
- `git add -A` in a clone with uninitialised submodules stages thirteen deletions. Stage by
  name. Check `git diff --cached --name-status | grep '^D'` before every commit.

**Build and verify loop** (all tasks use it):

```sh
python3 ~/DATA/project-observatory/tools/use_secret.py run --env prod nicegram-vr \
  TELEGRAM_APP_ID,TELEGRAM_APP_HASH -- ./gradlew --no-daemon \
  :TMessagesProj_AppQuest:testQuestDebugUnitTest :TMessagesProj_AppQuest:assembleQuestDebug
```

A change to `BuildConfig` inputs or to `TMessagesProj` Java recompiles the library and takes
about seven minutes; a change confined to the headset module takes under a minute. Read the
result off the APK (`aapt2 dump badging`, `unzip -l … lib/`), not off the log.

---

## Order

Dependencies, not appetite. Nothing after P-01 and P-02 is worth building on a client whose
scale is lost and whose silence has a hole in it.

```
P-01 density fix ─┐
P-02 gate at the choke point ─┼─► P-03 digest ─► P-04 first-run screen ─► P-05 exceptions v2
P-06 repository hygiene ──────┘                                           P-07 headset settings
                                                                            P-08 performance profile
P-09 device protocol (needs a headset; runs after P-01/P-02, repeats after each later task)
P-10 sign-in by code ─► P-11 dictation A (provider) ─► P-12 dictation B (composer)
P-13 action bar · P-14 gallery shortcuts · P-15 headset view · P-16 store metadata (needs VRQ-001)
P-17 docs reconciliation (runs alongside everything)
```

---

## P-01 · Apply the density factor where density is actually set — `VRQ-015`

> **DONE**, `72f2f218`. The factor is applied inside `checkDisplaySize` through the `VrDisplay`
> registry — `AndroidUtilities.java:2755`, `density *= org.telegram.vr.VrDisplay.factor();` —
> which clamps anything outside 0.5–4 back to 1. Closes A-01. *Device check still owed.*

**Fixes:** A-01 (critical). **Estimate:** half a day.

**Why.** `VrDensity.apply()` multiplies `AndroidUtilities.density` in `Application.onCreate`,
and `AndroidUtilities.checkDisplaySize` overwrites it from the display metrics at
`AndroidUtilities.java:2751`, called from `LaunchActivity.java:409` before any UI exists.
The scale the fork is named for is currently lost before the first screen.

**Where.**
- `TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java:2748-2756` —
  `checkDisplaySize`. The assignment is at `:2751`; the method then compares `oldDensity` with
  `newDensity` to decide `Theme.reloadAllResources`. The factor must be applied **between the
  assignment and that comparison**, or the reload logic will see a change on every call.
- `TMessagesProj/src/main/java/org/telegram/vr/` — add `VrDisplay.java`, a registry in the
  shape of `VrPolicy`: `interface Scale { float factor(); }`, `install(Scale)`, and
  `static float factor()` returning `1f` when nothing is installed.
- `TMessagesProj_AppQuest/.../QuestApplicationLoader.java:23` — replace the direct
  `VrDensity.apply(this)` with `VrDisplay.install(...)` returning `VrDensity.factor(context)`.
- `TMessagesProj_AppQuest/.../vr/quest/VrDensity.java` — `apply()` becomes `factor()`; keep
  `minTargetPx()` and the step storage unchanged.

**Steps.**
1. Create `VrDisplay` (shared, inert, one `Nicegram VR:` comment).
2. In `checkDisplaySize`, one line after `:2751`: `density *= org.telegram.vr.VrDisplay.factor();`
   with the comment: *Nicegram VR: headset scale, 1 on every other flavour; applied here because
   this is the only place density is assigned, and applied before the reload comparison below.*
3. Rewire the loader; delete the now-dead multiplication.
4. Add a JVM test in the headset module asserting `VrDensity.factor()` for the three steps
   equals `1.54 × {0.85, 1.0, 1.2}` and that `MIN_TARGET_DP` is unchanged by the step.

**Done when.**
- `adb logcat` after launch shows a line you add at debug level: `VrDisplay: density <raw> → <scaled>`
  with the scaled value ≈ raw × 1.54 at the normal step. *Device.*
- A `dp(64)` view measured with layout inspector or a debug overlay is 64 × scaled px. *Device.*
- Rotating / resizing the panel (`onConfigurationChanged`, `LaunchActivity.java:7117`) keeps the
  scale — check the log line fires again with the same factor.
- Every other flavour still compiles and its density is unchanged (factor 1).

**Traps.** Do not apply the factor in `LaunchActivity` after `checkDisplaySize`: the method is
also called on configuration change and from `ApplicationLoader.java:382`, and you would be
chasing three call sites forever. Do not call `Theme.reloadAllResources` yourself.

---

## P-02 · Move the silence gate to the single choke point — `VRQ-016`

> **DONE**, `72f2f218`. The gate sits inside `appendMessage`
> (`NotificationsController.java:1367`), before the de-duplication loop, at
> `NotificationsController.java:1374` — so neither caller can be added to later without it.
> Closes A-02. *Device check still owed.*

**Fixes:** A-02 (high). **Estimate:** half a day.

**Why.** Notifications enter the queue through `appendMessage`
(`NotificationsController.java:1371`), called from `processNewMessages` (`:1245`) *and* from
`processLoadedUnreadMessages` (`:1583`, `:1671`). The gate is only on the first. The second
runs on every launch and reconnect — on a headset, the main path — and shows banners past
the profile.

**Where.**
- `NotificationsController.java:1055` — remove the current `VrPolicy.filterForDisplay` call.
- `NotificationsController.java:1371` — at the top of `appendMessage`, before the duplicate
  scan: `if (!org.telegram.vr.VrPolicy.allows(currentAccount, messageObject)) return;` with the
  usual comment.
- `TMessagesProj/src/main/java/org/telegram/vr/VrPolicy.java` — add `static boolean allows(int,
  MessageObject)` that returns `true` with no gate, calls the gate inside `try` and returns
  `true` on any throwable (fail **open**, same as today), and calls `onSuppressed` when the
  answer is false. Keep `filterForDisplay` or delete it; do not leave two paths.

**Done when.**
- Unit: a JVM test of `VrPolicy.allows` with a fake `Gate` that throws returns `true`.
- Device: sleep the headset for ten minutes while two chats receive messages; wake it. Nothing
  is shown; the digest (P-03) lists both chats. Then add one of them as an exception and
  repeat: exactly one notification. *Device.*
- `total_unread_count` and the launcher badge are unchanged by suppression — in
  `processLoadedUnreadMessages` they are computed from the `dialogs` argument (`:1686`,
  `:1691`), not from `pushMessages`. Say so in the code comment; the next reader will ask.

**Traps.** `appendMessage` is private and de-duplicates by message id; put the gate before the
loop, not after, or a suppressed message that was already in the queue leaks through the early
`return`.

---

## P-03 · Make the digest real — `VRQ-006`

> **DONE**, `31a8273b`. `DigestActivity.java:39` is a real screen and is reachable; `lastText`
> is a truncated `String` and entries cap at 200 dialogs, which is what A-03 was about.

**Fixes:** A-03 (high). **Estimate:** two to three days.

**Where.**
- `TMessagesProj_AppQuest/.../vr/quest/Digest.java` — `lastText` becomes `String`, set to
  `String.valueOf(message.messageText)` truncated to 200 chars; add `since` as an epoch and a
  `sinceLabel()`; cap entries at 200 dialogs (drop the oldest).
- New `DigestActivity` in the headset module: a `BaseFragment` modelled on
  `SilenceRulesActivity`; rows are dialog name (see P-05 for loading names), count and last
  line; header shows the period; action "mark all read" calls `clear()` and returns.
- Reaching it: extend `VrEntryPoints` with a second row (`installDigestRow`) and expose a
  `value()` on `SettingsRow`; consume it in `NotificationsSettingsActivity` next to the
  silence row. A header icon in `DialogsActivity` is the eventual home (SCR-08) but is a
  larger shared edit — do the settings row first, the icon in P-07.

**Done when.**
- After P-02's device test the digest screen shows two chats with correct counts and the last
  text of each. *Device.*
- Empty state reads *«За это время никто не писал»* / *"Nobody wrote in that time"* — a
  sentence, never zeros. Strings via the registry (P-17).
- No `Spannable` is retained: `Entry.lastText` is a `String` and a test asserts the type.

---

## P-04 · First-run screen "It is quiet here" — `VRQ-005`

> **DONE.** `FirstRunActivity` — three sentences and the one that cannot be dropped: while the
> app is closed, nothing arrives. Shown at most once per install, through
> `VrEntryPoints.installFirstRun`. Made scrollable on 23 September (A-40).

**Fixes:** the product's honesty on first launch; the delivery-limit disclosure. **Estimate:** one day.

**Where.** `LaunchActivity.java:1089-1091` returns `LoginActivity` or `IntroActivity` as the
first fragment. After a successful sign-in the client lands in `DialogsActivity`. Add a
one-time headset screen presented once, from the headset module, via a new
`VrEntryPoints.firstRun()` hook consulted at that landing (one shared edit, guarded by a
`SharedPreferences` flag in the headset module).

**Content** (copy from the registry keys `silence.intro.*`): three lines — nothing arrives by
default; name people, chats and words worth interrupting you; **while the app is closed,
messages do not arrive and appear here on the next launch**; actions *Got it* and *Name
exceptions* → `SilenceRulesActivity`.

**Done when.** Shown exactly once per install; `adb uninstall my.nicegram.vr` and reinstall
shows it again; skipping it never re-shows it; the third line is present verbatim. *Device.*

---

## P-05 · Exceptions screen, second pass — `VRQ-005`

> **DONE**, `31a8273b`. `SilenceRulesActivity` has avatars, two sections and names loaded from
> local storage with a placeholder until they land; the Notifications row carries its own count.
> Closes A-12, A-13, and A-16 for the screens that exist.

**Fixes:** A-12, A-13, A-16. **Estimate:** one day.

**Where.** `TMessagesProj_AppQuest/.../vr/quest/SilenceRulesActivity.java`.
- Split "People" and "Chats" into two headers (registry keys `silence.list.people`,
  `silence.list.chats`); words stay third.
- Names: `getMessagesController().getUser(id)` / `getChat(-id)`; when null, call
  `getMessagesController().loadFullUser`/`loadFullChat` or the dialogs loader and render a
  placeholder *«Загружается…»* rather than an empty row; rebuild on
  `NotificationCenter.updateInterfaces`.
- Use an avatar-bearing cell (`org.telegram.ui.Cells.UserCell` or `ProfileSearchCell`) for
  people and chats.
- Override `getThemeDescriptions()` as upstream fragments do.
- `VrEntryPoints.SettingsRow.value()` returns the exception count; the Notifications row shows
  it (`NotificationsSettingsActivity.java` case 5, where the row is bound).

**Done when.** A freshly added exception shows a name and avatar immediately; removing it
asks first; the Notifications row reads "Exceptions to the silence · 3". No empty rows in a
profile of ten people. *Device.*

---

## P-06 · Repository hygiene — `VRQ-017`

> **DONE except one operator decision.** A-04 (`7cdabe86`), A-05, A-11, A-14 and A-17 are all
> closed — secrets travel from the vault on stdin, the build refuses api_id 4 by name, and the
> concurrency bump has a test that runs 4000 of them.
>
> **A-06 is still open and is not an engineering call:** whether the other flavours (Huawei,
> the standard build) stay unmaintained here or get the same fail-by-name treatment. It is
> question **Q-03** in the operator list at the end of this file.

**Fixes:** A-04, A-05, A-06, A-11, A-14, A-17. **Estimate:** one day.

1. **README.md:10** — replace "builds and installs" with "builds; nothing has run on a headset
   yet" until P-09 passes.
2. **CI** (`.github/workflows/build.yml`): `gradle/actions/setup-gradle@v4` instead of
   `actions/gradle-build-action@v3` (line 39); `actions/checkout` with
   `submodules: recursive` **and** `fetch-depth: 1` plus `git submodule update --depth 1` in a
   step of its own; cache `TMessagesProj/.cxx` and `~/.gradle/caches` keyed on the
   `.gitmodules` pins and `TMessagesProj/jni/**`; add repository secrets `TELEGRAM_APP_ID` and
   `TELEGRAM_APP_HASH` (an operator action — `gh secret set`, values on stdin, never in a
   command line). The workflow is green when the run finishes under the timeout with a
   cached native build.
3. **Other flavours** (`TMessagesProj/build.gradle:145`): decide and record. The recommended
   answer is *unmaintained here*: remove `TMessagesProj_App`, `_AppHockeyApp`, `_AppHuawei`
   and `_AppStandalone` from `settings.gradle`, keep the directories for upstream merges, and
   say so in README. If they are kept buildable instead, give them the same fail-by-name
   check the headset module has.
4. **Sample-credential guard**: in the headset module's key check, reject `TELEGRAM_APP_ID == 4`
   and the upstream sample hash (read it from the import commit, do not paste it) with a
   sentence saying it is Telegram's public sample and will not connect.
5. `SilenceStore.generation` → `AtomicInteger` (`SilenceStore.java:36`, `:56`, `:61`).
6. `lintOptions {}` → `lint {}` in `TMessagesProj_AppQuest/build.gradle`.

**Done when.** CI green on `main`; `assembleQuestDebug` unchanged; the README sentence is true.

---

## P-07 · Headset settings screen — `VRQ-012`

> **DONE.** `VrSettingsActivity` — 615 lines: interface size, layout, motion, the start folder,
> dictation and a reset. Reached from the registry row rather than from a hardcoded reference,
> because shared code cannot name this module.

**Fixes:** density is a setter with no UI; SCR-27. **Estimate:** two days.

**Where.** New `VrSettingsActivity` in the headset module, reached from a row in
`NotificationsSettingsActivity`'s neighbour — better: a row in the main settings list. That is
a larger shared edit (`ProfileActivity`); do it through `VrEntryPoints` with a third row kind
and one marked insertion.

**Rows.** Density step (three values → `VrDensity.setStep`, applied at next start; say so on
screen); panel distance (stored only, consumed by P-08 measurement notes); media autoplay
(`LiteMode.toggleFlag(FLAG_AUTOPLAY_VIDEOS|FLAG_AUTOPLAY_GIFS, …)` — `LiteMode.java:48-49`,
`:165`); animated stickers in chat (`FLAG_ANIMATED_STICKERS_CHAT`, `:24`); link to exceptions;
link to digest; reset to defaults; sign out (upstream's existing flow).

**Done when.** Each toggle persists across restart; density change is announced as taking
effect on next launch and does; the screen itself meets the 64 dp floor at all three steps.
*Device.*

---

## P-08 · Performance profile defaults — `VRQ-013`

> **DONE.** `VrPerformance.applyDefaultsOnce` (`VrPerformance.java:28`) sets them once per
> install, as defaults rather than locks: autoplay off is the cheapest way to keep 60 fps, and
> the user can turn it back on. *The frame-rate reading itself is still owed on a device.*

**Fixes:** the 60 fps store gate. **Estimate:** one day plus measurement.

**Where.** `QuestApplicationLoader.onCreate`, first launch only (a `SharedPreferences` flag):
`LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, false)`, same for GIFs; leave
`FLAG_ANIMATED_STICKERS_CHAT` on but cap concurrent animations — find the cap in
`RLottieDrawable`/`AnimatedEmojiDrawable` before deciding; document what was found.

**Also decide A-08 here.** After P-01, read `getResources().getConfiguration().smallestScreenWidthDp`
on the device and log it. If it is below 600, upstream selects the single-column layout
(`R.bool.isTablet` is true only in `values-sw600dp`/`-sw720dp`, `AndroidUtilities.java:2949`).
Then choose: force the two-column layout by overriding `R.bool.isTablet` in the headset
module's `res/values/bools.xml` (module resources win), or accept single-column. Write the
measured number and the choice into `docs/vr-layer.md`.

**Done when.** Frame time in the chat list and in a chat with three animated stickers is
recorded on Quest 3 and 3S with the platform profiler, with date and build, and is at or
above 60 fps. *Device.*

---

## P-09 · Device protocol — `VRQ-014`

> **DONE.** `docs/running-on-a-headset.md` — 231 lines: adb over Wi-Fi, the install, what to
> read and in what order, and why a port number in a runbook is a fact with a half-life.

Runs first after P-01 and P-02, and again after every later task. Steps and order are in
[running-on-a-headset.md](running-on-a-headset.md); the acceptance list is in
[audit-2026-09-19.md](audit-2026-09-19.md) under *The recurring audit*, step 7. The one check
that cannot be skipped: **a second device signed into the same account shows unchanged
notification settings** after exceptions were added on the headset.

**First run done, 19 September 2026.** The blocker cleared the moment the headset was worn:
`device offline` right after a successful `connect` is the RSA authorisation handshake waiting
for a human inside the headset, not a broken build or a bad address. Install 12 s, start clean,
no crash. The readings — panel geometry, the density proof, the tablet threshold, frames and
memory — are written up in [running-on-a-headset.md](running-on-a-headset.md) under *Measured on
a Quest 3*, and A-08 in the audit is rewritten around them.

**Two things that change how this task is run from now on:**

1. **`adb exec-out screencap` cannot photograph a 2D panel.** It returns the compositor frame —
   passthrough and immersive layers. `uiautomator dump` is the evidence channel: it names the
   package and gives exact pixel bounds per node, which is what every measurement in the runbook
   came from. Do not plan a device check around screenshots.
2. **A wearer is part of the protocol.** Authorisation, leaving an immersive app, and anything
   behind sign-in need the headset on a head. Batch those steps into one sitting rather than
   asking repeatedly.

**Still owed on a device:** everything behind sign-in. The silence gate on a real launch with
unread messages, the second-device check that account settings did not move, the signed-in
tablet layout that A-08 now turns on, a release-build frame reading while scrolling a real chat
list, and dictation end to end against a configured service.

---

## P-10 · Sign in by a code shown on the panel — `VRQ-004`

> **NOT STARTED, and not as written (A-41, 23 September 2026).** The flow below is "the headset
> displays, the phone scans", and **the panel is inside the headset** — there is nothing for a
> phone camera to point at, and the person wearing it cannot see the panel and their phone at
> once. Telegram's QR login is always the new device rendering a token while an already
> authorised device scans it, so there is no variant in which the headset scans, and the token
> is a long base64 blob, so there is none in which it is typed.
>
> The pain is real and has cheaper answers: a Bluetooth keyboard works on Quest (Meta's
> keyboard-overlay page says so), dictation already exists in this build (P-11, P-12) and can be
> pointed at the phone-number field, and casting makes the QR work for whoever uses it.
>
> Everything below is kept because it is accurate about the API and costs nothing to leave
> written down. It is a reference, not a plan.

**Fixes:** typing a phone number with a ray. **Estimate:** five to eight days.

**Where.**
- `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` — pages are `SlideView`s
  indexed by constants at `:243` (`VIEW_PHONE_INPUT = 0`, `VIEW_CODE_MESSAGE = 1`, …); the phone
  page is `PhoneView` at `:1957`. Add a `QrLoginView` page and make it the first page **only in
  the headset build**, via a `VrEntryPoints.loginStartsWithCode()` boolean (one shared edit at
  the point the first page is chosen).
- TL: `TLRPC.java:50523` `TL_auth_exportLoginToken`, `:50542` `TL_auth_importLoginToken`,
  `:50557` `TL_auth_acceptLoginToken`; responses `:18263` `TL_auth_loginToken`, `:18281`
  `TL_auth_loginTokenMigrateTo`, `TL_auth_loginTokenSuccess`.
- QR rendering: `TMessagesProj/src/main/java/org/telegram/messenger/TelegramQRCodeWriter.java`.
  An earlier version of this line claimed a vendored `com/google/zxing/qrcode/QRCodeWriter.java`;
  there is no such file anywhere in this repository, and `Tools/check_docs.py` is what said so.
  No new dependency.

**Flow.** `exportLoginToken(api_id, api_hash, except_ids)` → render `tg://login?token=<base64url>`
→ countdown from `expires` → on expiry re-request and show *«Код обновился»* → on
`loginTokenMigrateTo` switch datacenter (`ConnectionsManager.moveToDatacenter`) and
re-request → on `loginTokenSuccess` hand `authorization` to the existing sign-in completion →
on `SESSION_PASSWORD_NEEDED` go to the existing password page. *Sign in with a phone number*
stays as a secondary action.

**Done when.** Scenarios SCN-001, SCN-002, SCN-003 from the workspace pass on a headset;
the token value never appears in `FileLog` (grep the log file for the base64 prefix). *Device.*

**Traps.** The token is a credential for the session being created — do not log it, do not
persist it, stop the polling loop in `onPause`.

---

## P-11 · Dictation A — provider and settings — `VRQ-009`

> **DONE, 19 September 2026.** The whole provider layer plus a screen that runs it end to end.
> `org.telegram.vr.quest.speech`: `SpeechToText` (the five failures a user can act on),
> `SpeechResponse` (pure parser over `results[].alternatives[].transcript`, plus HTTP status →
> failure), `SpeechSettings` (endpoint, token and language in private prefs; `maskedToken`,
> `endpointHost`), `HttpSpeechToText` (bearer in a **header**, never the query string; 10 s
> connect, 30 s read), `VoiceRecorder` (16 kHz mono PCM, `VOICE_RECOGNITION`, 60 s cap, pure
> RMS meter) and `DictationActivity` — a screen reachable from headset settings that records,
> recognises and shows the text, with one sentence per failure and the recipient host named
> before the first recording. Four settings rows added to `VrSettingsActivity`; the token row
> starts empty on purpose, because a token on screen is a token in whatever the headset streams
> to a TV. 13 new JVM tests, 28 green in the module.
>
> **Deliberately not done:** the composer trigger, which is P-12 and needs a device. The screen
> exists so that step is small instead of speculative.

**Fixes:** the largest gap against the request. **Estimate:** three days.

**Design constraint from [production-setup.md](production-setup.md) and SECURITY.md:** no
recognition endpoint or token is compiled in. The user enters both; they live on the device.
The recipient of the audio is named on screen before the first recording.

**Do not** reach for `messages.transcribeAudio` — it takes `peer` and `msg_id`, transcribes a
message already sent, and is Premium-gated (`TranscribeButton.java:115`, `:212`, `:252`).
Written up in [roadmap.md](roadmap.md) so nobody spends a day on it.

**Where.** Headset module, package `org.telegram.vr.quest.speech`:
- `SpeechToText` interface: `recognize(File pcmOrOgg, String language) → Result{text|error}`.
- `HttpSpeechToText`: `POST <endpoint>` with the audio, bearer token from settings, response
  parsed as `results[].alternatives[].transcript` — the same contract the existing Nicegram
  Android client already consumes, so a backend the company already runs works unchanged.
  Timeouts, one retry, error mapping to the three user-visible causes: no permission, nothing
  heard, no connection.
- `SpeechSettings`: endpoint + token in a private `SharedPreferences` (mode private; never in
  logs); a settings screen row under P-07 to enter them.
- JVM tests for response parsing (empty, one alternative, several, malformed) and error
  mapping.

**Done when.** Tests green; with a configured endpoint, a recorded sample returns text through
the interface in an instrumentation test or a debug action. *Device for the second half.*

---

## P-12 · Dictation B — composer trigger, indicator, draft — `VRQ-009`

**Estimate:** four to six days. Depends on P-11.

**Where.** `TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java`
— the attach button is created at `:2794`, the audio/video button container at `:2944`, the
record UI at `createRecordCircle` (`:4474`), and text is set with `setFieldText(...)`
(`:10403`, `:10407`, `:10411`) and read with `getFieldText()` (`:10559`). Add a dictation
control through a `VrEntryPoints.composer()` hook returning a `View` the headset module
supplies, inserted next to the attach button — one marked shared edit.

**Behaviour** (scenarios SCN-006, SCN-007, SCN-008 in the workspace):
- Press starts recording (`AudioRecord`, 16 kHz mono PCM; compare `AudioSource.DEFAULT` with
  `VOICE_COMMUNICATION` on the device and keep the better); press again stops. A visible
  indicator with a live level meter is on screen for the whole recording — this is a store
  policy requirement, not decoration.
- Stop → *«Распознаю…»* with a working *Cancel* → text placed into the field with
  `setFieldText(text, true)`, cursor at the end. **Never sent automatically.**
- Errors, each with its own sentence and exit: no permission → explanation screen then the
  system prompt; nothing heard → *«Не расслышал»*, field untouched, retry; no connection →
  offer a voice message instead.
- Before the first ever recording, the permission screen names where the audio goes.

**Done when.** SCN-006/007/008 pass on a headset; characters per minute by dictation vs the
ray keyboard measured on one fixed phrase set, Russian and English, and written into
`docs/vr-layer.md`. *Device.*

---

## P-13 · Message action bar — `VRQ-008`

**Estimate:** two to three days.

**Where.** `ChatActivity.java:30618`, `:30660`, `:30665` — `createMenu(View v, boolean single,
boolean listView, float x, float y, …, boolean longpress)` is the long-press context menu.
Add a hover/dwell-triggered bar with four 64 dp targets (reply, react, forward, more) that
calls the same actions the menu does, shown via a `VrEntryPoints.messageActions()` hook. Keep
the long-press menu.

**Done when.** SCN-012 passes; every action in the menu is reachable from the bar or another
visible control — enumerate the menu items and tick each. *Device.*

---

## P-14 · Headset gallery shortcuts — `VRQ-011`

**Estimate:** two days.

**Where.** `MediaController.loadGalleryPhotosAlbums` (`MediaController.java:6106`) builds
`AlbumEntry(bucketId, bucketName, coverPhoto)` (`:224`, `:6182`) into `allMediaAlbums` (`:6335`).
Add, in the headset build only, named entries for the device's capture and download folders
**resolved through `MediaStore` buckets at runtime**, not hardcoded paths — the capture
directory has moved between Horizon OS versions. Surface them first in
`ChatAttachAlertPhotoLayout`.

**Done when.** SCN-013 and SCN-014 pass; a screenshot taken on the headset appears in the
picker without leaving the chat. *Device.*

---

## P-15 · Headset view — one folder as the starting list — `VRQ-005` (scope) / new

**Estimate:** one day.

**Where.** `DialogsActivity` selects a filter tab through `viewPages[…].selectedType`
(`:813`, `:3612`, `:3634`) and `filterTabsView.selectTabWithId` (`:452`, `:1384`). Folders
themselves exist (`FilterCreateActivity`, `FiltersSetupActivity`). Add a headset setting "start
in folder" (stored in the headset module) and, on first `DialogsActivity` creation in a session,
select that tab through a `VrEntryPoints.startupFilterId()` hook.

**Done when.** With the setting set, the app opens on that folder after restart; unset, it
opens where upstream would. *Device.*

> **Built, 22 September 2026.** The seam existed and had zero callers on either side — that is
> what "its seam and not its wiring" meant. Both ends are wired now:
>
> | Part | Where |
> |---|---|
> | the setting, holding the PERSISTENT `DialogFilter.id` | `VrStartFolder` (new) |
> | one-shot per session | `VrEntryPoints.startupFilterPending()` / `markStartupFilterApplied()` |
> | the selection | `DialogsActivity.updateFilterTabs`, after `finishAddingTabs`, once `filters.size() > 1` |
> | the row and its chooser | `VrSettingsActivity`, under Layout, listing the account's live folders |
> | four strings, en + ru | `strings_vr.xml`, `language-pack/strings_vr.ru.xml` |
>
> **The trap, found while reading rather than after shipping:** `DialogFilter` carries two ints
> and only one survives a restart. `localId` is `dialogFilterPointer++`
> (`MessagesController.java:1295`) — a process counter — and it is what `FilterTabsView` calls
> its *stable* id, which is true within a session and false across one. So the setting stores
> `id` and the tab is found by that row's `localId`; swapped, the client would open a different
> folder every launch and the folder list would get the blame. `StartFolderIdentityTest` asserts
> the call, and was **watched failing** against a planted `localId == wanted` before being
> believed.
>
> Still owed: the device check. Nothing above proves the folder is the one that opens; it proves
> the client asks for the right one.

> **Built, 22 September 2026.** The seam existed and had zero callers on either side — that is
> what "its seam and not its wiring" meant. Both ends are wired now:
>
> | Part | Where |
> |---|---|
> | the setting, holding the PERSISTENT `DialogFilter.id` | `VrStartFolder` (new) |
> | one-shot per session | `VrEntryPoints.startupFilterPending()` / `markStartupFilterApplied()` |
> | the selection | `DialogsActivity.updateFilterTabs`, after `finishAddingTabs`, once `filters.size() > 1` |
> | the row and its chooser | `VrSettingsActivity`, under Layout, listing the account's live folders |
> | four strings, en + ru | `strings_vr.xml`, `language-pack/strings_vr.ru.xml` |
>
> **The trap, found while reading rather than after shipping:** `DialogFilter` carries two ints
> and only one survives a restart. `localId` is `dialogFilterPointer++`
> (`MessagesController.java:1295`) — a process counter — and it is what `FilterTabsView` calls
> its *stable* id, which is true within a session and false across one. So the setting stores
> `id` and the tab is found by that row's `localId`; swapped, the client would open a different
> folder every launch and the folder list would get the blame. `StartFolderIdentityTest` asserts
> the call, and was **watched failing** against a planted `localId == wanted` before being
> believed.
>
> Still owed: the device check. Nothing above proves the folder is the one that opens; it proves
> the client asks for the right one.

---

## P-16 · Store metadata — `VRQ-001` then manifest

Blocked on the policy answer (VRQ-001). When it arrives, add to
`TMessagesProj_AppQuest/src/main/AndroidManifest.xml` the device-support and launch-category
declarations **copied from the current Horizon documentation with the URL and date in a
comment** — the earlier decision not to write them from memory stands.

---

## P-17 · Documentation reconciliation — `VRQ-018`

> **DONE, 23 September 2026**, all four items — and item 1 turned out to be describing something
> that could not be done as written.
>
> **1. Strings.** The registry names strings by meaning (`dictation.busy`); the client names them
> the Android way (`vr_dictation_busy`). **Two key spaces with nothing between them** — so
> "the registry is canonical" was an unenforceable claim, and neither generating one from the
> other nor comparing them was possible. The registry now carries a mapping table for its
> twelve `shipped` rows, and `Tools/check_docs.py` compares each one's English against
> `values/strings_vr.xml`, normalising `\uXXXX` and `%s`/`%1$s`. Measured on the day: **12 of 12
> equal**, no drift to fix. The check lives on the client side because only the client can break
> the contract, and it skips loudly when the workspace is not checked out beside it.
>
> `proposed` rows are deliberately not compared: one describes a screen that may not exist, and
> holding code to it would hold code to a design nobody built.
>
> **2. screens.md** — SCR-25 and SCR-26 carry their coverage.
> **3. Dataroom** — the status line names its revision and points here.
> **4. Rendered docs** — `scripts/check-vr-docs.mjs` re-runs `render.py` and fails on a diff; it
> is in the workspace's `npm run check`.

**Fixes:** A-09, A-10, A-15, A-18. Runs alongside every task that changes UI or copy.

1. **Strings.** The registry in the workspace (`design/docs/brand/strings.md`) is canonical.
   Either generate `TMessagesProj_AppQuest/src/main/res/values*/strings_vr.xml` from it or add a
   check that every `vr_*` key's text equals the registry's. Fix `mic.intro.body` to name the
   user-configured recipient, not a "Nicegram server". Split "People and chats" back into two.
2. **screens.md.** SCR-25, SCR-26 → `Coverage: TMessagesProj_AppQuest/.../SilenceRulesActivity.java`,
   status `drifted` until P-09 passes them, then `built`.
3. **Dataroom.** `dataroom/docs/architecture/nicegram-vr-quest-20260919.md:3` — status line
   names the revision it describes and points at this plan. The path is in the **dataroom**
   repository, not this one; unqualified it reads as a broken link here.
4. **Rendered docs.** Add to the workspace's `npm run check` a step that runs
   `design/docs/render.py` and fails on a diff, so Markdown and HTML cannot drift.

**Done when.** A grep for "Кода нет" in dataroom returns nothing; `npm run check` fails when an
`.md` changes without its `.html`; every `shipped` registry row matches its client resource.

---

## P-18 · Put the headset strings into the language pack — from `A-19`

**Estimate:** half a day, plus whoever owns the translation platform.

**Why this exists.** `localeFilters += ["zz"]` (`TMessagesProj_AppQuest/build.gradle:124`)
strips every Android locale from the package, so a `values-<lang>/` folder in this module is
compiled, merged and then dropped — measured on the 19 September debug APK, where
`aapt2 dump configurations` reports no locale config at all. Reading the strings through
`LocaleController.getString(res)` is already done (A-19); what remains is the other half, which
no code change can supply.

**Do.** Load the entry names of `language-pack/strings_vr.ru.xml` into the Nicegram language
pack. That file is the whole deliverable and it is ready: **115 keys**, one per key of
`values/strings_vr.xml`, 47 carried over unchanged from `fc887365^` and the rest written since
against the brand pack's voice. Its README says why it is not a `values-ru/` folder.

> **Status, 23 September 2026.** Everything that can be done without the translation platform is
> done, and the code half has now been wrong three times.
>
> A-19 declared the reading side finished; A-31 found five strings read through a `Context`,
> which never asks the pack, the first-run screen among them. A-36 found that the replacement —
> `LocaleController.getString(res)` — cannot resolve one of this module's ids at all and put the
> literal text `LOC_ERR:null` on every headset screen; the working path is `VrStrings`, which
> asks the pack by entry NAME and falls back to the compiled string. A-38 then found that the
> guard written against A-31 matched one line at a time and had let two wrapped calls through.
>
> `LanguagePackReachabilityTest` now matches across line breaks and fails the build on either
> mistake. `LanguagePackParityTest` holds the artifact against the resources — same key set,
> same format placeholders, and `values-ru/` may not come back.
>
> **The brand names are part of this and were not counted before (A-39).** Forty-two of the
> forty-seven entries in `VrBrandNames` were finished English sentences that replaced whatever
> the pack had just returned. Thirty-six are renames now — the product's name substituted into
> the translated sentence — and the other eleven are read through `getServerString` by entry
> name, so they arrive in Russian as soon as the keys below are loaded. The old note in that
> file said this plan would fix them; it would not have, and that sentence is gone.
>
> **What is left needs a person:** uploading those entry names to the pack, and then the device
> check below. The count in the old text said 72 keys; it was 99 by the time anyone looked.

**Done when.** With the app language set to Russian, the headset settings screen, the silence
rules, the digest and the dictation screen read Russian, and with it set to English they read
the resource text. Both checked on a device, because the language pack is fetched at runtime and
cannot be checked from a build. *Device.*

---

## P-19 · A rail of chat avatars beside an open chat — from the headset, 19 September

**Estimate:** three to five days. **Depends on:** nothing; the single-column default (`VrLayout`)
already shipped and this is the third mode beside it.

**Why.** Asked for from inside the headset, in the same breath as the complaint that produced
the single-column default: *«когда выбран чат, можно переключать в виде списка маленьких иконок
слева»*. One column is right for reading; a rail is right for switching between a handful of
chats without leaving the one you are in. They are different jobs, not competing defaults.

**What it is not.** Not a flag. `SharedConfig.forceDisableTabletMode` gives exactly two states,
and neither of them is a rail: tablet mode draws the full dialogs list at
`max(dp(320), 35% of width)` (`AndroidUtilities.getTabletLeftFragmentSize`, `:3015`), which is
the 616 px column that caused the complaint. A rail is a narrower left container **and** a
different cell.

**Where.**
- Width: `AndroidUtilities.getTabletLeftFragmentSize` (`:3015`) is the one place the split is
  sized, and `LaunchActivity`'s onMeasure (`:947`) is its only caller of consequence. A rail
  width goes through a seam in the same shape as `VrDisplay` — inert everywhere else, returning
  the upstream expression when not installed. Target: **72 dp** of avatar plus padding, which at
  this build's 1.925 px/dp is 139 px of a 1280 px panel — 11%, against today's 48%.
- Cell: `DialogCell` draws name, preview, time, badges. A rail needs avatar, unread badge and
  nothing else. Do **not** try to make `DialogCell` do both with flags — add a compact cell in
  the headset module and let `DialogsActivity` choose it through a seam, the way the settings
  rows already work.
- The rail must survive fragment changes, so it belongs in the left container that tablet mode
  already keeps alive, not inside the chat fragment.

**Behaviour.**
- The rail appears only when a chat is open. With no chat open, the list is full width — there
  is nothing to be beside.
- Tapping an avatar switches the right side to that chat. The current chat's avatar is marked.
- Unread count sits on the avatar. Muted chats show no badge, which is the whole point of this
  client.
- Order is the dialogs order, honouring the startup folder (P-15) when that lands.
- The mode is a third choice in the headset settings Layout row, not a separate switch.

**Traps.**
- `isTablet()` must be **true** in this mode, because the rail reuses the two-container layout.
  So the Layout setting maps: one column → `forceDisableTabletMode = true`; list and chat, or
  rail → `false`, plus the rail width seam. Getting this mapping wrong leaves the two settings
  disagreeing, which is exactly the failure P-15 was held back to avoid.
- 72 dp avatars at 1.925 px/dp are 139 px bitmaps; check the frame budget with the list
  scrolling, because the first device reading already showed one frame in ten missing 16.67 ms
  on a static screen.

**Done when.** All three layout modes are reachable from one setting and survive a restart; with
a chat open, the rail shows avatars only and switching chats from it does not rebuild the left
container; the frame reading while flicking the rail is no worse than the same reading in one
column. *Device.*

---

## P-20 · Round video messages are clipped in the portrait panel — reported 21 September

**Estimate:** one day, and most of it is looking at a headset.

**What was reported.** Text messages read fine; a round video message ("кружок") is only
partly visible now that the panel is portrait. Other media may be affected too — unchecked.

**What the code says, and why that is not yet an answer.** Round sizes are computed once in
`AndroidUtilities.checkDisplaySize` (`:2798-2806`) from RAW PIXELS:

```java
roundMessageSize            = min(displaySize.x, displaySize.y) * 0.6f
roundPlayingMessageSize     = min(displaySize.x, displaySize.y) - dp(28)
roundSidePlayingMessageSize = min(displaySize.x - dp(64), displaySize.y) - dp(28)
```

On the measured portrait panel (500×800 px, density 1.25 at the default step) that is 300 px
idle and 385 px playing beside an avatar, against a 500 px panel — which **fits**, with room to
spare. So the arithmetic does not reproduce the report, and the arithmetic is therefore the
thing under suspicion: three findings on 19 September (A-08, A-20, A-21) were confident
derivations that the device contradicted within hours.

**The interaction worth checking first.** These three numbers are in pixels and never change,
while everything around them — the avatar gutter `dp(64)`, the bubble insets — is in dp and
DOES change with this build's density step. At a larger step the surroundings grow while the
circle does not, so the relationship upstream assumed stops holding. If the report came from a
step above the default, that is the cause and the fix belongs in our seam: compute these three
from the same effective dp basis rather than from raw pixels.

**Do.**
1. On a headset, note the density step, then `adb shell dumpsys window` for the panel size.
2. Open a chat with a round video, idle and playing, with and without an avatar beside it.
3. Compare against the three values above computed for that panel and step.
4. Check the other media the report suspects: photos, stickers, GIFs, and the media viewer.
5. Only then decide whether the fix is a seam over these three numbers or something else.

**Done when.** A round video is fully visible idle and playing, at every density step, and the
values are pinned in a test the way `VrDensityTest` pins the scale.

---

## P-21 · Notifications and calls on a platform with no push — reported 21 September

**Estimate:** three to five days, and it cannot be finished without deciding what "notification"
means here.

**The constraint that shapes everything, and it is not negotiable.** Horizon OS has no Google
Play Services, this flavour excludes Firebase by name (`TMessagesProj_AppQuest/build.gradle:23`,
`exclude group: 'com.google.firebase'`), and there is no other push transport on the device.
**So there is no push while the app is not running.** A message or a call that arrives while
Nicegram VR is closed cannot wake it. Anything promising otherwise would be a lie in the
interface.

What IS possible, and is what the request actually needs:

- **While the app runs**, the MTProto connection delivers updates, and both messages and calls
  arrive on it. The whole `voip/` stack is compiled in, so calls are present in this build;
  nobody has ever placed or received one on a headset.
- **Android notifications** for those events — a call that can be answered from the
  notification, a message that opens its chat when tapped — are ordinary local notifications
  and work without push.
- **The master switch** already gates messages (`NotificationsMaster`, header). Calls do NOT go
  through `appendMessage` and are therefore NOT gated by it today. Decide deliberately: a
  silenced headset that still rings is a bug, and a silenced headset that misses a call is a
  different bug. A missed call is not recoverable the way a message is.

**Do.**
1. Place and receive a call on a headset. Record what the user sees and hears, and whether the
   panel comes forward. This has never been done.
2. Check the incoming-call notification and its answer action on Horizon OS.
3. Check that tapping a message notification opens that chat, in one column.
4. Wire calls into the master switch, with the decision above written down.
5. Say plainly, in the first-run screen, that nothing arrives while the app is closed. The
   screen already promises quiet; it must not accidentally promise delivery.

**Done when.** A call can be answered and a message notification opens its chat, on a device,
with the master switch honoured in both — and the first-run text matches what the platform can
actually do. *Device.*

---

## P-22 · Draw in the air and send it — asked for 21 September

**Estimate:** two to three weeks. A feature, not a fix.

**The idea, in the asker's words:** press the `+` in the composer, draw in the air with a brush,
say "done" somewhere in the air, and the drawing is baked and sent to the chat — as a photo, a
file, or a 3D object.

**Why it is a headset feature and not a gimmick.** Everything else in this client is Telegram
made reachable in VR. This is the first thing the headset can do that a phone cannot, and it
sends through a transport the recipient already has.

**The three outputs are three different amounts of work.**

| Output | What it takes | What the recipient sees |
|---|---|---|
| Photo | render the strokes from one camera pose to a bitmap | an ordinary image, everywhere |
| Animated GIF / video | render an orbit around the strokes | motion that shows it is 3D, still ordinary media |
| 3D object | export `.glb`, send as a document | a file most clients cannot preview |

**Recommendation: ship the photo first, then the orbit.** The 3D file is the least useful to a
recipient and the most work, and it can be added later without redoing the first two.

**Where.** Drawing and baking belong entirely in the headset module — a new
`org.telegram.vr.quest.draw` package. The only shared seam is one more entry in the composer's
attach menu, and P-12 already owes a composer seam for dictation; **do them together, once**,
rather than patching that view twice. The send path is upstream's existing
`SendMessagesHelper` for a photo or a document, so nothing new is needed on the network side.

**Traps.** Hand tracking is not guaranteed — controllers must work too. A stroke buffer is the
kind of thing that eats the 60 fps budget; the first device reading already showed one frame in
ten missing 16.67 ms on a static screen. And there must be a preview before sending: this
client's rule that recognised text is never sent unseen applies to a drawing just as much.

**Done when.** A drawing made in the air arrives in a chat as an image the recipient can open,
the frame budget holds while drawing, and nothing is sent without being seen first. *Device.*

## P-23 … P-28 · The VR room — `docs/vr-room.md`

Six phases, specified separately because the specification is longer than this file's entries and
because one of its findings changes the store schedule. In short:

| | | |
|---|---|---|
| P-23 | the button and the call | ~2 days |
| P-24 | the immersive activity — **the risk lives here** | ~1 week |
| P-25 | voice in the room | ~3 days |
| P-26 | the shared monitor | ~4 days |
| P-27 | the drawing surface and its relay on DigitalOcean | ~1 week |
| P-28 | the store consequences of becoming a hybrid app | ~3 days |

**The finding that matters most:** voice, participants, encryption, the SFU and screen sharing
are already in this codebase — `voip/ConferenceCall.java`, 35 group-call TL constructors, 54
presentation references in `VoIPService.java`, and an entry point at `ChatActivity.java:18849`.
A room is a Telegram group call with a body, not a new calling stack.

**The finding that changes the schedule:** an immersive activity moves the app from the 2D-panel
VRC subset to the full immersive set. Submit the 2D client first; the room ships as an update.

**Phase A is worth doing alone.** It ships an ordinary group-call button and measures whether
anyone presses it before phases B–E are built.

---

## P-29 · Analytics — `docs/analytics.md`

Aptabase, self-hosted on DigitalOcean: the Kotlin SDK is MIT and therefore compatible with this
app's GPL-2.0, while the server is AGPL-3.0 and never linked in. PostHog was rejected for this
shape — its self-hosting is the deployment its own authors steer people away from, and its
strongest features are the ones a messenger must switch off.

**It has a prerequisite that is not code:** the privacy policy published on 22 September says the
app collects nothing, and that has to change in the same commit as the first event. A Data Use
Checkup is owed to Meta before the next submission. Ten events are listed and the boundary is
absolute: nothing about conversations, correspondents or their content.

---

## P-30 · Nicegram on the phone, offered once, on the third launch — asked for 23 September

**DONE.** `MobilePromoActivity`, `VrMobilePromo`, `VrQrCode`, five tests.

The headset cannot be the only place this account is read from — it comes off, and this build
has no push (P-21), so while it is off nothing arrives at all. The phone app is the other half
of that sentence, and it is worth saying once.

**When.** The third launch, once per install, never again. The first launch already spends its
one screen on something the user must know; a second ask beside it turns the first minute into
two adverts before anything has been earned. `VrMobilePromo.countSession` counts once per
PROCESS rather than per activity — a Horizon panel's activity is recreated on a configuration
change, and resizing the panel is a configuration change, which would otherwise turn one
session into several and fire the offer on the first day. `VrMobilePromo.due(sessions,
alreadyShown)` is pure, so the rule is five assertions rather than a comment.

**Order.** Both one-time screens go through the same registry slot, and the first-run screen
wins whenever it is still due: only after the client has said that it is silent can it ask for
anything (`QuestApplicationLoader.java:149`).

**How the link gets out of the headset — and how it does not.** The primary action sends both
store links to **Saved Messages**, where the phone this offer is about picks them up seconds
later. That works because the account in the headset is the account on the phone, which is a
thing only a messenger can do.

The first version of this screen was built on a QR code instead, and that was wrong: **a code
drawn on a headset panel is inside the headset.** There is no external screen for a phone
camera to point at, and the person wearing it cannot see the panel and their phone at once.
The code is kept, because it was asked for and because a headset that is CASTING to a phone or
a television does put it on a real screen — but it is the third path on the screen, under a
label that says when it can be scanned, and the short address `nicegram.me/download` is printed
beside it for anyone reading rather than scanning. The two store buttons open the headset's own
browser for whoever would rather finish here. See A-40.

**Why the layout is a ScrollView.** At the largest interface step the panel is about 260 x 415
dp — `VrDensity.STEP_SCALE` tops out at 1.54 over a 500 x 800 px, 200 dpi panel — and this
screen's content is taller than that. The first version had no scroll and drew two 164 dp code
blocks side by side, 328 dp against 212 dp of usable width, so it overflowed on both axes at
every step but one.

**Where the encoder lives.** `com.google.zxing` is an `implementation` dependency of
`TMessagesProj` (`build.gradle:51`), so the headset module cannot name `EncodeHintType` at all.
`org.telegram.vr.VrQrCode` renders on that side and returns a `Bitmap` or null; promoting zxing
to `api` would put it on the classpath of every flavour that has no use for it.

---

## Questions for the operator — the only things an agent cannot do itself

Kept here rather than scattered through the plan, so that the count is visible and nothing
waits on a question nobody knew existed. **Everything on this list needs an account, a device or
a decision that is not an engineering call.** Work that is merely hard is not on it.

Each says what is blocked by it, so that a "no" is as useful as a "yes".

| # | Question | Blocks | Prepared for you |
|---|---|---|---|
| **Q-01** | May a third-party client of another messenger be published on the Horizon Store? (`VRQ-001`) | The whole store path — stages 4 and 5 of [horizon-store-readiness.md](horizon-store-readiness.md). Sideloading is unaffected. | A draft letter: [store/meta-policy-letter.md](../store/meta-policy-letter.md) |
| **Q-02** | Does the third-launch phone-app offer count as an advertisement under app policy 2.1.1? (`VRQ-002`) | Nothing today. If the answer is no, one line comes out of `QuestApplicationLoader.java:149` before submission. | Same letter; the clauses are quoted in it |
| **Q-03** | The other flavours — Huawei and the standard build — stay unmaintained here, or get the same fail-by-name credential treatment? (`A-06`) | P-06's last item. Nothing else. | Both options costed in A-06 |
| **Q-04** | Analytics: opt-out (on by default) or opt-in (off by default)? | P-29 entirely — the privacy policy text is written from the answer, and it is published, so it cannot be guessed and corrected later. | Ten events and the boundary are already decided in [analytics.md](analytics.md) |
| **Q-05** | One signed-in session on a headset, to capture the six in-use store screenshots. | Stage 5 of the store path. The capture recipe is written and the frames are named. | [store/assets-checklist.md](../store/assets-checklist.md) |
| **Q-06** | Upload `language-pack/strings_vr.ru.xml` to the Nicegram translation platform. | P-18 — the Russian interface. The code half is finished and guarded; this is the half no code change can supply. | The file is ready and parity-tested against the resources |
| **Q-07** | Developer organisation verification on the Meta dashboard. | Any submission at all. | — |
| **Q-08** | Data Use Checkup, the IARC age-rating questionnaire, and GRAC if South Korea is in scope. | Submission, and platform features stay limited to test users without a current DUC. | — |

**Eight open.** Q-01 and Q-02 travel in one letter; Q-07 and Q-08 are the same dashboard
session. Q-03 and Q-04 are decisions and cost nothing but a reply.

---

## What this plan does not include, on purpose

- Calls and video calls from the headset (no forward camera; a different product).
- The Meta Spatial SDK shell (stage 2): decided after P-09 has numbers, not before.
- Speech synthesis, translation, or any AI beyond dictation.
- Publishing to the store: P-16 prepares the manifest; the submission itself is a release
  decision with its own checklist.
