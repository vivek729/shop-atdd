import { describe, it, expect, vi, afterEach } from 'vitest';
import path from 'node:path';
import { PactV3 } from '@pact-foundation/pact';
import { screen } from '@testing-library/react';
import { AdminCoupons } from '../../../pages/AdminCoupons';
import { renderWithProviders, routeApiTo } from '../../test-utils';
import { browseCouponsInteraction } from '../../interactions/coupon.interactions';

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  dir: path.resolve(process.cwd(), '../../../contracts'),
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AdminCoupons', () => {
  it('shows coupons when they are returned', async () => {
    provider.addInteraction(browseCouponsInteraction());

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<AdminCoupons />);

      expect(await screen.findByText('SAVE10')).toBeInTheDocument();
    });
  });
});
