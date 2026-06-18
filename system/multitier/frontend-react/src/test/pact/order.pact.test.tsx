// Step 4 — Pact CONSUMER tests for the order flows.
// These render the real pages/services against the Pact mock server, so they
// double as the happy-path (and contracted-error) component tests: one test,
// two jobs. Running them writes the pact into the repo-owned shop/contracts/
// folder, which the backend provider verification replays.
import { describe, it, expect, afterEach, vi } from 'vitest';
import path from 'node:path';
import { PactV3, MatchersV3 } from '@pact-foundation/pact';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NewOrder } from '../../pages/NewOrder';
import { OrderHistory } from '../../pages/OrderHistory';
import { OrderDetails } from '../../pages/OrderDetails';
import { renderWithProviders, routeApiTo } from '../test-utils';

const { like, eachLike, integer, decimal } = MatchersV3;

const provider = new PactV3({
  consumer: 'frontend',
  provider: 'backend',
  // Repo-owned neutral contracts/ folder (shop/contracts), not under the
  // consumer. The backend provider points @PactFolder at the same location.
  dir: path.resolve(process.cwd(), '../../../contracts'),
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('order consumer contract', () => {
  it('places an order (POST /api/orders -> 201)', async () => {
    provider
      .given('product BOOK-123 exists and US is taxable')
      .uponReceiving('a place-order request for BOOK-123')
      .withRequest({
        method: 'POST',
        path: '/api/orders',
        headers: { 'Content-Type': 'application/json' },
        body: { sku: 'BOOK-123', quantity: 2, country: 'US' },
      })
      .willRespondWith({
        status: 201,
        headers: { 'Content-Type': 'application/json' },
        body: { orderNumber: like('ORD-1') },
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      const user = userEvent.setup();
      renderWithProviders(<NewOrder />);

      await user.type(screen.getByLabelText('SKU'), 'BOOK-123');
      await user.type(screen.getByLabelText('Quantity'), '2');
      // Country defaults to 'US' in the form.
      await user.click(screen.getByRole('button', { name: 'Place Order' }));

      expect(await screen.findByText(/Order Number ORD-1/)).toBeInTheDocument();
    });
  });

  it('browses order history (GET /api/orders -> 200)', async () => {
    provider
      .given('at least one order exists')
      .uponReceiving('a browse-order-history request')
      .withRequest({ method: 'GET', path: '/api/orders' })
      .willRespondWith({
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
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<OrderHistory />);

      expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    });
  });

  it('views order details (GET /api/orders/{n} -> 200)', async () => {
    provider
      .given('order ORD-1 exists')
      .uponReceiving('a view-order-details request for ORD-1')
      .withRequest({ method: 'GET', path: '/api/orders/ORD-1' })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          orderNumber: 'ORD-1',
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
      });

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

  it('reports a not-found order (GET /api/orders/{n} -> 404)', async () => {
    provider
      .given('no order UNKNOWN exists')
      .uponReceiving('a view-order-details request for a missing order')
      .withRequest({ method: 'GET', path: '/api/orders/UNKNOWN' })
      // Backend returns RFC-7807 problem+json for error responses.
      .willRespondWith({
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
        body: { status: 404, detail: like('Order not found') },
      });

    await provider.executeTest(async (mockserver) => {
      routeApiTo(mockserver.url);
      renderWithProviders(<OrderDetails />, {
        routePath: '/order-details/:orderNumber',
        initialEntry: '/order-details/UNKNOWN',
      });

      expect(await screen.findByText('Order not found')).toBeInTheDocument();
    });
  });

  it('reports a rejected order (POST /api/orders -> 422)', async () => {
    provider
      .given('order placement is blocked by the New Year blackout')
      .uponReceiving('a place-order request during the blackout')
      .withRequest({
        method: 'POST',
        path: '/api/orders',
        headers: { 'Content-Type': 'application/json' },
        body: { sku: 'BOOK-123', quantity: 2, country: 'US' },
      })
      // Backend returns RFC-7807 problem+json for error responses.
      .willRespondWith({
        status: 422,
        headers: { 'Content-Type': 'application/problem+json' },
        body: { status: 422, detail: like('Orders cannot be placed on December 31') },
      });

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
