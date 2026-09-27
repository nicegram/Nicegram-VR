import http from 'node:http';
import { NicegramIdentity } from './identity.mjs';
import { randomBytes, createHash, timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const token = () => randomBytes(32).toString('base64url');
const digest = value => createHash('sha256').update(value).digest();
const equal = (a, b) => typeof a === 'string' && typeof b === 'string' && timingSafeEqual(digest(a), digest(b));
const validToken = value => typeof value === 'string' && /^[\w-]{43}$/.test(value);
const validAddress = value => typeof value === 'string' && /^[0-9A-Fa-f:.]{2,45}$/.test(value);
class Failure extends Error { constructor(status, code) { super(code); this.status = status; } }
const stdoutLog = line => process.stdout.write(line + '\n');

// Served for anyone who opens an invitation outside the headset. The capability lives in the
// URL fragment, which browsers never send; the page carries no script that could read it.
const INVITE_PAGE = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex"><title>Nicegram VR room invitation</title>
<style>body{margin:0;font:17px/1.5 system-ui,sans-serif;background:#161b24;color:#e8ecf3}main{max-width:36rem;margin:0 auto;padding:2.5rem 1.25rem}h1{font-size:1.5rem;margin:0 0 1rem}p{margin:0 0 1rem}section+section{border-top:1px solid #2c3444;margin-top:1.5rem;padding-top:1.5rem}</style>
</head><body><main>
<section><h1>Nicegram VR room invitation</h1>
<p>This link opens a test room in Nicegram VR on Meta Quest 3 or 3S.</p>
<p>On the headset, open the Telegram group where the link was shared and tap it there. You need a Nicegram account.</p>
<p>Voice in the room is an ordinary Telegram group call, so you can also join it from your phone.</p></section>
<section lang="ru"><h1>Приглашение в VR-комнату Nicegram</h1>
<p>Эта ссылка открывает тестовую комнату в Nicegram VR на Meta Quest 3 или 3S.</p>
<p>В шлеме откройте группу Telegram, где отправили ссылку, и нажмите на неё там. Нужен аккаунт Nicegram.</p>
<p>Голос в комнате — обычный групповой звонок Telegram, к нему можно подключиться и с телефона.</p></section>
</main></body></html>`;

// Deliberately bounded, ephemeral closed-beta presence. It carries NO Telegram session,
// Telegram authorization key, messages or media. Restarting the service ends all rooms.
export function createRoomServer({ identity = new NicegramIdentity(), now = Date.now, ttl = 7200000, lease = 30000,
  idleGrace = 300000, maxRooms = 100, maxRoomsPerUser = 3, capacity = 8, clientIpHeader = null, log = stdoutLog } = {}) {
  const rooms = new Map();
  const rates = new Map();
  const headerName = clientIpHeader ? clientIpHeader.toLowerCase() : null;
  const sweep = () => {
    identity.sweep();
    for (const [id, room] of rooms) {
      if (now() >= room.expiresAt) { rooms.delete(id); continue; }
      for (const [id, member] of room.members) if (now() - member.seen >= lease) room.members.delete(id);
      if (room.members.size) { room.emptySince = null; continue; }
      // A room nobody is in is kept only long enough to reconnect after a crash or a sleep.
      if (room.emptySince === null) room.emptySince = now();
      else if (now() - room.emptySince >= idleGrace) rooms.delete(id);
    }
    for (const [key, rate] of rates) if (now() - rate.start >= 60000) rates.delete(key);
  };
  const snapshot = room => ({ roomId: room.id, chatKey: room.chatKey, expiresAt: room.expiresAt,
    participants: [...room.members].map(([id, member]) => ({ id, name: member.name })) });
  const join = (room, principal, identityToken) => {
    const name = principal.name;
    if (typeof name !== 'string' || !name.trim() || name.length > 60 || /[\x00-\x1f\x7f]/.test(name)) throw new Failure(400, 'INVALID_NAME');
    // The same account re-joining is a reconnect: its previous session is revoked, not kept.
    for (const [id, member] of room.members) if (member.telegramId === principal.telegramId) room.members.delete(id);
    if (room.members.size >= capacity) throw new Failure(409, 'ROOM_FULL');
    const sessionId = token(), sessionToken = token();
    room.members.set(sessionId, { name: name.trim(), telegramId: principal.telegramId, identityToken, secret: digest(sessionToken), seen: now() });
    room.emptySince = null;
    return { ...snapshot(room), sessionId, sessionToken };
  };
  // App Platform terminates every connection at its edge, so the socket address is the same for
  // all clients. The edge overwrites its own client-address header; any other value is ignored.
  const clientKey = req => {
    const forwarded = headerName ? req.headers[headerName] : undefined;
    return validAddress(forwarded) ? forwarded : req.socket.remoteAddress || 'unknown';
  };
  const handle = async (req, res, context) => {
    const send = (status, data, headers = {}) => {
      context.status = status;
      if (res.destroyed) return;
      res.writeHead(status, { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', ...headers });
      res.end(data);
    };
    const json = (status, data) => send(status, JSON.stringify(data), { 'Content-Type': 'application/json' });
    let path;
    try { path = new URL(req.url, 'http://room.invalid').pathname; } catch { path = ''; }
    try {
      if (req.method === 'GET' && path === '/healthz') {
        context.route = '/healthz';
        return json(identity.ready ? 200 : 503, { status: identity.ready ? 'ok' : 'unavailable', mode: 'closed-beta', identity: 'nicegram-required', ready: identity.ready });
      }
      if (req.method === 'GET' && path === '/room') {
        context.route = '/room';
        return send(200, INVITE_PAGE, { 'Content-Type': 'text/html; charset=utf-8',
          'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'" });
      }
      if (req.method !== 'POST') throw new Failure(404, 'NOT_FOUND');
      sweep();
      const client = clientKey(req);
      let rate = rates.get(client);
      if (!rate) { if (rates.size >= 10000) throw new Failure(429, 'RATE_LIMITED'); rates.set(client, rate = { start: now(), count: 0, authCount: 0 }); }
      if (++rate.count > 1200) throw new Failure(429, 'RATE_LIMITED');
      if (!req.headers['content-type']?.startsWith('application/json')) throw new Failure(415, 'JSON_REQUIRED');
      let size = 0, chunks = [];
      for await (const chunk of req) { size += chunk.length; if (size > 4096) throw new Failure(413, 'TOO_LARGE'); chunks.push(chunk); }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new Failure(400, 'INVALID_JSON'); }
      if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Failure(400, 'INVALID_JSON');
      const credential = req.headers.authorization?.replace(/^Bearer /, '') || '';
      if (path === '/v1/auth/start' || path === '/v1/auth/complete') {
        context.route = path;
        if (++rate.authCount > 20) throw new Failure(429, 'RATE_LIMITED');
        return json(200, path === '/v1/auth/start' ? await identity.start(body.telegramId) : await identity.complete(credential));
      }
      if (path === '/v1/rooms') {
        context.route = path;
        const principal = await identity.verify(credential);
        if (typeof body.chatKey !== 'string' || !/^(chat|channel):[1-9][0-9]{0,18}$/.test(body.chatKey)) throw new Failure(400, 'INVALID_CHAT');
        sweep();
        let own = 0;
        for (const room of rooms.values()) if (room.creator === principal.telegramId) own++;
        if (own >= maxRoomsPerUser) throw new Failure(429, 'USER_ROOM_LIMIT');
        if (rooms.size >= maxRooms) throw new Failure(429, 'ROOM_LIMIT');
        const room = { id: token(), invite: token(), chatKey: body.chatKey, creator: principal.telegramId,
          expiresAt: now() + ttl, members: new Map(), emptySince: null };
        const result = join(room, principal, credential);
        rooms.set(room.id, room);
        return json(201, { ...result, inviteToken: room.invite });
      }
      const match = /^\/v1\/rooms\/([\w-]{43})\/(join|heartbeat|leave)$/.exec(path);
      if (!match) throw new Failure(404, 'NOT_FOUND');
      context.route = `/v1/rooms/:room/${match[2]}`;
      const room = rooms.get(match[1]);
      if (!room) throw new Failure(404, 'ROOM_EXPIRED');
      if (match[2] === 'join') {
        const principal = await identity.verify(credential);
        sweep();
        if (rooms.get(room.id) !== room) throw new Failure(404, 'ROOM_EXPIRED');
        if (!validToken(body.inviteToken) || !equal(body.inviteToken, room.invite)) throw new Failure(401, 'UNAUTHORIZED');
        if (body.chatKey !== room.chatKey) throw new Failure(409, 'WRONG_CHAT');
        return json(200, join(room, principal, credential));
      }
      const member = room.members.get(body.sessionId);
      if (!member || !validToken(credential) || !timingSafeEqual(digest(credential), member.secret)) throw new Failure(401, 'SESSION_EXPIRED');
      if (match[2] === 'leave') {
        room.members.delete(body.sessionId);
        if (!room.members.size) room.emptySince = now();
      } else {
        await identity.verify(member.identityToken);
        sweep();
        if (rooms.get(room.id) !== room) throw new Failure(404, 'ROOM_EXPIRED');
        if (room.members.get(body.sessionId) !== member) throw new Failure(401, 'SESSION_EXPIRED');
        member.seen = now();
      }
      json(200, snapshot(room));
    } catch (error) {
      if (!error.status) context.failure = error;
      context.error = error.status ? error.message : 'INTERNAL_ERROR';
      json(error.status || 500, { error: context.error });
    }
  };
  const server = http.createServer((req, res) => {
    const started = process.hrtime.bigint();
    const context = { route: 'unmatched', status: 0, error: undefined, failure: undefined };
    handle(req, res, context).finally(() => {
      // Route templates only: room IDs, session IDs, tokens and bodies are never written.
      const entry = { ts: new Date(now()).toISOString(), level: context.status >= 500 ? 'error' : 'info', method: req.method,
        route: context.route, status: context.status, ms: Number((process.hrtime.bigint() - started) / 1000000n) };
      if (context.error) entry.error = context.error;
      if (context.failure) entry.failure = String(context.failure.stack || context.failure).slice(0, 2000);
      try { log(JSON.stringify(entry)); } catch { /* logging must never break a response */ }
    });
  }).on('connection', socket => { socket.setTimeout(10000, () => socket.destroy()); });
  server.maxConnections = 128;
  const timer = setInterval(sweep, 10000); timer.unref();
  server.on('close', () => clearInterval(timer));
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const identity = new NicegramIdentity();
  const server = createRoomServer({ identity, clientIpHeader: process.env.CLIENT_IP_HEADER || null });
  server.requestTimeout = 10000;
  server.headersTimeout = 10000;
  const port = Number(process.env.PORT || 8080);
  server.listen(port, '0.0.0.0', () => stdoutLog(JSON.stringify({ ts: new Date().toISOString(), level: identity.ready ? 'info' : 'error',
    event: 'listening', port, ready: identity.ready, clientIpHeader: process.env.CLIENT_IP_HEADER || null })));
  // App Platform sends SIGTERM before replacing the instance; finish in-flight requests first.
  process.on('SIGTERM', () => {
    stdoutLog(JSON.stringify({ ts: new Date().toISOString(), level: 'info', event: 'shutdown' }));
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref();
  });
}
