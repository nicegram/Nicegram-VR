# Analytics — what to use, and what it costs us to have any

**Status:** proposal, 22 September 2026. Nothing is implemented. One decision in it is not an
engineering decision and is marked as such.

---

## 0. The thing to settle before the tool

**Two hours before this was written, this project published a privacy policy saying the app
collects nothing.**

> "**Nothing.** Appvillis operates no server for this application and receives no data from it.
> There is no account with us, no identifier assigned by us, and no telemetry."
> — <https://nicegram.github.io/Nicegram-VR/privacy.html>

Shipping analytics makes that false. It is not a reason to refuse — a product with no idea
whether anyone opens it is a product managed by guesswork — but it has three consequences that
arrive whatever tool is chosen, and they are the expensive part:

| Consequence | What it costs |
|---|---|
| The privacy policy must change **before** the first build that sends an event | one commit; the page is in `gh-pages`, its history is public, and a policy that lags the code is the kind of untruth a store reviewer treats as a finding |
| Meta's **Data Use Checkup** must describe it | a form, filed before submission; platform features stay limited to test users without a current DUC |
| The positioning changes | "privacy first" is in the organisation's own profile. It survives *minimal, anonymous, self-hosted* analytics. It does not survive session replay in a messenger |

**This is a messenger.** Whatever is measured, the content of conversations, the identity of
correspondents and anything derived from them are out of scope permanently — not as a default to
be revisited, as a boundary. That single sentence eliminates most of the market.

---

## 1. What was compared, and against what

Four candidates, judged on constraints this project actually has rather than on feature counts.

| Constraint | Where it comes from |
|---|---|
| **The SDK must be GPL-2.0 compatible** | the app is GPL-2.0, inherited from Telegram and retained (`NOTICE.md`). An AGPL-3.0 library linked into it is a licence conflict, not a preference |
| **Self-hosted on DigitalOcean** | asked for; also the only shape that keeps "we hold nothing" close to true |
| **No unique device identifier, no PII** | the product's own claim, and the thing that makes a DUC answerable |
| **No session replay, ever** | in a messenger it would record conversations |
| **Light enough to run and keep running** | one small service beside an app with no revenue |

| Candidate | SDK licence | Self-host shape | Verdict |
|---|---|---|---|
| **Aptabase** | **MIT** (`aptabase/aptabase-kotlin`) — server is AGPL-3.0 | ASP.NET + ClickHouse + Postgres, one compose file | **recommended** |
| PostHog | MIT SDK | "unlikely to scale past a couple hundred thousand events without significant effort"; Kubernetes support dropped May 2023; PostHog themselves recommend Cloud | rejected for this shape |
| Countly | community edition | Node + MongoDB, heavier; strongest features are the ones a messenger must not use | rejected |
| Matomo / Plausible / Umami | GPL / MIT | built for **web pages**, not for an Android app's lifecycle | wrong tool |

**Why the licence line matters and is not pedantry.** Aptabase's *server* is AGPL-3.0 and its
*Kotlin SDK* is MIT. Only the SDK is linked into the app, so the app stays GPL-2.0-clean; the
AGPL obligations fall on whoever runs the server, which is us, and are discharged by publishing
any modifications — which an open-source-first organisation was going to do anyway. Had the SDK
been AGPL, this would have ended the option outright.

**Why PostHog loses despite being the better product.** Its strengths — session replay, feature
flags, surveys, autocapture — are the features that must be switched off in a messenger, and its
self-hosted deployment is the one its own authors steer people away from. Choosing it would mean
running the heavy thing to use the light part of it.

---

## 2. What gets measured

The rule: **an event may describe the client's own behaviour, never the user's conversations.**
If an event's value could differ depending on who someone talks to or what they said, it does not
ship.

| Event | Properties | Why it earns its place |
|---|---|---|
| `app_opened` | cold/warm, Horizon OS version, headset model | the denominator for everything else; also the only way to learn the OS spread |
| `session_length` | seconds, bucketed | a headset session is a different animal from a phone session and nobody has measured one |
| `density_changed` | step name | the readability scale is the feature this fork is named for, and which step people settle on is unknown |
| `layout_changed` | one-at-a-time / split / rail | settles P-19's premise with data instead of argument |
| `silence_master_toggled` | on/off | whether "quiet by default" is kept or turned off is the product's central bet |
| `silence_exception_added` | kind: person/chat/word — **never the value** | whether anyone builds a profile at all |
| `digest_opened` | chats waiting, bucketed | whether "what piled up" is used or ignored |
| `dictation_used` | outcome: text / empty / refused / no service | the second-largest feature, entirely unmeasured |
| `room_created` / `room_joined` | participant count, bucketed | when the VR room ships |
| `crash_free_session` | boolean | the honest minimum for stability, without a crash SDK |

**Never, under any tool:** message text, captions, chat or user identifiers, contact counts, the
words in a silence profile, the dictation endpoint or its token, the recognised text, screen
recordings, anything keyed to a stable device identifier.

---

## 3. Where it runs

```
Quest ──► analytics.nicegram.me ──► Aptabase (DO App Platform or a 2 GB Droplet)
                                      ├── ClickHouse  (events)
                                      └── Postgres    (accounts, app registry)
```

The smallest thing that works: one DigitalOcean App Platform component or a single 2 GB Droplet
running the project's compose file, Postgres as a managed database if it should outlive the
Droplet. Volumes are the only stateful part; ClickHouse is the one that grows.

**Ingest is one endpoint over HTTPS.** The app posts a batch when it is foregrounded and on exit;
nothing is sent while the app is closed, because on Horizon OS nothing runs while the app is
closed.

**An opt-out that works.** A switch in headset settings, default **on** or **off** is the
operator's call, and the switch must be honoured by not collecting rather than by discarding
later. The privacy policy states which default is shipped.

---

## 4. What to do, in order

1. **Decide the default** — analytics on with an opt-out, or off with an opt-in. This is a
   product decision, not an engineering one, and it changes what the privacy policy says.
2. Stand up Aptabase on DigitalOcean; register the app; keep the app key in the vault, not in the
   repository.
3. Add the MIT SDK to `TMessagesProj_AppQuest` **only**, behind a `VrAnalytics` registry in the
   same shape as `VrPolicy` — inert on every other flavour, one null check when absent.
4. Emit the ten events above and nothing else. A test asserts the event list against this
   document, so a new event has to be argued for here first.
5. **Update the privacy policy in the same change.** Not the next one.
6. File the Data Use Checkup before the next store submission.

**Estimate:** two days of work, plus the DigitalOcean setup, plus whatever the policy decision
takes. Step 1 blocks steps 4 and 5 and nothing else.
