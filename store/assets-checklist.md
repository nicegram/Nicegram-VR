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

| Asset | Status | Note |
|---|---|---|
| App icon | **have** — `ic_launcher_default`, Nicegram's own monogram on `#000000` | check the transparent-background variant is what the form wants |
| Cover art / hero | **missing** | keep all text out of the top and bottom fifths |
| Screenshots (min. several) | **missing** | must come off a headset or the Spatial Simulator |
| Trailer | **missing** | optional at first submission; strong for the listing |

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
The MCP tool `take_screenshot` with `method='metacam'` is the second route. **Neither has
been tried yet** — no device has been reachable since the tooling was installed.

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
