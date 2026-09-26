import test from 'node:test';
import assert from 'node:assert/strict';
import { createRoomServer } from '../server.mjs';
const key = 'test-only-creation-key-not-a-production-secret';
async function fixture(t, options = {}) {
  const server = createRoomServer({ createKey: key, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const post = async (path, body, secret = key) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${secret}` }, body: JSON.stringify(body) });
    return { status: response.status, ...await response.json() };
  };
  const create = () => post('/v1/rooms', { chatKey: 'channel:1234', name: 'Alice' });
  return { post, create };
}
test('two independent clients join, see presence, and leave without any media or message endpoint', async t => {
  const { post, create } = await fixture(t);
  const room = await create(); assert.equal(room.status, 201);
  const path = `/v1/rooms/${room.roomId}`;
  const bob = await post(`${path}/join`, { chatKey: room.chatKey, inviteToken: room.inviteToken, name: 'Bob' }, '');
  assert.equal(bob.participants.length, 2);
  assert.notEqual(bob.sessionToken, room.sessionToken);
  const alice = await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
  assert.deepEqual(alice.participants.map(p => p.name), ['Alice', 'Bob']);
  assert.equal(alice.inviteToken, undefined); assert.equal(alice.sessionToken, undefined);
  await post(`${path}/leave`, { sessionId: bob.sessionId }, bob.sessionToken);
  const remaining = await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken);
  assert.equal(remaining.participants.length, 1);
  assert.equal((await post(`${path}/media`, {})).status, 404);
});
test('capabilities isolate rooms, sessions and chat binding', async t => {
  const { post, create } = await fixture(t);
  assert.equal((await post('/v1/rooms', { chatKey: 'channel:1234', name: 'Alice' }, 'bad')).status, 401);
  const a = await create(), b = await create();
  const path = `/v1/rooms/${a.roomId}`;
  assert.equal((await post(`${path}/join`, { chatKey: a.chatKey, name: 'Bob', inviteToken: b.inviteToken })).status, 401);
  assert.equal((await post(`${path}/join`, { chatKey: 'channel:5678', name: 'Bob', inviteToken: a.inviteToken })).error, 'WRONG_CHAT');
  assert.equal((await post(`${path}/heartbeat`, { sessionId: a.sessionId }, b.sessionToken)).status, 401);
  assert.equal((await post(`${path}/leave`, { sessionId: a.sessionId }, a.inviteToken)).status, 401);
});
test('leases expire, capacity is bounded, expired sessions cannot revive', async t => {
  let clock = 100000;
  const { post, create } = await fixture(t, { now: () => clock, capacity: 1 });
  const room = await create(), path = `/v1/rooms/${room.roomId}`;
  const join = () => post(`${path}/join`, { chatKey: room.chatKey, name: 'Bob', inviteToken: room.inviteToken });
  assert.equal((await join()).error, 'ROOM_FULL');
  clock += 30001;
  assert.equal((await post(`${path}/heartbeat`, { sessionId: room.sessionId }, room.sessionToken)).error, 'SESSION_EXPIRED');
  assert.equal((await join()).status, 200);
  clock += 7200000;
  assert.equal((await join()).error, 'ROOM_EXPIRED');
});
test('invalid creation is atomic and room count is bounded', async t => {
  const { post, create } = await fixture(t, { maxRooms: 1 });
  assert.equal((await post('/v1/rooms', { chatKey: 'channel:1234', name: '' })).status, 400);
  assert.equal((await create()).status, 201);
  assert.equal((await create()).error, 'ROOM_LIMIT');
});
