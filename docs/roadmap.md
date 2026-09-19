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
- [ ] First build run on a physical Quest 3 and 3S

## Next

- [ ] **Sign-in by code shown on the panel.** The headset displays, the phone scans; the headset
      camera is not used. Upstream has the scanning side only, but the TL constructors are in
      the schema already.
- [ ] **Exceptions UI** — people, chats and words, with the on-screen statement that phone
      notification settings are untouched.
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
