// Calls OrderService directly (no React render); Pact mock server records interactions and emits into contracts/.
import { describe, it, expect } from 'vitest';
import path from 'node:path';
import { PactV3 } from '@pact-foundation/pact';
import { OrderService } from '../../services/order-service';
import { placeOrderInteraction, browseOrderHistoryInteraction } from '../interactions/order.interactions';

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  dir: path.resolve(process.cwd(), '../../../contracts'),
});

describe('order service narrow integration', () => {
  it('places an order via orderService directly', async () => {
    provider.addInteraction(placeOrderInteraction());

    await provider.executeTest(async (mockserver) => {
      const service = new OrderService(mockserver.url + '/api/orders');
      const result = await service.placeOrder('BOOK-123', 2, 'US');

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.orderNumber).toBeTruthy();
      }
    });
  });

  it('browses order history via orderService directly', async () => {
    provider.addInteraction(browseOrderHistoryInteraction());

    await provider.executeTest(async (mockserver) => {
      const service = new OrderService(mockserver.url + '/api/orders');
      const result = await service.browseOrderHistory();

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.orders.length).toBeGreaterThan(0);
      }
    });
  });
});
