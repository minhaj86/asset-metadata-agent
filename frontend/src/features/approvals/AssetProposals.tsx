import { useQuery } from '@tanstack/react-query'
import { Lightbulb } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { Section } from '@/components/PageHeader'
import { EmptyState, ErrorState } from '@/components/States'
import { MetadataStatusBadge, RouteBadge } from '@/components/status/StatusBadges'
import { fetchProposals } from '@/api/approvals'
import { queryKeys } from '@/api/queryKeys'
import { RunEnrichmentButton } from '@/features/enrichment/RunEnrichmentButton'
import type { CatalogItem, Proposal } from '@/types/api'
import { agentName, formatRelative } from '@/utils/format'
import { ProposalReviewCard } from './ProposalReviewCard'

function sortForReview(proposals: Proposal[]): Proposal[] {
  const weight = (proposal: Proposal) =>
    (proposal.policy.requiresHumanApproval ? 0 : 10) + (proposal.route === 'HUMAN_REVIEW' ? 0 : 5)
  return [...proposals].sort((a, b) => weight(a) - weight(b) || b.confidence - a.confidence)
}

/** Open AI suggestions of one asset (review cards) plus the history of decided proposals. */
export function AssetProposals({ asset }: { asset: CatalogItem }) {
  const params = { assetId: asset.id, status: 'ALL' as const, limit: 300 }
  const proposals = useQuery({
    queryKey: queryKeys.proposalList(params),
    queryFn: ({ signal }) => fetchProposals(params, signal),
  })

  if (proposals.isPending) {
    return <Skeleton className="h-48 w-full" aria-label="Loading AI suggestions" />
  }
  if (proposals.isError) {
    return <ErrorState error={proposals.error} onRetry={() => void proposals.refetch()} />
  }
  const open = sortForReview(proposals.data.filter((proposal) => proposal.status === 'PROPOSED'))
  const decided = proposals.data.filter((proposal) => proposal.status !== 'PROPOSED')

  return (
    <div className="flex flex-col gap-4">
      {open.length === 0 ? (
        <EmptyState
          icon={Lightbulb}
          title="No AI suggestions awaiting review"
          description="Run the enrichment workflow to get AI suggestions. Backend policy applies safe values automatically; everything else appears here for review."
          action={<RunEnrichmentButton asset={asset} />}
        />
      ) : (
        <section aria-label="Open AI suggestions" className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            {open.length} suggestion(s) await a decision. Sensitive fields are listed first; approving applies the value
            and records it in the audit history.
          </p>
          {open.map((proposal) => (
            <ProposalReviewCard key={proposal.id} proposal={proposal} />
          ))}
        </section>
      )}

      {decided.length > 0 && (
        <Section title={`Decided suggestions (${decided.length})`} description="Auto-applied by policy, approved, rejected or superseded" contentClassName="p-0">
          <div className="overflow-x-auto">
            <Table className="text-[13px]">
              <TableHeader className="bg-muted/30">
                <TableRow>
                  <TableHead>Field</TableHead>
                  <TableHead>Proposed value</TableHead>
                  <TableHead>Confidence</TableHead>
                  <TableHead>Route</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Decided by</TableHead>
                  <TableHead>Agent</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {decided.map((proposal) => (
                  <TableRow key={proposal.id}>
                    <TableCell className="font-medium">{proposal.fieldInfo.displayName}</TableCell>
                    <TableCell className="max-w-80 whitespace-normal">
                      <MetadataValueView value={proposal.appliedValue ?? proposal.proposedValue} dataType={proposal.fieldInfo.dataType} clamp />
                    </TableCell>
                    <TableCell>
                      <ConfidenceIndicator confidence={proposal.confidence} variant="compact" />
                    </TableCell>
                    <TableCell>
                      <RouteBadge route={proposal.route} />
                    </TableCell>
                    <TableCell>
                      <MetadataStatusBadge status={proposal.status} />
                    </TableCell>
                    <TableCell className="text-xs">
                      {proposal.decidedBy?.startsWith('system:') ? 'Approval policy' : proposal.decidedBy}
                      <div className="text-muted-foreground">{formatRelative(proposal.decidedAt)}</div>
                    </TableCell>
                    <TableCell className="text-xs">{agentName(proposal.proposedBy)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Section>
      )}
    </div>
  )
}
