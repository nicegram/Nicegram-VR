# VR rooms: corrected scope and implementation handoff

25 September 2026. **Proposed, not implemented.** This change updates documentation only;
it does not add rooms to the existing release candidate or establish hardware acceptance.
The [22 September proposal](vr-room.md) remains historical context.

## Evidence and corrections

Inspected client revision: `83768abb8e5cbdfd65391b9d5232bfc609d45bb7`.
The existing proposal explicitly reports no implementation. The following focused search
returned exit 1 (no matches) in the inspected Quest application and shared VR layer:

```sh
rg -n 'roomButton|RoomActivity|SpatialActivity|com.meta.spatial|livekit|rooms/' TMessagesProj_AppQuest TMessagesProj/src/main/java/org/telegram/vr -g '*.java' -g '*.kt' -g '*.gradle' -g '*.xml'
```

Existing call code is in `TMessagesProj/src/main/java/org/telegram/messenger/voip/`.
Source presence does not prove working Quest/phone interoperability. Telegram distinguishes
video chats/livestreams, live stories and conferences; creating another group call for a
chat can terminate its existing call. See [Telegram group calls](https://core.telegram.org/api/group-calls).
Creating a VR room must therefore not implicitly create a Telegram call.

Do not derive room authorization from a call ID, an emoji fingerprint, or the assumption
that all call types expose a shared key. Nicegram identity, VR capability, room permission
and Telegram media permission are separate checks. Internal service credentials never
belong in the APK. An account-existence lookup does not authenticate the caller.

## Client contract proposed for the next implementation

- Stable room UUID, separate live session epoch, separate media binding. Normalize chat,
  topic, direct-call and conference contexts rather than using one groupCallId for all.
- Resolve before showing Create/Join. Unknown/offline is not permission to create.
  Concurrent creation is idempotent; admission and capacity are server-authorized.
- Full target coverage: private chats, direct audio/video, groups/topics, group audio/video,
  conferences without chats, channel broadcasts; later co-watch for asynchronous media
  and live stories subject to current schema support. Secret-chat content is not exported.
- Two explicit modes: spatial presence around an existing Telegram call, or a standalone
  Nicegram room with self-hosted media on DigitalOcean. The latter does not automatically
  include ordinary Telegram clients. Transport selection remains an owner decision.
- All new room services, state and assets target DigitalOcean. Telegram's existing media
  and the existing Nicegram API are external dependencies, not silently migrated services.
- Private invitations can use authenticated Nicegram identities. Automatically admitting
  chat members or publishing an official chat room requires independent server-verifiable
  membership/admin proof; do not trust a client-supplied assertion.
- Public directory is opt-in and moderated, sorted by unique active VR participants.
  A public Telegram chat alone does not publish a room. Private metadata is not searchable.
- Hybrid 2D/immersive entry must retain one microphone owner. Return to 2D and leave call
  are separate actions. Reconnect uses snapshot/epoch; account change clears participation.
- New room transport must not claim end-to-end encryption merely because TLS is enabled.

Meta documents the [hybrid application pattern](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview/).
Compatibility with this project's build, lifecycle and native call stack still needs a spike.

## Bounded implementation packets

| Packet | Deliverable / acceptance |
|---|---|
| R0 | Verified Quest→Nicegram identity exchange, room access proof or explicit invite-only scope, media decision; two Quest + phone 2D↔3D spike |
| R1 | Resolve/create/join/leave, ACL, invitations, leases, epoch and capacity races; standalone voice depends on R4 |
| R2 | Direct/group audio/video, topic and conference adapters; actual device checks per type, listener role preserved |
| R3 | Directory, publication verification, moderation, unique presence counts; global voice depends on R4 |
| R4 | Inspect existing Nicegram signal service before introducing a second one; DO SFU/TURN mode and microphone arbitration |
| R5 | Co-watch and extended context adapters, without automatic content export |
| R6 | Device comfort/performance, reconnect/failure/load checks and hybrid release requirements |

Detailed product flows and cross-service contracts are maintained in the private product
workspace, WORK-018, rooms specification; native implementation belongs in this repository.
No new backend repository or infrastructure resource was created in this documentation task.

## Resume

Completed: measured source status, corrected historical assumptions, defined client contract
and task boundaries. Open: R0–R6, identity/access verifier, transport decision, infrastructure
ownership and physical-device acceptance. Start with **R0 identity + access + hybrid spike**;
never start by treating a Telegram key as a Nicegram login.

Local validation for this documentation change: `python3 Tools/check_docs.py` and
`git diff --check` (results recorded in the private central handoff). No new APK was built,
no hosted suite dispatched, no production deployment performed. Follow the current nightly
CI policy; missing checks are not passing checks. Keep credentials, local tool configuration,
third-party dependencies and device data out of task commits.
