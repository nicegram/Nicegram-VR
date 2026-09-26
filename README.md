# Nicegram-authenticated room presence

This first vertical slice is co-located with the native client. See
[the implementation handoff](https://github.com/nicegram/Nicegram-VR/blob/codex/vr-room-mvp-20260926/docs/vr-room-mvp-20260926.md). The earlier invitation-only
proposal was rejected by the owner; there is no configuration switch to restore it.

## Required identity flow

1. `POST /v1/auth/start` receives a claimed Telegram ID. This is not trusted identity.
2. The server checks the existing Nicegram account through `GET /internal/users/{telegramId}`
   using `x-internal-request` with only `internal.users.show`. A missing account is denied before
   invoking bot auth, because Nicegram bot auth can otherwise create an account.
3. Server calls `POST /v7/telegram/session`. Client explicitly opens the returned Nicegram
   auth-bot link and the user presses Start. Nicegram verifies the bot sender against the session.
4. `POST /v1/auth/complete` exchanges the server-held, single-use session through
   `POST /v7/telegram/auth`; the confirmed Telegram ID must match the requested account.
   A second Internal lookup confirms the account still exists.
5. Only then is a random room identity token issued. Create and join both require it;
   joining also requires the room invitation. Names come from the verified Nicegram response.
6. Room heartbeats revalidate the identity; Internal lookup is cached for at most 60 seconds.
   Missing config, failed auth, missing account or failed refresh deny access. A lost heartbeat
   disarms the client microphone. Bot challenges expire in five minutes; room identity in two hours.

No Telegram MTProto authorization key, bot token or general Nicegram auth token goes into the
APK or room snapshots. The general Nicegram token returned during confirmation is immediately
discarded. Internal tokens never leave the server. Display snapshots contain only participant
IDs generated for this room and verified profile names. Group-call membership is independently
checked by Telegram; this service does not yet attest Telegram chat membership for presence.

`identity.mjs` is an adapter over existing Nicegram routes; it does not duplicate balances,
subscription rules or entitlements. Verified account existence is not a VR entitlement rule.
A separate VR entitlement has not been specified or implemented in the existing Internal API.

## Runtime configuration and deployment

All three settings are mandatory for identity admission:

- `NICEGRAM_API_ORIGIN`: verified HTTPS origin of the Nicegram API, without a path.
- `NICEGRAM_AUTH_BOT`: verified production/staging auth-bot username (no `@`).
- `NICEGRAM_INTERNAL_TOKEN`: secret service token scoped to `internal.users.show`.

When missing, `/healthz` reports `identity: nicegram-required, ready: false`; auth returns 503.
No default user, creation key, shared password or invitation can bypass this state.
Client engineering builds expose the service URL input; pin the trusted gateway origin before
any end-user distribution, because authentication must not be delegated to arbitrary hosts.

`app-spec.yaml` is a proposed one-instance Frankfurt App Platform service (`basic-xxs`).
Checked price: $5/month, 512 MiB, 40 GiB transfer; extra transfer is separate.
[Official pricing, read 2026-09-26](https://docs.digitalocean.com/products/app-platform/details/pricing/).
No existing Nicegram production app or droplet is modified.

An initial staging app `d29765d3-4338-489f-bda1-cddd249c6222` began cloning the earlier prototype
at `93939f653b10f9a23f8166e0cb506ef861c4e3bc`; it was deleted after the owner rejected
invite-only admission. No user data or room sessions were created on it. Runtime credentials
and live authentication remain unavailable; do not label a deployment ready.

App Platform recursively clones the native repository submodules. The staging spec therefore uses the bounded `codex/vr-room-service-20260926` branch,
exported from `room-service/` with `git subtree split`. The native build receipt pins both
commits and checks that the service tree matches. Do not clone FFmpeg/BoringSSL to build Node.
Check that the deployed commit equals the exported commit before enabling access.
No deploy-on-push trigger is configured.

## Bounds and local checks

`server.mjs` uses Node built-ins. One instance only: memory holds 100 rooms, 8 participants per
room, 30-second presence leases and 2-hour room lifetimes. Restart ends all rooms. Expired
identity/room data is swept within 10 seconds. Requests are bounded to 4 KiB, connection count
to 128, auth attempts and challenges are rate/size limited. This is not a durable room catalog.
No endpoint transports messages or media; live audio remains Telegram VoIP.

```sh
node --test room-service/test/*.test.mjs
doctl --context nicegram apps spec validate room-service/app-spec.yaml --schema-only
```

Tests inject fake providers locally to test trust boundaries; production always uses the
Nicegram HTTPS adapter. Eleven tests cover authentication, replay, wrong identity, unavailable
Internal API, credential scoping, confirmed one-use session retry, two-client presence,
capacity and expiry. These tests do not prove deployment or live Nicegram credentials.

Source contract inspected at nicegram-api `3a904792d296e11aeaa109f693b31e3566e54033`:
`routes/api/v7.php`, `routes/api/internal.php`, `AuthService::getAuthToken`,
`AuthBot/StartCommand`, `UserInternalService::show`, `ResponseFormat` and V6 `UserResponse`.
No Nicegram API source or deployment was changed by this adapter.
