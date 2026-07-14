// Maintainable contract spec (component level) for BROWSE ORDER HISTORY.
import { describe, it } from 'vitest';
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('BrowseOrderHistory', () => {
  it('shows order history when orders are returned', async () => {
    backend.returnsOrderHistory();

    await frontend.browseOrderHistory().execute().showsOrder('ORD-1');
  });
});
