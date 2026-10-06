import { InjectionToken } from '@angular/core';

/** Base URL of the backend API. Injectable so it can be overridden per environment / in tests. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:5080/api'
});
