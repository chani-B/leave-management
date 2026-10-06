import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Inclusive number of calendar days between two ISO dates (yyyy-MM-dd), or null if either is missing.
 * Uses UTC so DST changes can't produce off-by-one results.
 */
export function inclusiveDays(start: string | null | undefined, end: string | null | undefined): number | null {
  if (!start || !end) {
    return null;
  }
  const s = Date.parse(`${start}T00:00:00Z`);
  const e = Date.parse(`${end}T00:00:00Z`);
  if (Number.isNaN(s) || Number.isNaN(e)) {
    return null;
  }
  return Math.round((e - s) / MS_PER_DAY) + 1;
}

/**
 * Group validator: start date must not be after end date.
 * This is also what guarantees a positive day count (days = end - start + 1 >= 1).
 */
export function dateRangeValidator(startKey: string, endKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const days = inclusiveDays(group.get(startKey)?.value, group.get(endKey)?.value);
    return days !== null && days < 1 ? { dateRange: true } : null;
  };
}
