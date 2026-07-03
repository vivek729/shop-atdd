import { describe, it, expect, vi, afterEach } from 'vitest';
import path from 'node:path';
import { PactV3 } from '@pact-foundation/pact';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NewOrder } from '../../pages/NewOrder';
import { OrderHistory } from '../../pages/OrderHistory';
import { OrderDetails } from '../../pages/OrderDetails';
import { renderWithProviders, routeApiTo } from '../test-utils';
import { OrderStatus } from '../../types/api.types';
import {
  placeOrderInteraction,
  placeOrderBlackoutInteraction,
  browseOrderHistoryInteraction,
  viewOrderDetailsInteraction,
  viewMissingOrderInteraction,
} from '../interactions/order.interactions';

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  dir: path.resolve(process.cwd(), '../../../contracts'),
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('NewOrder — places an order', () => {
  it('shows success message when order is accepted', async () => {
    provider.addInteraction(placeOrderInteraction({ sku: 'BOOK-123', quantity: 2, country: 'US', orderNumber: 'ORD-1' }));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      const user = userEvent.setup();
      renderWithProviders(<NewOrder />);

      await user.type(screen.getByLabelText('SKU'), 'BOOK-123');
      await user.type(screen.getByLabelText('Quantity'), '2');
      await user.click(screen.getByRole('button', { name: 'Place Order' }));

      expect(await screen.findByText(/Order Number ORD-1/)).toBeInTheDocument();
    });
  });

  it('shows error message when order is rejected by the backend', async () => {
    provider.addInteraction(placeOrderBlackoutInteraction());

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      const user = userEvent.setup();
      renderWithProviders(<NewOrder />);

      await user.type(screen.getByLabelText('SKU'), 'BOOK-123');
      await user.type(screen.getByLabelText('Quantity'), '2');
      await user.click(screen.getByRole('button', { name: 'Place Order' }));

      expect(
        await screen.findByText('Orders cannot be placed on December 31'),
      ).toBeInTheDocument();
    });
  });
});

describe('NewOrder — client-side validation (no request fired)', () => {
  it('shows validation errors and never calls the backend when the form is empty', async () => {
    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      const user = userEvent.setup();
      renderWithProviders(<NewOrder />);

      await user.click(screen.getByRole('button', { name: 'Place Order' }));

      expect(
        await screen.findByText('The request contains one or more validation errors'),
      ).toBeInTheDocument();
      expect(screen.getByText(/sku: SKU must not be empty/)).toBeInTheDocument();
    });
  });
});

describe('OrderHistory', () => {
  it('shows the loading spinner while the request is in flight', async () => {
    await provider.executeTest(async () => {
      vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));
      renderWithProviders(<OrderHistory />);

      expect(screen.getByText('Loading orders...')).toBeInTheDocument();
    });
  });

  it('surfaces a network error when the backend is unreachable', async () => {
    await provider.executeTest(async () => {
      vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('connection refused'))));
      renderWithProviders(<OrderHistory />);

      expect(await screen.findByText(/Network error/i)).toBeInTheDocument();
    });
  });

  it('shows order history when orders are returned', async () => {
    provider.addInteraction(browseOrderHistoryInteraction());

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<OrderHistory />);

      expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    });
  });
});

describe('OrderDetails', () => {
  it('shows order details for an existing order', async () => {
    provider.addInteraction(viewOrderDetailsInteraction('ORD-1'));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<OrderDetails />, {
        routePath: '/order-details/:orderNumber',
        initialEntry: '/order-details/ORD-1',
      });

      expect(await screen.findByLabelText('Display Order Number')).toHaveTextContent('ORD-1');
      expect(screen.getByLabelText('Display Total Price')).toHaveTextContent('$22.00');
    });
  });

  it('shows not-found error for a missing order', async () => {
    provider.addInteraction(viewMissingOrderInteraction('UNKNOWN'));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<OrderDetails />, {
        routePath: '/order-details/:orderNumber',
        initialEntry: '/order-details/UNKNOWN',
      });

      expect(await screen.findByText('Order not found')).toBeInTheDocument();
    });
  });
});

// The frontend BRANCHES on the exact `status` value (OrderDetails.tsx): the Cancel and
// Deliver actions are hidden for CANCELLED/DELIVERED orders. These drive the branch through
// the Pact mock server — each status is an interaction, so its provider state must have a
// matching @State handler in BackendPactVerificationTest that seeds an order in that state.
function renderOrderDetails() {
  renderWithProviders(<OrderDetails />, {
    routePath: '/order-details/:orderNumber',
    initialEntry: '/order-details/ORD-1',
  });
}

describe('OrderDetails — status gates the Cancel/Deliver actions', () => {
  it('shows Cancel Order and Deliver Order for a PLACED order', async () => {
    provider.addInteraction(viewOrderDetailsInteraction('ORD-1', OrderStatus.PLACED));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderOrderDetails();

      expect(await screen.findByLabelText('Display Order Number')).toHaveTextContent('ORD-1');
      expect(screen.getByRole('button', { name: 'Cancel Order' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Deliver Order' })).toBeInTheDocument();
    });
  });

  it('hides Cancel Order and Deliver Order for a CANCELLED order', async () => {
    provider.addInteraction(viewOrderDetailsInteraction('ORD-1', OrderStatus.CANCELLED));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderOrderDetails();

      expect(await screen.findByLabelText('Display Order Number')).toHaveTextContent('ORD-1');
      expect(screen.queryByRole('button', { name: 'Cancel Order' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Deliver Order' })).not.toBeInTheDocument();
    });
  });

  it('hides Cancel Order and Deliver Order for a DELIVERED order', async () => {
    provider.addInteraction(viewOrderDetailsInteraction('ORD-1', OrderStatus.DELIVERED));

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderOrderDetails();

      expect(await screen.findByLabelText('Display Order Number')).toHaveTextContent('ORD-1');
      expect(screen.queryByRole('button', { name: 'Cancel Order' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Deliver Order' })).not.toBeInTheDocument();
    });
  });
});
