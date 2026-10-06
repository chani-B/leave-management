// Mirrors the backend DTOs. type/status are numeric on the wire (see the Java enums),
// so we model them as numeric enums instead of magic numbers.

export enum LeaveType {
  Vacation = 0,
  Sick = 1,
  Unpaid = 2
}

export enum LeaveStatus {
  Pending = 0,
  Approved = 1,
  Rejected = 2
}

export const LEAVE_TYPE_LABELS: Record<LeaveType, string> = {
  [LeaveType.Vacation]: 'Vacation',
  [LeaveType.Sick]: 'Sick',
  [LeaveType.Unpaid]: 'Unpaid'
};

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  [LeaveStatus.Pending]: 'Pending',
  [LeaveStatus.Approved]: 'Approved',
  [LeaveStatus.Rejected]: 'Rejected'
};

export interface Employee {
  id: number;
  name: string;
  annualQuota: number;
}

/** GET /api/leave-requests item, and the response of create / approve. */
export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  type: LeaveType;
  startDate: string; // ISO date, yyyy-MM-dd
  endDate: string;   // ISO date, yyyy-MM-dd
  status: LeaveStatus;
  days: number;
}

/** POST /api/leave-requests body. */
export interface CreateLeaveRequest {
  employeeId: number;
  type: LeaveType;
  startDate: string;
  endDate: string;
}

/** RFC 7807 error body returned by the backend's GlobalExceptionHandler. */
export interface ApiProblem {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}
