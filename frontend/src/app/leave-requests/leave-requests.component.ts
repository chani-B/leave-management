import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { toErrorMessage } from '../core/api-error';
import { LeaveRequestService } from '../core/leave-request.service';
import { LeaveRequestFormComponent } from '../leave-request-form/leave-request-form.component';
import {
  LEAVE_STATUS_LABELS,
  LEAVE_TYPE_LABELS,
  LeaveRequest,
  LeaveStatus
} from '../models/leave-request.model';

type Notice = { kind: 'success' | 'error'; text: string };

/**
 * State is held in signals (no manual change detection, no stray subscriptions).
 * Every HTTP subscription is bound to the component's lifetime with takeUntilDestroyed.
 */
@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [DatePipe, LeaveRequestFormComponent],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  private readonly api = inject(LeaveRequestService);
  private readonly destroyRef = inject(DestroyRef);

  readonly LeaveStatus = LeaveStatus;
  readonly typeLabels = LEAVE_TYPE_LABELS;
  readonly statusLabels = LEAVE_STATUS_LABELS;

  readonly requests = signal<LeaveRequest[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  /** Ids with an approve call in flight -> per-row spinner, and blocks double clicks. */
  readonly approving = signal<ReadonlySet<number>>(new Set());
  /** Per-row error (e.g. "already approved"), shown next to the row that failed. */
  readonly rowErrors = signal<ReadonlyMap<number, string>>(new Map());
  /** Page-level feedback for the latest action. */
  readonly notice = signal<Notice | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getAll()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (list) => {
          this.requests.set(list);
          this.rowErrors.set(new Map());
        },
        error: (err) => this.loadError.set(toErrorMessage(err, 'Could not load leave requests.'))
      });
  }

  approve(request: LeaveRequest): void {
    if (this.approving().has(request.id)) {
      return;
    }
    this.setApproving(request.id, true);
    this.setRowError(request.id, null);
    this.notice.set(null);

    this.api
      .approve(request.id)
      .pipe(
        finalize(() => this.setApproving(request.id, false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (updated) => {
          // Replace only the affected row with the server's version — no full reload.
          this.requests.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
          this.notice.set({
            kind: 'success',
            text: `Approved ${updated.employeeName}'s request (${updated.days} days).`
          });
        },
        error: (err) => this.setRowError(request.id, toErrorMessage(err, 'Approval failed.'))
      });
  }

  /** New request from the form: put it on top of the list, no reload. */
  onCreated(request: LeaveRequest): void {
    this.requests.update((list) => [request, ...list]);
    this.notice.set({ kind: 'success', text: `Request submitted for ${request.employeeName}.` });
  }

  dismissNotice(): void {
    this.notice.set(null);
  }

  private setApproving(id: number, on: boolean): void {
    this.approving.update((current) => {
      const next = new Set(current);
      if (on) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  private setRowError(id: number, message: string | null): void {
    this.rowErrors.update((current) => {
      const next = new Map(current);
      if (message) {
        next.set(id, message);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
