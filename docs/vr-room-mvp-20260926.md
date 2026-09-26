# Room alpha implementation — 26 September 2026

**Mandatory Nicegram authentication is implemented; live backend integration and headset
acceptance remain unverified.** The owner explicitly rejected postponing identity verification.
The earlier invite-only prototype at `93939f65` is superseded. Do not distribute its APK.
This is a narrow implementation of [the full architecture](vr-room-architecture-20260926.md),
not completion of A00–A22 or a Store-ready candidate.

## Runtime and trust boundaries

- [RoomLobbyActivity](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomLobbyActivity.java):
  group menu → Nicegram account verification → create/join → enter immersive room.
  The explicit bot confirmation is bound to the same Telegram account and gateway. Invitation
  alone is insufficient. General Nicegram auth and Internal tokens never enter the client.
- [Room service](../room-service/README.md): Internal account lookup plus server-confirmed
  Nicegram auth-bot session. Creates a bounded identity token only after both agree. No fake
  production provider, development login bypass or invite-only fallback exists.
- [RoomSpatialActivity](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomSpatialActivity.kt):
  Meta Spatial SDK 0.14.0 immersive floor and large panel with verified participant names,
  native Telegram group-call controls, microphone toggle and exit.
- [RoomCallBridge](../TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomCallBridge.java):
  refreshes full-chat data, checks native membership/create rights and uses existing VoIPService
  with an explicit account/group. Joins the existing call; never replaces another call. Native
  VoIPService starts group calls muted. No audio relay, voice-note send or recording path is added.
- Loss of focus or room connectivity disarms the mic; repeated mute handles native `micSwitching`.
  Recovery does not arm it. Scheduled, streaming and anonymous/join-as calls use the regular
  Telegram UI instead. These lifecycle behaviours still require hardware confirmation.

Account existence is not a VR entitlement. Existing Internal API does not expose a dedicated
VR entitlement contract. Presence admission also does not independently attest Telegram group
membership; private content is not mirrored and Telegram checks access to the actual call.
Client engineering builds expose a gateway URL input; pin the trusted gateway for end users.

## Delivered scope and remaining work

Native build version is `0.2.0-room-alpha.2`, code `7089069`, Android 14+ on Quest 3/3S.
The ordinary messenger remains 2D. Spatial activity is internal, not exported.
The backend has ephemeral rooms, 8 participants, 2-hour lifetime and 30-second leases; one
instance only. Restart ends rooms. Keys and process state are not persisted in Git.

Shared chat display, voice-message autoplay, composer, PTT bindings, avatars/pose sync, public
catalog, personal-call and conference adapters remain open. English and Russian language-pack
sources have matching room/auth strings. Canonical UX/copy is SCN-038 in the private workspace.

Meta SDK is licensed separately from the GPL client. Compatibility of distributing the combined
APK is unresolved; [NOTICE](../NOTICE.md) records the exact license. Keep it as a local
engineering build pending resolution. No public binary release or Store submission was made.

## Verification and infrastructure

The final signed-build receipt is in [the build receipt](release/2026-09-26-room-alpha.md).
Do not use success from the earlier invite-only build as evidence for this authenticated one.

The isolated DO app `d29765d3-4338-489f-bda1-cddd249c6222` was deleted after scope correction.
It was still cloning the old source and contained no user room data. Nothing in production was
modified. Final deployment waits on the verified Nicegram API origin, auth-bot username and
server token scoped to `internal.users.show`; their live values were not available in this run.
The server denies admission when those settings are missing. No account lookup or bot message
was sent on behalf of a real user during the automated tests.

SDK API reference: [official HybridSample](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/HybridSample).
The renderer uses SDK primitives and XML panels; no sample assets were copied.
Existing Nicegram contract: `nicegram-api` remote master at `3a904792d296e11aeaa109f693b31e3566e54033`.
No source change or production deployment of that service is needed by this adapter.

## Exact next task and device checklist

1. Supply the three server settings above from the authorized secret/config store. Export a
   service-only deployment source (native submodules are unnecessary for Node), then deploy
   one DO test instance from the checked commit. Verify `/healthz` reports `ready: true`.
2. On two Quest, open the same Telegram group. In its menu open the VR room. Verify each
   existing Nicegram account through its auth bot and return to check confirmation.
3. Creator creates a room and manually shares the invitation; second verified account joins.
   Enter VR on both. Confirm both names, panel readability, controller targets and exit.
4. Admin starts the Telegram group call inside the room; the other client joins the same call.
   Join from a normal Telegram phone too. Verify both voices and that live speech makes no notes.
5. Check non-admin/no-call, moderator mute, permission denial, another active call, account
   switching, network loss, focus loss, sleep and reconnect. Mic must remain off after recovery
   until an explicit action. Room exit must end only that user's call participation.
6. Resolve renderer distribution licensing and pin the gateway before distributing to end users.
   Complete the remaining full-plan modules and repeat release review for a new Store candidate.

No Quest is connected: rendering, two-user calls and lifecycle/performance acceptance are
**NOT_RUN**. Server tests use mocked Nicegram responses; live identity acceptance is **NOT_RUN**.
The native repository owns code/receipts, workspace owns scenarios/copy, Dataroom owns the
cross-repository entry point. Hosted full CI stays on the nightly integration schedule; this
unmerged branch is outside that snapshot. No CI pass, merge or publication is implied by push.
