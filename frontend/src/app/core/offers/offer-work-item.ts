export type OfferStatus = 'RADICADO' | 'EN_PROCESO' | 'ESCALADO' | 'APROBADO' | 'RECHAZADO' | 'DESISTIDO';

export interface OfferSummary {
  registrationNumber: string;
  brpCode: string;
  referencePrice: string;
  total: string;
  discount: string;
  overprice: boolean;
  status: OfferStatus;
  createdAt: string;
}

export interface WorkItem {
  offer: OfferSummary;
  analystId: string | null;
  version: string;
  modifiedAt: string | null;
  escalatedAt: string | null;
  returnedAt: string | null;
  closedAt: string | null;
}

const integerFields = new Set(['version', 'registrationNumber', 'analystId']);
const decimalFields = new Set(['referencePrice', 'total', 'discount']);

export function decodeWorkItems<T extends WorkItem | WorkItem[]>(source: string): T {
  return JSON.parse(source, (key: string, value: unknown, context?: { source?: string }) => {
    if (typeof value !== 'number' || (!integerFields.has(key) && !decimalFields.has(key))) return value;
    if (context?.source) return context.source;
    const exact = integerFields.has(key) ? Number.isSafeInteger(value) : Math.abs(value) < 1e11;
    if (!exact) throw new Error('La respuesta excede la precisión disponible en el navegador.');
    return String(value);
  }) as T;
}

export function versionCommand(item: WorkItem): string {
  if (!/^[1-9]\d*$/.test(item.offer.registrationNumber) || !/^(0|[1-9]\d*)$/.test(item.version)) {
    throw new Error('Número de radicación o versión inválidos.');
  }
  return `{"version":${item.version}}`;
}
