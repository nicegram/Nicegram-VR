# Room alpha implementation — 26 September 2026

**Experimental closed-test implementation; headset acceptance and service deployment are
not yet verified.** This is the first narrow slice requested after
[the full architecture](vr-room-architecture-20260926.md), not completion of A00–A22.

## What this change contains

- Group chat menu → invitation lobby → immersive Meta Spatial SDK 0.14.0 activity, floor,
  large presence panel and Telegram group-call controls. See
  [RoomLobbyActivity](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomLobbyActivity.java)
  and [RoomSpatialActivity](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomSpatialActivity.kt).
- [RoomCallBridge](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomCallBridge.java)
  refreshes Telegram full-chat data, checks membership and create rights, and starts the existing
  VoIPService with an explicit account and group. Existing calls are joined; an unrelated call is
  not replaced. Native `VoIPService.java` initializes group calls muted before `startGroupCall`.
  Voice uses Telegram only and never invokes a message-send or recording API.
- Microphone is explicit-toggle in this alpha. Loss of activity focus or room connectivity disarms
  it; a periodic mute guard handles the native `micSwitching` interval. PTT/controller bindings,
  replay safety on hardware and audio focus across Spatial SDK/native VoIP remain unverified.
- [Room service](../room-service/README.md): bounded ephemeral presence and capability invitations,
  HTTP contract tests and proposed DigitalOcean spec. Its names are NOT verified Nicegram identities.
- English resources and Russian language-pack source add 32 matching room strings. The canonical
  private product workspace adds SCN-038 and the matching string registry.

## Deliberate limits of this slice

The owner requested speed; this implements a closed-test capability path while A01 (Nicegram
identity/entitlement and independently verified room membership) remains open. The earlier
question about accepting this reduced identity scope has not been answered. Do not distribute
this as the approved product or enable public access pending that decision and A01.

No public directory, avatar or pose sync, full shared chat display, autoplay queue, composer,
voice-note/round-video capture, personal calls or conference contexts are implemented here.
Scheduled, streaming and anonymous/join-as calls direct the tester back to native Telegram UI.
The room invitation binds to the selected group, but is not a verified membership assertion.
No bot automatically sends invitations or other messages.

The app now requires Android 14 (minSdk 34), matching the selected Spatial SDK hybrid sample
baseline. The regular messenger still launches as 2D. The new spatial activity is not exported.
This changes the prior 2D-only release scope; Meta Store acceptance must be repeated.
The newly linked Meta SDK has a license distinct from this GPL client; binary distribution
compatibility is unresolved. See [the exact dependency gate](../NOTICE.md). Keep this APK
local for engineering review until resolved; no public binary release is authorized by a build.

## Checks and source references

- Local `assembleQuestDebug` succeeded; `testQuestDebugUnitTest`: 102 tests / 22 classes,
  zero failures. Final signed-build result is recorded in the follow-up receipt, not assumed.
- `node --test room-service/test/*.test.mjs`: 4 tests passed, including two independent HTTP clients.
- `python3 -m unittest discover -s Tools/tests -v`: 8 tests passed.
- DO spec schema validation passed; this is not deployment evidence.
- Device: no Quest connected. Two-headset calls, rendering, controller interaction, lifecycle,
  pause/background mic, reconnect, call moderator roles and performance: **NOT_RUN**.
- SDK API reference: [official HybridSample](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/HybridSample).
  Implementation uses SDK primitives and XML panel registration; no sample assets were copied.

## Exact next tasks and acceptance checklist

1. Finish signed build, APK signature/manifest inspection and record hash/size/version/source SHA.
2. Resolve closed-test identity scope, deploy isolated DO service from the tested SHA, then test
   two HTTP clients against its HTTPS endpoint. Creation key belongs only in secure runtime config.
3. Install the signed APK on two Quest 3/3S. Open the same Telegram group on each; creator enters
   service address/key, creates the room, manually shares its invitation; second tester joins.
4. Enter VR on both: both names visible, readable panel, controller ray targets and exit work.
5. Admin starts a Telegram call inside the room; second client joins the same call. Confirm a
   normal Telegram phone also joins and hears both; no recorded voice messages appear in chat.
6. Test ordinary member with no active call, moderator mute, microphone permission denial,
   another active call, account switching, network drop, focus loss, sleep and reconnect. Mic
   must remain off after recovery until an explicit new action. Exit leaves own call participation.
7. Only after these receipts address A01 and the rest of the full plan, then produce a new Store
   candidate. The older 2D RC and its release checklist do not validate this immersive build.

Source repository owns the runtime, service and build receipt; the private product workspace
owns scenarios and copy; Dataroom will link the exact commits as the central handoff.
No production merge, tag, release upload, hosted full-suite dispatch or store submission is
implied by this working branch.
