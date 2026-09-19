# Plan — remaining work, decomposed

> **Status, 19 September 2026, later the same day.** Done and pushed: P-01 … P-08, **P-11**
> (dictation's provider layer and its own screen) and P-17. P-15 has its seam and not its
> wiring. The headset build now also starts in **one column** and carries the Nicegram name and
> icon — both from what the panel actually looked like, not from a plan.
>
> Open: **P-09** (device protocol — the first run happened; what is left is behind sign-in),
> **P-10** (sign in by code), **P-12** (the composer trigger, the second half of dictation),
> **P-13** (message action bar), **P-14** (gallery shortcuts), **P-15** (the `DialogsActivity`
> half), **P-16** (store metadata, blocked on VRQ-001), **P-18** (load the headset strings into
> the language pack — out of finding A-19, without which the interface is English whatever
> language the user chose) and **P-19** (the rail of chat avatars beside an open chat, asked
> for from inside the headset).
>
> **This build has now run on a Quest 3**, 19 September 2026: installed in 12 s, started without
> a crash, and the density fix proved itself in the running interface — a button declared 56 dp
> measured 108 px, against the 1.925 px/dp this build intends. Readings in
> [running-on-a-headset.md](running-on-a-headset.md). Every acceptance *behind sign-in* is still
> owed, and that is most of them.

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

**Done when.** Shown exactly once per install; `adb uninstall app.nicegram.vr` and reinstall
shows it again; skipping it never re-shows it; the third line is present verbatim. *Device.*

---

## P-05 · Exceptions screen, second pass — `VRQ-005`

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
- QR rendering: `TMessagesProj/src/main/java/com/google/zxing/qrcode/QRCodeWriter.java` is
  vendored; `org.telegram.messenger.TelegramQRCodeWriter` is used at `QrActivity.java:1311`.
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

---

## P-16 · Store metadata — `VRQ-001` then manifest

Blocked on the policy answer (VRQ-001). When it arrives, add to
`TMessagesProj_AppQuest/src/main/AndroidManifest.xml` the device-support and launch-category
declarations **copied from the current Horizon documentation with the URL and date in a
comment** — the earlier decision not to write them from memory stands.

---

## P-17 · Documentation reconciliation — `VRQ-018`

**Fixes:** A-09, A-10, A-15, A-18. Runs alongside every task that changes UI or copy.

1. **Strings.** The registry in the workspace (`design/docs/brand/strings.md`) is canonical.
   Either generate `TMessagesProj_AppQuest/src/main/res/values*/strings_vr.xml` from it or add a
   check that every `vr_*` key's text equals the registry's. Fix `mic.intro.body` to name the
   user-configured recipient, not a "Nicegram server". Split "People and chats" back into two.
2. **screens.md.** SCR-25, SCR-26 → `Coverage: TMessagesProj_AppQuest/.../SilenceRulesActivity.java`,
   status `drifted` until P-09 passes them, then `built`.
3. **Dataroom.** `docs/architecture/nicegram-vr-quest-20260919.md:3` — status line names the
   revision it describes and points at this plan.
4. **Rendered docs.** Add to the workspace's `npm run check` a step that runs
   `design/docs/render.py` and fails on a diff, so Markdown and HTML cannot drift.

**Done when.** A grep for "Кода нет" in dataroom returns nothing; `npm run check` fails when an
`.md` changes without its `.html`; every `vr_*` string matches the registry.

---

---

## P-18 · Put the headset strings into the language pack — from `A-19`

**Estimate:** half a day, plus whoever owns the translation platform.

**Why this exists.** `localeFilters += ["zz"]` (`TMessagesProj_AppQuest/build.gradle:124`)
strips every Android locale from the package, so a `values-<lang>/` folder in this module is
compiled, merged and then dropped — measured on the 19 September debug APK, where
`aapt2 dump configurations` reports no locale config at all. Reading the strings through
`LocaleController.getString(res)` is already done (A-19); what remains is the other half, which
no code change can supply.

**Do.** Take the 72 keys of `TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml` and load
them into the Nicegram language pack under exactly those resource entry names. The Russian
source text is in git history at `7a54dbba` and its successors — it was written for these keys
and should not be re-translated from scratch.

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

## What this plan does not include, on purpose

- Calls and video calls from the headset (no forward camera; a different product).
- The Meta Spatial SDK shell (stage 2): decided after P-09 has numbers, not before.
- Speech synthesis, translation, or any AI beyond dictation.
- Publishing to the store: P-16 prepares the manifest; the submission itself is a release
  decision with its own checklist.
