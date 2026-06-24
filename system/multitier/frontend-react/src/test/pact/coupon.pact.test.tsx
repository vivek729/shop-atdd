// Test taxonomy and suite boundaries: docs/atdd/test-taxonomy.md
import { describe, it, expect, afterEach, vi } from 'vitest';
import path from 'node:path';
import { PactV3, MatchersV3 } from '@pact-foundation/pact';
import { screen } from '@testing-library/react';
import { AdminCoupons } from '../../pages/AdminCoupons';
import { createCoupon } from '../../services/coupon-service';
import { renderWithProviders, routeApiTo } from '../test-utils';

const { like, eachLike, integer, decimal } = MatchersV3;

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  // Repo-owned neutral contracts/ folder (shop/contracts), not under the
  // consumer. The backend provider points @PactFolder at the same location.
  dir: path.resolve(process.cwd(), '../../../contracts'),
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

  it('publishes a coupon (POST /api/coupons -> 204)', async () => {
    provider
      .given('no coupon SAVE10 exists yet')
      .uponReceiving('a publish-coupon request')
      .withRequest({
        method: 'POST',
        path: '/api/coupons',
        headers: { 'Content-Type': 'application/json' },
        body: { code: 'SAVE10', discountRate: 0.2 },
      })
      // Backend returns 204 No Content (and the real backend + system tests
      // agree); the consumer publish flow only needs success, not a body.
      .willRespondWith({
        status: 204,
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);

      const result = await createCoupon('SAVE10', 0.2, null, null, null);

      expect(result.success).toBe(true);
    });
  });
});
