<sub>ssheleg skills — task-pipeline · evidence-docs · quest-store · metavr-cli · brand-voice · copywriting · ux-scenarios</sub>

# Nicegram VR: Store preparation and alpha.4

**27 September 2026, Europe/Warsaw. Signed engineering build ready for device testing;
Meta submission remains Draft.** Device acceptance was explicitly deferred by the operator.
This is not an approved or final Store release.

Later same-day continuation: [fresh dashboard, artifact and policy checks](2026-09-27-resume.md).

## Fixed and checked

The asynchronous group-info callback did not include a pending incoming private call in its
occupancy decision. An incoming call arriving after the initial button press could therefore
reach the room create/join path. Both active and pending calls now produce OTHER_CALL before
starting the service. Source and regression:
[08204bd7](https://github.com/nicegram/Nicegram-VR/commit/08204bd7e971cc2fe55f1983567f541dc0b50e40).
`RoomContractsTest.incomingCallDuringChatRefreshPreventsBothCreateAndJoin` was observed failing
with the pending input ignored, then passing after the fix. This proves the decision policy;
the timing scenario still needs the headset test.

Store copy previously described the hybrid APK as panel-only and every dependency as GPL.
[Listing](../../store/listing-en.md) and brand facts now distinguish GPL client source,
separately licensed dependencies and experimental rooms. Godot worlds, avatars, editor and
account-profile redesign are not claimed as implemented.

## Exact build

| Item | Value |
|---|---|
| Version / code | `0.2.0-room-alpha.4 (Telegram 12.10.3)` / `7089089` |
| Source | `08204bd7e971cc2fe55f1983567f541dc0b50e40` |
| SHA-256 | `6f01ee237881246459473ccb10999abb8172c8b1de043e29ff585c7b62793ddc` |
| Bytes | `117869966` |
| Identity | `my.nicegram.vr`, arm64-only, min/target 34, v2 signature |
| Certificate SHA-256 | `7481f8cddfa604bb228c691a344585c7451c7f493c565f82f8926ebcaa60497b` |

Local-only APK: `build/release-review/v0.2.0-room-alpha.4/nicegram-vr-v0.2.0-room-alpha.4.apk`.
[Portable APK receipt](2026-09-27-room-alpha4.json) and [check receipt](2026-09-27-checks.json).
Build inputs were injected through the existing Observatory wrapper, including the existing
signing identity. The Internal server key was not a build input. Working-tree differences at
build time were documentation only. No APK was uploaded to GitHub or Meta.

Measured checks:

- Local `testQuestDebugUnitTest` and `assembleQuestStandalone`: BUILD SUCCESSFUL, 104 tests,
  22 classes, zero failures/errors/skips. Existing SDK XML/resource/deprecation warnings remain.
- `node --test room-service/test/*.test.mjs`: 15 pass; Python Tools unittest suite: 10 pass.
- `Tools/check_release.py --surface hybrid`, fresh prohibited-permission page, previous code
  7089079 and expected certificate: PASS; 117 names checked, zero prohibited matches.
- `Tools/check_room_secret_absence.py`: reused server credential absent from 7,226 unpacked APK
  entries and five changed Git blobs. No credential or credential digest printed.
- `curl -fLsS` of the existing gateway `/healthz`: ready=true, nicegram-required, closed-beta.
  No backend deployment/change was performed. This is not a bot-login/audio test.
- Brand linter: clean. Workspace check/render pass, 111 tests pass; UX lint has zero errors
  and 19 warnings about existing screen/flow coverage. Native docs checker run after count update.
- No full hosted CI dispatch. The new branch is unmerged and outside the main nightly snapshot.

## Saved Meta draft

Safari, Appvillis UAB, [submission](https://developers.meta.com/horizon/manage/applications/1252502307955842/submissions/1252502321289174/):
name `Nicegram VR — initial release preparation`, contact `support@appvillis.com`, reviewer notes
explaining Telegram/Nicegram login and pending acceptance. Saved metadata: Name (36-character
short description, 1,249-character long description), Apps → Community, publisher Appvillis UAB,
source website, privacy and terms URLs. Name, Categorization and Details showed completed.
Submission fields survived navigation/reload. Binary N/A, status Draft, Submit for Review disabled.

The dashboard requires organization verification, sharing-preference confirmation and the
Developer Distribution Agreement. The verification page states that a business or admin identity
must be verified to publish/update; its first question asks whether official business documents
are available. No identity documents were supplied and no agreement was accepted.

Assets: five actual screenshots at 2560×1440 are required, plus cover art/hero/icon. Existing
icon selection showed a disabled Upload button in Safari's file picker; it was cancelled.
No asset upload is claimed. Current requirements and missing files are in
[assets checklist](../../store/assets-checklist.md). Specs, age questionnaire, pricing and reviewer
access remain unfinished; no untested capability or invented rating was selected.

## Remaining gates and exact next work

1. **Distribution dependency:** resolve the existing Meta Spatial SDK/GPL compatibility gate
   in [NOTICE](../../NOTICE.md). Meta's [package documentation](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-packages/)
   identifies the [SDK license](https://developers.meta.com/horizon/licenses/oculussdk/).
   No permission/exception is established here. Do not silently remove rooms to claim a release.
2. **Privacy:** the live page still says no app server/data collection, which is false for rooms.
   Prepared a separate [policy draft and data inventory](https://github.com/nicegram/Nicegram-VR/blob/e1e2b35faa051c7357cf02c4bde2551a4dbff720/POLICY-REVIEW.md)
   on `codex/vr-room-privacy-20260927`. It is pushed, not deployed. Verify Meta SDK data behavior,
   hosting/account-service log retention and deletion procedure, finish the draft and publish
   under the Pages integration policy. A working URL does not make its contents correct.
3. **Organization:** owner completes business/admin verification and reviews the Distribution
   Agreement. Accepting that agreement through UI requires explicit confirmation at that action
   under the computer-use policy; no confirmation was sought prematurely in this blocked run.
4. **Exact-APK device acceptance:** install alpha.4 on Quest 3/3S without clearing app data;
   cold launch, Telegram sign-in, Nicegram bot confirmation, create/invite/join one group room,
   then start/join a Telegram call with a second tester. Check muted join, explicit unmute,
   moderator mute, exit, focus/sleep/network loss and the pending incoming-call race. Also run
   [device session](../device-session.md) for messaging/dictation/notifications/performance.
   All hardware results remain NOT_RUN. Capture five distinct scenes using test chats.
5. **Final submission:** complete specs/rating/sharing/pricing and workable reviewer access,
   upload only the licensed, tested APK, inspect upload validation, select its build in the
   submission and press Submit for Review. Record the returned build/submission IDs and state.
   Approval and the later release action are separate steps.

Next agent starts with the dependency/privacy gates (1–2), not a new room rewrite or an APK
upload. The operator will perform device testing later. Once a headset is available, use the
exact hash above. The broader U01–U06/W01–W08 plan remains in the private product workspace;
this bug fix does not complete it. The central multi-branch handoff is in private Dataroom,
`docs/workspaces/nicegram-vr/store-review-20260927.md` on `codex/vr-store-review-20260927`.

## Delivery boundaries

Keep APK, signing keys, secrets, logs with user data, caches and machine configuration local-only.
Commit/push source and receipts; no production merge, tag, backend redeploy or public binary
release is implied. A pre-existing unowned zero-byte Git index lock dated 26 September was
preserved under `.git/index.lock.stale-20260927` after no holder/staged edits were found.
No unrelated work was reset or stashed. No helper servers/processes remain from this run;
existing DO service is intentionally unchanged and running. The privacy worktree is retained
at `/tmp/nicegram-vr-room-privacy-20260927`, but the pushed branch is its durable delivery.

Humanization: on, own editorial pass; factual rewrite only, no extra stylistic revision.
Skills used below describe actual work; no agents were spawned.

---

**Made with [ssheleg skills](https://github.com/ssheleg/sshlg-skills)**

- [`task-pipeline`](https://github.com/ssheleg/task-pipeline) — scope and release handoff
- [`evidence-docs`](https://github.com/ssheleg/task-pipeline) — receipts and checks
- [`quest-store`](https://github.com/ssheleg/xr-dev) — Meta submission requirements
- `metavr-cli` — Quest connectivity — not a skill this family ships
- [`brand-voice`](https://github.com/ssheleg/super-ux) — corrected runtime facts
- [`copywriting`](https://github.com/ssheleg/super-ux) — store description
- [`ux-scenarios`](https://github.com/ssheleg/super-ux) — incoming-call regression scenario

<sub>A star on [the bundle](https://github.com/ssheleg/sshlg-skills) helps.</sub>
