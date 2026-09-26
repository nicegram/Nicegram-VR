# VR rooms: Telegram media, chat panel and two voice actions

26 September 2026. **Implementation target, not a shipped feature.** Supersedes the open
transport choice in [the earlier scope](vr-room-scope-20260925.md) and the historical
[initial room proposal](vr-room.md). All live voice/video remains in Telegram. No new SFU,
TURN, LiveKit deployment or bridge is part of this feature.

## Confirmed product behavior

The room contains a large, scrollable display of the associated Telegram chat. Each client
loads that content under its own Telegram account permissions; room servers do not mirror
message history. Drafts and scrolling are private by default; optional follow-presenter
synchronizes an anchor, not protected content.

Two distinct actions:

1. **Compose a message:** type text, dictate text into an editable draft, record a voice
   message, or record a round video. Voice-message hold/release sends through the native
   Telegram sender; cancellation never sends. Dictation requires explicit Send after review.
2. **Speak live:** use the existing Telegram call, or explicitly start/join one first.
   A separate controller action can hold-to-unmute. It does not record a voice message,
   send text, or run transcription. Telegram may still show its own call service events.

New incoming voice messages play sequentially when the large autoplay checkbox is enabled;
it defaults on but preserves an explicit off choice. No historical backlog or own outgoing
messages autoplay on entry. Pause/skip/retry and queue status remain visible. Live calls and
capture suppress autoplay. Room/account/focus loss cannot trigger delayed old playback.
This is local playback per headset, not a promise of sample-synchronous shared sound.

## Native integration seams measured at 89f91e16c86ad0d2a28a33083f958f9e5e67e2a6

- `TMessagesProj/src/main/java/org/telegram/messenger/voip/VoIPService.java:545`:
  `setMicMute(mute, hold, send)` is an integration point. It may return early while switching;
  observe actual native state before indicating TALKING. Release during delayed unmute
  requires a new mute, not merely changing the UI icon.
- `TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java:3618`:
  native playback entry; existing voice playlist must not compete with a room auto-next queue.
  Recording starts at line 4707 and stops at line 4965 in that pinned source.
- `TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java:30`:
  new-message events; history-page loading must never enqueue old voice notes as new audio.
- `TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/speech/HttpSpeechToText.java`:
  existing configurable STT transport. Reuse its abstraction with scoped product auth;
  never pass room credentials to an arbitrary configured endpoint.
- `TMessagesProj/src/main/java/org/telegram/tgnet/tl/TL_phone.java:1194`:
  conference creation schema exists; actual Quest interoperation remains unverified.

Room modules proposed: context resolver, identity adapter, coordinator/store, Telegram chat
adapter, large chat panel, composer, dictation, voice queue, call adapter, audio/input policy,
realtime client, scene, directory, round-video adapter and diagnostics. Keep pure state/ports
separate from Android/Telegram implementations; scene/activity destruction must not hang up
an existing call. Meta dependencies stay in the Quest flavor.

## Safety invariants and open engineering work

- ComposeHold and LiveHold are logical, remappable input actions with on-screen equivalents.
  No hidden call initiation from a held button before permission/join completes.
- One capture owner. Initial implementation disables voice-message/STT/round recording
  while a Telegram call owns capture, including muted calls; text remains available.
  Seamless recorder handover requires a separately proven native suspension API.
- PTT starts muted, respects Telegram speaker roles, and closes on release/cancel/focus,
  controller loss, headset removal or role revocation. Old hold generations cannot unmute.
- Audio audience is the actual Telegram call, including ordinary phone participants.
  Forum-topic VR rooms do not isolate the audio of a shared group call.
- Round video needs explicit source capability and preview. Outward Quest cameras are not
  a selfie camera; avatar capture is a separate implementation, not assumed face tracking.
- VR admission is distinct from Telegram content/call permission. Nicegram account lookup
  alone is not authentication; service credentials never ship in the APK.
- All new room state/identity integration/scene assets and optional explicit STT processing
  target DigitalOcean. Live media and chat files continue to travel through Telegram.

## Implementation order and resume

The private product workspace WORK-018 owns the complete architecture, module contracts and
packets A00–A22; those supersede R0–R6. Start with verified identity plus a hybrid/native-media
spike (A01/A03), then room service admission/realtime and a silent room. Add the chat panel,
composer/autoplay/voice/STT/round video, followed by live Telegram/PTT, communication-type
coverage, directory/moderation and release acceptance. A hardware failure is NOT_RUN/failed,
not a green host-test result.

Proposed future regression targets include native playlist isolation, account-switch queue
fencing, new-vs-history updates, recording cancellation, delayed STT result, release during
unmute, role revocation, and zero sendMessage calls from live voice. These tests are not yet
implemented by this documentation change. The existing 2D release candidate is unchanged.

Validation: documentation checker and diff whitespace checks are run before handoff; exact
results are recorded centrally. No APK build, deployment, hosted workflow dispatch or room
runtime implementation occurred in this planning change. Preserve the nightly CI policy and
exact-SHA release guards. Keep device logs containing user data, credentials and local config
out of task commits.
