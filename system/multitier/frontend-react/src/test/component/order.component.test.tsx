// Test taxonomy and suite boundaries: docs/atdd/test-taxonomy.md
import { describe, it, expect, vi, afterEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NewOrder } from '../../pages/NewOrder';
import { OrderHistory } from '../../pages/OrderHistory';
import { renderWithProviders } from '../test-utils';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('NewOrder — client-side validation (no request fired)', () => {
  it('shows validation errors and never calls the backend when the form is empty', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const user = userEvent.setup();

    renderWithProviders(<NewOrder />);

    await user.click(screen.getByRole('button', { name: 'Place Order' }));

    expect(
      await screen.findByText('The request contains one or more validation errors'),
    ).toBeInTheDocument();
    expect(screen.getByText(/sku: SKU must not be empty/)).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('OrderHistory — client-side loading / network states', () => {
  it('shows the loading spinner while the request is in flight', () => {
    // fetch that never resolves keeps the hook in its loading state.
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));

    renderWithProviders(<OrderHistory />);

    expect(screen.getByText('Loading orders...')).toBeInTheDocument();
  });

  it('surfaces a network error when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('connection refused'))));

    renderWithProviders(<OrderHistory />);

    expect(await screen.findByText(/Network error/i)).toBeInTheDocument();
  });
});
