import http from 'node:http';
import { randomBytes, createHash, timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const token = () => randomBytes(32).toString('base64url');
const digest = value => createHash('sha256').update(value).digest();
const equal = (a, b) => typeof a === 'string' && typeof b === 'string' && timingSafeEqual(digest(a), digest(b));
const validToken = value => typeof value === 'string' && /^[\w-]{43}$/.test(value);
class Failure extends Error { constructor(status, code) { super(code); this.status = status; } }

// Deliberately bounded, ephemeral closed-beta presence. It carries NO Telegram session,
// verified identity, messages or media. Restarting the service ends all rooms.
export function createRoomServer({ createKey, now = Date.now, ttl = 7200000, lease = 30000, maxRooms = 100, capacity = 8 } = {}) {
  if (typeof createKey !== 'string' || createKey.length < 32) throw new Error('ROOM_CREATE_KEY must contain at least 32 characters');
  const rooms = new Map();
  const rates = new Map();
  const sweep = () => {
    for (const [id, room] of rooms) {
      if (now() >= room.expiresAt) { rooms.delete(id); continue; }
      for (const [id, member] of room.members) if (now() - member.seen >= lease) room.members.delete(id);
    }
    for (const [key, rate] of rates) if (now() - rate.start >= 60000) rates.delete(key);
  };
  const snapshot = room => ({ roomId: room.id, chatKey: room.chatKey, expiresAt: room.expiresAt,
    participants: [...room.members].map(([id, member]) => ({ id, name: member.name })) });
  const join = (room, name) => {
    if (room.members.size >= capacity) throw new Failure(409, 'ROOM_FULL');
    if (typeof name !== 'string' || !name.trim() || name.length > 60 || /[\x00-\x1f\x7f]/.test(name)) throw new Failure(400, 'INVALID_NAME');
    const sessionId = token(), sessionToken = token();
    room.members.set(sessionId, { name: name.trim(), secret: digest(sessionToken), seen: now() });
    return { ...snapshot(room), sessionId, sessionToken };
  };
  const server = http.createServer(async (req, res) => {
    const send = (status, data) => { if (res.destroyed) return; res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(data)); };
    try {
      if (req.method === 'GET' && req.url === '/healthz') return send(200, { status: 'ok', mode: 'closed-beta', identity: 'invite-only' });
      if (req.method !== 'POST') throw new Failure(404, 'NOT_FOUND');
      sweep();
      // Never trust client-controlled X-Forwarded-For. App Platform may share an egress IP;
      // this coarse limit protects memory and is deliberately generous for eight testers.
      const ip = req.socket.remoteAddress || 'unknown';
      let rate = rates.get(ip);
      if (!rate) { if (rates.size >= 10000) throw new Failure(429, 'RATE_LIMITED'); rates.set(ip, rate = { start: now(), count: 0 }); }
      if (++rate.count > 1200) throw new Failure(429, 'RATE_LIMITED');
      if (!req.headers['content-type']?.startsWith('application/json')) throw new Failure(415, 'JSON_REQUIRED');
      let size = 0, chunks = [];
      for await (const chunk of req) { size += chunk.length; if (size > 4096) throw new Failure(413, 'TOO_LARGE'); chunks.push(chunk); }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new Failure(400, 'INVALID_JSON'); }
      if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Failure(400, 'INVALID_JSON');
      if (req.url === '/v1/rooms') {
        if (!equal(req.headers.authorization, `Bearer ${createKey}`)) throw new Failure(401, 'UNAUTHORIZED');
        if (!/^(chat|channel):[1-9][0-9]{0,18}$/.test(body.chatKey)) throw new Failure(400, 'INVALID_CHAT');
        if (rooms.size >= maxRooms) throw new Failure(429, 'ROOM_LIMIT');
        const room = { id: token(), invite: token(), chatKey: body.chatKey, expiresAt: now() + ttl, members: new Map() };
        const result = join(room, body.name);
        rooms.set(room.id, room);
        return send(201, { ...result, inviteToken: room.invite });
      }
      const match = /^\/v1\/rooms\/([\w-]{43})\/(join|heartbeat|leave)$/.exec(req.url);
      if (!match) throw new Failure(404, 'NOT_FOUND');
      const room = rooms.get(match[1]);
      if (!room) throw new Failure(404, 'ROOM_EXPIRED');
      if (match[2] === 'join') {
        if (!validToken(body.inviteToken) || !equal(body.inviteToken, room.invite)) throw new Failure(401, 'UNAUTHORIZED');
        if (body.chatKey !== room.chatKey) throw new Failure(409, 'WRONG_CHAT');
        return send(200, join(room, body.name));
      }
      const member = room.members.get(body.sessionId);
      const bearer = req.headers.authorization?.replace(/^Bearer /, '');
      if (!member || !validToken(bearer) || !timingSafeEqual(digest(bearer), member.secret)) throw new Failure(401, 'SESSION_EXPIRED');
      if (match[2] === 'leave') room.members.delete(body.sessionId); else member.seen = now();
      send(200, snapshot(room));
    } catch (error) { send(error.status || 500, { error: error.status ? error.message : 'INTERNAL_ERROR' }); }
  }).on('connection', socket => { socket.setTimeout(10000, () => socket.destroy()); });
  server.maxConnections = 128;
  const timer = setInterval(sweep, 10000); timer.unref();
  server.on('close', () => clearInterval(timer));
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createRoomServer({ createKey: process.env.ROOM_CREATE_KEY });
  server.requestTimeout = 10000;
  server.headersTimeout = 10000;
  server.listen(Number(process.env.PORT || 8080), '0.0.0.0');
}
