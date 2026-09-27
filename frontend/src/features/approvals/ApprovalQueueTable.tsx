import { Link } from 'react-router'
import { ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { MetadataStatusBadge, PolicyBadge, RiskBadge } from '@/components/status/StatusBadges'
import type { Proposal } from '@/types/api'
import { agentName, formatRelative } from '@/utils/format'

interface ApprovalQueueTableProps {
  proposals: Proposal[]
  selected: Set<string>
  onToggle: (proposalId: string) => void
  onReview: (proposal: Proposal) => void
}

/**
 * Review queue. A proposal can only be selected for bulk approval when the backend policy marks it
 * bulk-approvable; the frontend never decides which fields are safe.
 */
export function ApprovalQueueTable({ proposals, selected, onToggle, onReview }: ApprovalQueueTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border">
      <Table className="text-[13px]">
        <TableHeader className="bg-muted/40">
          <TableRow>
            <TableHead className="w-9">
              <span className="sr-only">Select for bulk approval</span>
            </TableHead>
            <TableHead>Asset</TableHead>
            <TableHead>Field</TableHead>
            <TableHead>Current</TableHead>
            <TableHead>Proposed</TableHead>
            <TableHead>Confidence</TableHead>
            <TableHead>Policy</TableHead>
            <TableHead>Reason</TableHead>
            <TableHead>Agent</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">
              <span className="sr-only">Action</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {proposals.map((proposal) => {
            const eligible = proposal.status === 'PROPOSED' && proposal.policy.bulkApprovable
            return (
              <TableRow key={proposal.id} className="align-top" data-state={selected.has(proposal.id) ? 'selected' : undefined}>
                <TableCell>
                  {eligible ? (
                    <Checkbox
                      aria-label={`Select ${proposal.fieldInfo.displayName} of ${proposal.externalAssetId} for bulk approval`}
                      checked={selected.has(proposal.id)}
                      onCheckedChange={() => onToggle(proposal.id)}
                    />
                  ) : (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span tabIndex={0} className="inline-flex" aria-label="Requires individual review">
                          <Checkbox disabled aria-hidden tabIndex={-1} />
                        </span>
                      </TooltipTrigger>
                      <TooltipContent className="max-w-64">
                        Requires individual review{proposal.policy.riskFactors.length ? `: ${proposal.policy.riskFactors.join('; ')}` : ''}
                      </TooltipContent>
                    </Tooltip>
                  )}
                </TableCell>
                <TableCell>
                  <Link to={`/assets/${proposal.assetId}?tab=suggestions`} className="font-mono text-xs font-medium text-primary hover:underline">
                    {proposal.externalAssetId ?? proposal.assetId.slice(0, 8)}
                  </Link>
                </TableCell>
                <TableCell className="font-medium">{proposal.fieldInfo.displayName}</TableCell>
                <TableCell className="max-w-48 whitespace-normal text-muted-foreground">
                  <MetadataValueView value={proposal.currentValue} dataType={proposal.fieldInfo.dataType} clamp />
                </TableCell>
                <TableCell className="max-w-64 whitespace-normal">
                  <MetadataValueView value={proposal.proposedValue} dataType={proposal.fieldInfo.dataType} clamp />
                </TableCell>
                <TableCell>
                  <ConfidenceIndicator confidence={proposal.confidence} variant="compact" />
                </TableCell>
                <TableCell>
                  <div className="flex flex-col items-start gap-1">
                    <PolicyBadge policy={proposal.policy} />
                    <RiskBadge level={proposal.policy.riskLevel} />
                    {proposal.status !== 'PROPOSED' && <MetadataStatusBadge status={proposal.status} />}
                  </div>
                </TableCell>
                <TableCell className="max-w-56 whitespace-normal text-xs text-muted-foreground">{proposal.routeReason}</TableCell>
                <TableCell className="text-xs">{agentName(proposal.proposedBy)}</TableCell>
                <TableCell className="text-xs whitespace-nowrap text-muted-foreground">{formatRelative(proposal.createdAt)}</TableCell>
                <TableCell className="text-right">
                  <Button size="sm" variant="outline" onClick={() => onReview(proposal)}>
                    <ShieldCheck aria-hidden />
                    Review
                  </Button>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
