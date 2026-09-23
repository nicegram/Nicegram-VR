# Store assets — what to produce, and the rules each one must survive

Rules from `quest-store`'s VRC list and asset guidance, read 2026-09-20/21. **Re-check the
live requirements before submitting**: asset specs change and a stale spec is a rejection.

## The hard rules that fail an asset

| Rule | Where it bites |
|---|---|
| Logo on a **transparent** background | the icon and logo assets |
| **No text in the top or bottom 20%** of cover art | the cover is cropped differently in different placements |
| Screenshots must be **captured in the headset** | a phone screenshot or an emulator frame is rejected |
| **No other platform's hardware** anywhere (`VRC.Quest.Functional.6`) | only Meta Quest headsets and controllers may appear |
| Trailer **≤ 2 minutes**, text **≥ 24 pt** | a 2:01 trailer is a resubmission |
| Screenshots must be **representative** | no mock-ups of features that do not exist |

## The set to produce

| Asset | Status | File |
|---|---|---|
| App icon | **made**, 512 and 1024 | `assets/icon-512.png`, `assets/icon-1024.png` |
| Logo on transparency | **made**, 1024 | `assets/logo-transparent-1024.png` — corner alpha 0, verified |
| Cover art / hero | **made**, 2560×1440 and a 1440 square | `assets/cover-2560x1440.png`, `assets/cover-square-1440.png` |
| Screenshots — onboarding | **made**, one frame, looked at 23 September | `assets/screenshots/onboarding.png` |
| Screenshots — the app in use | **still missing** | the six below; every one is behind sign-in |
| Trailer | **missing** | optional at first submission; strong for the listing |

Everything marked *made* is rendered by `store/make-assets.py` from the app's **own vector mark**
— `drawable/nicegram_mark.xml`, the file the running app draws — rather than exported by hand.
Re-run it and the store art cannot drift from the product. A hand-exported folder is exactly what
let a blank white mark ship for three days (A-34).

Checked by pixel rather than by eye:

| Check | Result |
|---|---|
| no text in the cover's top or bottom 20% | **clear on both covers** — every sampled row in those bands is a single gradient colour |
| the logo's background is transparent | **alpha 0** at the corner |
| the icon carries the brand mark | rendered from the vector, not upscaled from the 192 px launcher icon |

**What the onboarding screenshot is, and what it is not.** It is **one** real frame of the app,
captured from the Spatial Simulator at 2064×2208 — `assets/screenshots/onboarding.png`.

This paragraph said "six real frames" until 23 September, and the six it counted no longer
exist: A-35 cut the intro carousel from six pages to one, because five of them were Telegram's
marketing about Telegram's service under our words. One page is the whole of onboarding now, and
one frame is the whole of it captured.

**Looked at, not hashed.** Opened and read on 23 September: one page, the Nicegram mark rather
than Telegram's animated plane, the title as text, the violet day/night control, and the
subtitle "An **unofficial** Telegram client, built for a headset. Quiet by default — you choose
what may interrupt you." No `LOC_ERR` — those strings reach the screen through `VrBrandNames`,
which was never on the broken path (A-36). A-35 exists because six frames were once verified as
six *distinct* images by hash and shipped showing the wrong thing; a hash cannot say a picture
is right.

It is still **not** one of the six below: it shows the app introducing itself, not the app in
use, and a listing carried by onboarding alone is a listing that shows nothing being done. Two
caveats remain untested rather than known: whether Meta accepts a simulator capture where the
rule says "captured in the headset", and whether a near-square 0.935 aspect suits placements
that expect 16:9.

## How to capture them without a headset in hand

Both headsets have been offline for much of this project. Two routes now exist:

```bash
# 1. The Spatial Simulator — renders the panel in VR on this machine
metavr ssim download && metavr ssim start
metavr app install nicegram-vr.apk && metavr app launch my.nicegram.vr

# 2. On a real device, Meta's camera service rather than adb screencap
#    (adb exec-out screencap returns the compositor frame, not the panel —
#     measured 2026-09-19, twice)
```
The MCP tool `take_screenshot` with `method='metacam'` is the second route.

> **The simulator route works, tried 22 September.** `take_screenshot` with
> `method='screencap'` against `emulator-5554` returns the panel floating in the simulator's
> room — the panel itself, not the compositor frame, which is what `adb exec-out screencap`
> returns on a real headset. The intro screen came back correct and legible at 760x1200.
>
> **It does not unblock the six below, and the reason is not the tool.** Every one of them is
> behind sign-in: a chat list, a conversation, dictation, the exceptions screen, the digest,
> the headset settings. The simulator can photograph any screen the app will draw; it cannot
> sign in to an account. What is still needed is a person signing in once, on a device or in
> the simulator, with conversations that look like conversations.
>
> **Whether Meta accepts a simulator capture is untested.** The rule says "captured in the
> headset". The Spatial Simulator is Meta's own tool and renders the same panel, but nothing
> here proves a reviewer treats the two alike — treat the simulator set as a candidate, and
> prefer `metacam` on a real Quest for the final upload.

## The six screenshots worth having

Chosen to show what the app is for rather than what it contains:

1. **The chat list, quiet** — the notification bell in its off state, visible in the header.
2. **A chat open**, one column, a real conversation.
3. **Dictation**, mid-recognition, with the text shown before sending.
4. **The silence exceptions screen** — the feature that makes "quiet by default" usable.
5. **The digest** — what waited while it was quiet.
6. **Headset settings** — the density steps naming how many chats each shows.

Captured against a **real signed-in account with plausible conversations**. An empty chat
list is a screenshot of nothing, and a reviewer judging "completeness and value" sees it.
