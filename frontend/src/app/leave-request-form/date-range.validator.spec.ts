import { FormControl, FormGroup } from '@angular/forms';
import { dateRangeValidator, inclusiveDays } from './date-range.validator';

describe('dateRangeValidator', () => {
  const group = (start: string | null, end: string | null) =>
    new FormGroup(
      { start: new FormControl(start), end: new FormControl(end) },
      { validators: dateRangeValidator('start', 'end') }
    );

  it('accepts a single-day range', () => {
    expect(group('2026-03-01', '2026-03-01').errors).toBeNull();
  });

  it('rejects start after end', () => {
    expect(group('2026-03-05', '2026-03-01').hasError('dateRange')).toBeTrue();
  });

  it('ignores incomplete input (required handles it)', () => {
    expect(group('2026-03-05', null).errors).toBeNull();
  });

  it('counts days inclusively across a DST change', () => {
    expect(inclusiveDays('2026-03-25', '2026-03-31')).toBe(7);
  });
});
