import { Link } from 'react-router'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { RunStatusBadge } from '@/components/status/StatusBadges'
import type { WorkflowRun } from '@/types/api'
import { formatDuration, formatRelative } from '@/utils/format'

function outcome(run: WorkflowRun): string {
  if (run.error) {
    return run.error.message
  }
  if (run.summary) {
    return `${run.summary.autoApplied} applied · ${run.summary.pendingReview} for review · ${run.summary.lowConfidenceSuggestions} low-confidence`
  }
  return run.currentStage ? `${run.currentStage} executing` : 'Waiting for a free slot'
}

export function WorkflowRunsTable({ runs }: { runs: WorkflowRun[] }) {
  return (
    <div className="overflow-x-auto">
      <Table className="text-[13px]">
        <TableHeader className="bg-muted/30">
          <TableRow>
            <TableHead>Asset</TableHead>
            <TableHead>Workflow</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Stage / outcome</TableHead>
            <TableHead>Requested by</TableHead>
            <TableHead>Started</TableHead>
            <TableHead>Duration</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => (
            <TableRow key={run.workflowId}>
              <TableCell>
                <Link to={`/assets/${run.assetId}`} className="font-mono text-xs font-medium text-primary hover:underline">
                  {run.externalAssetId ?? run.assetId.slice(0, 8)}
                </Link>
              </TableCell>
              <TableCell>
                <Link to={`/assets/${run.assetId}/enrich?run=${run.workflowId}`} className="hover:underline">
                  Metadata enrichment
                </Link>
              </TableCell>
              <TableCell>
                <RunStatusBadge status={run.status} />
              </TableCell>
              <TableCell className="max-w-96 whitespace-normal text-muted-foreground">{outcome(run)}</TableCell>
              <TableCell className="text-xs">{run.requestedBy}</TableCell>
              <TableCell className="text-xs whitespace-nowrap text-muted-foreground">{formatRelative(run.createdAt)}</TableCell>
              <TableCell className="text-xs tabular">{formatDuration(run.durationMillis)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
