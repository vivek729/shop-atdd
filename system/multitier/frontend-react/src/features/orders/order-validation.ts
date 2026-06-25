import type { OrderFormData } from '../../types/form.types';

export interface ValidationError {
  field: string;
  message: string;
}

export function validateOrderForm(data: OrderFormData): ValidationError[] {
  const errors: ValidationError[] = [];
  const quantityTrimmed = data.quantityValue.trim();

  if (!data.sku) {
    errors.push({ field: 'sku', message: 'SKU must not be empty' });
  }

  if (quantityTrimmed === '') {
    errors.push({ field: 'quantity', message: 'Quantity must not be empty' });
  } else {
    const quantityNum = Number.parseFloat(quantityTrimmed);

    if (Number.isNaN(quantityNum)) {
      errors.push({ field: 'quantity', message: 'Quantity must be an integer' });
    } else if (!Number.isInteger(quantityNum)) {
      errors.push({ field: 'quantity', message: 'Quantity must be an integer' });
    } else if (quantityNum <= 0) {
      errors.push({ field: 'quantity', message: 'Quantity must be positive' });
    }
  }

  if (!data.country) {
    errors.push({ field: 'country', message: 'Country must not be empty' });
  }

  return errors;
}
