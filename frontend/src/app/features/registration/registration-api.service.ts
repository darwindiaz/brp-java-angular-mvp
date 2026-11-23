import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';

export type IdentificationType = 'CC' | 'NIT';
export type PaymentMethod = 'CONTADO' | 'PRESTAMO';
export interface PaymentInput { method: PaymentMethod; date: string; amount: string; lender?: string }
export interface BidderInput {
  identificationType: IdentificationType;
  identificationNumber: string;
  name: string;
  contact?: string;
  percentage: string;
  payments: PaymentInput[];
}
export interface CatalogItem {
  code: string;
  assetType: string;
  description: string | null;
  location: { department: string | null; municipality: string | null };
  referencePrice: string;
}
export interface DiscountPreview { referencePrice: string; total: string; discount: string; overprice: boolean }
export interface RegisteredOffer extends DiscountPreview {
  registrationNumber: string;
  brpCode: string;
  status: 'RADICADO';
  createdAt: string;
}

const numericFields = new Set(['referencePrice', 'total', 'discount', 'registrationNumber']);
const headers = new HttpHeaders({ 'Content-Type': 'application/json' });

function decode<T>(source: string): T {
  // Los navegadores con soporte de context.source conservan el literal decimal de la API.
  return JSON.parse(source, (key: string, value: unknown, context?: { source?: string }) => {
    if (!numericFields.has(key) || typeof value !== 'number') return value;
    if (context?.source) return context.source;
    const safelyRepresentable = key === 'registrationNumber'
      ? Number.isSafeInteger(value)
      : Math.abs(value) < (key === 'discount' ? 1e11 : 1e12);
    if (!safelyRepresentable) {
      throw new Error('El navegador no puede preservar la precisión del importe recibido.');
    }
    return String(value);
  }) as T;
}

function decimalLiteral(value: string): string {
  if (!/^\d+(?:\.\d+)?$/.test(value)) throw new Error('Decimal inválido');
  return value.replace(/^0+(?=\d)/, '');
}

@Injectable({ providedIn: 'root' })
export class RegistrationApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/offers`;

  catalog() {
    return this.http.get(`${this.url}/catalog`, { responseType: 'text' }).pipe(map(decode<CatalogItem[]>));
  }

  preview(brpCode: string, total: string) {
    const body = `{"brpCode":${JSON.stringify(brpCode)},"total":${decimalLiteral(total)}}`;
    return this.post<DiscountPreview>(`${this.url}/discount-preview`, body);
  }

  register(brpCode: string, bidders: BidderInput[]) {
    const entries = bidders.map(({ percentage, payments, ...bidder }) => {
      const paid = payments.map(({ amount, ...payment }) =>
        `{${JSON.stringify(payment).slice(1, -1)},"amount":${decimalLiteral(amount)}}`);
      return `{${JSON.stringify(bidder).slice(1, -1)},"percentage":${decimalLiteral(percentage)},"payments":[${paid.join(',')}]}`;
    });
    return this.post<RegisteredOffer>(this.url,
      `{"brpCode":${JSON.stringify(brpCode)},"bidders":[${entries.join(',')}]}`);
  }

  private post<T>(url: string, body: string) {
    return this.http.post(url, body, { headers, responseType: 'text' }).pipe(map(decode<T>));
  }
}

export function registrationError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'No se pudo leer la respuesta de radicación.';
  if (error.status === 0) return 'No se pudo conectar con el servidor.';
  if (error.status === 401) return 'Tu sesión terminó. Vuelve a iniciar sesión.';
  if (error.status === 403) return 'Tu cuenta no tiene permiso para radicar ofertas.';
  if (error.status === 400 || error.status === 409) {
    try {
      const problem = JSON.parse(error.error) as { detail?: string; errors?: { field: string }[] };
      const fields = problem.errors?.map(issue => issue.field).join(', ');
      if (problem.detail) return fields ? `${problem.detail} Campos: ${fields}.` : problem.detail;
    } catch { /* La API también puede devolver un cuerpo vacío. */ }
    return error.status === 409 ? 'El BRP cambió. Actualiza el catálogo y vuelve a intentarlo.' : 'Revisa los datos de la oferta.';
  }
  return 'No se pudo completar la solicitud. Comprueba el estado de la oferta antes de reintentar.';
}
