# Store preparation — 27 September 2026

Status: in progress. The operator requested bug checks, fixes and Meta Store submission;
the supplied application is `1252502307955842`. This extends the room-alpha release,
not the unimplemented account/world roadmap. Existing authorization covers local builds,
routine dashboard preparation and committing/pushing task changes. No new spending,
invented review accounts, or acceptance of a legal agreement is inferred.

## Scope, dependencies and acceptance

| Requirement | Work / verification |
|---|---|
| R1 | Audit the current room/call boundary; fix confirmed defects; run focused tests and compile the signed APK. |
| R2 | Reconcile store claims with the APK and preserve exact source/hash/signing receipts. |
| R3 | Prepare the existing Meta submission through Safari; report saved fields and each remaining dashboard requirement. |
| R4 | Submit only after licensing, device acceptance, review access and required forms are satisfied. Draft/upload is not publication. |
| R5 | Push this report, implementation and next steps; keep credentials, build caches and APK local-only. |

Dependencies read: [previous beta receipt](2026-09-26-room-live-beta.md),
[distribution restriction](../../NOTICE.md), [store fields](../../store/listing-en.md),
[contribution constraints](../../CONTRIBUTING.md), and the private product workspace's
`public/projects/nicegram-vr/execution/README.md` (world/account modules remain planned).
Starting native source: `6f2553d442a0dd74a41dccfef43273b372361761`.

Contradictions: the old listing calls the build panel-only and describes every dependency
as GPL. The room-alpha APK includes an immersive activity and proprietary Meta SDK binaries.
The dashboard has no uploaded build. These are not release acceptance evidence.

## Sequence and resume point

1. Inspect native call/presence/auth transitions and current Meta requirements.
2. Fix the asynchronous call-occupancy check, verify policy tests and build locally.
3. Correct store preparation documentation; save supported dashboard draft fields.
4. Record package checks, unresolved gates and an exact next task, then commit/push.

Call regression scenario: user requests a room call; while Telegram group details load,
an incoming private call becomes pending. The room must not start a second service or
replace that incoming call. Apply the existing OTHER_CALL outcome to both active and
pending calls immediately before the create/join decision. This preserves the existing
room call flow and changes no visual layout or wording.

Hosted full CI remains nightly under the current repository policy. This unmerged branch
does not inherit a nightly pass. Hardware acceptance requires an online Quest; the two
remembered Wi-Fi devices currently report offline. Licensing remains the explicit gate
in NOTICE, not a conclusion invented by this audit.

Next task: implement R1, then append measured results and the final handoff entry.
