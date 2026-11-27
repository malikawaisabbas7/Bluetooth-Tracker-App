import assert from 'node:assert/strict';
import { once } from 'node:events';
import { test } from 'node:test';
import { entitlement, makeServer } from './server.mjs';

test('matches the premium product and bounds entitlement by expiry and state', () => {
  const now = Date.parse('2026-01-01T00:00:00Z');
  const make = (state, expiry, productId = 'premium_weekly') => ({
    subscriptionState: 'SUBSCRIPTION_STATE_' + state,
    lineItems: [{ productId, expiryTime: new Date(now + expiry).toISOString() }],
  });
  for (const state of ['ACTIVE', 'IN_GRACE_PERIOD', 'CANCELED']) {
    assert.equal(entitlement(make(state, 60_000), 'premium_weekly', now), now + 60_000);
  }
  for (const state of ['PENDING', 'ON_HOLD', 'PAUSED', 'EXPIRED']) {
    assert.equal(entitlement(make(state, 60_000), 'premium_weekly', now), null);
  }
  assert.equal(entitlement(make('ACTIVE', 60_000, 'other'), 'premium_weekly', now), null);
  assert.equal(entitlement(make('ACTIVE', 0), 'premium_weekly', now), null);
  assert.equal(entitlement(make('ACTIVE', 3_600_000), 'premium_weekly', now), now + 900_000);
});

test('the verifier rejects malformed requests and hides upstream failures', async t => {
  const tokens = [];
  const server = makeServer(async token => {
    tokens.push(token);
    if (token === 'unavailable') throw new Error('Upstream details');
    return { entitled: false };
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = 'http://127.0.0.1:' + server.address().port + '/verify';
  const post = body => fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body,
  });
  assert.equal((await post('{')).status, 400);
  assert.equal((await post(JSON.stringify({ purchaseToken: 'x'.repeat(4_100) }))).status, 400);
  const valid = await post(JSON.stringify({ purchaseToken: 'test-token' }));
  assert.equal(valid.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await valid.json(), { entitled: false });
  const failure = await post(JSON.stringify({ purchaseToken: 'unavailable' }));
  assert.equal(failure.status, 503);
  assert.deepEqual(await failure.json(), { error: 'verification_unavailable' });
  assert.deepEqual(tokens, ['test-token', 'unavailable']);
});
