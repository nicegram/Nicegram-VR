import test from 'node:test';
import assert from 'node:assert/strict';
import { NicegramIdentity, nicegramProvider, AuthFailure } from '../identity.mjs';
const fake = (overrides = {}) => ({ ready: true, account: async () => {}, start: async id => ({ sessionId: id, loginUrl: 'https://t.me/TestBot' }), complete: async id => ({ telegramId: id, name: 'Verified' }), ...overrides });
test('existing account is required before starting bot auth', async () => {
  let called = false;
  const auth = new NicegramIdentity(fake({ account: async () => { throw new AuthFailure(403, 'NICEGRAM_ACCOUNT_REQUIRED'); }, start: async () => { called = true; } }));
  await assert.rejects(auth.start('1'), /NICEGRAM_ACCOUNT_REQUIRED/); assert.equal(called, false);
});
test('unconfirmed bot session cannot mint identity; confirmation is single-use', async () => {
  let confirmed = false;
  const auth = new NicegramIdentity(fake({ complete: async id => { if (!confirmed) throw new AuthFailure(401, 'NICEGRAM_CONFIRM_REQUIRED'); return { telegramId: id, name: 'Verified' }; } }));
  const challenge = await auth.start('1');
  await assert.rejects(auth.complete(challenge.challengeToken), /NICEGRAM_CONFIRM_REQUIRED/);
  await assert.rejects(auth.verify(challenge.challengeToken), /NICEGRAM_AUTH_REQUIRED/);
  confirmed = true;
  const user = await auth.complete(challenge.challengeToken);
  assert.equal((await auth.verify(user.identityToken)).telegramId, '1');
  await assert.rejects(auth.complete(challenge.challengeToken), /AUTH_EXPIRED/);
});
test('wrong-account confirmation fails closed and consumes challenge', async () => {
  const auth = new NicegramIdentity(fake({ complete: async () => ({ telegramId: '2', name: 'Other' }) }));
  const challenge = await auth.start('1');
  await assert.rejects(auth.complete(challenge.challengeToken), /ACCOUNT_MISMATCH/);
  await assert.rejects(auth.complete(challenge.challengeToken), /AUTH_EXPIRED/);
});
test('account removal or Internal API outage blocks admission and heartbeat refresh', async () => {
  let now = 100000, available = true;
  const auth = new NicegramIdentity(fake({ account: async () => { if (!available) throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE'); } }), () => now);
  const challenge = await auth.start('1'), user = await auth.complete(challenge.challengeToken);
  available = false; now += 60001;
  await assert.rejects(auth.verify(user.identityToken), /NICEGRAM_UNAVAILABLE/);
  now += 7200000;
  await assert.rejects(auth.verify(user.identityToken), /NICEGRAM_AUTH_REQUIRED/);
});
test('missing production configuration has no invite-only fallback', async () => {
  const auth = new NicegramIdentity(nicegramProvider({}));
  assert.equal(auth.ready, false);
  await assert.rejects(auth.start('1'), /NICEGRAM_NOT_CONFIGURED/);
  await assert.rejects(auth.verify('forged'), /NICEGRAM_AUTH_REQUIRED/);
});
test('production adapter uses scoped Internal header only server-to-server and strips general tokens', async () => {
  const seen = [];
  const provider = nicegramProvider({ NICEGRAM_API_ORIGIN: 'https://nicegram.example', NICEGRAM_AUTH_BOT: 'ExampleBot', NICEGRAM_INTERNAL_TOKEN: 's'.repeat(43) }, async (url, options) => {
    seen.push({ url, options });
    let data = url.includes('/internal/') ? { telegramId: 1 } : url.endsWith('/session') ? { sessionId: 'a'.repeat(64) } : { user: { id: 5, telegramId: 1, first_name: 'Alice', telegramAuthToken: 'must-not-escape' } };
    return new Response(JSON.stringify({ data }), { status: 200 });
  });
  await provider.account('1'); const start = await provider.start('1'); const user = await provider.complete(start.sessionId);
  assert.deepEqual(user, { telegramId: '1', name: 'Alice' });
  assert.equal(seen[0].options.headers['x-internal-request'], 's'.repeat(43));
  assert.equal(seen[1].options.headers['x-internal-request'], undefined);
  assert.equal(seen[2].options.redirect, 'error');
  assert.equal(start.loginUrl, 'https://t.me/ExampleBot?start=' + 'a'.repeat(64));
});
test('confirmed one-use auth survives a temporary second Internal lookup outage', async () => {
  let lookups = 0, exchanges = 0;
  const auth = new NicegramIdentity(fake({ account: async () => { if (++lookups === 2) throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE'); }, complete: async id => { ++exchanges; return { telegramId: id, name: 'Verified' }; } }));
  const challenge = await auth.start('1');
  await assert.rejects(auth.complete(challenge.challengeToken), /NICEGRAM_UNAVAILABLE/);
  const user = await auth.complete(challenge.challengeToken);
  assert.equal(user.telegramId, '1'); assert.equal(exchanges, 1);
});
