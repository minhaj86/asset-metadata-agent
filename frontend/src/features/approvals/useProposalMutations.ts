import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { approveProposal, bulkApproveProposals, rejectProposal } from '@/api/approvals'
import { errorMessage, errorTitle } from '@/api/errors'
import { invalidateAsset } from '@/features/assets/assetQueries'
import type { Proposal } from '@/types/api'
import { humanize } from '@/utils/format'

export function useApproveProposal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ proposal, comment, value }: { proposal: Proposal; comment?: string; value?: unknown }) =>
      approveProposal(proposal.id, { comment, value }),
    onSuccess: (approved) => {
      void invalidateAsset(client, approved.assetId)
      toast.success('Proposal approved', {
        description: `${approved.fieldInfo.displayName ?? humanize(approved.field)} applied${approved.appliedValue !== null ? ' with your edits' : ''} and audited`,
      })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}

export function useRejectProposal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ proposal, reason }: { proposal: Proposal; reason: string }) => rejectProposal(proposal.id, reason),
    onSuccess: (rejected) => {
      void invalidateAsset(client, rejected.assetId)
      toast.success('Proposal rejected', { description: `${rejected.fieldInfo.displayName} left unchanged; decision audited` })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}

/** The backend applies only proposals its policy marks bulk-approvable and reports the others as skipped. */
export function useBulkApprove() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ proposalIds, comment }: { proposalIds: string[]; comment?: string }) => bulkApproveProposals(proposalIds, comment),
    onSuccess: (result) => {
      void invalidateAsset(client)
      const detail = [`${result.approved} approved`, result.skipped && `${result.skipped} need individual review`, result.failed && `${result.failed} failed`]
        .filter(Boolean)
        .join(' · ')
      if (result.failed > 0) {
        toast.warning('Bulk approval completed with problems', { description: detail })
      } else {
        toast.success('Bulk approval completed', { description: detail })
      }
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}
