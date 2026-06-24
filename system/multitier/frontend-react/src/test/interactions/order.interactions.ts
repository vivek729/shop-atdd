// Shared Pact interaction builders; both suites import here so interactions merge idempotently.
import { MatchersV3 } from '@pact-foundation/pact';
import type { V3Interaction } from '@pact-foundation/pact/src/v3/types';

const { like, eachLike, integer, decimal } = MatchersV3;

export function placeOrderInteraction(): V3Interaction {
  return {
    states: [{ description: 'product BOOK-123 exists and US is taxable' }],
    uponReceiving: 'a place-order request for BOOK-123',
    withRequest: {
      method: 'POST',
      path: '/api/orders',
      headers: { 'Content-Type': 'application/json' },
      body: { sku: 'BOOK-123', quantity: 2, country: 'US' },
    },
    willRespondWith: {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
      body: { orderNumber: like('ORD-1') },
    },
  };
}

export function browseOrderHistoryInteraction(): V3Interaction {
  return {
    states: [{ description: 'at least one order exists' }],
    uponReceiving: 'a browse-order-history request',
    withRequest: {
      method: 'GET',
      path: '/api/orders',
    },
    willRespondWith: {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: {
        orders: eachLike({
          orderNumber: like('ORD-1'),
          orderTimestamp: like('2026-03-10T12:00:00Z'),
          country: like('US'),
          sku: like('BOOK-123'),
          quantity: integer(2),
          totalPrice: decimal(22),
          appliedCouponCode: null,
          status: like('PLACED'),
        }),
      },
    },
  };
}
