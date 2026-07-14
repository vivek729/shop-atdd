// Maintainable contract spec (component level) for BROWSE COUPONS — the frontend twin of the
// system test's latest/acceptance/BrowseCouponsPositiveTest.
import { describe, it } from 'vitest';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('BrowseCoupons', () => {
  it('shows coupons when they are returned', async () => {
    backend.returnsCoupons();

    await frontend.browseCoupons().execute().showsCoupon('SAVE10');
  });
});
