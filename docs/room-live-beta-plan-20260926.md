# Live room beta — execution plan, 26 September 2026

Owner instruction: reuse the existing Nicegram AI bot account-check mechanism and key,
keep secrets server-side, deploy to the Nicegram DigitalOcean account, produce a signed
APK ready for device testing, audit and push the work. This extends the approved room
MVP; the complete A00–A22 roadmap is not the device-test release scope.

| Task | Inputs / dependency | Completion evidence | State |
|---|---|---|---|
| R1 Discover existing integration | Observatory project/credential metadata; AI bot source | Pinned source + live response shape, no payload or secret recorded | Complete: existing account accepted; unknown 403; unconfirmed login 401 |
| R2 Correct identity adapter | R1 verified endpoint/header/registration semantics | Tests for absent account, wrong identity, replay, failure and secret isolation | Complete: 15 Node tests pass |
| R3 Deploy isolated backend | R2 tests + service-only Git commit + server-only secret | DO active commit, health and live auth rejection/start checks | Complete: active DO source and live auth rejection/start receipt |
| R4 Pin gateway and build client | R3 HTTPS origin; existing SCN-038 | Signed APK, exact SHA/version/cert, native tests and package validator | Complete: signed alpha.3; 103 JVM and package checks pass |
| R5 Audit and handoff | R1–R4 receipts | Repository documentation and scenarios updated, focused gates, all task commits pushed | Pending |
| R6 Device acceptance | R4 APK + two Quest / Nicegram accounts | Human test of auth, presence, same Telegram call, mute and lifecycle | Requires devices |

Decisions: no invite-only fallback; presence requires an existing Nicegram account and
bot-confirmed Telegram identity; no paid subscription/balance threshold was requested.
Reuse the AI bot credential only through Observatory's secret injector and DO encrypted
environment settings. Do not change the AI bot, API production, Observatory registry or
other existing DO apps. No full GitHub suite dispatch; local checks plus existing nightly
policy. This is a private engineering/device beta; renderer licensing remains a public
binary/Store distribution gate.

Resume: deploy the tested service-only branch to an isolated DO App Platform service.
R1 evidence: AI agents source `4d5123b511a4e5ba307922b28c564bc4cd12cb1b`,
`src/nicegram_api.py:20-28`; live adapter probes returned accepted / 403 account required /
401 confirmation required. Values and user payloads were not printed. Native repository owns implementation;
workspace SCN-038 owns UX; Dataroom owns the cross-repository handoff index.

Final device-beta evidence and exact next task: [release receipt and checklist](release/2026-09-26-room-live-beta.md).
