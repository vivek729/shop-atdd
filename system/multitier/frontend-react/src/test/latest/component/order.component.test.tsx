// Maintainable contract spec (component level) — the same interactions as the
// legacy order.component test, but driven through the shared test-kit: a Backend
// Stub DSL over the Pact driver declares what the backend returns, and a Frontend
// DSL over the UI driver drives the rendered screen. Reads like a Component Test;
// the Pact plumbing lives in the driver. Co-generates the canonical pact by
// idempotent merge (same interactions as legacy, from the same shared builders).
import { describe, it, afterEach, vi } from 'vitest';
import { OrderStatus } from '../../../types/api.types';
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

describe('NewOrder — places an order', () => {
  it('shows success message when order is accepted', async () => {
    backend
      .returnsPlacedOrder()
      .withSku('BOOK-123')
      .withQuantity(2)
      .withCountry('US')
      .withOrderNumber('ORD-1')
      .execute();

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend
        .placeOrder()
        .withSku('BOOK-123')
        .withQuantity(2)
        .execute()
        .hasConfirmation('ORD-1');
    });
  });

  it('shows error message when order is rejected by the backend', async () => {
    backend.rejectsPlaceOrderDuringBlackout();

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend
        .placeOrder()
        .withSku('BOOK-123')
        .withQuantity(2)
        .execute()
        .hasError('Orders cannot be placed on December 31');
    });
  });
});

describe('OrderHistory', () => {
  it('shows order history when orders are returned', async () => {
    backend.returnsOrderHistory();

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.browseOrderHistory().execute().showsOrder('ORD-1');
    });
  });
});

describe('OrderDetails', () => {
  it('shows order details for an existing order', async () => {
    backend.returnsOrderDetails('ORD-1');

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.viewOrderDetails('ORD-1').execute().showsOrderDetails('ORD-1', '$22.00');
    });
  });

  it('shows not-found error for a missing order', async () => {
    backend.returnsNoOrder('UNKNOWN');

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.viewOrderDetails('UNKNOWN').execute().showsNotFound();
    });
  });
});

// The frontend BRANCHES on the exact `status` value: Cancel/Deliver are hidden for
// CANCELLED/DELIVERED orders. Each status is its own interaction (exact-matched),
// so the contract fails if the backend stops returning that exact status.
describe('OrderDetails — status gates the Cancel/Deliver actions', () => {
  it('shows Cancel Order and Deliver Order for a PLACED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.PLACED);

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.viewOrderDetails('ORD-1').execute().showsCancelAndDeliverActions();
    });
  });

  it('hides Cancel Order and Deliver Order for a CANCELLED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.CANCELLED);

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.viewOrderDetails('ORD-1').execute().hidesCancelAndDeliverActions();
    });
  });

  it('hides Cancel Order and Deliver Order for a DELIVERED order', async () => {
    backend.returnsOrderDetails('ORD-1', OrderStatus.DELIVERED);

    await backendDriver.runContract(async (baseUrl) => {
      const frontend = new FrontendDsl(new UiFrontendDriver());
      frontend.useBackend(baseUrl);

      await frontend.viewOrderDetails('ORD-1').execute().hidesCancelAndDeliverActions();
    });
  });
});
