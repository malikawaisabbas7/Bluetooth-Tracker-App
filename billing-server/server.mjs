import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const publisher = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications';
const scope = 'https://www.googleapis.com/auth/androidpublisher';
const packageName = process.env.PLAY_PACKAGE_NAME || 'com.example.bluetoothtracker';
const product = process.env.PLAY_PRODUCT_ID || 'premium_weekly';
const playAccount = process.env.PLAY_SERVICE_ACCOUNT;
const eligibleStates = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED',
]);

export function entitlement(purchase, productId, now = Date.now()) {
  if (!eligibleStates.has(purchase.subscriptionState)) return null;
  const expiry = Math.max(0, ...(purchase.lineItems ?? [])
    .filter(item => item.productId === productId)
    .map(item => Date.parse(item.expiryTime))
    .filter(Number.isFinite));
  return expiry > now ? Math.min(expiry, now + 15 * 60_000) : null;
}

async function request(url, options) {
  return fetch(url, { ...options, redirect: 'error', signal: AbortSignal.timeout(7_000) });
}

let cachedToken;
let tokenRequest;
async function playAccessToken() {
  if (cachedToken?.expiresAt > Date.now() + 60_000) return cachedToken.value;
  if (!tokenRequest) {
    tokenRequest = (async () => {
      const metadata = await request('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', {
        headers: { 'Metadata-Flavor': 'Google' },
      });
      if (!metadata.ok) throw new Error('Runtime identity unavailable');
      const { access_token: runtimeToken } = await metadata.json();
      const account = encodeURIComponent(playAccount);
      const result = await request(`https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${account}:generateAccessToken`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${runtimeToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ scope: [scope] }),
      });
      if (!result.ok) throw new Error('Play identity unavailable');
      const { accessToken, expireTime } = await result.json();
      if (!accessToken) throw new Error('Play identity unavailable');
      cachedToken = { value: accessToken, expiresAt: Date.parse(expireTime) };
      return accessToken;
    })().finally(() => { tokenRequest = undefined; });
  }
  return tokenRequest;
}

async function verify(token) {
  const headers = { Authorization: `Bearer ${await playAccessToken()}` };
  const base = `${publisher}/${encodeURIComponent(packageName)}/purchases`;
  const encodedToken = encodeURIComponent(token);
  const lookup = () => request(`${base}/subscriptionsv2/tokens/${encodedToken}`, { headers });
  const response = await lookup();
  if ([400, 404, 410].includes(response.status)) return { entitled: false };
  if (!response.ok) throw new Error('Play lookup unavailable');
  const purchase = await response.json();
  const validUntilMs = entitlement(purchase, product);
  if (!validUntilMs) return { entitled: false };

  if (purchase.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
    const result = await request(`${base}/subscriptions/${encodeURIComponent(product)}/tokens/${encodedToken}:acknowledge`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: '{}',
    });
    if (!result.ok) {
      const retry = await lookup();
      if (!retry.ok) throw new Error('Play acknowledgement unavailable');
      const current = await retry.json();
      const currentExpiry = entitlement(current, product);
      if (!currentExpiry) return { entitled: false };
      if (current.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') {
        throw new Error('Play acknowledgement unavailable');
      }
      return { entitled: true, validUntilMs: currentExpiry };
    }
  } else if (purchase.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') {
    throw new Error('Unknown acknowledgement state');
  }
  return { entitled: true, validUntilMs };
}

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

async function readToken(req) {
  let bytes = 0;
  const chunks = [];
  for await (const chunk of req.iterator({ destroyOnReturn: false })) {
    bytes += chunk.length;
    if (bytes > 4_096) {
      req.resume();
      return null;
    }
    chunks.push(chunk);
  }
  const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  const token = body?.purchaseToken;
  return typeof token === 'string' && /^[!-~]{1,2048}$/.test(token) ? token : null;
}

export function makeServer(verifyPurchase = verify) {
  let inFlight = 0;
  return createServer({ requestTimeout: 12_000, headersTimeout: 5_000 }, async (req, res) => {
    if (req.url !== '/verify' || req.method !== 'POST') return reply(res, 404, { error: 'not_found' });
    if (req.headers['content-type']?.split(';')[0] !== 'application/json') {
      return reply(res, 415, { error: 'json_required' });
    }
    if (inFlight >= 16) return reply(res, 503, { error: 'busy' });
    inFlight++;
    req.setTimeout(5_000, () => req.destroy());
    try {
      const token = await readToken(req).catch(() => null);
      if (!token) return reply(res, 400, { error: 'invalid_request' });
      try {
        return reply(res, 200, await verifyPurchase(token));
      } catch {
        return reply(res, 503, { error: 'verification_unavailable' });
      }
    } finally {
      inFlight--;
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!playAccount) throw new Error('PLAY_SERVICE_ACCOUNT is required');
  makeServer().listen(Number(process.env.PORT || 8080), '0.0.0.0');
}
