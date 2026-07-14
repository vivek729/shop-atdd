// Maintainable contract spec (component level) for the coupon admin screen —
// same interaction as the legacy coupon.component test, driven through the shared
// test-kit. Co-generates the canonical pact by idempotent merge.
import { describe, it } from 'vitest';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('AdminCoupons', () => {
  it('shows coupons when they are returned', async () => {
    backend.returnsCoupons();

    await frontend.browseCoupons().execute().showsCoupon('SAVE10');
  });
});
