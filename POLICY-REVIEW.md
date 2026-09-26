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
