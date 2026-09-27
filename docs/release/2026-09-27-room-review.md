<sub>ssheleg skills — task-pipeline · quest-spatial · hz-spatial-sdk · copywriting · evidence-docs</sub>

# VR rooms: full review, the room chat, and alpha.5

**27 September 2026, Europe/Warsaw.** Review of everything the room work contained up to
`2a159788` (alpha.4): architecture, room service, identity, the Telegram call bridge, lobby,
spatial scene and UX. Every defect found is fixed below with its evidence. The room now carries
the group's own Telegram chat on its large panel — the owner-approved behaviour that alpha.4 did
not implement. **Device acceptance remains NOT_RUN**: no Quest was connected.

## What the room is now

| Layer | Implementation |
|---|---|
| Entry | Group menu → [lobby](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomLobbyActivity.java), or a tapped invitation link in the group ([hook](../../TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java), `openVrRoomInvitation`) |
| Identity | Nicegram bot confirmation once per process ([store](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomIdentityStore.java)); confirmation picked up automatically on return from the bot and every 5 s |
| Presence | [room service](../../room-service/README.md) on DigitalOcean; [session](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomSession.java) with a 12 s link grace ([state](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomConnection.java)) and one automatic rejoin of a lapsed lease |
| Room chat | [RoomChatActivity](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomChatActivity.java): the group's own `ChatActivity` in a Spatial SDK intent panel, 1.3 × 1.45 m, 780 × 870 px |
| Voice | Native Telegram group call ([bridge](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomCallBridge.java)); control panel lists call participants, speakers first |
| Capture | While a room is active and a call is live, voice and round-video recording is refused with a notice ([gate](../../TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java), `vrRecordingBlocked`) |

The chat host follows upstream `BubbleActivity`, the one existing host of a single-dialog
`ChatActivity`, minus its `closeOtherAppActivities` broadcast (it would finish the 2D
`LaunchActivity` behind the room) and its `closeChats` broadcast (it would close the same group
there). Messages load under the person's own Telegram account; the room service never sees them.
Composer, voice notes, history and media are Telegram's own. Panel activity flags follow Meta's
SpatialVideoSample manifest (`allowEmbedded`, `exported=false`); the virtual-keyboard and
hand-tracking features follow the MediaPlayer, PremiumMedia and Hybrid samples.

## Defects found and fixed

Room service — commit `f2ee8a40` and follow-up, 10 new tests in
[hardening.test.mjs](../../room-service/test/hardening.test.mjs), each observed failing first
(9 of 10 red before the change):

| # | Defect in alpha.4 | Fix |
|---|---|---|
| S1 | Rate limits keyed by socket address; behind App Platform's edge that is one address, so 20 auth calls/min were shared by every user | `CLIENT_IP_HEADER=do-connecting-ip`; verified live, see below |
| S2 | The same account re-joining got `ALREADY_JOINED` for a whole lease — a crashed headset could not return | Re-join replaces the previous session |
| S3 | Empty rooms kept their slot for 2 h and one account could take all 100 | Empty rooms removed after 5 min; at most 3 rooms per creator (`USER_ROOM_LIMIT`) |
| S4 | A per-ID 30 s start lock let anyone keep a known Telegram ID out of login | Three pending challenges per ID, oldest evicted |
| S5 | One Internal API blip ended everyone's heartbeat and disarmed their microphones | 5-minute outage grace; a removed account is still denied at once |
| S6 | No logs; `/healthz` 200 while unconfigured; no graceful stop; query strings broke routing | JSON request log without IDs or tokens, 503 when not ready, `SIGTERM` drain, path parsing |
| S7 | An invitation opened outside the headset returned a JSON 404 | Static `/room` page, no script, strict CSP |

Client — commit `5f59f4d6`:

| # | Defect in alpha.4 | Fix |
|---|---|---|
| C1 | No room chat | Room chat panel (above) |
| C2 | Verification token lived in the lobby screen: every reopening meant the bot again | Per-account process store with expiry margin |
| C3 | After Start in the bot, a second manual "check" press was required | Checked on return and every 5 s while pending |
| C4 | Leaving the lobby screen closed the creator's room | The session outlives the screen; Leave is explicit |
| C5 | One failed heartbeat said "connection lost, leave and join again" and disarmed the mic; a lapsed lease never recovered; an ended room was polled for ever | 12 s grace, automatic rejoin, terminal codes end the room |
| C6 | Invitations only by clipboard; the link opened a browser | Explicit "Send invitation to this group"; tapping the link opens the room and joins after verification |
| C7 | Entering the room paused the Telegram connection: `LaunchActivity.onPause` → `setAppPaused(true)` while the call bridge requires `ConnectionStateConnected` | `QuestApplicationLoader.onPause()` keeps it live while a room is active |
| C8 | A call still loading failed the press; a granted microphone needed a second press | Waits for `groupCallUpdated`; continues after the permission dialog |
| C9 | Recording a voice or round video during the room call was possible (upstream does not block it) | Gate at the single recording start point |
| C10 | No virtual keyboard or hand-tracking declaration for the immersive panels | Manifest features; `HAND_TRACKING` is not on Meta's prohibited list |
| C11 | Error texts differed per screen; unknown server codes fell back to "could not connect" | One [table](../../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomMessages.java); a test reads the server sources and fails on any code without its own sentence |

