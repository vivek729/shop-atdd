// Maintainable contract spec (component level) for the coupon admin screen —
// same interaction as the legacy coupon.component test, driven through the shared
// test-kit. Co-generates the canonical pact by idempotent merge.
import { describe, it, afterEach, vi } from 'vitest';
import {
  PactBackendStubDriver,
  BackendStubDsl,
  FrontendDsl,
  UiFrontendDriver,
} from '../../support';

const backendDriver = new PactBackendStubDriver();
const backend = new BackendStubDsl(backendDriver);

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AdminCoupons', () => {
  it('shows coupons when they are returned', async () => {
    backend.returnsCoupons();

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.browseCoupons().execute().showsCoupon('SAVE10');
    });
  });
});
