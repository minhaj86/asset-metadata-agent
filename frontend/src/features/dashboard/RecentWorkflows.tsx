import { Link } from 'react-router'
import { Bot } from 'lucide-react'
import { EmptyState } from '@/components/States'
import { RunStatusBadge } from '@/components/status/StatusBadges'
import type { WorkflowRun } from '@/types/api'
import { formatRelative } from '@/utils/format'

function describe(run: WorkflowRun): string {
  if (run.status === 'FAILED') {
    return run.error?.message ?? 'Failed'
  }
  if (run.status === 'COMPLETED' && run.summary) {
    const total = run.summary.autoApplied + run.summary.pendingReview + run.summary.lowConfidenceSuggestions + run.summary.rejectedByPolicy
    return `${total} proposals · ${run.summary.autoApplied} applied · ${run.summary.pendingReview} require review`
  }
  return run.currentStage ? `${run.currentStage} executing` : 'Queued'
}

export function RecentWorkflows({ runs }: { runs: WorkflowRun[] }) {
  if (runs.length === 0) {
    return <EmptyState icon={Bot} title="No AI workflow activity yet" />
  }
  return (
    <ul className="divide-y">
      {runs.map((run) => (
        <li key={run.workflowId}>
          <Link
            to={`/assets/${run.assetId}/enrich?run=${run.workflowId}`}
            className="flex flex-col gap-1 px-4 py-2.5 outline-none hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
          >
            <span className="flex items-center justify-between gap-2">
              <span className="font-mono text-xs font-medium">{run.externalAssetId ?? run.assetId.slice(0, 8)}</span>
              <RunStatusBadge status={run.status} />
            </span>
            <span className="text-sm">Metadata enrichment</span>
            <span className="flex justify-between gap-2 text-xs text-muted-foreground">
              <span className="truncate">{describe(run)}</span>
              <span className="shrink-0">{formatRelative(run.createdAt)}</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
