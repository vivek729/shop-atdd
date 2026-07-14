// Maintainable contract spec (component level) for VIEW ORDER — the frontend twin of the system
// test's latest/acceptance/ViewOrder{Positive,Negative}Test.
import { describe, it } from 'vitest';
import { OrderStatus } from '../../../types/api.types';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('ViewOrder', () => {
  it('shows order details for an existing order', async () => {
    backend.returnsOrderDetails('ORD-1');

    await frontend.viewOrderDetails('ORD-1').execute().showsOrderDetails('ORD-1', '$22.00');
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