Two races found in this change's own first draft and fixed before commit: a heartbeat queued
before Leave would have received `SESSION_EXPIRED` and rejoined the room just left; and moving
focus to the chat panel could disarm the microphone before focus settled (300 ms settle now).

## Checks run

| Check | Result |
|---|---|
| `node --test room-service/test/*.test.mjs` | 25 pass, 0 fail |
| `testQuestDebugUnitTest` | 112 tests, 23 classes, 0 failures/errors/skips (104 before) |
| `python3 -m unittest discover -s Tools/tests` | OK |
| Deploy | App `b3c7c427-9c33-4edc-89be-ad08a6feb2ea`, deployment `867dd0ae-09d4-47e2-8342-06ec0a4b90df` ACTIVE, source `368772663144ca252b194c4808fd837cc2ebda9d` (subtree of `room-service/`) |
| Live: health / page / unauthenticated create / unknown account | 200 ready / 200 `text/html` / 401 `NICEGRAM_AUTH_REQUIRED` / 403 `NICEGRAM_ACCOUNT_REQUIRED` |
| Live: forged `do-connecting-ip` | 42 attempts with 41 distinct forged values: 40 answered, 2 `RATE_LIMITED` — one budget, the forged value is overwritten |
| Live logs | one JSON line per request, route templates and codes only |
| APK | packaging PASS, see [Build](#build) |
| `Tools/check_room_secret_absence.py --git-base 2a159788` | PASS: server credential absent from 7,226 APK entries and 28 changed Git blobs |
| Device | NOT_RUN — `metavr device list` shows both Quest 3 `offline`; reconnecting did not complete |

## Build

| Item | Value |
|---|---|
| Version / code | `0.2.0-room-alpha.5 (Telegram 12.10.3)` / `7089099` |
| Source | `5f59f4d6cf08009beca626da26944096159c58f7` |
| SHA-256 | `bedcd37696c5e092e09fa790f87398b7ecc113cfd5c1dca6062adb08c6033fd7` |
| Bytes | `117889566` |
| Identity | `my.nicegram.vr`, arm64-only, min/target 34, v2 signature |
| Certificate SHA-256 | `7481f8cddfa604bb228c691a344585c7451c7f493c565f82f8926ebcaa60497b` — same as alpha.4, so `adb install -r` keeps the Telegram session |

`testQuestDebugUnitTest` + `assembleQuestStandalone`: BUILD SUCCESSFUL in 8 m 45 s. Signing
inputs came from the Observatory vault through `use_secret.py run`; the keystore existed as a
mode-600 temporary file only during Gradle. `Tools/check_release.py --surface hybrid`, previous
code 7089089, fresh prohibited-permission page (117 names): PASS, no matches;
[portable receipt](2026-09-27-room-alpha5.json). The built manifest carries `RoomChatActivity`
with `allowEmbedded=true`, `exported=false`, the optional `VIRTUAL_KEYBOARD`,
`overlay_keyboard` and `handtracking` features and `HAND_TRACKING`.

Local-only APK: `build/release-review/v0.2.0-room-alpha.5/nicegram-vr-v0.2.0-room-alpha.5.apk`
in the native checkout. Nothing was uploaded to GitHub or Meta.

## Not done, and why

- **Headset acceptance.** Panel rendering, controller/hand input on the chat panel, the system
  keyboard in immersive mode, focus behaviour between the two panels and two-headset calls all
  need a Quest. The device checklist in [the live beta receipt](2026-09-26-room-live-beta.md)
  still applies, plus: type and send a message in the room chat; record a voice message with no
  call (works) and during the call (refused with a notice); tap an invitation in the group.
- **Autoplay of new voice messages, PTT, avatars, catalog** remain architecture packets A00–A22.
  Autoplay needs native playlist isolation proven on hardware first.
- **Meta SDK / GPL distribution boundary** in [NOTICE](../../NOTICE.md) is unchanged: this APK
  stays a local engineering build.
- **Nicegram membership of the group** is still not attested by the room service; the
  invitation capability travels only inside the group, and Telegram checks the call itself.
