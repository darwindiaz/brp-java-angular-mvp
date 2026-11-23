import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject, catchError, exhaustMap, map, of, startWith } from 'rxjs';

import { BackendHealthService } from '../../core/services/backend-health.service';

type Probe =
  | { state: 'checking' }
  | { state: 'up'; detail: string }
  | { state: 'unavailable'; detail: string };

@Component({
  selector: 'app-connection-check',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './connection-check.component.html',
  styleUrl: './connection-check.component.scss',
})
export class ConnectionCheckComponent implements OnInit {
  private readonly backend = inject(BackendHealthService);
  private readonly checks = new Subject<void>();
  protected readonly result = signal<Probe>({ state: 'checking' });
  protected readonly showDetails = new FormControl(false, { nonNullable: true });

  constructor() {
    this.checks.pipe(
      exhaustMap(() => this.backend.check().pipe(
        map((response): Probe => ({
          state: response.status === 'UP' ? 'up' : 'unavailable',
          detail: response.status || 'Respuesta sin estado',
        })),
        catchError((error: unknown) => of<Probe>({
          state: 'unavailable',
          detail: error instanceof HttpErrorResponse && error.status
            ? `HTTP ${error.status}`
            : 'Sin respuesta del servidor',
        })),
        startWith({ state: 'checking' } as const),
      )),
      takeUntilDestroyed(),
    ).subscribe((result) => this.result.set(result));
  }

  ngOnInit(): void {
    this.checkBackend();
  }

  protected checkBackend(): void {
    this.checks.next();
  }
}
