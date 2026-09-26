import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { startBulkEnrichment, startEnrichment } from '@/api/enrichment'
import { errorMessage, errorTitle } from '@/api/errors'
import { queryKeys } from '@/api/queryKeys'
import { invalidateAsset } from '@/features/assets/assetQueries'

/** Starts the agentic workflow in the background and opens its live view. */
export function useStartEnrichment() {
  const client = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: (assetId: string) => startEnrichment(assetId),
    onSuccess: (run) => {
      client.setQueryData(queryKeys.workflow(run.workflowId), run)
      void invalidateAsset(client, run.assetId)
      toast.success('AI enrichment started', { description: `${run.externalAssetId ?? 'Asset'} · workflow is running` })
      navigate(`/assets/${run.assetId}/enrich?run=${run.workflowId}`)
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}

export function useStartBulkEnrichment() {
  const client = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: (assetIds: string[]) => startBulkEnrichment(assetIds),
    onSuccess: (runs) => {
      void invalidateAsset(client)
      toast.success(`Started ${runs.length} enrichment workflow${runs.length === 1 ? '' : 's'}`, {
        description: 'Proposals will be routed by backend policy; sensitive fields wait for review.',
        action: { label: 'View progress', onClick: () => navigate('/enrichment') },
      })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}
