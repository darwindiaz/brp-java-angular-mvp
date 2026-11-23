import type { WorkItem } from '../../../core/offers/offer-work-item';

export type { WorkItem } from '../../../core/offers/offer-work-item';

export type AnalysisAction = 'take' | 'escalate' | 'reject';
export type AnalysisFilter = 'all' | 'pending' | 'assigned' | 'returned';

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
