import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { decodeWorkItems, versionCommand, WorkItem } from '../../core/offers/offer-work-item';
import { AnalysisAction } from './models/analysis.models';

const headers = new HttpHeaders({ 'Content-Type': 'application/json' });

@Injectable({ providedIn: 'root' })
export class AnalysisApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/offers/analysis`;

  list() {
    return this.http.get(this.url, { responseType: 'text' }).pipe(map(decodeWorkItems<WorkItem[]>));
  }

  transition(item: WorkItem, action: AnalysisAction) {
    const body = versionCommand(item);
    return this.http.post(`${this.url}/${item.offer.registrationNumber}/${action}`, body,
      { headers, responseType: 'text' }).pipe(map(decodeWorkItems<WorkItem>));
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
