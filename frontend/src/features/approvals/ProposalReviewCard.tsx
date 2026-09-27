import { useState } from 'react'
import { Link } from 'react-router'
import { Check, CheckCircle2, Loader2, Pencil, Sparkles, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataStatusBadge, Pill, PolicyBadge, RiskBadge, RouteBadge } from '@/components/status/StatusBadges'
import { cn } from '@/lib/utils'
import type { Proposal } from '@/types/api'
import { agentName, formatDateTime, formatRelative } from '@/utils/format'
import { EditApproveDialog } from './EditApproveDialog'
import { ProposalComparison } from './ProposalComparison'
import { RejectDialog } from './RejectDialog'
import { useApproveProposal } from './useProposalMutations'

interface ProposalReviewCardProps {
  proposal: Proposal
  /** show which asset the proposal belongs to (queue context) */
  showAsset?: boolean
  onDecided?: () => void
  className?: string
}

/**
 * Makes an AI proposal reviewable: what exists, what the AI proposes, why, the evidence, how confident the model
 * is and whether backend policy requires a human decision. Language stays "suggestion", never "fix".
 */
export function ProposalReviewCard({ proposal, showAsset, onDecided, className }: ProposalReviewCardProps) {
  const approve = useApproveProposal()
  const [rejecting, setRejecting] = useState(false)
  const [editing, setEditing] = useState(false)
  const open = proposal.status === 'PROPOSED'
  const headingId = `proposal-${proposal.id}-title`

  return (
    <article aria-labelledby={headingId} className={cn('flex flex-col gap-3 rounded-lg border bg-card p-4', className)}>
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 id={headingId} className="flex items-center gap-2 text-sm font-semibold tracking-wide uppercase">
            <Sparkles aria-hidden className="size-4 text-violet-600" />
            {proposal.fieldInfo.displayName}
            <span className="font-mono text-[11px] font-normal tracking-normal text-muted-foreground normal-case">{proposal.field}</span>
          </h3>
          {showAsset && (
            <Link to={`/assets/${proposal.assetId}?tab=suggestions`} className="text-xs text-primary hover:underline">
              {proposal.externalAssetId ?? proposal.assetId} · {proposal.assetFilename}
            </Link>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <Pill tone="ai" icon={Sparkles}>
            AI suggestion
          </Pill>
          <PolicyBadge policy={proposal.policy} />
          <RiskBadge level={proposal.policy.riskLevel} />
        </div>
      </header>

      <ProposalComparison
        current={proposal.currentValue}
        proposed={proposal.proposedValue}
        applied={proposal.appliedValue}
        dataType={proposal.fieldInfo.dataType}
      />

      <dl className="grid gap-x-6 gap-y-2 text-sm md:grid-cols-[8rem_1fr]">
        <dt className="text-muted-foreground">Confidence</dt>
        <dd>
          <ConfidenceIndicator confidence={proposal.confidence} />
        </dd>
        <dt className="text-muted-foreground">Why</dt>
        <dd>{proposal.explanation ?? <span className="text-muted-foreground">No explanation provided</span>}</dd>
        <dt className="text-muted-foreground">Evidence</dt>
        <dd>
          {proposal.evidence.length > 0 ? (
            <ul className="space-y-0.5">
              {proposal.evidence.map((evidence) => (
                <li key={evidence} className="flex items-start gap-1.5">
                  <CheckCircle2 aria-hidden className="mt-0.5 size-3.5 shrink-0 text-emerald-600" />
                  <span>{evidence}</span>
                </li>
              ))}
            </ul>
          ) : (
            <span className="text-muted-foreground">No evidence listed — treat with care</span>
          )}
        </dd>
        <dt className="text-muted-foreground">Agent</dt>
        <dd className="flex flex-wrap items-center gap-x-2">
          <span>{agentName(proposal.proposedBy)}</span>
          {proposal.aiModel && <span className="font-mono text-xs text-muted-foreground">{proposal.aiModel}</span>}
          {proposal.workflowRunId && (
            <Link to={`/assets/${proposal.assetId}/enrich?run=${proposal.workflowRunId}`} className="text-xs text-primary hover:underline">
              View workflow run
            </Link>
          )}
        </dd>
        <dt className="text-muted-foreground">Policy</dt>
        <dd className="space-y-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <RouteBadge route={proposal.route} />
            {proposal.routeReason && <span className="text-muted-foreground">{proposal.routeReason}</span>}
          </div>
          {proposal.policy.riskFactors.length > 0 && (
            <ul className="list-disc pl-4 text-xs text-muted-foreground">
              {proposal.policy.riskFactors.map((factor) => (
                <li key={factor}>{factor}</li>
              ))}
            </ul>
          )}
        </dd>
      </dl>

      <footer className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
        <span className="text-xs text-muted-foreground" title={formatDateTime(proposal.createdAt)}>
          Proposed {formatRelative(proposal.createdAt)}
        </span>
        {open ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setRejecting(true)}>
              <X aria-hidden />
              Reject
            </Button>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit &amp; approve
            </Button>
            <Button size="sm" onClick={() => approve.mutate({ proposal }, { onSuccess: onDecided })} disabled={approve.isPending}>
              {approve.isPending ? <Loader2 aria-hidden className="animate-spin" /> : <Check aria-hidden />}
              Approve
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <MetadataStatusBadge status={proposal.status} />
            <span>
              {proposal.decidedBy?.startsWith('system:') ? 'by approval policy' : `by ${proposal.decidedBy ?? '—'}`} ·{' '}
              {formatDateTime(proposal.decidedAt)}
            </span>
            {proposal.decisionComment && <span>“{proposal.decisionComment}”</span>}
          </div>
        )}
      </footer>
      {open && (
        <>
          <RejectDialog proposal={proposal} open={rejecting} onOpenChange={setRejecting} onDone={onDecided} />
          <EditApproveDialog proposal={proposal} open={editing} onOpenChange={setEditing} onDone={onDecided} />
        </>
      )}
    </article>
  )
}
