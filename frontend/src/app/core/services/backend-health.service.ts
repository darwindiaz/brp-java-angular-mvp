import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface BackendHealth {
  status: string;
}

@Injectable({ providedIn: 'root' })
export class BackendHealthService {
  private readonly http = inject(HttpClient);

  check(): Observable<BackendHealth> {
    return this.http.get<BackendHealth>(`${environment.apiBaseUrl}/health`, { timeout: 5000 });
  }
}
