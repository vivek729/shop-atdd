// Maintainable contract spec (narrow-integration level) — the same interactions
// as the legacy order.integration test, but driven through the shared test-kit.
// The SAME Frontend DSL lines as the component spec run against a gateway driver
// instead of the UI driver: hasConfirmation is realized as a Result here, as
// screen text there. Only the harness differs. Co-generates the canonical pact by
// idempotent merge.
import { describe, it } from 'vitest';
import { integrationHarness } from '../../support';

const { backend, frontend } = integrationHarness();

describe('order gateway narrow integration', () => {
  it('places an order via the gateway directly', async () => {
    backend.returnsPlacedOrder().withSku('BOOK-123').withQuantity(2).withCountry('US').execute();

    await frontend.placeOrder().withSku('BOOK-123').withQuantity(2).execute().hasConfirmation('ORD-1');
  });

  it('places an order with a coupon code via the gateway directly', async () => {
    backend
      .returnsPlacedOrder()
      .withSku('BOOK-123')
      .withQuantity(2)
      .withCountry('US')
      .withCoupon('SAVE10')
      .execute();

    await frontend
      .placeOrder()
      .withSku('BOOK-123')
      .withQuantity(2)
      .withCoupon('SAVE10')
      .execute()
      .hasConfirmation('ORD-1');
  });

  it('browses order history via the gateway directly', async () => {
    backend.returnsOrderHistory();

    await frontend.browseOrderHistory().execute().showsOrder('ORD-1');
  });
});
