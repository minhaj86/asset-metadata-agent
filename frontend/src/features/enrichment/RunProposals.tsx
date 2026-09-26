import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { Section } from '@/components/PageHeader'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { ErrorState } from '@/components/States'
import { fetchProposals } from '@/api/approvals'
import { queryKeys } from '@/api/queryKeys'
import { ProposalReviewCard } from '@/features/approvals/ProposalReviewCard'
import type { Proposal } from '@/types/api'
import { agentName } from '@/utils/format'

function CompactList({ proposals }: { proposals: Proposal[] }) {
  return (
    <ul className="divide-y">
      {proposals.map((proposal) => (
        <li key={proposal.id} className="grid gap-1 px-4 py-2 text-sm sm:grid-cols-[12rem_1fr_auto] sm:items-center sm:gap-3">
          <span className="font-medium">{proposal.fieldInfo.displayName}</span>
          <MetadataValueView value={proposal.appliedValue ?? proposal.proposedValue} dataType={proposal.fieldInfo.dataType} clamp />
          <span className="flex items-center gap-2 text-xs text-muted-foreground">
            <ConfidenceIndicator confidence={proposal.confidence} variant="compact" />
            {agentName(proposal.proposedBy)}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** Proposals created by one run, grouped by the route the backend policy decided. */
export function RunProposals({ workflowId, assetId }: { workflowId: string; assetId: string }) {
  const params = { workflowRunId: workflowId, status: 'ALL' as const, limit: 200 }
  const proposals = useQuery({
    queryKey: queryKeys.proposalList(params),
    queryFn: ({ signal }) => fetchProposals(params, signal),
  })
  if (proposals.isPending) {
    return <Skeleton className="h-32 w-full" aria-label="Loading run proposals" />
  }
  if (proposals.isError) {
    return <ErrorState error={proposals.error} onRetry={() => void proposals.refetch()} />
  }
  const autoApplied = proposals.data.filter((proposal) => proposal.route === 'AUTO_APPROVE')
  const awaiting = proposals.data.filter((proposal) => proposal.status === 'PROPOSED')
  const decided = proposals.data.filter((proposal) => proposal.route !== 'AUTO_APPROVE' && proposal.status !== 'PROPOSED')

  return (
    <div className="flex flex-col gap-4">
      {awaiting.length > 0 && (
        <section aria-label="Suggestions awaiting review" className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-sm font-semibold">Awaiting your review ({awaiting.length})</h2>
            <Link to={`/assets/${assetId}?tab=suggestions`} className="text-xs text-primary hover:underline">
              Review on asset page
            </Link>
          </div>
          {awaiting.map((proposal) => (
            <ProposalReviewCard key={proposal.id} proposal={proposal} />
          ))}
        </section>
      )}
      <Section
        title={`Applied automatically by backend policy (${autoApplied.length})`}
        description="Safe fields whose confidence reached the auto-approval threshold; values are marked AI-generated and audited"
        contentClassName="p-0"
      >
        {autoApplied.length ? <CompactList proposals={autoApplied} /> : <p className="px-4 py-3 text-sm text-muted-foreground">Nothing was applied automatically.</p>}
      </Section>
      {decided.length > 0 && (
        <Section title={`Decided or rejected (${decided.length})`} contentClassName="p-0">
          <CompactList proposals={decided} />
        </Section>
      )}
    </div>
  )
}
