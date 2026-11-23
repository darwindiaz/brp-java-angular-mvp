import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CatalogFreshness, TrackedOffer, TrackingCatalogItem } from './tracking.models';

const integerFields = new Set(['registrationNumber', 'id']);
const decimalFields = new Set(['total', 'discount', 'referencePrice']);
const precisionError = 'El navegador no puede mostrar algunos valores sin perder precisión.';

function decode<T>(source: string): T {
  return JSON.parse(source, (key: string, value: unknown, context?: { source?: string }) => {
    if (typeof value !== 'number' || (!integerFields.has(key) && !decimalFields.has(key))) return value;
    if (context?.source) return context.source;
    const safe = integerFields.has(key) ? Number.isSafeInteger(value) : Math.abs(value) < 1e11;
    if (!safe) throw new RangeError(precisionError);
    return String(value);
  }) as T;
}

@Injectable({ providedIn: 'root' })
export class TrackingApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/follow-up`;

  offers() {
    return this.http.get(`${this.url}/offers`, { responseType: 'text' }).pipe(map(decode<TrackedOffer[]>));
  }

  catalog() {
    return this.http.get(`${this.url}/catalog`, { responseType: 'text' }).pipe(map(decode<TrackingCatalogItem[]>));
  }

  freshness() {
    return this.http.get<CatalogFreshness>(`${this.url}/catalog/freshness`);
  }
}

export function trackingError(error: unknown): string {
  if (error instanceof RangeError && error.message === precisionError) return precisionError;
  if (error instanceof HttpErrorResponse) {
    switch (error.status) {
      case 0: return 'No se pudo conectar con el servidor. Intenta de nuevo.';
      case 401: return 'Tu sesión terminó. Vuelve a iniciar sesión.';
      case 403: return 'Tu cuenta no tiene permiso para consultar Seguimiento.';
    }
  }
  return 'No se pudo completar la consulta. Intenta de nuevo.';
}
