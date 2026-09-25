import type { SystemStatus } from '@/types/api'
import { apiFetch } from './client'

export function fetchSystemStatus(signal?: AbortSignal): Promise<SystemStatus> {
  return apiFetch('/api/system/status', { signal })
}
