# Authenticated room alpha — measured build receipt

**Local engineering APK built; live Nicegram integration, two-headset acceptance and distribution
licensing remain open.** No public binary release or Store submission occurred.
[Implementation and next steps](../vr-room-mvp-20260926.md).

| Evidence | Result |
|---|---|
| Artifact version | `0.2.0-room-alpha.2 (Telegram 12.10.3)` |
| Application / versionCode | `my.nicegram.vr` / `7089069` |
| Source commit | `3c35918e7af8bac269dc028a697540a24c1fd598` |
| Release validator commit | `29169c86103a2683a350e88c7218fb7f069c917b` |
| APK bytes | `117870038` |
| SHA-256 | `ff31ed42f808b08052e68379fc72010cdca78edb6237625c3f6d56f425990da7` |
| Signature | APK v2, Nicegram certificate `7481f8cddfa604bb228c691a344585c7451c7f493c565f82f8926ebcaa60497b` |
| ABI / min / target | arm64-v8a only / 34 / 34 |
| Artifact validation | PASS with explicit `--surface hybrid`; 117 prohibited-permission names parsed, zero matches |
| JVM | 103 tests across 22 classes; zero failures/errors |
| Room service | 11 tests PASS, also repeated in fresh remote clone |
| Release tooling | 10 Python tests PASS |
| Native documentation | 22 documents / 250 checkable claims PASS at source validation |
| Hardware | `metavr device list`: `No devices connected`; NOT_RUN |
| Live Nicegram authentication | NOT_RUN; verified service settings and scoped token unavailable |
| Distribution license | UNRESOLVED: Meta SDK versus GPL client, see NOTICE |

The local file is `build/release-review/v0.2.0-room-alpha.2/nicegram-vr-v0.2.0-room-alpha.2.apk`.
The APK and signing material are not tracked; [portable receipt](2026-09-26-room-alpha.json)
and source are in Git. SHA256SUMS and the local receipt sit beside the local APK.
The prior invite-only alpha.1 is superseded and is not the approved deliverable.

Build actually run using existing vault credentials by name:

```sh
./gradlew --no-daemon -PquestAbiOnly :TMessagesProj_AppQuest:assembleQuestStandalone :TMessagesProj_AppQuest:testQuestDebugUnitTest
node --test room-service/test/*.test.mjs
python3 -m unittest discover -s Tools/tests -v
python3 Tools/check_docs.py
```

Final signed build: `BUILD SUCCESSFUL in 3m 2s`. The validator was extended to accept an exact
prerelease package version and explicitly validate hybrid headtracking/internal spatial
activity/native library. Its default remains strict 2D; no release workflow was switched to
publish this experimental hybrid APK. This validation does not establish all Meta Store policy
requirements. SDK/certificate inspection was performed on the actual APK, not Gradle settings.

## DigitalOcean source and current blocker

The service-only branch `codex/vr-room-service-20260926` points to
`512e6cffb00dfecd739a078dc92ac5d53bcce9c3`. It was exported with `git subtree split` from the
native source above. Both `native-SHA:room-service` and `service-SHA^{tree}` resolve to
`74de4f9317cf113ab158541c573ea8e7eb16e3d0`. Fresh clone, all 11 service tests and DO spec
schema validation passed. This avoids recursively cloning native Telegram submodules on DO.

The initial invite-only DO app was deleted and no VR service remains active. The prepared spec
is $5/month for one isolated Frankfurt App Platform instance; it was not redeployed with fake
credentials. Required: verified `NICEGRAM_API_ORIGIN`, `NICEGRAM_AUTH_BOT` and secret
`NICEGRAM_INTERNAL_TOKEN` scoped to `internal.users.show`. Nothing in the app may bypass them.
Do not paste the token into Git, the APK, chat or a shell command transcript.

**Exact next task:** fill those settings from the authorized vault, deploy the service-only
commit, verify deployed SHA and `ready: true`, then perform real Nicegram bot confirmation for
two test accounts. Follow the device checklist in the implementation handoff. Fix any hardware
issues and resolve the renderer's distribution boundary before a public APK/Store release.
The existing full-system A00–A22 packets remain the roadmap after this narrow room slice.

Private workspace source for SCN-038 and copy: `60ce51861733f4cf4eca5ad030c452900f5a9b4d` on
`codex/vr-rooms-architecture-20260926`. Workspace `npm run check` and 111 tests passed;
its handoff/scenario files were readable from a fresh sparse clone. Dataroom holds the central
repository/branch/commit index. No production pins were silently advanced to these branches.
