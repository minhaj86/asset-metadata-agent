import type { AuditAction, AuditEvent, MetadataSource, Page } from '@/types/api'
import { apiFetch } from './client'

export interface AuditParams {
  /** substring of the external asset id */
  asset?: string
  assetId?: string
  field?: string
  source?: MetadataSource
  actor?: string
  actorType?: 'HUMAN' | 'AI' | 'SYSTEM'
  action?: AuditAction
  from?: string
  to?: string
  page?: number
  size?: number
}

export function fetchAuditEvents(params: AuditParams, signal?: AbortSignal): Promise<Page<AuditEvent>> {
  return apiFetch('/api/audit/events', { query: { ...params }, signal })
}
