// Maintainable contract spec (component level) for VIEW ORDER — the frontend twin of the system
// test's latest/acceptance/ViewOrder{Positive,Negative}Test.
import { describe, it } from 'vitest';
import { OrderStatus } from '../../../types/api.types';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('ViewOrder', () => {
  it('shows order details for an existing order', async () => {
    backend.returnsOrderDetails('ORD-1');

    // Assert the WHOLE breakdown the screen renders, not just the total — the frontend twin of
    // system-test's ThenOrder price/tax/discount/status assertions, but as a rendering check (the
    // values are the backend's; here we pin that each one reaches the screen against its own field).
    await frontend.viewOrderDetails('ORD-1').execute().showsOrderDetails('ORD-1', {
      status: 'PLACED',
      sku: 'BOOK-123',
      country: 'US',
      quantity: '2',
      unitPrice: '$10.00',
      basePrice: '$20.00',
      discountRate: '0.00%',
      discountAmount: '$0.00',
      subtotalPrice: '$20.00',
      taxRate: '10.00%',
      taxAmount: '$2.00',
      totalPrice: '$22.00',
      appliedCoupon: 'None',
    });
  });

  it('shows not-found error for a missing order', async () => {
    backend.returnsNoOrder('UNKNOWN');

    await frontend.viewOrderDetails('UNKNOWN').execute().showsNotFound();
  });
});

// The frontend BRANCHES on the exact `status` value: Cancel/Deliver are hidden for
// CANCELLED/DELIVERED orders. Each status is its own interaction (exact-matched),
// so the contract fails if the backend stops returning that exact status.
describe('ViewOrder — status gates the Cancel/Deliver actions', () => {
  it('shows Cancel Order and Deliver Order for a PLACED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.PLACED);

    await frontend.viewOrderDetails('ORD-1').execute().showsCancelAndDeliverActions();
  });

  it('hides Cancel Order and Deliver Order for a CANCELLED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.CANCELLED);

    await frontend.viewOrderDetails('ORD-1').execute().hidesCancelAndDeliverActions();
  });

  it('hides Cancel Order and Deliver Order for a DELIVERED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.DELIVERED);

    await frontend.viewOrderDetails('ORD-1').execute().hidesCancelAndDeliverActions();
  });
});
