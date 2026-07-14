// UI Frontend Driver — realizes the Frontend DSL by driving the rendered UI:
// it renders the page, fires the user's gestures through userEvent, and asserts
// against the rendered screen. Used by the component/latest specs.
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect } from 'vitest';
import { NewOrder } from '../../pages/NewOrder';
import { OrderHistory } from '../../pages/OrderHistory';
import { OrderDetails } from '../../pages/OrderDetails';
import { AdminCoupons } from '../../pages/AdminCoupons';
import { renderWithProviders, routeApiTo } from '../test-utils';
import type { FrontendDriver, PlaceOrderGesture } from './frontend-dsl';

export class UiFrontendDriver implements FrontendDriver {
  private readonly user = userEvent.setup();
  private publishedCode = '';

  useBackend(baseUrl: string): void {
    // Rendered components call relative /api/*; point them at the Pact mock server.
    routeApiTo(baseUrl);
  }

  async placeOrder(gesture: PlaceOrderGesture): Promise<void> {
    renderWithProviders(<NewOrder />);
    await this.fill('SKU', gesture.sku);
    await this.fill('Quantity', String(gesture.quantity));
    // Country is pre-filled with 'US' by the form, so it must be cleared before it can be
    // set — including to empty, which is the whole point of the empty-country scenario.
    await this.fill('Country', gesture.country);
    await this.fill('Coupon Code', gesture.couponCode ?? '');
    await this.user.click(screen.getByRole('button', { name: 'Place Order' }));
  }

  // clear-then-type, so '' means "leave this field empty" rather than "leave it alone".
  private async fill(label: string, value: string): Promise<void> {
    const field = screen.getByLabelText(label);
    await this.user.clear(field);
    if (value !== '') {
      await this.user.type(field, value);
    }
  }

  async hasConfirmation(orderNumber: string): Promise<void> {
    expect(await screen.findByText(new RegExp(`Order Number ${orderNumber}`))).toBeInTheDocument();
  }

  async hasError(message: string): Promise<void> {
    expect(await screen.findByText(message)).toBeInTheDocument();
  }

  // The banner renders each field error as "field: message" (see Notification.tsx), so the
  // field name is part of what the user reads — assert the whole rendered line.
  async hasFieldError(field: string, message: string): Promise<void> {
    expect(await screen.findByText(`${field}: ${message}`)).toBeInTheDocument();
  }

  async browseOrderHistory(): Promise<void> {
    renderWithProviders(<OrderHistory />);
  }

  async showsOrder(orderNumber: string): Promise<void> {
    expect(await screen.findByText(orderNumber)).toBeInTheDocument();
  }

  async browseCoupons(): Promise<void> {
    renderWithProviders(<AdminCoupons />);
  }

  async showsCoupon(code: string): Promise<void> {
    expect(await screen.findByText(code)).toBeInTheDocument();
  }

  async viewOrderDetails(orderNumber: string): Promise<void> {
    renderWithProviders(<OrderDetails />, {
      routePath: '/order-details/:orderNumber',
      initialEntry: `/order-details/${orderNumber}`,
    });
  }

  async showsOrderDetails(orderNumber: string, totalPrice: string): Promise<void> {
    expect(await screen.findByLabelText('Display Order Number')).toHaveTextContent(orderNumber);
    expect(screen.getByLabelText('Display Total Price')).toHaveTextContent(totalPrice);
  }

  async showsCancelAndDeliverActions(): Promise<void> {
    // Wait for the details to load before querying actions — otherwise we assert
    // against the loading spinner (and tear the mock server down before the GET lands).
    await screen.findByLabelText('Display Order Number');
    expect(screen.getByRole('button', { name: 'Cancel Order' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deliver Order' })).toBeInTheDocument();
  }

  async hidesCancelAndDeliverActions(): Promise<void> {
    await screen.findByLabelText('Display Order Number');
    expect(screen.queryByRole('button', { name: 'Cancel Order' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Deliver Order' })).not.toBeInTheDocument();
  }

  async showsNotFound(): Promise<void> {
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
  }

  // Cancelling is not a screen of its own: the user opens the order and presses the action there,
  // so the gesture is both steps. Wait for the details to land first — the button does not exist
  // until they do.
  async cancelOrder(orderNumber: string): Promise<void> {
    await this.viewOrderDetails(orderNumber);
    await screen.findByLabelText('Display Order Number');
    await this.user.click(screen.getByRole('button', { name: 'Cancel Order' }));
  }

  async wasCancelled(): Promise<void> {
    expect(await screen.findByText('Order has been cancelled successfully')).toBeInTheDocument();
  }

  async cancelWasRejected(message: string): Promise<void> {
    expect(await screen.findByText(message)).toBeInTheDocument();
  }

  // The coupon admin screen loads the coupon table AND hosts the create form, so publishing a
  // coupon from the UI necessarily reads the coupons too — a spec here stages both interactions.
  // The code field arrives pre-filled with a generated code, so it is cleared before typing.
  async publishCoupon(code: string, discountRate: number): Promise<void> {
    this.publishedCode = code;
    renderWithProviders(<AdminCoupons />);
    await this.fill('Coupon Code', code);
    // A number input, and a decimal is typed one keystroke at a time: "0." is not a number, so
    // userEvent.type would drive the field through a NaN state. One change event carries the value
    // the user ends up with.
    fireEvent.change(screen.getByLabelText('Discount Rate'), {
      target: { value: String(discountRate) },
    });
    await this.user.click(screen.getByRole('button', { name: 'Create Coupon' }));
  }

  async succeeded(): Promise<void> {
    expect(
      await screen.findByText(`Coupon '${this.publishedCode}' created successfully!`),
    ).toBeInTheDocument();
  }
}
