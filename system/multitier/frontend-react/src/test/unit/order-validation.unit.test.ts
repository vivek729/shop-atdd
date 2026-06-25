import { describe, it, expect } from 'vitest';
import { validateOrderForm } from '../../features/orders/order-validation';

const valid = {
  sku: 'ABC-123',
  quantity: 2,
  quantityValue: '2',
  country: 'US',
  couponCode: '',
};

describe('validateOrderForm', () => {
  it('returns no errors for valid input', () => {
    expect(validateOrderForm(valid)).toEqual([]);
  });

  describe('sku', () => {
    it('errors when sku is empty', () => {
      const errors = validateOrderForm({ ...valid, sku: '' });
      expect(errors).toContainEqual({ field: 'sku', message: 'SKU must not be empty' });
    });
  });

  describe('quantity', () => {
    it('errors when quantity is empty string', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: '' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must not be empty' });
    });

    it('errors when quantity is whitespace only', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: '   ' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must not be empty' });
    });

    it('errors when quantity is not a number', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: 'abc' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must be an integer' });
    });

    it('errors when quantity is a decimal', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: '1.5' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must be an integer' });
    });

    it('errors when quantity is zero', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: '0' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must be positive' });
    });

    it('errors when quantity is negative', () => {
      const errors = validateOrderForm({ ...valid, quantityValue: '-1' });
      expect(errors).toContainEqual({ field: 'quantity', message: 'Quantity must be positive' });
    });
  });

  describe('country', () => {
    it('errors when country is empty', () => {
      const errors = validateOrderForm({ ...valid, country: '' });
      expect(errors).toContainEqual({ field: 'country', message: 'Country must not be empty' });
    });
  });

  it('returns multiple errors when several fields are invalid', () => {
    const errors = validateOrderForm({ ...valid, sku: '', quantityValue: '', country: '' });
    expect(errors).toHaveLength(3);
  });
});
