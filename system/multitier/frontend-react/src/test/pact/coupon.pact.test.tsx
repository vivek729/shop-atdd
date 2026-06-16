// Step 4 — Pact CONSUMER tests for the coupon flows.
// Browse is driven through the rendered AdminCoupons page (one GET on mount,
// so it doubles as the happy-path component test). Publish is driven through
// the coupon-service directly: the AdminCoupons submit path fires several timed
// requests (mount-browse, post, refresh-browse), so the service call is the
// clean single-interaction consumer test for the POST contract.
import { describe, it, expect, afterEach, vi } from 'vitest';
import path from 'node:path';
import { PactV3, MatchersV3 } from '@pact-foundation/pact';
import { screen } from '@testing-library/react';
import { AdminCoupons } from '../../pages/AdminCoupons';
import { createCoupon } from '../../services/coupon-service';
import { renderWithProviders, routeApiTo } from '../test-utils';

const { like, eachLike, integer, decimal } = MatchersV3;

const provider = new PactV3({
  consumer: 'frontend-react',
  provider: 'backend-java',
  dir: path.resolve(process.cwd(), 'pacts'),
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('coupon consumer contract', () => {
  it('browses coupons (GET /api/coupons -> 200)', async () => {
    provider
      .given('at least one coupon exists')
      .uponReceiving('a browse-coupons request')
      .withRequest({ method: 'GET', path: '/api/coupons' })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          coupons: eachLike({
            code: like('SAVE10'),
            discountRate: decimal(0.2),
            usageLimit: integer(100),
            usedCount: integer(0),
          }),
        },
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<AdminCoupons />);

      expect(await screen.findByText('SAVE10')).toBeInTheDocument();
    });
  });

  it('publishes a coupon (POST /api/coupons -> 201)', async () => {
    provider
      .given('no coupon SAVE10 exists yet')
      .uponReceiving('a publish-coupon request')
      .withRequest({
        method: 'POST',
        path: '/api/coupons',
        headers: { 'Content-Type': 'application/json' },
        body: { code: 'SAVE10', discountRate: 0.2 },
      })
      .willRespondWith({
        status: 201,
        headers: { 'Content-Type': 'application/json' },
        body: { code: like('SAVE10') },
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);

      const result = await createCoupon('SAVE10', 0.2, null, null, null);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.code).toBe('SAVE10');
      }
    });
  });
});
