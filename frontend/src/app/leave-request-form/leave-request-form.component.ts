import { Component, DestroyRef, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { toErrorMessage } from '../core/api-error';
import { LeaveRequestService } from '../core/leave-request.service';
import { Employee, LEAVE_TYPE_LABELS, LeaveRequest, LeaveType } from '../models/leave-request.model';
import { dateRangeValidator, inclusiveDays } from './date-range.validator';

@Component({
  selector: 'app-leave-request-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './leave-request-form.component.html',
  styleUrls: ['./leave-request-form.component.css']
})
export class LeaveRequestFormComponent implements OnInit {
  /** Emits the request as returned by the server, so the parent can add it without reloading everything. */
  @Output() readonly created = new EventEmitter<LeaveRequest>();

  private readonly api = inject(LeaveRequestService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly employees = signal<Employee[]>([]);
  readonly submitting = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly submitted = signal(false);

  readonly leaveTypes = [LeaveType.Vacation, LeaveType.Sick, LeaveType.Unpaid].map((value) => ({
    value,
    label: LEAVE_TYPE_LABELS[value]
  }));

  readonly form = this.fb.group(
    {
      employeeId: this.fb.control<number | null>(null, Validators.required),
      type: this.fb.control<LeaveType | null>(null, Validators.required),
      startDate: this.fb.control<string>('', Validators.required),
      endDate: this.fb.control<string>('', Validators.required)
    },
    { validators: dateRangeValidator('startDate', 'endDate') }
  );

  ngOnInit(): void {
    this.api
      .getEmployees()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => this.employees.set(list),
        error: (err) => this.serverError.set(toErrorMessage(err, 'Could not load employees.'))
      });
  }

  get days(): number | null {
    const v = this.form.getRawValue();
    const d = inclusiveDays(v.startDate, v.endDate);
    return d !== null && d > 0 ? d : null;
  }

  /** Show a field's error once the user touched it or tried to submit. */
  showError(name: 'employeeId' | 'type' | 'startDate' | 'endDate'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || this.submitted());
  }

  get showRangeError(): boolean {
    return this.form.hasError('dateRange') && (this.form.controls.endDate.touched || this.submitted());
  }

  submit(): void {
    this.submitted.set(true);
    this.serverError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.api
      .create({
        employeeId: Number(v.employeeId),
        type: Number(v.type) as LeaveType,
        startDate: v.startDate!,
        endDate: v.endDate!
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (request) => {
          this.created.emit(request);
          this.form.reset();
          this.submitted.set(false);
        },
        // e.g. 422 "only 2 of 20 vacation days remain" from the server
        error: (err) => this.serverError.set(toErrorMessage(err))
      });
  }
}
