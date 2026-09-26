# Closed-test room presence

Experimental service co-located with the client for this first vertical slice. It is not the
planned authenticated Nicegram room backend. See [the client handoff](../docs/vr-room-mvp-20260926.md).

`server.mjs` uses only Node built-ins; `node --test room-service/test/*.test.mjs` exercises two
HTTP clients, cross-room/session isolation, capacity, expiry and atomic creation. It accepts
no messages, Telegram authorization keys or media. Display names are unverified aliases.

A random creation key (at least 32 characters) is supplied as `ROOM_CREATE_KEY` in the runtime
environment. Never commit it, bake it into the APK/image, or paste it into logs. Create requires
that key; join requires a 256-bit invitation capability. The invitation lives in the URL fragment;
the native client explicitly sends it in the JSON join body. A joined participant receives a
separate session capability; snapshots contain no tokens. Invitation holders can share their
access. There is no Nicegram account attestation or server-side Telegram membership check.
Telegram independently checks actual call membership and privileges; presence is not authority.

Default bounds in `createRoomServer`: 8 participants per room, 100 rooms, 30-second presence
lease, 2-hour room lifetime, 4 KiB request / bounded response, 128 connections and request rate
limit. Expired data is swept at most 10 seconds later. A restart discards all rooms; run ONE
instance only. This is intentionally unsuitable for public discovery or production access.

## DigitalOcean staging recipe

`app-spec.yaml` describes one `basic-xxs` App Platform instance in Frankfurt. It has a placeholder
creation key; replace it in a private temporary copy or through the DO secret interface.
The intended source is `codex/vr-room-mvp-20260926`, with no deploy-on-push trigger. Verify the
actual deployment commit against the tested commit before distributing its endpoint.

The checked DO price is $5/month for 512 MiB and 40 GiB transfer; excess transfer is separate.
[Official pricing, read 2026-09-26](https://docs.digitalocean.com/products/app-platform/details/pricing/).
This spec is a proposed isolated test resource, not evidence that deployment happened.
No existing Nicegram app, DNS, database or production droplet should be modified for it.

```sh
node --test room-service/test/*.test.mjs
doctl --context nicegram apps spec validate room-service/app-spec.yaml --schema-only
# After replacing the placeholder in a private local file:
# doctl --context nicegram apps create --spec /private/path/room-app-spec.yaml
```

After deploy, `/healthz` must report `closed-beta` and `invite-only`. Repeat the two-client
create/join/heartbeat/leave flow against its HTTPS address. Do not log response credentials.
Deletion of this isolated staging app is the rollback; all invitations expire with its process.
