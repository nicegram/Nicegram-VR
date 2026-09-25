# One device session, and what it is worth

**Why this document exists.** Fifty-two separate lines across [plan.md](plan.md) and the
[audit](audit-2026-09-19.md) say some version of *"still owed on a device"*. Scattered, they are
a mood. Gathered and ordered, they are **one session's agenda** — and most of this project's
remaining uncertainty is in it.

[running-on-a-headset.md](running-on-a-headset.md) is the runbook: how to connect, install,
read logs. This is the **agenda**: what to look at while you are in there, in an order chosen so
that nothing has to be set up twice, and what to write down for each. Take the runbook to get
in; take this to know what the session was for.

*Written 24 September 2026, against the state of `main` at that date. Every row names the
finding or task that will be closed, or corrected, by the answer.*

---

## Before you put it on

| | |
|---|---|
| Build | Use the signed APK and SHA-256 from [the release review](release-review-2026-09-24.md); do not substitute a debug build |
| **Do not use the published pre-release** | `v0.1.0-alpha.3` predates the `LOC_ERR` fix and every screen this fork adds is unreadable in it (A-36, Q-09) |
| A second device | Signed into the same Telegram account. Three checks below cannot be done from the headset alone |
| Someone to message you | And to call you. Two checks need an incoming call from a person who is, and is not, on your exceptions list |

---

## 1 · It starts, and it is readable *(no sign-in needed)*

| # | What to do | What closes | Write down |
|---|---|---|---|
| 1.1 | Cold start. Leave it running half an hour. | A-23 | Did it stay up |
| 1.2 | Read the panel at arm's length at each of the four interface steps. | **A-01**, P-01 | Which step you would actually use |
| 1.3 | At the largest step, check the first-run screen's buttons are reachable — scroll if you must. | **A-40** | Whether scrolling was needed |
| 1.4 | `adb shell dumpsys activity a my.nicegram.vr` — the panel's real size. | P-01, and every panel number in these documents | The px and the dp |
| 1.5 | The day/night control in the corner: switch both ways. The dark side must stay **our** theme, violet accent. | **A-37** | What each side looked like |
| 1.6 | Every screen this fork adds: first run, headset settings, silence rules, digest, dictation. **No `LOC_ERR` anywhere.** | **A-36**, A-38 | Any screen that still shows it |

## 2 · Sign in *(and it is unpleasant — that is data too)*

| # | What to do | What closes | Write down |
|---|---|---|---|
| 2.1 | Sign in by phone number, typed with a ray. Time it. | **A-41**, P-10 | Seconds, and how many mistyped characters |
| 2.2 | If a Bluetooth keyboard is to hand, do it again with that. | A-41 | The difference |
| 2.3 | The intro screen before sign-in: one page, our mark, no carousel dots. | A-34, **A-35** | Whether it matches `store/assets/screenshots/onboarding.png` |

## 3 · The promise: quiet by default

This is the section the product exists for. **Do it before anything that might mute or unmute.**

| # | What to do | What closes | Write down |
|---|---|---|---|
| 3.1 | With no exceptions set, have someone message you. Nothing must notify. | **A-02**, P-02 | Anything that appeared |
| 3.2 | Add that person as an exception. Message again. Exactly one notification. | P-02, P-05 | Count |
| 3.3 | Message from someone else. Nothing. | P-02 | |
| 3.4 | **On the second device**, open that chat's notification settings. **Nothing there may have changed.** | A-02, and the whole silence design | What they say |
| 3.5 | Master switch off in the chat-list header. Message again. Nothing. | P-07 | |
| 3.6 | Sleep the headset, have messages arrive, wake it. The digest shows them; no banners. | **A-03**, P-03 | What the digest held |

## 4 · Calls — nobody has ever done this

| # | What to do | What closes | Write down |
|---|---|---|---|
| 4.1 | Receive a call from someone **on** your exceptions list. It should ring. | **A-43**, P-21 | Did it ring, what did the panel do |
| 4.2 | Receive a call from someone **not** on it. **It must not ring** — and the missed call must still appear in the chat. | **A-43** | Both halves |
| 4.3 | Master switch off, receive a call. Nothing. | A-43 | |
| 4.4 | Answer one and place one. | P-21 | Whether audio worked at all |

