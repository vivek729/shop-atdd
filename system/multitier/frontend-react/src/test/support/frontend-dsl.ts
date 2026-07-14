// Frontend DSL — the semantic "what the frontend does and sees" surface for the
// "latest" contract specs. It reads the same whether it drives the rendered UI
// (component spec) or the gateway directly (integration spec): the difference is
// absorbed by the swappable FrontendDriver the harness hands in.
//
// hasConfirmation/showsOrder/… are SEMANTIC — "a confirmation carrying order
// number X", not a literal UI string. Each driver realizes them its own way
// (screen text vs. a gateway result), so the DSL never hard-codes UI formatting.
//
// The DSL also owns the act-time binding: it takes a driver FACTORY and a backend-URL
// supplier, and on the first gesture it boots the stubbed backend, builds a fresh
// driver, and points it at that backend. That's what lets a spec go straight from
// backend.* (arrange) to frontend.* (act) with no mock-server URL in sight — the
// backend can't boot until the arrange phase is done staging, so first-gesture is
// exactly the right moment.

// quantity is `number | string` because the user types free text: "3.5" and "lala" are
// gestures a real person can make, and the frontend has its own rules about them.
export interface PlaceOrderGesture {
  sku: string;
  quantity: number | string;
  country: string;
  couponCode?: string;
}

// The seam both drivers implement. Gestures drive; the matching query methods
// assert the outcome. useBackend points the driver at the stubbed backend — the
// harness calls it, never a spec (routeApiTo for the UI, base URL for the gateway).
// Some interactions only exist at one level — order details and its status-gated
// actions are UI-only; publishing a coupon is gateway-only — so the driver that
// can't realize a verb throws a clearly-labelled error. The latest specs never call
// an unsupported verb, so those throws stay dormant and simply document the level
// boundary.
export interface FrontendDriver {
  useBackend(baseUrl: string): void;

  // place order (both levels)
  placeOrder(gesture: PlaceOrderGesture): Promise<void>;
  hasConfirmation(orderNumber: string): Promise<void>;
  hasError(message: string): Promise<void>;
  hasFieldError(field: string, message: string): Promise<void>;

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

// Resolved on the first gesture, memoized for the rest of the test, dropped by reset().
type DriverHandle = () => Promise<FrontendDriver>;

export class FrontendDsl {
  private driver?: Promise<FrontendDriver>;

  constructor(
    private readonly newDriver: () => FrontendDriver,
    private readonly backendUrl: () => Promise<string>,
  ) {}

  // Called by the harness between tests: the next gesture builds a fresh driver
  // against a freshly booted backend.
  reset(): void {
    this.driver = undefined;
  }

  placeOrder(): PlaceOrderCommand {
    return new PlaceOrderCommand(this.handle());
  }

  browseOrderHistory(): BrowseOrderHistoryCommand {
    return new BrowseOrderHistoryCommand(this.handle());
  }

  browseCoupons(): BrowseCouponsCommand {
    return new BrowseCouponsCommand(this.handle());
  }

  viewOrderDetails(orderNumber: string): ViewOrderDetailsCommand {
    return new ViewOrderDetailsCommand(this.handle(), orderNumber);
  }

  publishCoupon(): PublishCouponCommand {
    return new PublishCouponCommand(this.handle());
  }

  private handle(): DriverHandle {
    return () => this.ready();
  }

  private ready(): Promise<FrontendDriver> {
    if (!this.driver) {
      this.driver = (async () => {
        const baseUrl = await this.backendUrl();
        const driver = this.newDriver();
        driver.useBackend(baseUrl);
        return driver;
      })();
    }
    return this.driver;
  }
}

class PlaceOrderCommand {
  private sku = 'BOOK-123';
  private quantity: number | string = 2;
  private country = 'US';
  private couponCode?: string;

  constructor(private readonly driver: DriverHandle) {}

  withSku(sku: string): this {
    this.sku = sku;
    return this;
  }

