/** Opt-in beta smoke. Creates a short-lived login challenge; sends no bot messages.
 * VR_SMOKE_TELEGRAM_ID must identify the operator's authorized existing test account.
 * Never prints IDs, challenges, credentials, bot session URLs or account payloads. */
import assert from 'node:assert/strict';
const origin = new URL(process.argv[2]);
assert.equal(origin.protocol, 'https:');
assert.equal(origin.href, origin.origin + '/');
const id = process.env.VR_SMOKE_TELEGRAM_ID;
assert.match(id || '', /^[1-9][0-9]{0,15}$/);
async function call(path, body, credential) {
  const response = await fetch(origin.origin + path, { method: body ? 'POST' : 'GET', redirect: 'error',
    signal: AbortSignal.timeout(25000), headers: { ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(credential ? { Authorization: `Bearer ${credential}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  return { status: response.status, body: await response.json() };
}
const health = await call('/healthz');
assert.equal(health.status, 200); assert.equal(health.body.ready, true); assert.equal(health.body.identity, 'nicegram-required');
const denied = await call('/v1/rooms', { chatKey: 'channel:1234' });
assert.equal(denied.status, 401); assert.equal(denied.body.error, 'NICEGRAM_AUTH_REQUIRED');
const missing = await call('/v1/auth/start', { telegramId: '9000000000000000' });
assert.equal(missing.status, 403); assert.equal(missing.body.error, 'NICEGRAM_ACCOUNT_REQUIRED');
const start = await call('/v1/auth/start', { telegramId: id });
assert.equal(start.status, 200); assert.ok(start.body.telegramId === id, 'account mismatch');
assert.ok(/^[A-Za-z0-9_-]{43}$/.test(start.body.challengeToken || ''), 'invalid challenge');
assert.ok(/^https:\/\/t.me\/nicegram_auth_bot\?start=[a-f0-9]{64}$/.test(start.body.loginUrl || ''), 'invalid bot link');
const complete = await call('/v1/auth/complete', {}, start.body.challengeToken);
assert.equal(complete.status, 401); assert.equal(complete.body.error, 'NICEGRAM_CONFIRM_REQUIRED');
console.log(JSON.stringify({ checked_at: new Date().toISOString(), origin: origin.origin,
  health: 'PASS', unauthenticated_creation: 'DENIED', unknown_nicegram_account: 'DENIED',
  existing_nicegram_account: 'ACCEPTED_FOR_BOT_CONFIRMATION', unconfirmed_identity: 'DENIED',
  confirmed_identity_and_two_device_call: 'NOT_RUN' }, null, 2));
