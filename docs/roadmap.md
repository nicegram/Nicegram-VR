# Roadmap

Ordered by dependency, not by appetite. Each item says what would prove it done; none of them is
done by being written.

## Before any of it

**Confirm Horizon Store policy for a Telegram fork.** A negative answer removes the distribution
channel, so it is settled before code is spent on the store path. Sideloading is unaffected and
is how development happens meanwhile.

## Foundation — in this repository now

- [x] Fork imported at a pinned upstream revision, licence and provenance recorded
- [x] `TMessagesProj_AppQuest` module: arm64-v8a, no Play services, no Firebase plugin
- [x] Keys out of source, build fails by name without them
- [x] `NoPushProvider` — the no-services path taken deliberately
- [x] `VrPolicy` hook, inert on every other flavour
- [x] Silence profile, gate and digest
- [x] Density scale and the hit-target floor
- [x] APK assembles: 134.9 MB debug / **60.6 MB signed release**, arm64-v8a only, application
      class QuestApplicationLoader (measured 23 September 2026; the 119 MB in this line was the
      debug APK of 19 September and had been stale for four days)
- [x] Exceptions screen, reachable from Notifications, with the phone sentence on it
- [ ] First run on a physical Quest 3 and 3S

## Next

- [ ] ~~**Sign-in by code shown on the panel.** The headset displays, the phone scans.~~
      **Dropped as written (A-41).** The panel is inside the headset, so no phone camera can
      reach it. Dictation on the phone-number field answers the same pain for a fraction of the
      work; a Bluetooth keyboard already works. The TL constructors stay listed in `plan.md`
      P-10 as a reference.
- [ ] **Exceptions UI, second pass** — the screen exists and edits the profile; still to do is
      separating people from chats in the list, and letting the gate know to drop its cached
      profile when the screen writes one.
- [ ] **Digest screen** — period, chats, counts, last line, and an empty state that says nobody
      wrote rather than showing zeroes.
- [ ] **A headset view.** "Show me only these chats" is a chat folder with an include-only
      rule, and folders already exist upstream — `FilterCreateActivity`, `FiltersSetupActivity`
      and 86 references to `DialogFilter` in `MessagesController`. What is missing is making one
      of them the list the client opens on, so a headset session starts in the narrow view
      rather than in everything. That is a setting and a startup path, not a new feature, and it
      is worth saying so before someone rebuilds folders.
- [ ] **Message action bar** — reply, react, forward, more, as visible 64 dp targets. Nothing
      reachable only by a long press.
- [ ] **Dictation** — push-to-talk bound to a controller trigger or a pinch, a visible recording
      indicator for the whole recording, and a draft the user edits before sending. Recognised
      speech is never sent on its own.
- [ ] **Speech recognition provider** — endpoint and token entered by the user, stored on the
      device, never compiled in. The recipient of the audio is named on screen before the first
      recording.

      **Telegram's own transcription cannot be reused for this, and it is worth writing down
      before someone spends a day on it.** Upstream has `messages.transcribeAudio`
      (`TLRPC.java`, constructor `0x269e9a49`) behind `TranscribeButton`, and it looks like the
      obvious answer: no third-party key, no new disclosure, already authenticated. It is not.
      The request takes a `peer` and a `msg_id`, so it transcribes a message that has already
      been **sent**, and `TranscribeButton` gates it on Premium with a small trial allowance
      (`TranscribeButton.java:115`, `:212`, `:252`). Dictation happens before anything is sent
      and must work for everyone, so it needs its own recognition path.
- [ ] **Headset gallery** — shortcuts to the folders a Quest actually has, both directions.
      Folder paths are read from the system rather than hardcoded; the capture directory has
      moved between Horizon OS versions.
- [ ] **Headset settings** — density, panel distance, media autoplay, animated-sticker limit,
      and the route into exceptions.
- [ ] **Performance profile** — autoplay off, sticker limit, no blur or shadow inside the
      interface, and a frame measurement in the chat list and in a chat with media. 60 fps is a
      store requirement, not a preference.

## Measured, not asserted

Nothing above is claimed until it has been run on a headset. The three numbers that decide
whether this is a usable client, and none of which has been taken yet:

1. Frames per second in the chat list and in a media-heavy chat, on Quest 3 and 3S.
2. Characters per minute by dictation against the ray keyboard, on one fixed phrase set, for
   Russian and English separately, with the word error rate alongside.
3. Whether `MediaRecorder.AudioSource.DEFAULT` picks a usable microphone on Horizon OS, compared
   against `VOICE_COMMUNICATION` on the same phrase.