  withQuantity(quantity: number | string): this {
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

  execute(): PlaceOrderOutcome {
    const gesture = this.driver().then((driver) =>
      driver.placeOrder({
        sku: this.sku,
        quantity: this.quantity,
        country: this.country,
        couponCode: this.couponCode,
      }),
    );
    return new PlaceOrderOutcome(this.driver, gesture);
  }
}

class PlaceOrderOutcome {
  constructor(
    private readonly driver: DriverHandle,
    private readonly gesture: Promise<void>,
  ) {}

  async hasConfirmation(orderNumber: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).hasConfirmation(orderNumber);
  }

  async hasError(message: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).hasError(message);
  }

  // "the message for THIS field", not "this string is somewhere on the page" — a field
  // error that renders against the wrong field is a real defect, and this is what catches it.
  async hasFieldError(field: string, message: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).hasFieldError(field, message);
  }
}

class BrowseOrderHistoryCommand {
  constructor(private readonly driver: DriverHandle) {}

  execute(): BrowseOrderHistoryOutcome {
    return new BrowseOrderHistoryOutcome(
      this.driver,
      this.driver().then((driver) => driver.browseOrderHistory()),
    );
  }
}

class BrowseOrderHistoryOutcome {
  constructor(
    private readonly driver: DriverHandle,
    private readonly gesture: Promise<void>,
  ) {}

  async showsOrder(orderNumber: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).showsOrder(orderNumber);
  }
}

class BrowseCouponsCommand {
  constructor(private readonly driver: DriverHandle) {}

  execute(): BrowseCouponsOutcome {
    return new BrowseCouponsOutcome(
      this.driver,
      this.driver().then((driver) => driver.browseCoupons()),
    );
  }
}

class BrowseCouponsOutcome {
  constructor(
    private readonly driver: DriverHandle,
    private readonly gesture: Promise<void>,
  ) {}

  async showsCoupon(code: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).showsCoupon(code);
  }
}

class ViewOrderDetailsCommand {
  constructor(
    private readonly driver: DriverHandle,
    private readonly orderNumber: string,
  ) {}

  execute(): ViewOrderDetailsOutcome {
    return new ViewOrderDetailsOutcome(
      this.driver,
      this.driver().then((driver) => driver.viewOrderDetails(this.orderNumber)),
    );
  }
}

class ViewOrderDetailsOutcome {
  constructor(
    private readonly driver: DriverHandle,
    private readonly gesture: Promise<void>,
  ) {}

  async showsOrderDetails(orderNumber: string, totalPrice: string): Promise<void> {
    await this.gesture;
    await (await this.driver()).showsOrderDetails(orderNumber, totalPrice);
  }

  async showsCancelAndDeliverActions(): Promise<void> {
    await this.gesture;
    await (await this.driver()).showsCancelAndDeliverActions();
  }

  async hidesCancelAndDeliverActions(): Promise<void> {
    await this.gesture;
    await (await this.driver()).hidesCancelAndDeliverActions();
  }

  async showsNotFound(): Promise<void> {
    await this.gesture;
    await (await this.driver()).showsNotFound();
  }
}

class PublishCouponCommand {
  private code = 'SAVE10';
  private discountRate = 0.2;

  constructor(private readonly driver: DriverHandle) {}

  withCode(code: string): this {
    this.code = code;
    return this;
  }

  withDiscountRate(discountRate: number): this {
    this.discountRate = discountRate;
    return this;
  }

  execute(): PublishCouponOutcome {
    return new PublishCouponOutcome(
      this.driver,
      this.driver().then((driver) => driver.publishCoupon(this.code, this.discountRate)),
    );
  }
}

class PublishCouponOutcome {
  constructor(
    private readonly driver: DriverHandle,
    private readonly gesture: Promise<void>,
  ) {}

  async succeeded(): Promise<void> {
    await this.gesture;
    await (await this.driver()).succeeded();
  }
}
