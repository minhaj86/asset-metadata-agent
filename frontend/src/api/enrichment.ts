import type { RunStatus, StageDefinition, WorkflowRun } from '@/types/api'
import { apiFetch } from './client'

/** Starts the agentic enrichment workflow in the background (202 + tracked run). */
export function startEnrichment(assetId: string): Promise<WorkflowRun> {
  return apiFetch(`/api/assets/${assetId}/enrich`, { method: 'POST', headers: { Prefer: 'respond-async' } })
}

/** Bulk enrichment only creates proposals; backend policy decides what is applied. */
export function startBulkEnrichment(assetIds: string[]): Promise<WorkflowRun[]> {
  return apiFetch('/api/assets/enrich', { method: 'POST', body: { assetIds } })
}

export function fetchWorkflow(workflowId: string, signal?: AbortSignal): Promise<WorkflowRun> {
  return apiFetch(`/api/workflows/${workflowId}`, { signal })
}

export interface WorkflowListParams {
  assetId?: string
  status?: RunStatus
  limit?: number
}

export function fetchWorkflows(params: WorkflowListParams, signal?: AbortSignal): Promise<WorkflowRun[]> {
  return apiFetch('/api/workflows', { query: { ...params }, signal })
}

export function fetchWorkflowPlan(signal?: AbortSignal): Promise<StageDefinition[]> {
  return apiFetch('/api/workflows/plan', { signal })
}

export function isTerminal(status: RunStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}

/**
 * Subscribes to the run's server-sent events (a full snapshot per stage change). Returns an unsubscribe function,
 * or `null` when the browser has no EventSource support (callers then poll instead).
 */
export function subscribeToWorkflow(
  workflowId: string,
  onSnapshot: (run: WorkflowRun) => void,
  onError: () => void,
): (() => void) | null {
  if (typeof EventSource === 'undefined') {
    return null
  }
  const source = new EventSource(`/api/workflows/${workflowId}/events`)
  source.addEventListener('workflow', (event) => {
    const run = JSON.parse((event as MessageEvent<string>).data) as WorkflowRun
    onSnapshot(run)
    if (isTerminal(run.status)) {
      source.close()
    }
  })
  source.onerror = () => {
    source.close()
    onError()
  }
  return () => source.close()
}