## 5 · Dictation — written, never seen

| # | What to do | What closes | Write down |
|---|---|---|---|
| 5.1 | Open a chat. Is the dictation button in the composer at all? | **P-12** | Where it sits, whether it is hittable |
| 5.2 | Tap it with no service configured. It must open the settings screen, not fail silently. | P-12 | |
| 5.3 | Configure a service. Dictate a sentence. The text appears in the field and **is not sent**. | P-12 | |
| 5.4 | Watch the ring around the icon while speaking. It must move with your voice. | P-12, and the store's recording-indicator rule | |
| 5.5 | Start dictating, then leave the chat. **The microphone must be released** — check the system indicator. | **A-44** | |
| 5.6 | Dictate the same fixed phrase set by voice and by ray keyboard. Count characters per minute. | P-12's "done when" | Both numbers, Russian and English |

## 6 · The things a screenshot cannot show

| # | What to do | What closes | Write down |
|---|---|---|---|
| 6.1 | Scroll the chat list hard. Watch for dropped frames. | P-08 | Whether it held 60 |
| 6.2 | Open a chat with a round video message. Idle and playing, with and without an avatar beside it. **Fully visible?** | **A-42's neighbour P-20** | |
| 6.3 | The other media P-20 never checked: photos, stickers, GIFs, the media viewer. | P-20 | Anything clipped |
| 6.4 | Set a start folder. Restart. Does it open there? | P-15 | |
| 6.5 | Take a screenshot on the headset. Does it appear in the picker without leaving the chat? | P-14 | |

## 7 · Language *(only after `language-pack/strings_vr.ru.xml` is uploaded — Q-06)*

| # | What to do | What closes | Write down |
|---|---|---|---|
| 7.1 | Set the app language to Russian. The headset screens must be Russian. | **P-18**, A-36 | Any screen still English |
| 7.2 | The places the app names itself — update prompt, lock screen, storage, the call banner. Russian sentence, our name inside it. | **A-39** | |
| 7.3 | Settings → the version line. "Nicegram VR 0.1.0", not "Nicegram VR for Android". | A-39 | |
| 7.4 | Back to English. Everything still reads. | P-18 | |

## 8 · The six store frames *(Q-05 — the reason a signed-in session was asked for)*

Capture recipe and the frame list: [store/assets-checklist.md](../store/assets-checklist.md).
Do this **last**, with the silence rules already set up by section 3, so the frames show a client
in use rather than a client just installed.

---

## Added regression checks, 24 September 2026

- Dictate until the 60-second limit: recognition starts once and the draft is not sent.
- Stop manually: the interface stays responsive; microphone indicator clears.
- Leave the chat, switch apps or sleep during capture/recognition: recording stops and no
  late result is inserted into the abandoned composer. An already submitted request cannot
  be unsent; its late result must be ignored.
- With two test accounts containing the same chat, confirm separate digests and counts;
  clearing one does not clear the other. Reconnect does not double-count recent messages.
- Sign out and reuse the account slot: the previous account's preview must not appear.
- Deny microphone permission and simulate a service timeout/redirect: recover without losing
  the draft. No transcript is sent automatically.
- Install over the previous **release-signed** build and confirm account/settings retention.
  Do not uninstall a debug-signed installation to solve an update-signature mismatch without
  first arranging the user's account/session backup and explicit data-removal approval.

## What to do with the answers

Each row names a finding or a task. Put the answer **next to that finding**, with the date —
not in a separate report, which becomes a fourth copy of the truth that nobody maintains.
A row that fails is a new finding; a row that passes turns *"device check still owed"* into
*"measured on a device, 〈date〉"*, and that sentence is the only thing that can retire it.

**Do not skip the ones you expect to pass.** Three of this project's confident derivations were
contradicted by a device within hours of being written — A-08 is one of them, and it is kept in
the audit for exactly that reason.
