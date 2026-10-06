import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from '../models/leave-request.model';

/** Turns any HTTP error into a single human-readable message for the UI. */
export function toErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  const problem = err.error as Partial<ApiProblem> | null;
  if (problem && typeof problem === 'object') {
    if (problem.errors && Object.keys(problem.errors).length > 0) {
      return Object.values(problem.errors).join(' ');
    }
    if (problem.detail) {
      return problem.detail;
    }
  }
  return fallback;
}
