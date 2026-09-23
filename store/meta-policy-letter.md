# Two policy questions for Meta, before anything is submitted

**Status:** draft, not sent. It answers questions **Q-01** and **Q-02** in
[plan.md](../docs/plan.md); both are on the same subject and belong in one message, because a
reviewer who answers the first will already have the app in front of them.

**Where it goes.** The Developer Dashboard support channel for the organisation, or the developer
support form at `developers.meta.com`. Not a store submission — the point of asking is to find
out *before* paying for stages 4 and 5 of
[horizon-store-readiness.md](../docs/horizon-store-readiness.md).

**Why it is asked at all.** A negative answer to Q-01 removes the distribution channel entirely.
Sideloading is unaffected either way, and that is how development happens meanwhile. Everything
else in the plan is work that improves the client whatever the answer; this is the one item that
could make the work moot, so it is the first thing to do and the cheapest.

**Before sending:** replace `<organisation>` and `<app id, if one exists>`, and check that the
two policy clauses still read as quoted — the page carries its own date and was read on
23 September 2026.

---

## The message

> **Subject:** Policy check before submission — a third-party client for another messaging
> service, and an in-app link to our own mobile app
>
> Hello,
>
> We are `<organisation>`, and we are preparing a Horizon Store submission for a **2D panel app**
> on Quest 3 and 3S: a Telegram client built for a headset. Before we spend the remaining
> engineering and asset work on the store path, we would like a ruling on two points, because a
> negative answer to the first would change what we build rather than how we submit it.
>
> **1. A third-party client for another messaging service.**
>
> The app is an open-source client for Telegram, built from Telegram's own published Android
> source under GPL-2.0, with the provenance and licence recorded in the repository. It is not
> published by Telegram and does not present itself as official: it carries its own name, its own
> icon, and its own listing copy, and it names Telegram only where naming the service is accurate
> — the account, the protocol, the terms the user's account is under.
>
> It connects to Telegram's own servers with our own API credentials. It carries user-generated
> content, as any messaging client does. It sells nothing, contains no in-app purchases, and
> serves no third-party advertising.
>
> Our question: **does the Horizon Store permit a third-party client of another company's
> messaging service**, and if so, is there anything in the listing, the content rating, or the
> functional review that we should prepare differently because of it? We have read
> "Store within a store" (3.1), "Windows into an existing service" (4.1) and "Limited
> functionality apps" (4.3) and believe none of them describes this app, but we would rather be
> told than assume.
>
> **2. An in-app screen offering our own mobile app.**
>
> The app has no push notifications — Horizon OS has no Play services — so while it is closed,
> nothing arrives. On the third launch, once per install, we show one screen that says so and
> offers the same publisher's free mobile app: a button that sends the two store links to the
> user's own Saved Messages, two buttons that open the App Store and Google Play pages in the
> system browser, and a QR code for a headset that is casting to an external screen.
>
> Nothing is sold and no third party is involved; it is our own free app on other platforms.
>
> Our reading of the policy is that this is not advertising in the sense of **2.1.1**, which we
> understand to govern ad serving inside an app, and that **3.1 "Store within a store"** is about
> enabling access to other apps *on the headset* and about purchase, neither of which this does.
> We note, though, that **2.1.4** gives "the ad is promoting the download of another app" as an
> example of ad content, and we do not want to discover at review that the clause was meant to
> reach this.
>
> Our question: **does a once-per-install screen offering the same publisher's free app on other
> platforms require the written agreement described in 2.1.1?** If it does, we will remove the
> screen before submitting — it is one line of code and nothing else depends on it.
>
> Thank you. We are happy to send a build or a recording of either screen.
>
> `<name>`
> `<organisation>` — `<app id, if one exists>`
> support@appvillis.com

---

## What each answer changes

| Answer | What happens |
|---|---|
| Q-01 yes | Stages 4 and 5 of the store path proceed; nothing in the code changes. |
| Q-01 no | The store path closes. Sideloading and the signed pre-releases continue; the roadmap loses its store items and keeps everything else. |
| Q-02 yes (it is an ad) | `VrEntryPoints.installFirstRun` in `QuestApplicationLoader.java:149` stops returning `MobilePromoActivity`. One line, one test, no other dependency. |
| Q-02 no | The screen ships as built. |

Record the answer in [horizon-store-readiness.md](../docs/horizon-store-readiness.md) next to
`VRQ-001` and `VRQ-002`, with the date and who said it — a policy answer with no date is worth
about as much as an undated measurement.
