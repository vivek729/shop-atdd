// Frontend DSL — the semantic "what the frontend does and sees" surface for the
// "latest" contract specs. It reads the same whether it drives the rendered UI
// (component spec) or the gateway directly (integration spec): the difference is
// absorbed by the swappable FrontendDriver handed in at construction.
//
// hasConfirmation/showsOrder/… are SEMANTIC — "a confirmation carrying order
// number X", not a literal UI string. Each driver realizes them its own way
// (screen text vs. a gateway result), so the DSL never hard-codes UI formatting.

export interface PlaceOrderGesture {
  sku: string;
  quantity: number;
  couponCode?: string;
}

// The seam both drivers implement. Gestures drive; the matching query methods
// assert the outcome. Some interactions only exist at one level — order details
// and its status-gated actions are UI-only; publishing a coupon is gateway-only —
// so the driver that can't realize a verb throws a clearly-labelled error. The
// latest specs never call an unsupported verb, so those throws stay dormant and
// simply document the level boundary.
export interface FrontendDriver {
  useBackend(baseUrl: string): void;

  // place order (both levels)
  placeOrder(gesture: PlaceOrderGesture): Promise<void>;
  hasConfirmation(orderNumber: string): Promise<void>;
  hasError(message: string): Promise<void>;

  // browse order history (both levels)
  browseOrderHistory(): Promise<void>;
  showsOrder(orderNumber: string): Promise<void>;

  // browse coupons (both levels)
  browseCoupons(): Promise<void>;
  showsCoupon(code: string): Promise<void>;

  // view order details (UI only — asserts on the rendered screen)
  viewOrderDetails(orderNumber: string): Promise<void>;
  showsOrderDetails(orderNumber: string, totalPrice: string): Promise<void>;
  showsCancelAndDeliverActions(): Promise<void>;
  hidesCancelAndDeliverActions(): Promise<void>;
  showsNotFound(): Promise<void>;

  // publish coupon (gateway only)
  publishCoupon(code: string, discountRate: number): Promise<void>;
  succeeded(): Promise<void>;
}

export class FrontendDsl {
  constructor(private readonly driver: FrontendDriver) {}

  // Point the frontend at the Pact mock server. This is the one plumbing leak the
  // driver can't fully hide — Pact stands up a real HTTP server on a random port —
  // so it's an explicit line, realized per driver (routeApiTo for UI, base URL for
  // the gateway).
  useBackend(baseUrl: string): void {
    this.driver.useBackend(baseUrl);
  }

  placeOrder(): PlaceOrderCommand {
    return new PlaceOrderCommand(this.driver);
  }

  browseOrderHistory(): BrowseOrderHistoryCommand {
    return new BrowseOrderHistoryCommand(this.driver);
  }

  browseCoupons(): BrowseCouponsCommand {
    return new BrowseCouponsCommand(this.driver);
  }

  viewOrderDetails(orderNumber: string): ViewOrderDetailsCommand {
    return new ViewOrderDetailsCommand(this.driver, orderNumber);
  }

  publishCoupon(): PublishCouponCommand {
    return new PublishCouponCommand(this.driver);
  }
}

class PlaceOrderCommand {
  private sku = 'BOOK-123';
  private quantity = 2;
  private couponCode?: string;

  constructor(private readonly driver: FrontendDriver) {}

  withSku(sku: string): this {
    this.sku = sku;
    return this;
  }

  withQuantity(quantity: number): this {
    this.quantity = quantity;
    return this;
  }

  withCoupon(couponCode: string): this {
    this.couponCode = couponCode;
    return this;
  }

  execute(): PlaceOrderOutcome {
    const gesture = this.driver.placeOrder({
      sku: this.sku,
      quantity: this.quantity,
      couponCode: this.couponCode,
    });
    return new PlaceOrderOutcome(this.driver, gesture);
  }
}

class PlaceOrderOutcome {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly gesture: Promise<void>,
  ) {}

  async hasConfirmation(orderNumber: string): Promise<void> {
    await this.gesture;
    await this.driver.hasConfirmation(orderNumber);
  }

  async hasError(message: string): Promise<void> {
    await this.gesture;
    await this.driver.hasError(message);
  }
}

class BrowseOrderHistoryCommand {
  constructor(private readonly driver: FrontendDriver) {}

  execute(): BrowseOrderHistoryOutcome {
    return new BrowseOrderHistoryOutcome(this.driver, this.driver.browseOrderHistory());
  }
}

class BrowseOrderHistoryOutcome {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly gesture: Promise<void>,
  ) {}

  async showsOrder(orderNumber: string): Promise<void> {
    await this.gesture;
    await this.driver.showsOrder(orderNumber);
  }
}

class BrowseCouponsCommand {
  constructor(private readonly driver: FrontendDriver) {}

  execute(): BrowseCouponsOutcome {
    return new BrowseCouponsOutcome(this.driver, this.driver.browseCoupons());
  }
}

class BrowseCouponsOutcome {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly gesture: Promise<void>,
  ) {}

  async showsCoupon(code: string): Promise<void> {
    await this.gesture;
    await this.driver.showsCoupon(code);
  }
}

class ViewOrderDetailsCommand {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly orderNumber: string,
  ) {}

  execute(): ViewOrderDetailsOutcome {
    return new ViewOrderDetailsOutcome(this.driver, this.driver.viewOrderDetails(this.orderNumber));
  }
}

class ViewOrderDetailsOutcome {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly gesture: Promise<void>,
  ) {}

  async showsOrderDetails(orderNumber: string, totalPrice: string): Promise<void> {
    await this.gesture;
    await this.driver.showsOrderDetails(orderNumber, totalPrice);
  }

  async showsCancelAndDeliverActions(): Promise<void> {
    await this.gesture;
    await this.driver.showsCancelAndDeliverActions();
  }

  async hidesCancelAndDeliverActions(): Promise<void> {
    await this.gesture;
    await this.driver.hidesCancelAndDeliverActions();
  }

  async showsNotFound(): Promise<void> {
    await this.gesture;
    await this.driver.showsNotFound();
  }
}

class PublishCouponCommand {
  private code = 'SAVE10';
  private discountRate = 0.2;

  constructor(private readonly driver: FrontendDriver) {}

  withCode(code: string): this {
    this.code = code;
    return this;
  }

  withDiscountRate(discountRate: number): this {
    this.discountRate = discountRate;
    return this;
  }

  execute(): PublishCouponOutcome {
    return new PublishCouponOutcome(this.driver, this.driver.publishCoupon(this.code, this.discountRate));
  }
}

class PublishCouponOutcome {
  constructor(
    private readonly driver: FrontendDriver,
    private readonly gesture: Promise<void>,
  ) {}

  async succeeded(): Promise<void> {
    await this.gesture;
    await this.driver.succeeded();
  }
}
