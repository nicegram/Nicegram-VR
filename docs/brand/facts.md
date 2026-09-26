Contract: brand-contract v1

# Facts

| Fact | Value | Source | Checked | Review by | Public |
|---|---|---|---|---|---|
| Client class | unofficial Telegram fork; client source GPL-2.0, third-party binaries separately licensed | NOTICE.md, LICENSE | 2026-09-24 | 2026-10-24 | yes |
| Supported headset family | Quest 3 and Quest 3S | TMessagesProj_AppQuest/src/main/AndroidManifest.xml | 2026-09-24 | 2026-10-24 | yes |
| Dictation | configured service; editable text; no automatic send | TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/speech/DictationButton.java | 2026-09-24 | 2026-10-24 | yes |
| Push transport | NoPushProvider; no push while closed in this build | TMessagesProj_AppQuest/src/main/java/org/telegram/messenger/NoPushProvider.java | 2026-09-24 | 2026-10-24 | yes |
| Privacy URL | https://nicegram.github.io/Nicegram-VR/privacy.html | curl -fLsS: HTTP 200 | 2026-09-24 | 2026-10-24 | yes |
| Terms URL | https://nicegram.github.io/Nicegram-VR/terms.html | curl -fLsS: HTTP 200 | 2026-09-24 | 2026-10-24 | yes |
| Store approval | not submitted in this run | docs/release/2026-09-24-brief.md | 2026-09-24 | 2026-10-24 | yes |

Hardware quality, all-controls target size, microphone quality and language-pack upload
are unverified and must not be promoted to a claim of measured readiness.

## Room-alpha draft additions — 27 September 2026

Status: draft. Code support is not headset acceptance.

| Fact | Value | Source | Checked | Review by | Public |
|---|---|---|---|---|---|
| Experimental rooms | group/supergroup invitation rooms, existing Nicegram account and auth-bot confirmation required | docs/release/2026-09-26-room-live-beta.md @ 6f2553d442a0dd74a41dccfef43273b372361761 | 2026-09-27 | 2026-10-27 | yes |
| Room audio | Telegram group calls, no separate media server | TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/rooms/RoomCallBridge.java @ 6f2553d442a0dd74a41dccfef43273b372361761 | 2026-09-27 | 2026-10-27 | yes |
| Source scope | GPL client source, separately licensed SDKs; distribution compatibility unresolved | NOTICE.md @ 6f2553d442a0dd74a41dccfef43273b372361761 | 2026-09-27 | 2026-10-27 | yes |
| Store fields | name 40, short 500, long 1500 characters; up to 5 keywords | Safari app 1252502307955842, submission 1252502321289174 metadata form | 2026-09-27 | 2026-10-27 | yes |

The live privacy page was read on 27 September. It still states there is no app backend;
that is inaccurate for experimental rooms. HTTP 200 is not approval of its contents.
Do not submit this room build before updating the policy and reviewing the data inventory.
