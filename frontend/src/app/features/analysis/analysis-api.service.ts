import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AnalysisAction, WorkItem } from './models/analysis.models';

const integerFields = new Set(['version', 'registrationNumber', 'analystId']);
const decimalFields = new Set(['referencePrice', 'total', 'discount']);
const headers = new HttpHeaders({ 'Content-Type': 'application/json' });

function decode<T>(source: string): T {
  return JSON.parse(source, (key: string, value: unknown, context?: { source?: string }) => {
    if (typeof value !== 'number' || (!integerFields.has(key) && !decimalFields.has(key))) return value;
    if (context?.source) return context.source;
    const exact = integerFields.has(key) ? Number.isSafeInteger(value) : Math.abs(value) < 1e11;
    if (!exact) throw new Error('La respuesta excede la precisión disponible en el navegador.');
    return String(value);
  }) as T;
}

@Injectable({ providedIn: 'root' })
export class AnalysisApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/offers/analysis`;

  list() {
    return this.http.get(this.url, { responseType: 'text' }).pipe(map(decode<WorkItem[]>));
  }

  transition(item: WorkItem, action: AnalysisAction) {
    const { registrationNumber } = item.offer;
    if (!/^[1-9]\d*$/.test(registrationNumber) || !/^(0|[1-9]\d*)$/.test(item.version)) {
      throw new Error('Número de radicación o versión inválidos.');
    }
    return this.http.post(`${this.url}/${registrationNumber}/${action}`, `{"version":${item.version}}`,
      { headers, responseType: 'text' }).pipe(map(decode<WorkItem>));
  }
}

export function analysisError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'No se pudo leer la información del servidor.';
  switch (error.status) {
    case 401: return ''; // El interceptor invalida la sesión.
    case 403: return 'Tu cuenta no tiene autorización para realizar esta operación.';
    case 404: return 'La oferta ya no está disponible.';
    case 409: return 'La oferta cambió o fue atendida por otro usuario. Revisa la bandeja actualizada.';
    case 400: return 'El servidor rechazó los datos de la operación. Actualiza la bandeja.';
    case 0: return 'No se pudo conectar con el servidor.';
    default: return 'No se pudo completar la operación.';
  }
}
