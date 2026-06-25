// Shared Pact interaction builders; both suites import here so interactions merge idempotently.
import { MatchersV3 } from '@pact-foundation/pact';
import type { V3Interaction } from '@pact-foundation/pact/src/v3/types';

const { like, eachLike, integer, decimal } = MatchersV3;

interface PlaceOrderParams {
  sku: string;
  quantity: number;
  country: string;
  couponCode?: string;
}

export function placeOrderInteraction({ sku, quantity, country, couponCode }: PlaceOrderParams): V3Interaction {
  const body: Record<string, unknown> = { sku, quantity, country };
  if (couponCode) body.couponCode = couponCode;

  const label = couponCode
    ? `a place-order request for ${sku} qty ${quantity} from ${country} with coupon ${couponCode}`
    : `a place-order request for ${sku} qty ${quantity} from ${country}`;

  const states: { description: string }[] = [{ description: `product ${sku} exists and ${country} is taxable` }];
  if (couponCode) states.push({ description: `coupon ${couponCode} exists` });

  return {
    states,
    uponReceiving: label,
    withRequest: {
      method: 'POST',
      path: '/api/orders',
      headers: { 'Content-Type': 'application/json' },
      body,
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

export function viewOrderDetailsInteraction(orderNumber: string): V3Interaction {
  return {
    states: [{ description: `order ${orderNumber} exists` }],
    uponReceiving: `a view-order-details request for ${orderNumber}`,
    withRequest: { method: 'GET', path: `/api/orders/${orderNumber}` },
    willRespondWith: {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: {
        orderNumber,
        orderTimestamp: like('2026-03-10T12:00:00Z'),
        country: like('US'),
        sku: like('BOOK-123'),
        quantity: integer(2),
        unitPrice: decimal(10),
        basePrice: decimal(20),
        discountRate: decimal(0),
        discountAmount: decimal(0),
        subtotalPrice: decimal(20),
        taxRate: decimal(0.1),
        taxAmount: decimal(2),
        totalPrice: decimal(22),
        appliedCouponCode: null,
        status: like('PLACED'),
      },
    },
  };
}

export function viewMissingOrderInteraction(orderNumber: string): V3Interaction {
  return {
    states: [{ description: `no order ${orderNumber} exists` }],
    uponReceiving: `a view-order-details request for a missing order`,
    withRequest: { method: 'GET', path: `/api/orders/${orderNumber}` },
    willRespondWith: {
      status: 404,
      headers: { 'Content-Type': 'application/problem+json' },
      body: { status: 404, detail: like('Order not found') },
    },
  };
}

export function placeOrderBlackoutInteraction(): V3Interaction {
  return {
    states: [{ description: 'order placement is blocked by the New Year blackout' }],
    uponReceiving: 'a place-order request during the blackout',
    withRequest: {
      method: 'POST',
      path: '/api/orders',
      headers: { 'Content-Type': 'application/json' },
      body: { sku: 'BOOK-123', quantity: 2, country: 'US' },
    },
    willRespondWith: {
      status: 422,
      headers: { 'Content-Type': 'application/problem+json' },
      body: { status: 422, detail: like('Orders cannot be placed on December 31') },
    },
  };
}
