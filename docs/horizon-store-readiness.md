# Horizon Store readiness — audit and the road to release

**Measured 21 September 2026** against the built package, with requirements read from
Meta's own documentation the same day rather than from memory. Every number below is a
command's output; every requirement carries where it came from.

> **Verdict: this build cannot be uploaded today, and the reason is four lines of manifest.**
> Not the interface, not the features, not the frame budget — the permission set it inherited
> from Telegram. Everything else is closer to ready than it looks.

---

## 1. The wall: permissions

Meta's porting guide states it without hedging:

> "Horizon OS prohibits many Android permissions… **A build that requests a prohibited
> permission fails store upload.**"
> — [port-an-existing-app](https://developers.meta.com/horizon/documentation/android-apps/port-an-existing-app), updated 2026-09-14

Note *upload*, not *review*. A prohibited permission is not a rejection two weeks later; it is
a failed upload in the first minute.

**This build requests 65 permissions.** Two of them are on the prohibited list Meta returned:

| Permission | Status |
|---|---|
| `android.permission.READ_CONTACTS` | **prohibited** — named on Meta's list |
| `android.permission.WRITE_CONTACTS` | **prohibited** — same family |

Eleven more are dangerous, review-required, or meaningless on a headset, and every one of them
is an invitation for a reviewer to ask a question we do not want to answer:

| Permission | Why it is a problem here |
|---|---|
| `ACCESS_BACKGROUND_LOCATION` | background location in a messenger, on a device that does not move |
| `FOREGROUND_SERVICE_LOCATION` | the service behind it |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | telephony; a Quest has no SIM |
| `MANAGE_OWN_CALLS` | the ConnectionService path, for a device with no dialler |
| `REQUEST_INSTALL_PACKAGES` | Meta names `INSTALL_PACKAGES` as its example of a dangerous permission |
| `SYSTEM_ALERT_WINDOW` | drawing over other apps inside a spatial shell |
| `USE_FINGERPRINT` | no fingerprint sensor exists on any Quest |
| `CAMERA` | Quest camera access has its own permission model (`horizonos.permission.HEADSET_CAMERA`) |
| `GET_ACCOUNTS` | the Android account system, which Horizon does not use the way a phone does |
| `RECORD_AUDIO` | **keep** — dictation and voice messages need it; it is allowed and justified |

Plus roughly twenty launcher-badge permissions for Samsung, HTC, Huawei, Oppo and Sony
launchers, `com.android.vending.BILLING` for Google Play billing and
`com.google.android.c2dm.permission.RECEIVE` for Firebase Cloud Messaging — three families of
permission that are, on this device, requests to talk to software that is not installed.

**Reproduce:**
```bash
aapt2 dump badging nicegram-vr.apk | grep '^uses-permission' | wc -l    # 65
```

**Caveat, stated because it changes the work:** Meta's full prohibited list
(`/resources/permissions-prohibited/`) did not load when fetched. The two confirmed above come
from the fragment the search returned. **Fetch the complete list before doing the removal** —
it is likely longer than what is shown here, and guessing at it is how this audit would need
redoing.

> **Closed, 21 September 2026.** The list loads now: `Tools/extract_prohibited.py` against the
> live page yields **151** permission names. Eight permissions were removed from the quest
> flavour with `tools:node="remove"` (the two contacts ones above, `GET_ACCOUNTS`,
> `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `ACCESS_BACKGROUND_LOCATION`,
> `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`), taking the build from 65 to **57**, of
> which **zero** appear on Meta's list:
>
> ```
> $ aapt2 dump badging nicegram-vr.apk | grep '^uses-permission' | ... | sort -u > ours.txt
> $ comm -12 ours.txt prohibited.txt
> (no output)
> ```
>
> That comparison was run by hand against the **published** `v0.1.0-alpha.1` APK on
> 21 September. The release workflow runs the same check, but it did **not** run for that
> release: `Tools/extract_prohibited.py` had never been committed, and `|| true` turned the
> resulting crash into a warning. The script is tracked now and a missing extractor fails the
> release (A-25). Read the alpha's clean result as *measured locally*, not as *proved by CI*.

---

## 2. The four manifest declarations — all four are in, measured 23 September 2026

**This section said "we have none of it" for four days after all four had shipped.** Read from
the built APK rather than from the source, with
`aapt2 dump xmltree --file AndroidManifest.xml`:

| Declaration | Ours | Read from |
|---|---|---|
| `com.oculus.supportedDevices` = `quest2\|questpro\|quest3\|quest3s` | **present** | debug and standalone APK |
| `<layout android:defaultWidth="420dp" android:defaultHeight="720dp">` | **present** | both |
| `<layout android:minWidth="360dp" android:minHeight="480dp">` | **present** | both |
| `android:installLocation="auto"` | **present** | both |

And the three release-spec items that go with them, on the **standalone** APK:

| Requirement | Ours |
|---|---|
| `android:debuggable` absent | **absent** (it is `true` in the debug APK, which is correct) |
| `android.hardware.vr.headtracking` absent or `required="false"` for a 2D panel app | **absent entirely** |
| launch activity `android:excludeFromRecents="true"` | **present** on `LaunchActivity` |

The second row of the first table is still worth stopping on. **The panel's default size is ours
to declare**, and it was fought from the other end for a week — landscape, then portrait, then
the density re-base, then round videos clipped (P-20) — because this declaration was missing.
420×720 dp is a phone's shape, which is what a column of short lines wants; at the measured
system density of 1.25 that is 525×900 px, and it is where every panel measurement in these
documents comes from. `screenOrientation` was the improvised mechanism; this is the supported one.

---

## 3. What is already right

Recorded so nobody audits it twice.

| Requirement | Evidence |
|---|---|
| `VRC.Quest.Packaging.2` — v2 signature | `apksigner verify` prints a V2 signer, DN `Nicegram VR` |
| `VRC.Quest.Packaging.5` — APK < 1 GB | **60.6 MB** release (134 MB debug) |
| `VRC.Quest.Packaging.6` — 64-bit only | `native-code: 'arm64-v8a'`, nothing else |
| Release manifest — not debuggable | `application-debuggable` absent from the release badging |
| Release manifest — `excludeFromRecents` | present on the launch activity |
| Release manifest — unique `android:label` | `Nicegram VR` |
| 2D app — no head-tracking feature | no `uses-feature` at all; correct for a panel app |
| Release signing key | RSA-4096 to 2054, outside the repository, build refuses to sign without it |

---

## 4. Two facts about the platform that shape the product

### There is no per-message push, and Meta's notifications are not a substitute

Horizon OS has no Google Mobile Services. Firebase is excluded from this flavour by name. Meta
offers **User Notifications** as the replacement for Cloud Messaging — but reading the page
rather than the mapping table shows what it is: *"short, free-form notifications that you can
send to people"*, sent from a dashboard, "single send" to an audience or event-based.

That is a developer-to-user **engagement broadcast**. It cannot deliver "Ivan sent you a
message" from Telegram's servers in real time. **Nothing arrives while the app is closed**, and
the first-run screen must not imply otherwise.

### A Quest can be tested without a Quest

`metavr ssim` runs the **Meta Spatial Simulator**, which renders a panel app in a VR
environment on the development machine:

```bash
metavr ssim download && metavr ssim start
metavr app install nicegram-vr.apk && metavr app launch my.nicegram.vr
```

Two headsets have been unreachable for most of this project's life. The simulator removes that
as a blocker for layout, resizing, input and window configuration — not for frame rate, hand
tracking or anything about real optics.

---

## 5. The road to the store

Five stages. Each one's exit is a thing you can check, not a feeling.

> **Where this stands, 22 September 2026.** Stage 1 is done and measured; the rest is not, and
> two of the three things still missing need a person rather than more code.
>
> | | State | What it rests on |
> |---|---|---|
> | **Stage 1** — uploadable package | **done** | 57 permissions, 0 of the 151 on Meta's live list; `installLocation`, `supportedDevices`, `<layout>` and a unique label all present in the release APK; v2-signed with the project key; arm64 only; 60.6 MB against a 1 GB limit; `versionCode` now advances independently of upstream (A-33) |
> | **Stage 2** — good on the device | **partly** | it installs and runs on a Quest 3; the panel is 500×800 at 200 dpi; **nothing behind sign-in has been exercised**, so no frame reading, no dictation against a service, no input-speed measurement |
> | **Stage 3** — VRC self-pass | **not started** | needs Stage 2's device work |
> | **Stage 4** — business gates | **blocked on VRQ-001** | the policy question below. A Data Use Checkup is also owed if platform features are used |
> | **Stage 5** — listing and submission | **partly** | the text is written (`store/listing-en.md`); **six screenshots and a support address are missing**, and both need a person |
>
> **The three things that block submission, in the order they can be started:**
>
> 1. **VRQ-001** — ask Meta whether a third-party client of someone else's messaging service may
>    be published at all. A negative answer ends the store channel and nothing else matters.
> 2. **A signed-in session** — every one of the six screenshots is behind it. The capture route
>    itself now works (the Spatial Simulator, proven 22 September); what is missing is an
>    account with conversations that look like conversations.
> 3. **A support address** somebody reads. Two candidates are listed in the listing file; neither
>    is chosen, because inventing one is worse than leaving it open.
>
> Sideload is unaffected by all three and works today.

### Stage 1 — Make the package uploadable *(blocks everything; ~2 days)*

1. **Fetch the complete prohibited list** from `/resources/permissions-prohibited/` and the
   review-required list beside it. Do not work from this document's fragment.
2. **Strip the prohibited permissions in the quest flavour only**, with
   `tools:node="remove"` in `TMessagesProj_AppQuest/src/main/AndroidManifest.xml`. The phone
   flavours keep theirs; this is exactly the product-flavor separation Meta's guide recommends.
3. **Then fix what breaks.** Removing `READ_CONTACTS` from a Telegram fork disables contact
   sync, and the code paths behind it must degrade honestly rather than crash — this client
   already holds that rule for dictation and the silence gate.
4. Add the four missing manifest declarations, `defaultWidth`/`defaultHeight` chosen from the
   measured panel rather than copied from the example.
5. Re-measure: permission count, and that the app still signs in and sends a message.

**Exit:** `metavr store dist upload --channel ALPHA` succeeds. Nothing before this matters.

### Stage 2 — Make it good on the device *(~1 week, needs a headset or the simulator)*

The work already planned, now ordered by what a reviewer sees first:

| Plan item | Why it belongs here |
|---|---|
| **P-20** round videos clipped | `VRC.Quest.Functional.3` — the user must not be stuck; clipped media is close enough to invite a finding |
| **P-12** dictation in the composer | the feature this client is *for*; a reviewer judging "value" meets it immediately |
| **P-09** the device protocol, finished | nothing below can be claimed without it |
| **P-21** calls, and the master switch over them | `VRC.Quest.Functional.1` — a call that crashes the panel is a failed build |
| **P-18** the language pack | `VRC.Quest.Functional.13` — localisation must follow the user's language |

**Exit:** a build runs from cold boot, signs in, sends and receives, takes a call, and nothing
in it is visibly broken to someone who has never seen it before.

### Stage 3 — Self-pass the VRCs *(~3 days)*

Cheaper than a rejection: review may stop at the first violation, so the round trip costs two
weeks and tells you one thing.

- **Performance.1 / .3** — declared refresh rate held; something on screen within 4 seconds.
  Measure with `capture_perfetto_trace`, not `gfxinfo`: the one reading taken so far showed
  one frame in ten missing 16.67 ms on a *static* screen, and that was a debug build.
- **Functional.1, .3, .4** — no crash, no dead end, no data loss. The "no data loss" line
  deserves a deliberate test: this is a messenger.
- **Functional.7** — say so when there is no network. Telegram does; verify it survives here.
- **Functional.12** — multiple entitled users on one headset. Untested, and Quests are shared.
- **Security.2** — the permission set, again, after Stage 1.
- **Input.4** — focus-aware: keep rendering, ignore input when unfocused.

**Exit:** every ✓ row of the 2D column in `references/vrc-checklist.md` has an answer with
evidence beside it.

### Stage 4 — The business gates *(parallel, but they have calendar time of their own)*

These are not code, and each can stall a launch on its own.

1. **Developer organisation verification** — required before submitting; has its own deadline
   policy.
2. **VRQ-001, still unanswered, and the largest single risk in this document.** Meta's app
   policies include rules on store-within-a-store and cross-app linking, and content review
   judges "completeness, polish and value". **A third-party client for someone else's
   messaging service, carrying arbitrary user content, is a policy question before it is a
   technical one.** It should be asked of Meta directly, in writing, before Stage 2 is paid
   for. Everything else in this plan is work; this is the only item that could make the work
   moot.
3. **VRQ-002 — whether the phone-app offer counts as an advertisement.** The third-launch
   screen (P-30) links to this developer's own free app on the App Store and Google Play.
   Two clauses in Meta's app policies bear on it, and neither settles it:

   - *2.1.1* — "Apps hosted on the platform may not run ads unless expressly agreed by you and
     Meta Platforms Technologies in writing", and *2.1.4* gives "the ad is promoting the
     download of another app" as an example of ad content. Read broadly, a screen promoting a
     download is an ad.
   - *3.1* "Store within a store" is about enabling access to other apps **on the headset**,
     and *3.1.2* is about **purchase**; this screen does neither, and Nicegram Mobile is free.

   Self-promotion of the publisher's own app is common and is not what clause 2.1 was written
   for, but the wording does not carve it out, so this is a **question for Meta in the same
   letter as VRQ-001**, not a judgement to make here. If the answer is unfavourable the screen
   comes out in one line — `VrEntryPoints.installFirstRun` in `QuestApplicationLoader.java:149`
   — and nothing else in the build depends on it. Read 23 September 2026 from
   `developers.meta.com/horizon/llmstxt/policy/app-policies.md`, which carries its own date.

4. **Data Use Checkup** — required before submission if platform features are used; without a
   current one, platform features stay limited to test users.
5. **Age rating questionnaire**, and **GRAC** separately if South Korea is in scope — it binds
   even for test channels there.
6. **Privacy policy URL live** — `nicegram.me/privacy-policy` answers 200 today.

### Stage 5 — Listing and submission *(~1 week, then ≥2 weeks of review)*

- Store assets to spec: logo on transparent background, **no text in the top or bottom 20%**
  of cover art, screenshots captured **in the headset**, trailer **≤ 2 minutes**, text ≥ 24 pt,
  and no other platform's hardware anywhere in them (`VRC.Quest.Functional.6`).
- Upload to Production — and remember it **does nothing until "Submit for Review" is clicked**.
- Budget **at least two weeks**, and expect at least one correction round: upload validation
  reports packaging problems *after* the upload.

---

## 6. What this means for the schedule

| Stage | Work | Calendar |
|---|---|---|
| 1 — uploadable | 2 days | 2 days |
| 2 — good on device | 1 week | 1 week |
| 3 — VRC self-pass | 3 days | 3 days |
| 4 — business gates | small, but **VRQ-001 is unbounded** | parallel, and possibly blocking |
| 5 — listing + review | 1 week | **+2 weeks of Meta's review** |

**Roughly five weeks of work, plus review** — if VRQ-001 comes back favourable. If it does not,
the honest answer is that the distribution route changes to release channels and sideloading,
which need no review at all and are already available.

---

## 7. The one thing to do first

Ask Meta about VRQ-001. Everything in Stages 1–3 is real work that improves the client whatever
the answer, but only the policy answer decides whether it ends on the Horizon Store or on a
release-channel link. It costs an email and it gates five weeks.
