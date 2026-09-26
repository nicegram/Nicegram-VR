import { randomBytes, createHash } from 'node:crypto';
const token = () => randomBytes(32).toString('base64url');
const hash = value => createHash('sha256').update(value).digest('hex');
const validId = value => /^[1-9][0-9]{0,15}$/.test(String(value)) && Number.isSafeInteger(Number(value));
export class AuthFailure extends Error { constructor(status, code) { super(code); this.status = status; } }

/** The only production provider. No user assertion or lookup alone proves identity. */
export function nicegramProvider(env = process.env, fetcher = fetch) {
  let origin;
  try {
    const url = new URL(env.NICEGRAM_API_ORIGIN);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error();
    origin = url.origin;
  } catch { origin = null; }
  const bot = env.NICEGRAM_AUTH_BOT;
  const internal = env.NICEGRAM_INTERNAL_TOKEN;
  const ready = Boolean(origin && /^[A-Za-z0-9_]{2,29}bot$/i.test(bot || '') && internal?.length >= 32 && !internal.startsWith('REPLACE_'));
  async function request(path, body, isInternal = false) {
    if (!ready) throw new AuthFailure(503, 'NICEGRAM_NOT_CONFIGURED');
    let response;
    try {
      response = await fetcher(origin + path, { method: body ? 'POST' : 'GET', redirect: 'error',
        signal: AbortSignal.timeout(8000), headers: { Accept: 'application/json', 'Content-Type': 'application/json',
          ...(isInternal ? { 'x-internal-request': internal } : { 'X-agent': 'nicegram-vr', 'X-language': 'en' }) },
        ...(body ? { body: JSON.stringify(body) } : {}) });
    } catch { throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE'); }
    if (response.status === 401 && !isInternal) throw new AuthFailure(401, 'NICEGRAM_CONFIRM_REQUIRED');
    if (response.status === 406) throw new AuthFailure(401, 'AUTH_EXPIRED');
    if (response.status === 404 && isInternal) throw new AuthFailure(403, 'NICEGRAM_ACCOUNT_REQUIRED');
    if (!response.ok) throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE');
    const raw = await response.text();
    if (raw.length > 262144) throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE');
    try { return JSON.parse(raw).data; } catch { throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE'); }
  }
  return {
    ready,
    async account(id) {
      const data = await request(`/internal/users/${id}`, null, true);
      if (!data || String(data.telegramId) !== id) throw new AuthFailure(403, 'NICEGRAM_ACCOUNT_REQUIRED');
    },
    async start(id) {
      const data = await request('/v7/telegram/session', { telegramId: Number(id), source: 'nicegram_default' });
      if (!/^[a-f0-9]{64}$/.test(data?.sessionId || '')) throw new AuthFailure(503, 'NICEGRAM_UNAVAILABLE');
      return { sessionId: data.sessionId, loginUrl: `https://t.me/${bot}?start=${data.sessionId}` };
    },
    async complete(sessionId) {
      const data = await request('/v7/telegram/auth', { sessionId });
      const user = data?.user;
      if (!user || !validId(user.telegramId) || !user.id || !user.telegramAuthToken) throw new AuthFailure(401, 'NICEGRAM_CONFIRM_REQUIRED');
      // Discard the general Nicegram auth token, balances and unrelated profile data.
      return { telegramId: String(user.telegramId), name: String(user.first_name || user.user_name || 'Nicegram user').replace(/[\x00-\x1f\x7f]/g, '').slice(0, 60) || 'Nicegram user' };
    }
  };
}

export class NicegramIdentity {
  constructor(provider = nicegramProvider(), now = Date.now) {
    this.provider = provider; this.now = now; this.challenges = new Map(); this.identities = new Map(); this.starts = new Map();
  }
  get ready() { return this.provider.ready; }
  sweep() {
    for (const [key, row] of this.challenges) if (row.expiresAt <= this.now()) this.challenges.delete(key);
    for (const [key, row] of this.identities) if (row.expiresAt <= this.now()) this.identities.delete(key);
    for (const [id, until] of this.starts) if (until <= this.now()) this.starts.delete(id);
  }
  async start(id) {
    this.sweep();
    if (!this.ready) throw new AuthFailure(503, 'NICEGRAM_NOT_CONFIGURED');
    if (!validId(id)) throw new AuthFailure(400, 'INVALID_ACCOUNT');
    id = String(id);
    if (this.starts.has(id) || this.challenges.size >= 256 || this.starts.size >= 256) throw new AuthFailure(429, 'RATE_LIMITED');
    this.starts.set(id, this.now() + 30000);
    // Must already exist in Nicegram BEFORE bot auth (which can otherwise register users).
    await this.provider.account(id);
    const login = await this.provider.start(id);
    const challengeToken = token(), expiresAt = this.now() + 300000;
    this.challenges.set(hash(challengeToken), { ...login, telegramId: id, expiresAt, busy: false });
    return { challengeToken, loginUrl: login.loginUrl, telegramId: id, expiresAt };
  }
  async complete(secret) {
    this.sweep();
    const key = hash(secret), row = this.challenges.get(key);
    if (!row) throw new AuthFailure(401, 'AUTH_EXPIRED');
    if (row.busy) throw new AuthFailure(409, 'AUTH_IN_PROGRESS');
    row.busy = true;
    try {
      const user = row.confirmedUser || await this.provider.complete(row.sessionId);
      if (user.telegramId !== row.telegramId) { this.challenges.delete(key); throw new AuthFailure(403, 'ACCOUNT_MISMATCH'); }
      row.confirmedUser = user;
      await this.provider.account(user.telegramId);
      this.challenges.delete(key);
      if (row.expiresAt <= this.now()) throw new AuthFailure(401, 'AUTH_EXPIRED');
      if (this.identities.size >= 1000) throw new AuthFailure(429, 'RATE_LIMITED');
      const identityToken = token(), expiresAt = this.now() + 7200000;
      const principal = { ...user, expiresAt, checked: this.now() };
      this.identities.set(hash(identityToken), principal);
      return { identityToken, telegramId: user.telegramId, name: user.name, expiresAt };
    } finally { row.busy = false; }
  }
  async verify(secret) {
    this.sweep();
    const principal = this.identities.get(hash(secret));
    if (!principal) throw new AuthFailure(401, 'NICEGRAM_AUTH_REQUIRED');
    if (this.now() - principal.checked >= 60000) {
      await this.provider.account(principal.telegramId);
      principal.checked = this.now();
    }
    return principal;
  }
}
