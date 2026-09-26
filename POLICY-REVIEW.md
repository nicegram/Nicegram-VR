# Room-build policy draft — 27 September 2026

Not deployed. This branch proposes factual corrections for experimental rooms;
it does not establish legal completeness, SDK compatibility or retention outside our process.
The currently published page belongs to the older panel-only build.

Implementation baseline: [native 08204bd7](https://github.com/nicegram/Nicegram-VR/tree/08204bd7e971cc2fe55f1983567f541dc0b50e40).

| Data / behavior | Evidence at that revision |
|---|---|
| Telegram ID, account check and bot confirmation; broader account response discarded after selecting fields | `room-service/identity.mjs`, `nicegramProvider.account/start/complete` |
| Name, Telegram ID, chat identifier, invitation and session tokens, timestamps; participant names visible in the room | `room-service/server.mjs`, `join`, `snapshot`, room creation and heartbeat |
| Five-minute challenges, two-hour identities and rooms, 30-second presence lease, 10-second cleanup | `NicegramIdentity.start/complete`; `createRoomServer` defaults and timer |
| IP-based rate limit in process memory; no application request/body logging | `server.mjs`, `rates` and HTTP handler |
| Telegram carries call audio | `TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomCallBridge.java` |
| DigitalOcean Frankfurt deployment | `docs/release/2026-09-26-room-live-backend.json` |

Remaining release work:

1. Verify actual Meta Spatial SDK network/data behavior and include its recipients/purposes.
2. Establish operational log retention for DigitalOcean and the Nicegram identity service;
   process-memory expiry does not prove infrastructure deletion.
3. Review account deletion/support procedure and applicable privacy disclosures with the owner.
4. Review the complete draft, replace draft banners/date only after the inventory is complete,
   then publish to `gh-pages` under the repository's integration policy. Never claim a pushed
   draft branch updated the live policy.
5. Re-open the public URLs and compare their text with the approved revision before Meta submission.

Checks: HTML parsed with Python HTMLParser; local relative links resolve; source assertions
reviewed against the linked native revision. No browser rendering or legal review claimed.
No secrets, user records, session values or signing material are included.

Next agent: start with items 1–3; the initial implementation and publisher contact are already
known. The native release entry is `docs/release/2026-09-27-store-review.md` on
`codex/vr-store-review-20260927`. Keep the room build blocked while these items remain open.

## Resume evidence — 27 September 2026

The operator asked to finish the release and confirmed that no new licensing, organization
verification or device results had arrived. This update records research, not policy approval.
The HTML draft and published site are unchanged.

### Meta SDK: distinguish documented categories from observed traffic

Meta's [essential data policy](https://developers.meta.com/horizon/policy/essential-data-use-tools-sdk/)
(updated 5 February 2025; re-read 27 September 2026) describes identifiers, environment and OS
configuration, SDK usage, errors/crashes, loading failures, security and developer-mode testing
data. The [optional data policy](https://developers.meta.com/horizon/policy/non-essential-developer-data-use/)
(updated 20 July 2026) explicitly includes Spatial SDK in its developer-tool settings scope.
Optional developer telemetry choices are not proof that the shipped APK sends no data.

The app registers `VRFeature` at
[RoomSpatialActivity.kt:52 @ 08204bd7](https://github.com/nicegram/Nicegram-VR/blob/08204bd7e971cc2fe55f1983567f541dc0b50e40/TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomSpatialActivity.kt#L52).
This source proves use of the SDK, not its complete network behavior. **Unverified:** exact
SDK recipients, payload categories and retention in a release APK on Quest. Record those in
the device session and reconcile with vendor documentation before finalizing the disclosure.
Do not describe developer-tool documentation as a measured end-user traffic inventory.

### Hosting: memory expiry does not cover operational logs

[DigitalOcean's log documentation](https://docs.digitalocean.com/products/app-platform/how-to/view-logs/)
(last verified 23 March 2026; re-read 27 September) states that build and deploy logs are kept
for 90 days. Runtime output and crash output are distinct; runtime retention uses configured
forwarding. This page does not establish retention of all infrastructure metadata or the
configuration of our app. No universal two-hour deletion promise follows from room expiry.

Read-only `doctl apps get` for the deployment identified in the native receipt returned HTTP
404 in the current CLI context. No log contents or credentials were read. The public health
endpoint returned HTTP 200, ready=true, closed-beta, nicegram-required; a 404 from this CLI
context therefore does not prove that the deployment is gone. **Unverified:** actual forwarding,
infrastructure metadata retention, account-service retention and support deletion workflow.
Use the authorized account that owns this app to inspect configuration only; avoid exporting
user-bearing logs into Git.

### Exact questions to resolve

1. SDK distribution: have the rights holder or qualified reviewer assess the combined
   GPL-2.0 client and Meta Spatial SDK 0.14.0. The current
   [SDK agreement](https://developers.meta.com/horizon/licenses/oculussdk/) still contains
   section 1.2.8 restricting uses that subject the SDK to open-source terms. Identify the
   applicable permission/exception, or choose a renderer replacement and validate it on Quest.
   No exception was established by this research; no request was sent to Meta.
2. SDK privacy: obtain release-runtime data categories, recipients, controls and retention
   for this SDK version, including distinction from developer tooling diagnostics.
3. Operations: establish forwarding destinations and retention for hosting and account/auth
   services; identify the support owner and the actual deletion procedure. Leaving a room
   is not deletion of a Nicegram account.

Checks in this resume: primary sources opened, source line resolved, live privacy page fetched
with HTTP 200 and its no-server/no-data statement read. No legal approval, device traffic
capture, hosted CI or deployment is claimed. Next task: resolve questions 1–3, then complete
and review the HTML draft; do not remove its draft banner merely because this evidence exists.
