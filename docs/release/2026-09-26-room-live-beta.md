# Room alpha.3 — ready for the first device session

The signed engineering APK and isolated DigitalOcean backend are ready for operator testing.
Successful bot confirmation, spatial rendering and two-headset calls are **NOT_RUN**: no Quest
was connected. This is the group-room vertical slice, not the full room roadmap or Store review.

## Exact artifacts

| Item | Verified value |
|---|---|
| APK version / code | `0.2.0-room-alpha.3 (Telegram 12.10.3)` / `7089079` |
| Native build source | `ba171f2664a78dc84bee7ecc1126936def6a6083` |
| APK bytes | `117869966` |
| APK SHA-256 | `f3ff9daa498af6e9152864ee4cbb056850879f48c522842f08439cad4d4d6e16` |
| Signing certificate SHA-256 | `7481f8cddfa604bb228c691a344585c7451c7f493c565f82f8926ebcaa60497b` |
| Package / ABI / Android | `my.nicegram.vr` / arm64-only / min and target 34 |
| DO app | `b3c7c427-9c33-4edc-89be-ad08a6feb2ea` |
| Active deployment | `a3f2786f-31fe-48a4-b95c-e005adf8840b` |
| Service source | `12878bcb2a33d2873bbac356eea3f61c5a28f31a` |
| Gateway pinned in APK | `https://nicegram-vr-room-beta-dshuv.ondigitalocean.app` |
| Resources | Frankfurt, one `basic-xxs`; ephemeral 8-person rooms |

Local artifact: `build/release-review/v0.2.0-room-alpha.3/nicegram-vr-v0.2.0-room-alpha.3.apk`.
The binary is local-only; it was not uploaded to a public release. Portable receipts:
[APK](2026-09-26-room-live-beta.json), [live backend](2026-09-26-room-live-backend.json).
The older [alpha.2 receipt](2026-09-26-room-alpha.md) is historical.

## Fixes and evidence

- Corrected API base `/api/` and reused the same deployed account-check route/header as
  AI agents. A successful HTTP response with null `nicegramReg` now denies admission.
  Existing account lookup passed; unknown account returned 403; unconfirmed bot session 401.
- Preserved actual caller proof: Nicegram auth bot confirms the Telegram sender, and the server
  exchanges that one-use session, compares IDs and checks account existence again. A shared
  invitation, claimed ID or AI agents key in a client cannot replace the confirmation.
- Pinned gateway and `nicegram_auth_bot`; removed manual gateway input. Foreign invitation
  origins cannot receive the identity token. Call-cache ID must match the refreshed full chat.
- Bounded upstream response buffering before accumulation. Expiry during asynchronous identity
  refresh can no longer revive identity, room or participant leases. Two regression tests were
  observed failing before the fix and passing afterward.
- No subscription threshold or money operation was added. The AI product checks Premium using
  the same response, while this beta checks registration only. No balances are stored or sent
  to the client. The reused key is a DO runtime SECRET; no scoped-token claim is made.

Source evidence: native `e0f694c6` (actual AI API contract), `2c52ec8c` (lease races),
`ba171f26` (pinned client). AI agents `4d5123b511a4e5ba307922b28c564bc4cd12cb1b`,
`src/nicegram_api.py:20-28`; Nicegram API contract inspected at
`3a904792d296e11aeaa109f693b31e3566e54033`. Those two repositories were not changed.
Auth bot reference: [Nicegram Authenticate Bot](https://t.me/nicegram_auth_bot).

## Checks actually run

- `assembleQuestStandalone` and `testQuestDebugUnitTest`: BUILD SUCCESSFUL; 103 tests in
  22 classes, zero failures/errors/skips. The build retains upstream resource/SDK-tool warnings.
- `node --test room-service/test/*.test.mjs`: 15 passed.
- `python3 -m unittest discover -s Tools/tests -p 'test_*.py'`: 10 passed.
- `Tools/check_release.py` with `--surface hybrid`, previous code 7089069 and expected certificate:
  PASS; v2 signature; 117 prohibited permission names checked, zero matches.
- `Tools/check_room_live.mjs` against the deployed origin: health/ready PASS; unauthenticated
  create denied; existing account reaches bot confirmation; absent account and unconfirmed
  exchange denied. The check creates a challenge but sends no Telegram messages.
- `doctl --context nicegram apps get`: ACTIVE source matches the service commit above.
- `metavr device list`: No devices connected. No hardware, audio or performance pass is claimed.

Local release reproduction uses Java from Android Studio and the existing Observatory secret
injector for signing; never put passwords or the Internal credential into command arguments.
`Tools/check_room_secret_absence.py --apk <local-apk> --git-base 9c65ca00` accepts
`NICEGRAM_AUTH_HEADER` only from the process environment and checks unpacked APK entries and
changed Git blobs without printing a key or its digest. The Internal key is not a build input.
The service-only exported Git tree equals the native `room-service` tree. Future service changes
must be exported and pushed before explicit DO deployment; verify the active SHA afterward.
No full hosted GitHub suite was dispatched; this unmerged branch is outside the nightly snapshot.

## Install and test

1. Connect Quest 3/3S over USB, enable developer mode and accept USB debugging. Install the
   local APK with `adb install -r <local-apk>` (choose `-s <serial>` when two devices are attached).
   It updates the existing Nicegram VR package without clearing its data.
2. Open Nicegram VR, sign into Telegram if needed, and open a group you belong to.
   From the group menu open the VR room. Press the Nicegram verification button, press Start
   in the linked auth bot, return to the room and check confirmation. No server/key setup is needed.
3. Create a room. Copy its invitation and manually share it with a second tester. That tester
   verifies their own Nicegram account, opens the same group, pastes the invitation and joins.
4. Enter the immersive room on both Quest. Check both participant names, panel legibility,
   controller clicks, back/exit and a second entry after leaving.
5. A group admin starts the Telegram call inside the room; the other tester joins the same call.
   Join from normal Telegram on a phone too. Check both directions of audio. Live speech must
   not create voice messages. Enable the mic explicitly: joins start muted.
6. Test listener/moderator mute, denied mic permission, another existing call, account switch,
   Wi-Fi loss, focus loss and headset sleep. Recovery must not turn the mic on automatically.
   Leaving the room must end only that user's participation, not the entire group call.

## Remaining boundaries and operation

Presence is invite-bound but does not independently attest Telegram chat membership. Telegram
checks access to the actual call. No chat content is mirrored. Public discovery, chat wall,
autoplay, composer/STT/round videos, PTT bindings, avatars and other communication adapters
remain in the [full plan](../vr-room-architecture-20260926.md).

Room state is memory-only: deployment/restart ends rooms; idle presence expires after 30 seconds,
rooms and identity after two hours. The DO app is intentionally left running for the test.
Do not scale above one instance without a shared state design. Check `/healthz` for process/config
health; upstream failures are enforced on authentication/refresh, not inferred from that endpoint.
The same reused credential has coupled rotation with AI agents; update the DO runtime secret
when that credential rotates. Never copy bot tokens, user sessions or API response payloads.

Public binary/Store distribution remains gated by the Meta SDK/GPL compatibility question in
[NOTICE](../../NOTICE.md), plus device acceptance. No merge, public binary release or Store
submission occurred. The next task is the two-device checklist above, followed by fixes grounded
in device evidence. Keep unrelated WIP in Observatory and other repositories untouched.
