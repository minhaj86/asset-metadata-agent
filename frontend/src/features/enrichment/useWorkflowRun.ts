import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchWorkflow, isTerminal, subscribeToWorkflow } from '@/api/enrichment'
import { queryKeys } from '@/api/queryKeys'
import { invalidateAsset } from '@/features/assets/assetQueries'
import type { WorkflowRun } from '@/types/api'

const POLL_INTERVAL_MS = 1000

/**
 * Live state of one workflow run: server-sent events push a full snapshot on every stage change; when the stream is
 * unavailable the hook polls instead. Only real backend stage states are shown (no simulated progress).
 */
export function useWorkflowRun(workflowId: string | undefined) {
  const client = useQueryClient()
  const [streamFailed, setStreamFailed] = useState(typeof EventSource === 'undefined')

  const query = useQuery({
    queryKey: queryKeys.workflow(workflowId ?? ''),
    queryFn: ({ signal }) => fetchWorkflow(workflowId!, signal),
    enabled: Boolean(workflowId),
    refetchInterval: (current) => {
      const run = current.state.data
      return run && !isTerminal(run.status) && streamFailed ? POLL_INTERVAL_MS : false
    },
  })

  // Keep run lists (run history, recent runs) consistent with the live snapshot, whether it came from SSE or polling.
  const snapshot = query.data
  useEffect(() => {
    if (!snapshot) {
      return
    }
    client.setQueriesData<WorkflowRun[]>({ queryKey: queryKeys.workflows }, (runs) =>
      Array.isArray(runs) ? runs.map((item) => (item.workflowId === snapshot.workflowId ? snapshot : item)) : runs,
    )
  }, [snapshot, client])

  useEffect(() => {
    if (!workflowId) {
      return
    }
    const unsubscribe = subscribeToWorkflow(
      workflowId,
      (run: WorkflowRun) => {
        const previous = client.getQueryData<WorkflowRun>(queryKeys.workflow(workflowId))
        client.setQueryData(queryKeys.workflow(workflowId), run)
        if (isTerminal(run.status) && (!previous || !isTerminal(previous.status))) {
          void invalidateAsset(client, run.assetId)
        }
      },
      () => setStreamFailed(true),
    )
    return () => unsubscribe?.()
  }, [workflowId, client])

  return { ...query, live: !streamFailed }
}
