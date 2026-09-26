import type {
  AssetMetadata,
  AssetType,
  AuditEvent,
  BulkValidationItem,
  MetadataEntry,
  MetadataSchema,
  QualityReport,
  ValidationResult,
} from '@/types/api'
import { apiDownload, apiFetch, type DownloadedFile } from './client'

export function fetchMetadata(assetId: string, signal?: AbortSignal): Promise<AssetMetadata> {
  return apiFetch(`/api/assets/${assetId}/metadata`, { signal })
}

/** Manual change: validated against the schema and audited by the backend. */
export function setMetadataValue(assetId: string, field: string, value: unknown, reason?: string): Promise<MetadataEntry> {
  return apiFetch(`/api/assets/${assetId}/metadata/${encodeURIComponent(field)}`, {
    method: 'PUT',
    body: { value, reason: reason || undefined },
  })
}

export function removeMetadataValue(assetId: string, field: string, reason?: string): Promise<void> {
  return apiFetch(`/api/assets/${assetId}/metadata/${encodeURIComponent(field)}`, {
    method: 'DELETE',
    query: { reason },
  })
}

export function validateAsset(assetId: string): Promise<ValidationResult> {
  return apiFetch(`/api/assets/${assetId}/validate`, { method: 'POST' })
}

export function validateAssets(assetIds: string[]): Promise<BulkValidationItem[]> {
  return apiFetch('/api/assets/validate', { method: 'POST', body: { assetIds } })
}

/** Quality report; `includeAi` adds the semantic review of the MetadataQualityAgent (an LLM call). */
export function fetchQuality(assetId: string, includeAi: boolean, signal?: AbortSignal): Promise<QualityReport> {
  return apiFetch(`/api/assets/${assetId}/quality`, { query: { includeAi }, signal })
}

export function fetchSchema(assetType: AssetType, signal?: AbortSignal): Promise<MetadataSchema> {
  return apiFetch(`/api/metadata/schema/${assetType}`, { signal })
}

export function fetchHistory(assetId: string, field?: string, signal?: AbortSignal): Promise<AuditEvent[]> {
  return apiFetch(`/api/assets/${assetId}/metadata/history`, { query: { field, limit: 500 }, signal })
}

export function exportMetadata(assetIds: string[], format: 'csv' | 'json'): Promise<DownloadedFile> {
  return apiDownload('/api/assets/export', { body: { assetIds, format } })
}
