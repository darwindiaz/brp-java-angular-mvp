export type AnalysisAction = 'take' | 'escalate' | 'reject';
export type AnalysisFilter = 'all' | 'pending' | 'assigned' | 'returned';
export type OfferStatus = 'RADICADO' | 'EN_PROCESO' | 'ESCALADO' | 'APROBADO' | 'RECHAZADO' | 'DESISTIDO';

export interface AnalysisOffer {
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
  offer: AnalysisOffer;
  analystId: string | null;
  version: string;
  modifiedAt: string | null;
  escalatedAt: string | null;
  returnedAt: string | null;
  closedAt: string | null;
}

export function matchesAnalysisFilter(item: WorkItem, filter: AnalysisFilter): boolean {
  switch (filter) {
    case 'all': return true;
    case 'pending': return item.offer.status === 'RADICADO';
    case 'assigned': return item.offer.status === 'EN_PROCESO';
    case 'returned': return item.offer.status === 'EN_PROCESO' && item.returnedAt !== null;
  }
}

export function availableAnalysisActions(item: WorkItem, analystId: number | undefined): readonly AnalysisAction[] {
  if (analystId === undefined) return [];
  if (item.offer.status === 'RADICADO') return ['take'];
  if (item.offer.status === 'EN_PROCESO' && item.analystId === String(analystId)) {
    return ['escalate', 'reject'];
  }
  return [];
}
