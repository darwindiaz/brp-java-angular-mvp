import type { OfferStatus } from '../../core/offers/offer-work-item';

export interface TrackedOffer {
  registrationNumber: string;
  brpCode: string;
  total: string;
  discount: string;
  status: OfferStatus;
  analyst: { id: string; name: string } | null;
  createdAt: string;
  modifiedAt: string | null;
  escalatedAt: string | null;
  returnedAt: string | null;
  closedAt: string | null;
}

export type AssetType = 'APARTAMENTO' | 'CASA' | 'LOCAL' | 'LOTE' | 'VEHICULO' | 'MAQUINARIA';
export type CatalogStatus = 'DISPONIBLE' | 'SIN_PRECIO' | 'PENDIENTE_TECNICO'
  | 'EN_TRAMITE_DE_VENTA' | 'SANEAMIENTO' | 'DADO_DE_BAJA';

export interface TrackingCatalogItem {
  code: string;
  assetType: AssetType;
  description: string | null;
  location: { department: string | null; municipality: string | null };
  status: CatalogStatus;
  referencePrice: string | null;
  ingestionDate: string;
}

export interface CatalogFreshness {
  latestIngestionDate: string | null;
}
