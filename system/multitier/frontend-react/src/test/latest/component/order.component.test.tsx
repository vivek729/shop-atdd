// Maintainable contract spec (component level) — the same interactions as the
// legacy order.component test, but driven through the shared test-kit: a Backend
// Stub DSL declares what the backend returns, and a Frontend DSL drives the
// rendered screen. Reads like a Component Test; the Pact plumbing and the driver
// wiring live behind the harness. Co-generates the canonical pact by idempotent
// merge (same interactions as legacy, from the same shared builders).
import { describe, it } from 'vitest';
import { OrderStatus } from '../../../types/api.types';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('NewOrder — places an order', () => {
  it('shows success message when order is accepted', async () => {
    backend
      .returnsPlacedOrder()
      .withSku('BOOK-123')
      .withQuantity(2)
      .withCountry('US')
      .withOrderNumber('ORD-1')
      .execute();

    await frontend.placeOrder().withSku('BOOK-123').withQuantity(2).execute().hasConfirmation('ORD-1');
  });

  it('shows error message when order is rejected by the backend', async () => {
    backend.rejectsPlaceOrderDuringBlackout();

    await frontend
      .placeOrder()
      .withSku('BOOK-123')
      .withQuantity(2)
      .execute()
      .hasError('Orders cannot be placed on December 31');
  });
});

describe('OrderHistory', () => {
  it('shows order history when orders are returned', async () => {
    backend.returnsOrderHistory();

    await frontend.browseOrderHistory().execute().showsOrder('ORD-1');
  });
});

describe('OrderDetails', () => {
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
describe('OrderDetails — status gates the Cancel/Deliver actions', () => {
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
