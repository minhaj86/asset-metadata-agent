import { AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { RunStatusBadge } from '@/components/status/StatusBadges'
import type { WorkflowRun } from '@/types/api'
import { formatDateTime, formatDuration } from '@/utils/format'

function Count({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="flex flex-col">
      <span className={tone ? `text-lg font-semibold tabular ${tone}` : 'text-lg font-semibold tabular'}>{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  )
}

/** Run-level status, timing and the deterministic outcome summary recorded by the backend. */
export function WorkflowRunSummary({ run }: { run: WorkflowRun }) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <RunStatusBadge status={run.status} />
        {run.currentStage && <span className="text-sky-700 dark:text-sky-300">Running: {run.currentStage}</span>}
        <span className="text-muted-foreground">Started {formatDateTime(run.startedAt ?? run.createdAt)}</span>
        {run.durationMillis !== null && <span className="text-muted-foreground">Duration {formatDuration(run.durationMillis)}</span>}
        <span className="text-muted-foreground">Requested by {run.requestedBy}</span>
        {run.aiModel && <span className="font-mono text-xs text-muted-foreground">{run.aiModel}</span>}
      </div>
      {run.summary && (
        <div className="grid grid-cols-2 gap-4 border-t pt-3 sm:grid-cols-5">
          <Count label="Applied automatically" value={run.summary.autoApplied} tone="text-emerald-700 dark:text-emerald-300" />
          <Count label="Awaiting review" value={run.summary.pendingReview} tone="text-amber-800 dark:text-amber-300" />
          <Count label="Low-confidence suggestions" value={run.summary.lowConfidenceSuggestions} />
          <Count label="Rejected by policy" value={run.summary.rejectedByPolicy} tone="text-red-700 dark:text-red-300" />
          <Count label="Unchanged" value={run.summary.unchanged} />
        </div>
      )}
      {run.error && (
        <Alert variant="destructive">
          <AlertTriangle aria-hidden />
          <AlertTitle>Workflow failed</AlertTitle>
          <AlertDescription>
            {run.error.message} No metadata was changed by this run; the asset can still be edited manually.
          </AlertDescription>
        </Alert>
      )}
      {run.warnings.length > 0 && (
        <ul className="space-y-1 border-t pt-3 text-xs text-amber-800 dark:text-amber-300">
          {run.warnings.map((warning) => (
            <li key={warning} className="flex items-start gap-1.5">
              <AlertTriangle aria-hidden className="mt-0.5 size-3 shrink-0" />
              {warning}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
