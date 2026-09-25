# Daily CI batching — operator decision, 25 September 2026

Scope: `.github/workflows/build.yml`. The full Quest debug build and existing checks
run at **23:00 Europe/Warsaw** on the default-branch snapshot. Pushes and pull requests
no longer trigger this full hosted suite. GitHub can delay scheduled runs.
`workflow_dispatch` remains available for an explicitly selected urgent candidate.

This applies the operator instruction `ci-batch-2026-09-25-v1`, supplied on resumption
of the VR release task. The policy was read from the pinned Observatory policy and
acknowledged for the current session. Nicegram VR was not enrolled in the local notice
mechanism and still had push/PR triggers at base `2fc5691ab741cbf89616ac6b8aafed82a24668df`.

## Working under the budget

- Run focused checks locally; do not dispatch a complete hosted suite after each push.
- The existing job, its test/build commands, 120-minute timeout, native cache and
  per-ref cancellation remain. APK artifacts expire after seven days; release assets
  and existing artifacts are not deleted by this change.
- Unmerged branches are outside the nightly snapshot. Integration follows the project
  review rules. An absent, queued, cancelled or billing-blocked check is not a pass.
- A run checks its event SHA. A result for an older SHA does not verify later edits.
- `.github/workflows/release.yml` retains its explicit tag/manual release triggers.
  It is not a nightly job. A push of a release tag can publish a public release;
  do not use that action merely to obtain a test result.
- This client has no SMS metrics job. The three-day SMS cadence belongs to the
  repositories that own that workload.

## Verification and handoff

Objective: apply the new CI cadence without changing runtime code or weakening checks.
The change is intentionally separate from the unmerged VR runtime candidate, whose
physical Quest acceptance is still pending. No paid workflow was manually dispatched.

Validation: actionlint 1.7.12 on both workflows; parsed before/after comparison against
`2fc5691ab741cbf89616ac6b8aafed82a24668df` preserves the original job and steps after
removing only the new retention field. The trigger set is exactly schedule/manual;
cron is `0 23 * * *`, timezone is `Europe/Warsaw`, retention is seven days.
Results are recorded by the associated commit and local command output; hosted
execution of the new schedule remains **Unverified** until its first run.

Next task: integrate this isolated CI change, then read the first scheduled run for
its exact SHA. Reconcile its result before calling the integration snapshot green.
Hardware acceptance and Store submission are separate gates; a cadence change does
not complete them. Credentials, machine policy metadata and local logs stay outside Git.

GitHub documents the timezone field and default-branch behavior in
[Events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
