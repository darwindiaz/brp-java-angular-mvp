import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { decodeWorkItems, versionCommand, WorkItem } from '../../core/offers/offer-work-item';

export type ApprovalAction = 'approve' | 'reject' | 'return';

const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
const requestTimeout = 30_000;

@Injectable({ providedIn: 'root' })
export class ApprovalApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/offers/approval`;

  list() {
    return this.http.get(this.url, { responseType: 'text', timeout: requestTimeout })
      .pipe(map(decodeWorkItems<WorkItem[]>));
  }

  decide(item: WorkItem, action: ApprovalAction) {
    const body = versionCommand(item);
    return this.http.post(`${this.url}/${item.offer.registrationNumber}/${action}`, body,
      { headers, responseType: 'text', timeout: requestTimeout }).pipe(map(decodeWorkItems<WorkItem>));
  }
}

export function approvalError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'No se pudo leer la información del servidor.';
  switch (error.status) {
    case 401: return ''; // La sesión se gestiona en el interceptor.
    case 403: return 'Tu cuenta no tiene permiso para realizar esta operación.';
    case 404: return 'La oferta ya no está disponible.';
    case 409: return 'La oferta cambió o fue atendida por otro usuario. Revisa la bandeja actualizada.';
    case 400: return 'El servidor rechazó los datos. Actualiza la bandeja.';
    case 0: return 'No se pudo conectar con el servidor.';
    default: return 'No se pudo completar la operación.';
  }
}
