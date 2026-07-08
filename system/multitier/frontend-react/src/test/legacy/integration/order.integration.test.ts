// Calls OrderService directly (no React render); Pact mock server records interactions and emits into contracts/.
import { describe, it, expect } from 'vitest';
import path from 'node:path';
import { PactV3 } from '@pact-foundation/pact';
import { OrderGateway } from '../../../services/order-service';
import { placeOrderInteraction, browseOrderHistoryInteraction } from '../../interactions/order.interactions';

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  dir: path.resolve(process.cwd(), '../../../contracts'),
});

describe('order service narrow integration', () => {
  it('places an order via orderService directly', async () => {
    provider.addInteraction(placeOrderInteraction({ sku: 'BOOK-123', quantity: 2, country: 'US' }));

    await provider.executeTest(async (mockserver) => {
      const service = new OrderGateway(mockserver.url + '/api/orders');
      const result = await service.placeOrder('BOOK-123', 2, 'US');

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.orderNumber).toBeTruthy();
      }
    });
  });

  it('places an order with a coupon code via orderService directly', async () => {
    provider.addInteraction(placeOrderInteraction({ sku: 'BOOK-123', quantity: 2, country: 'US', couponCode: 'SAVE10' }));

    await provider.executeTest(async (mockserver) => {
      const service = new OrderGateway(mockserver.url + '/api/orders');
      const result = await service.placeOrder('BOOK-123', 2, 'US', 'SAVE10');

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.orderNumber).toBeTruthy();
      }
    });
  });

  it('browses order history via orderService directly', async () => {
    provider.addInteraction(browseOrderHistoryInteraction());

    await provider.executeTest(async (mockserver) => {
      const service = new OrderGateway(mockserver.url + '/api/orders');
      const result = await service.browseOrderHistory();

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.orders.length).toBeGreaterThan(0);
      }
    });
  });
});
