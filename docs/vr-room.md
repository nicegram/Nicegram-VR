# The VR room — specification and implementation plan

> **25 September 2026: historical proposal, not an implementation contract.**
> Read [the corrected scope and next task](vr-room-scope-20260925.md) first.
> The assumptions below about universal call keys, authorization, infrastructure size,
> estimates and already-working phone interoperability have not been validated.
> Room identity must be separate from Telegram call identity; the expanded design also
> covers direct calls, chats without calls, conferences and a public directory.

**Status:** specification, 22 September 2026. Nothing is implemented. Every claim about the
existing code carries the file and line it was read from; every claim about the platform carries
where it came from and when.

---

## 0. The one-sentence version

**A VR room is a Telegram group call with a body.** The call already exists in this codebase and
already works for phone users; what this feature adds is a place to be during it.

That sentence is the whole architecture, and it was not the obvious one. The obvious one — an
SFU on DigitalOcean, our own signalling, our own identities, our own encryption — would have been
four weeks of work to arrive somewhere worse, because a room built that way is invisible to
everyone not wearing a headset.

---

## 1. What the client already has

Measured in this repository, not assumed.

| Piece | Where | State |
|---|---|---|
| Group calls (conference, many participants) | `TMessagesProj/src/main/java/org/telegram/messenger/voip/` — `ConferenceCall.java`, `GroupCallMessagesController.java`, `VoIPService.java` | present |
| Group-call TL constructors | `TLRPC.java` — 35 matches for `TL_phone_createGroupCall` / `joinGroupCall` / `TL_groupCall` | present |
| Native calling stack | `TMessagesProj/jni/voip` (tgcalls) | present, compiled into this build |
| **Screen sharing inside a group call** | `VoIPService.java` — 54 matches for presentation/screencast | present |
| A shared encryption key for conference calls | `voip/EncryptionKeyEmojifier.java` | present |
| **The entry point in a chat** | `ChatActivity.java:18849` and `:24974` — `VoIPHelper.startCall(currentChat, …, createGroupCall, …)` | present |

**So voice, participants, permissions, encryption, the SFU and screen sharing are not on the
build list.** They are Telegram's, they are already in the APK, and a phone user joins the same
room from the same chat without installing anything.

What is genuinely new is three things: **an immersive scene**, **where people sit in it**, and
**a surface everyone can draw on**.

---

## 2. What the platform allows

From `quest-spatial`, read 2026-09-20/21 against Meta's documentation and the
`meta-quest/Meta-Spatial-SDK-Samples` repository (MIT).

**A hybrid app is a supported shape.** One package moves between 2D panel activities and
immersive activities. Panel activities run in three contexts — Home, Overlay, and Embedded inside
an immersive activity. Two interaction models: **exclusive** (one experience at a time; terminate
the old activity *after* starting the new one) and **cooperative** (both live). `HybridSample` is
the reference implementation.

**Our toolchain already fits**, which was not guaranteed:

