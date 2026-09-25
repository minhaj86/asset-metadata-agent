import type { Dashboard } from '@/types/api'
import { apiFetch } from './client'

export function fetchDashboard(signal?: AbortSignal): Promise<Dashboard> {
  return apiFetch('/api/dashboard', { signal })
}
