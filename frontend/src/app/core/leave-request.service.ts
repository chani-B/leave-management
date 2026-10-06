import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateLeaveRequest, Employee, LeaveRequest } from '../models/leave-request.model';
import { API_BASE_URL } from './api-config';

/** All HTTP access for leave requests lives here; components never touch HttpClient. */
@Injectable({ providedIn: 'root' })
export class LeaveRequestService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getAll(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.baseUrl}/leave-requests`);
  }

  create(request: CreateLeaveRequest): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.baseUrl}/leave-requests`, request);
  }

  approve(id: number): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.baseUrl}/leave-requests/${id}/approve`, null);
  }

  getEmployees(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.baseUrl}/employees`);
  }
}
