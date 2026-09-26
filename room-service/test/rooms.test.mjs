import test from 'node:test';
import assert from 'node:assert/strict';
import { createRoomServer } from '../server.mjs';
import { NicegramIdentity, AuthFailure } from '../identity.mjs';
async function fixture(t, options = {}) {
  const identity = new NicegramIdentity({ ready: true, account: async () => {},
    start: async id => ({ sessionId: id, loginUrl: 'https://t.me/TestBot' }),
    complete: async id => ({ telegramId: id, name: id === '1' ? 'Alice' : 'Bob' }) }, options.now || Date.now);
  async function login(id) { const challenge = await identity.start(id); return (await identity.complete(challenge.challengeToken)).identityToken; }
  const aliceToken = await login('1'), bobToken = await login('2');
  const server = createRoomServer({ identity, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const post = async (path, body, secret = aliceToken) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${secret}` }, body: JSON.stringify(body) });
    return { status: response.status, ...await response.json() };
  };
  return { post, bobToken, identity, create: () => post('/v1/rooms', { chatKey: 'channel:1234', name: 'forged client name' }) };
}
test('two verified clients share presence; caller names never override Nicegram identity', async t => {
  const { post, create, bobToken } = await fixture(t);
  const room = await create(); assert.equal(room.status, 201);
  const path = `/v1/rooms/${room.roomId}`;
  const bob = await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken, name: 'forged' }, bobToken);
  assert.equal(bob.participants.length, 2);
  const alice = await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
  assert.deepEqual(alice.participants.map(p => p.name), ['Alice', 'Bob']);
  assert.equal(alice.inviteToken, undefined); assert.equal(alice.sessionToken, undefined);
  assert.equal(alice.participants[0].telegramId, undefined);
  await post(`${path}/leave`, { sessionId: bob.sessionId }, bob.sessionToken);
  assert.equal((await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken)).participants.length, 1);
  assert.equal((await post(`${path}/media`, {})).status, 404);
});
test('invitation and claimed Telegram ID never replace Nicegram authentication', async t => {
  const { post, create, bobToken } = await fixture(t);
  assert.equal((await post('/v1/rooms', { chatKey: 'channel:1234', telegramId: 1 }, 'bad')).status, 401);
  const a = await create(), b = await create(), path = `/v1/rooms/${a.roomId}`;
  assert.equal((await post(`${path}/join`, { chatKey: a.chatKey, inviteToken: a.inviteToken, telegramId: 1 }, '')).status, 401);
  assert.equal((await post(`${path}/join`, { chatKey: a.chatKey, inviteToken: b.inviteToken }, bobToken)).status, 401);
  assert.equal((await post(`${path}/join`, { chatKey: 'channel:5678', inviteToken: a.inviteToken }, bobToken)).error, 'WRONG_CHAT');
  assert.equal((await post(`${path}/heartbeat`, { sessionId: a.sessionId }, b.sessionToken)).status, 401);
  assert.equal((await post(`${path}/leave`, { sessionId: a.sessionId }, a.inviteToken)).status, 401);
});
test('leases expire, capacity is bounded, expired sessions cannot revive', async t => {
  let clock = 100000;
  const { post, create, bobToken } = await fixture(t, { now: () => clock, capacity: 1 });
  const room = await create(), path = `/v1/rooms/${room.roomId}`;
  const join = () => post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken }, bobToken);
  assert.equal((await join()).error, 'ROOM_FULL'); clock += 30001;
  assert.equal((await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken)).error, 'SESSION_EXPIRED');
  assert.equal((await join()).status, 200); clock += 7200000;
  assert.equal((await join()).error, 'ROOM_EXPIRED');
});
test('invalid creation is atomic and room count is bounded', async t => {
  const { post, create } = await fixture(t, { maxRooms: 1 });
  assert.equal((await post('/v1/rooms', { chatKey: 'bad' })).status, 400);
  assert.equal((await create()).status, 201); assert.equal((await create()).error, 'ROOM_LIMIT');
});

test('room and participant expiry during async identity lookup cannot revive presence', async t => {
  for (const expiringRoom of [true, false]) {
    let clock = 100000;
    const { post, create, bobToken, identity } = await fixture(t, { now: () => clock, ttl: 60000 });
    const room = await create(), path = `/v1/rooms/${room.roomId}`;
    const original = identity.verify.bind(identity);
    identity.verify = async secret => { const user = await original(secret); clock += expiringRoom ? 60001 : 30001; return user; };
    const result = expiringRoom
      ? await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken }, bobToken)
      : await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
    assert.equal(result.error, expiringRoom ? 'ROOM_EXPIRED' : 'SESSION_EXPIRED');
  }
});
