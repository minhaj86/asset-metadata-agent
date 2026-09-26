import type {
  AiStatus,
  Asset,
  AssetStatus,
  AssetType,
  CatalogItem,
  Page,
  Recommendation,
  RightsStatus,
  SimilarAsset,
} from '@/types/api'
import { apiFetch } from './client'

export type CatalogSortDirection = 'asc' | 'desc'

/** Catalog listing parameters (GET /api/catalog/assets). Filters use backend semantics. */
export interface CatalogParams {
  q?: string
  type?: AssetType
  status?: AssetStatus
  aiStatus?: AiStatus
  rightsStatus?: RightsStatus
  missingRequired?: boolean
  aiEnriched?: boolean
  completenessMin?: number
  completenessMax?: number
  qualityMin?: number
  taxonomyPath?: string
  /** field:value pairs, e.g. color:BLUE */
  filter?: string[]
  sort?: string
  direction?: CatalogSortDirection
  page?: number
  size?: number
}

export function fetchCatalog(params: CatalogParams, signal?: AbortSignal): Promise<Page<CatalogItem>> {
  return apiFetch('/api/catalog/assets', { query: { ...params }, signal })
}

export function fetchCatalogItem(assetId: string, signal?: AbortSignal): Promise<CatalogItem> {
  return apiFetch(`/api/catalog/assets/${assetId}`, { signal })
}

export function fetchAsset(assetId: string, signal?: AbortSignal): Promise<Asset> {
  return apiFetch(`/api/assets/${assetId}`, { signal })
}

export function fetchDuplicates(assetId: string, signal?: AbortSignal): Promise<SimilarAsset[]> {
  return apiFetch(`/api/assets/${assetId}/duplicates`, { signal })
}

export function fetchRecommendations(assetId: string, signal?: AbortSignal): Promise<Recommendation[]> {
  return apiFetch(`/api/assets/${assetId}/recommendations`, { signal })
}
