import test from 'node:test';
import assert from 'node:assert/strict';
import { createRoomServer } from '../server.mjs';
import { NicegramIdentity, AuthFailure } from '../identity.mjs';

// Review of 27 September: behaviour a real closed beta needs behind App Platform's edge.
async function fixture(t, options = {}, providerOverrides = {}) {
  const clock = options.clock || { now: 100000 };
  const now = () => clock.now;
  const identity = new NicegramIdentity({ ready: true, account: async () => {},
    start: async id => ({ sessionId: id, loginUrl: 'https://t.me/TestBot' }),
    complete: async id => ({ telegramId: id, name: id === '1' ? 'Alice' : 'Bob' }), ...providerOverrides }, now);
  async function login(id) { const challenge = await identity.start(id); return (await identity.complete(challenge.challengeToken)).identityToken; }
  const aliceToken = await login('1'), bobToken = await login('2');
  const logs = [];
  const server = createRoomServer({ identity, now, log: line => logs.push(line), ...options.server });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = async (path, body, secret = aliceToken, headers = {}) => {
    const response = await fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${secret}`, ...headers }, body: JSON.stringify(body) });
    return { status: response.status, ...await response.json() };
  };
  const create = (secret = aliceToken) => post('/v1/rooms', { chatKey: 'channel:1234' }, secret);
  return { base, post, create, aliceToken, bobToken, identity, logs, clock };
}

test('the same Nicegram account re-joining replaces its stale session instead of being locked out', async t => {
  const { post, create } = await fixture(t);
  const room = await create(), path = `/v1/rooms/${room.roomId}`;
  const again = await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken });
  assert.equal(again.status, 200);
  assert.equal(again.participants.length, 1);
  assert.notEqual(again.sessionId, room.sessionId);
  assert.equal((await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken)).error, 'SESSION_EXPIRED');
  assert.equal((await post(`${path}/heartbeat`, { sessionId: again.sessionId }, again.sessionToken)).status, 200);
});

test('an empty room is removed after the reconnect grace, not kept for its whole lifetime', async t => {
  const clock = { now: 100000 };
  const { post, create, bobToken } = await fixture(t, { clock, server: { idleGrace: 300000 } });
  const room = await create(), path = `/v1/rooms/${room.roomId}`;
  await post(`${path}/leave`, { sessionId: room.sessionId }, room.sessionToken);
  clock.now += 299000;
  const bob = await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken }, bobToken);
  assert.equal(bob.status, 200, 'joining within the grace keeps the room');
  await post(`${path}/leave`, { sessionId: bob.sessionId }, bob.sessionToken);
  clock.now += 300001;
  assert.equal((await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken }, bobToken)).error, 'ROOM_EXPIRED');
});

test('one account cannot occupy the whole room table', async t => {
  const { create, bobToken } = await fixture(t, { server: { maxRoomsPerUser: 2 } });
  assert.equal((await create()).status, 201);
  assert.equal((await create()).status, 201);
  const third = await create();
  assert.equal(third.status, 429); assert.equal(third.error, 'USER_ROOM_LIMIT');
  assert.equal((await create(bobToken)).status, 201, 'another account is unaffected');
});

test('the configured edge header identifies the client, so one tester cannot exhaust everyone', async t => {
  const { post } = await fixture(t, { server: { clientIpHeader: 'do-connecting-ip' } });
  const attempt = ip => post('/v1/auth/start', { telegramId: '0' }, '', { 'do-connecting-ip': ip });
  for (let i = 0; i < 20; i++) assert.equal((await attempt('203.0.113.1')).error, 'INVALID_ACCOUNT');
  assert.equal((await attempt('203.0.113.1')).error, 'RATE_LIMITED');
  assert.equal((await attempt('203.0.113.2')).error, 'INVALID_ACCOUNT', 'a second client keeps its own budget');
});

test('a malformed edge header falls back to the socket address', async t => {
  const { post } = await fixture(t, { server: { clientIpHeader: 'do-connecting-ip' } });
  for (let i = 0; i < 20; i++) await post('/v1/auth/start', { telegramId: '0' }, '', { 'do-connecting-ip': `bad value ${i}` });
  assert.equal((await post('/v1/auth/start', { telegramId: '0' }, '', { 'do-connecting-ip': 'another bad' })).error, 'RATE_LIMITED');
});

test('a stranger starting login for someone else cannot lock that account out', async t => {
  const { identity } = await fixture(t);
  const first = await identity.start('7');
  for (let i = 0; i < 3; i++) await identity.start('7');
  await assert.rejects(identity.complete(first.challengeToken), /AUTH_EXPIRED/, 'oldest challenge is evicted');
  const latest = await identity.start('7');
  assert.equal((await identity.complete(latest.challengeToken)).telegramId, '7');
});

test('a short Internal API outage keeps admitted people in the room; a long one or a removed account does not', async t => {
  let mode = 'up';
  const clock = { now: 100000 };
  const { post, create } = await fixture(t, { clock }, { account: async () => {
    if (mode === 'down') throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE');
    if (mode === 'gone') throw new AuthFailure(403, 'NICEGRAM_ACCOUNT_REQUIRED');
  } });
  const room = await create(), path = `/v1/rooms/${room.roomId}`;
  const beat = () => post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
  mode = 'down';
  // Heartbeats keep the lease; the last successful Internal check was at admission.
  for (let step = 0; step < 11; step++) { clock.now += 25000; assert.equal((await beat()).status, 200); }
  clock.now += 25000;
  assert.equal((await beat()).error, 'NICEGRAM_UNAVAILABLE');
  mode = 'gone';
  assert.equal((await beat()).error, 'NICEGRAM_ACCOUNT_REQUIRED');
});

test('health is 503 while identity is not configured, so a broken deploy never goes live', async t => {
  const server = createRoomServer({ identity: new NicegramIdentity({ ready: false }), log: () => {} });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const response = await fetch(`http://127.0.0.1:${server.address().port}/healthz`);
  assert.equal(response.status, 503);
  assert.equal((await response.json()).ready, false);
});

test('the invitation link opens an explanatory page for someone without the headset app', async t => {
  const { base } = await fixture(t);
  const response = await fetch(`${base}/room`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /^text\/html; charset=utf-8/);
  assert.match(response.headers.get('content-security-policy'), /default-src 'none'/);
  const html = await response.text();
  assert.match(html, /<html lang="en">/); assert.match(html, /Nicegram VR/);
  assert.doesNotMatch(html, /<script/, 'the capability lives in the fragment and no script may read it');
  assert.equal((await fetch(`${base}/room?x=1`)).status, 200, 'query strings do not break routing');
});

test('every request is logged as one structured line without credentials or room capabilities', async t => {
  const { post, create, logs } = await fixture(t);
  const room = await create();
  await post(`/v1/rooms/${room.roomId}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
  await post('/v1/rooms', { chatKey: 'bad' });
  assert.ok(logs.length >= 3);
  for (const line of logs) {
    const entry = JSON.parse(line);
    assert.equal(typeof entry.ts, 'string');
    for (const secret of [room.roomId, room.sessionId, room.sessionToken, room.inviteToken]) assert.ok(!line.includes(secret), 'secret in log');
  }
  const entries = logs.map(line => JSON.parse(line));
  assert.ok(entries.some(e => e.route === '/v1/rooms/:room/heartbeat' && e.status === 200));
  assert.ok(entries.some(e => e.route === '/v1/rooms' && e.status === 400 && e.error === 'INVALID_CHAT'));
});