| | Samples snapshot | This repository | |
|---|---|---|---|
| Android Gradle Plugin | 8.11.1 | **8.13.2** | newer ✓ |
| Kotlin | 2.1.0 | **2.1.0** | exact ✓ |
| Gradle | 9.4.1 (samples' wrapper) | 8.13 | ✓ — the documented trap is AGP **≤ 8.5** on a Gradle 9 wrapper, which is not our combination |
| compileSdk | — | 36 | ✓ |

**Unverified and load-bearing:** Spatial SDK requires **Horizon OS v69 or newer**. The Quest 3 in
this project has not been reachable to check. It is the first thing to measure when it is.

### The frame budget decides the furniture

Meta's own workload estimates (`spatial-sdk-runtime-guidelines`), which are isolated test
workloads rather than hard ceilings:

| Panel kind | FPS dips below 90 at | Consequence for this room |
|---|---|---|
| **Video view** | **3 panels** | **one** monitor. Not a wall of participant video |
| **Activity-based** | **2 panels** | **do not put the chat UI in the room** in v1 |
| UI-only view | 15 panels | the drawing surface and labels are cheap |
| Empty view | 20 panels | |

Plus: each extra 480,000 panel pixels ≈ +1% GPU; ~100 GLB objects in view; ~1,000 scene entities.

**The room this permits**, and it is enough: one shared monitor, one drawing surface, a handful of
simple avatars, and nothing else competing for the frame.

---

## 3. What the user does

### The button

In any chat where a group call is possible, beside the call control that already exists. It says
**"Open a VR room"** when none is running and **"Join the VR room"** when one is. On a phone
build the control is absent — the registry returns null, exactly as the silence rows do.

Tapping it when no call exists creates a Telegram group call through the path at
`ChatActivity.java:18849`, then opens the room. Tapping it when a call is already running joins
it and opens the room. **A phone user tapping the ordinary call button in the same chat joins the
same call** and hears everyone; they simply have no room to stand in.

### The room

```
        ┌─────────────────────────────────────────────┐
        │                                             │
        │        ┌───────────────────────┐            │   the monitor
        │        │                       │            │   one video panel,
        │        │   shared monitor      │            │   fed by the group
        │        │                       │            │   call's presentation
        │        └───────────────────────┘            │   stream
        │                                             │
        │   ╭────────────────────────────────────╮    │   the surface
        │   │        drawing surface             │    │   a UI-only panel,
        │   ╰────────────────────────────────────╯    │   strokes shared
        │                                             │
        │      ◯        ◯         ◯        ◯          │   participants
        │     you      Anna      Denis   (phone)      │   seated in a ring
        └─────────────────────────────────────────────┘
```

- **Seats are assigned, not chosen.** People arrive into a ring in join order. Walking around is
  a later question; a meeting where everyone must first find a chair is a meeting that starts
  late.
- **Participants on a phone get a seat too**, drawn as a plain marker with their name. They can
  hear, speak, share a screen and see nothing of the room — and the room shows that honestly
  rather than pretending they are present in it.
- **Leaving the room does not leave the call.** Two different acts: taking the headset out of the
  room returns to the chat panel with the call still running, exactly as it behaves on a phone.

### The monitor

Anyone in the call shares their screen the way they already can — from a phone, from Telegram
Desktop, from the headset. The stream appears on the monitor for everyone wearing a headset and
in the ordinary call UI for everyone who is not. **No new sharing mechanism is built**; this is
the group call's existing presentation stream rendered onto a surface in a scene.

### The drawing surface

Strokes, an eraser, a colour per participant, and nothing else in v1 — no shapes, no text, no
images. It persists for the life of the room and is offered as a PNG into the chat when the room
closes, which is the only artefact a meeting usually needs.

---

## 4. What has to be built, and where it runs

Only one piece of this needs a server of our own.

```
  Quest A ─┐                                    ┌─► Telegram  (voice, screen share,
  Quest B ─┼─── Telegram group call ────────────┤             participants, encryption)
  Phone C ─┘                                    └─► already works, nothing to build

  Quest A ─┐                                    ┌─► DigitalOcean
  Quest B ─┴─── room state (WebSocket) ─────────┴─► strokes + seats, ~200 lines of service
```

**The room-state service** is the whole backend, and it is deliberately tiny:

| | |
|---|---|
| What it carries | stroke events and seat assignments. Nothing else, ever |
| Room identity | the Telegram group call id. No accounts, no separate login |
| Authorisation | a token the client derives from the group call's own key material (`EncryptionKeyEmojifier` proves that key exists). Someone not in the call cannot get a token |
| Payload | opaque to the server — encrypted client-side with a key derived from the same material, so a stroke is a blob with a room id and a sequence number |
| Persistence | in memory for the life of the room; the closing PNG is made on a client, not the server |
| Shape | one WebSocket service. DigitalOcean App Platform, smallest instance, scales to zero between meetings |

**Why encrypt what is only a drawing:** because the alternative is a server that can read what
people sketch during a private conversation, and because the key is already there. It costs a
derivation, not an architecture.

**Not built, on purpose:** no SFU, no TURN, no media server, no user database, no session store.
If any of those appear on a task list, something has gone wrong — they are Telegram's job in this
design.

---

## 5. What it costs at the store, and why the order matters

**This is the consequence most easily missed.** The client is a **2D panel app** today, and 2D
panel apps are reviewed against a *subset* of the Virtual Reality Checks. Adding an immersive
activity makes it a hybrid app, and a hybrid app is reviewed as an immersive one:

| | 2D panel today | Hybrid, after the room |
|---|---|---|
| `android.hardware.vr.headtracking` | absent / `required="false"` | **`required="true"`** for the immersive path |
| VRC set | a subset | **the full set** — declared refresh rate, head-tracked graphics or a loading indicator within 4 s, focus-aware behaviour, positional tracking honoured |
| Performance review | light | frame time measured in the room, not just in the list |

**Recommendation, and it is a scheduling one:** submit the 2D client first, add the room after.
The 2D app is packaged and measured today (`docs/horizon-store-readiness.md`); the room changes
the review class, and bundling them means the first submission carries the harder checks and a
feature nobody has tested on a device. The room can ship as an update into a channel that already
exists.

---

## 6. The decomposition

Each phase ends in something checkable. Phases A and B are the risky ones — everything after them
is ordinary work.

### P-23 · A · The button and the call *(~2 days)*

The entry point, with no VR at all. `VrEntryPoints.roomButton()` in the shape the settings rows
already use; `ChatActivity` shows it only when the registry is filled. Tapping it starts or joins
a Telegram group call through the existing `VoIPHelper.startCall` path.

**Done when:** the control appears on the headset build and on no other; tapping it starts a group
call that a phone in the same chat sees and can join; nothing about the phone build changed.
*Device for the last one.*

### P-24 · B · The immersive activity *(~1 week, the risk lives here)*

Spatial SDK added to `TMessagesProj_AppQuest` only. A `RoomActivity` with
`com.oculus.intent.category.VR`, exclusive transition model, an empty scene.

**The three traps, from Meta's own known issues:**
- `finish()` on an immersive activity may not bring the 2D panel back, and with panels on
  `enableLayer = true` it can crash inside `libMetaSpatialSDK.so`. Use `layerConfig`.
- Entering immersive can kill other apps' audio — request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
  and handle `OnAudioFocusChangeListener`, or the call being joined is silenced by the room
  being opened.
- **Judge performance on the release variant.** Debug builds are much slower, and Meta lists this
  as a known issue rather than a surprise.

**Done when:** the room opens from the chat and the chat comes back on exit, repeatedly, with no
crash; Horizon OS version confirmed ≥ v69 on the device; frame time measured in an empty room.
*Device.*

### P-25 · C · Voice in the room *(~3 days)*

The group call's audio continues into the immersive activity — the audio-focus trap above is the
whole of this phase — and each participant's voice is placed at their seat.

**Done when:** voices come from the direction of their speaker; a phone participant hears
everyone; leaving the room leaves the call running. *Device, two participants.*

### P-26 · D · The monitor *(~4 days)*

Subscribe to the group call's presentation stream and render it onto one video panel.

**Done when:** a phone shares a screen and it appears on the monitor within a couple of seconds;
frame time with the monitor live stays inside budget; **one** video panel, measured, not three.
*Device.*

### P-27 · E · The drawing surface and the relay *(~1 week)*

The service on DigitalOcean, the client's stroke capture, the shared rendering, the PNG on close.

**Done when:** two headsets see each other's strokes; the latency is measured and written down;
the server is shown to hold only opaque blobs; the closing PNG lands in the chat. *Device, two
participants.*

### P-28 · F · The store consequences *(~3 days)*

The manifest changes for a hybrid app, the full VRC self-pass, the listing and screenshots
updated for a feature that did not exist.

**Done when:** the VRC list is walked item by item with evidence per line, as
`docs/horizon-store-readiness.md` does for the 2D set.

---

## 7. What this specification does not decide

Named rather than left to be discovered mid-build.

- **Whether the room is worth building at all.** Nothing here measures demand. `docs/analytics.md`
  proposes `room_created` / `room_joined` for exactly that reason, and the honest sequence is to
  ship the button that starts an ordinary group call (phase A) and see whether anyone presses it
  before building phases B to E.
- **Walking, hands and avatars beyond a marker.** A seated ring is the cheapest thing that works.
- **Whether a phone participant should see the room at all** — a flat video of it is possible and
  is a separate product question.
- **Persisting a room between meetings.** v1 rooms die with the call.
- **"на Instagram разворачиваем"** in the request appears to be a transcription slip; this
  specification assumes DigitalOcean, which was named explicitly, and either App Platform or a
  Droplet. If another target was meant, only §4 changes.

---

## 8. Sources

- The existing client: files and line numbers in §1, read in this repository at `bc2ae2c3`.
- Spatial SDK, hybrid apps, the runtime guidelines and the known issues: the `quest-spatial`
  skill, whose own figures were read from `developers.meta.com` and
  `meta-quest/Meta-Spatial-SDK-Samples` on 2026-09-20/21. **Re-check before quoting a number** —
  the skill says so of itself, and both sources move.
- The store consequence: the `quest-store` skill's VRC tables and its note that 2D panel apps are
  a different rule set.
