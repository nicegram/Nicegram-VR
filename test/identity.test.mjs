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
test('production adapter uses Internal header only server-to-server and strips general tokens', async () => {
  const seen = [];
  const provider = nicegramProvider({ NICEGRAM_API_BASE_URL: 'https://nicegram.example', NICEGRAM_AUTH_BOT: 'ExampleBot', NICEGRAM_INTERNAL_TOKEN: 's'.repeat(43) }, async (url, options) => {
    seen.push({ url, options });
    let data = url.includes('/info-internal-full/') ? { nicegramReg: '2020-01-01 00:00:00' } : url.endsWith('/session') ? { sessionId: 'a'.repeat(64) } : { user: { id: 5, telegramId: 1, first_name: 'Alice', telegramAuthToken: 'must-not-escape' } };
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
const config = { NICEGRAM_API_BASE_URL: 'https://nicegram.example/api/', NICEGRAM_AUTH_BOT: 'ExampleBot', NICEGRAM_INTERNAL_TOKEN: 's'.repeat(43) };
test('AI bot API prefix and registration distinguish existing Nicegram accounts from HTTP 200 unknown IDs', async () => {
  let registered = true;
  const provider = nicegramProvider(config, async (url, options) => {
    assert.equal(url, 'https://nicegram.example/api/v7/user/info-internal-full/1');
    assert.equal(options.headers['x-internal-request'], config.NICEGRAM_INTERNAL_TOKEN);
    return new Response(JSON.stringify({ status: 200, data: { nicegramReg: registered ? '2020-01-01 00:00:00' : null, regdate: '2019-01', gems: 0, hasPremiumPlus: false } }));
  });
  await provider.account('1'); registered = false;
  await assert.rejects(provider.account('1'), /NICEGRAM_ACCOUNT_REQUIRED/);
  await assert.rejects(provider.account('../other'), /INVALID_ACCOUNT/);
});
test('API location, forbidden key, malformed and oversized responses fail closed', async () => {
  for (const location of ['http://nicegram.example/api/', 'https://u:p@nicegram.example/api/', 'https://nicegram.example/api/?key=x', 'https://nicegram.example/other']) {
    assert.equal(nicegramProvider({ ...config, NICEGRAM_API_BASE_URL: location }).ready, false);
  }
  for (const response of [new Response('{}', { status: 403 }), new Response('<html>'), new Response('x'.repeat(262145)), new Response(JSON.stringify({ status: 403, data: { nicegramReg: '2020-01-01' } }))]) {
    await assert.rejects(nicegramProvider(config, async () => response).account('1'), /NICEGRAM_UNAVAILABLE/);
  }
});
