// Backend Stub DSL — the semantic "what the backend returns" surface for the
// "latest" contract specs. Each verb stages ONE interaction on the driver, built
// from the shared interactions/ builders — so matcher policy (like() baseline,
// exact on the one field the frontend branches on) lives in exactly one place and
// legacy + latest emit identical interactions that Pact merges idempotently.
import type { BackendStubDriver } from './pact-backend-stub-driver';
import { OrderStatus } from '../../types/api.types';
import {
  placeOrderInteraction,
  placeOrderBlackoutInteraction,
  browseOrderHistoryInteraction,
  viewOrderDetailsInteraction,
  viewMissingOrderInteraction,
} from '../interactions/order.interactions';
import {
  browseCouponsInteraction,
  publishCouponInteraction,
} from '../interactions/coupon.interactions';

export class BackendStubDsl {
  constructor(private readonly driver: BackendStubDriver) {}

  returnsPlacedOrder(): ReturnsPlacedOrderCommand {
    return new ReturnsPlacedOrderCommand(this.driver);
  }

  rejectsPlaceOrderDuringBlackout(): void {
    this.driver.stub(placeOrderBlackoutInteraction());
  }

  returnsOrderHistory(): void {
    this.driver.stub(browseOrderHistoryInteraction());
  }

  returnsOrderDetails(orderNumber: string, status: OrderStatus = OrderStatus.PLACED): void {
    this.driver.stub(viewOrderDetailsInteraction(orderNumber, status));
  }

  returnsNoOrder(orderNumber: string): void {
    this.driver.stub(viewMissingOrderInteraction(orderNumber));
  }

  returnsCoupons(): void {
    this.driver.stub(browseCouponsInteraction());
  }

  acceptsPublishedCoupon(code: string, discountRate: number): void {
    this.driver.stub(publishCouponInteraction({ code, discountRate }));
  }
}

// Fluent builder for the place-order interaction. Defaults mirror the running
// example so a spec only overrides what it cares about, then calls execute() to
// stage the interaction via the shared builder.
class ReturnsPlacedOrderCommand {
  private sku = 'BOOK-123';
  private quantity = 2;
  private country = 'US';
  private couponCode?: string;
  private orderNumber = 'ORD-1';

  constructor(private readonly driver: BackendStubDriver) {}

  withSku(sku: string): this {
    this.sku = sku;
    return this;
  }

  withQuantity(quantity: number): this {
    this.quantity = quantity;
    return this;
  }

  withCountry(country: string): this {
    this.country = country;
    return this;
  }

  withCoupon(couponCode: string): this {
    this.couponCode = couponCode;
    return this;
  }

  withOrderNumber(orderNumber: string): this {
    this.orderNumber = orderNumber;
    return this;
  }

  execute(): void {
    this.driver.stub(
      placeOrderInteraction({
        sku: this.sku,
        quantity: this.quantity,
        country: this.country,
        couponCode: this.couponCode,
        orderNumber: this.orderNumber,
      }),
    );
  }
}
