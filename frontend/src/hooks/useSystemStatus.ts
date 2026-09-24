import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/api/queryKeys'
import { fetchSystemStatus } from '@/api/system'
import type { SystemStatus } from '@/types/api'

/**
 * Capability status (AI, vector search, DAM) and governance thresholds. Polled so that the top bar reflects
 * outages; normal metadata management never depends on it.
 */
export function useSystemStatus() {
  return useQuery({
    queryKey: queryKeys.systemStatus,
    queryFn: ({ signal }) => fetchSystemStatus(signal),
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

export function isAiAvailable(status: SystemStatus | undefined): boolean {
  return status?.ai.state === 'AVAILABLE'
}

export function aiUnavailableReason(status: SystemStatus | undefined): string {
  if (!status) {
    return 'AI status unknown'
  }
  if (status.ai.state === 'DISABLED') {
    return 'AI is disabled in this environment.'
  }
  return status.ai.detail ?? 'The AI provider is unavailable.'
}
