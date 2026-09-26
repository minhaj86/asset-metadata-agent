import { useState } from 'react'
import { CheckCircle2, ChevronDown, Circle, CircleSlash, GitFork, Loader2, XCircle } from 'lucide-react'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { KindBadge, Pill } from '@/components/status/StatusBadges'
import { cn } from '@/lib/utils'
import type { StageStatus, WorkflowRun, WorkflowStage } from '@/types/api'
import { formatDateTime, formatDuration, formatNumber } from '@/utils/format'

const STATUS: Record<StageStatus, { label: string; icon: typeof Circle; className: string; spin?: boolean }> = {
  WAITING: { label: 'Waiting', icon: Circle, className: 'text-muted-foreground' },
  RUNNING: { label: 'Running', icon: Loader2, className: 'text-sky-600', spin: true },
  COMPLETED: { label: 'Completed', icon: CheckCircle2, className: 'text-emerald-600' },
  FAILED: { label: 'Failed', icon: XCircle, className: 'text-red-600' },
  SKIPPED: { label: 'Skipped', icon: CircleSlash, className: 'text-muted-foreground' },
}

interface StageItemProps {
  stage: WorkflowStage
  model: string | null
}

function StageItem({ stage, model }: StageItemProps) {
  const [open, setOpen] = useState(false)
  const status = STATUS[stage.status]
  const Icon = status.icon
  const hasTokens = stage.inputTokens !== null || stage.outputTokens !== null
  return (
    <Collapsible open={open} onOpenChange={setOpen} asChild>
      <li
        data-status={stage.status}
        className={cn(
          'rounded-md border bg-card',
          stage.status === 'RUNNING' && 'border-sky-300 ring-1 ring-sky-200 dark:border-sky-800 dark:ring-sky-900',
          stage.status === 'FAILED' && 'border-red-300 dark:border-red-900',
          (stage.status === 'SKIPPED' || stage.status === 'WAITING') && 'bg-muted/20',
        )}
      >
        <CollapsibleTrigger className="flex w-full items-start gap-3 rounded-md px-3 py-2.5 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <Icon aria-hidden className={cn('mt-0.5 size-4 shrink-0', status.className, status.spin && 'animate-spin')} />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="text-sm font-medium">{stage.name}</span>
              <KindBadge kind={stage.kind} />
              {stage.conditional && <Pill tone="muted">Conditional</Pill>}
              {stage.attempts > 1 && <Pill tone="warning">Attempt {stage.attempts}</Pill>}
            </div>
            <span className="text-xs text-muted-foreground">
              <span className={cn('font-medium', status.className)}>{status.label}</span>
              {stage.durationMillis !== null && ` · ${formatDuration(stage.durationMillis)}`}
              {stage.toolCalls.length > 0 && ` · ${stage.toolCalls.length} tool call(s)`}
              {stage.detail && ` · ${stage.detail}`}
            </span>
          </div>
          <ChevronDown aria-hidden className={cn('mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform', open && 'rotate-180')} />
          <span className="sr-only">{open ? 'Hide' : 'Show'} details of {stage.name}</span>
        </CollapsibleTrigger>
        <CollapsibleContent className="border-t px-3 py-2.5 text-xs">
          <dl className="grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5">
            {stage.description && (
              <>
                <dt className="text-muted-foreground">What it does</dt>
                <dd>{stage.description}</dd>
              </>
            )}
            {stage.kind === 'AI_AGENT' && (
              <>
                <dt className="text-muted-foreground">Model</dt>
                <dd className="font-mono">{model ?? '—'}</dd>
              </>
            )}
            <dt className="text-muted-foreground">Tool calls</dt>
            <dd>
              {stage.toolCalls.length ? (
                <ul className="space-y-0.5 font-mono">
                  {stage.toolCalls.map((tool, index) => (
                    <li key={`${tool}-${index}`}>{tool}</li>
                  ))}
                </ul>
              ) : (
                'None'
              )}
            </dd>
            {hasTokens && (
              <>
                <dt className="text-muted-foreground">Tokens</dt>
                <dd className="tabular">
                  {formatNumber(stage.inputTokens)} input · {formatNumber(stage.outputTokens)} output
                </dd>
              </>
            )}
            <dt className="text-muted-foreground">Started</dt>
            <dd>{formatDateTime(stage.startedAt)}</dd>
            <dt className="text-muted-foreground">Outcome</dt>
            <dd>{stage.detail ?? status.label}</dd>
          </dl>
          <p className="mt-2 text-[11px] text-muted-foreground">
            Only safe outcome summaries are shown; model prompts and reasoning are not exposed.
          </p>
        </CollapsibleContent>
      </li>
    </Collapsible>
  )
}

type Block = { kind: 'single'; stage: WorkflowStage } | { kind: 'parallel'; group: string; stages: WorkflowStage[] }

function toBlocks(stages: WorkflowStage[]): Block[] {
  const blocks: Block[] = []
  stages.forEach((stage) => {
    const last = blocks.at(-1)
    if (stage.parallelGroup && last?.kind === 'parallel' && last.group === stage.parallelGroup) {
      last.stages.push(stage)
    } else if (stage.parallelGroup) {
      blocks.push({ kind: 'parallel', group: stage.parallelGroup, stages: [stage] })
    } else {
      blocks.push({ kind: 'single', stage })
    }
  })
  return blocks
}

/**
 * Agentic workflow as an ordered timeline; parallel stages (Taxonomy + Enrichment agents) are shown side by side.
 * Every status comes from the backend run snapshot.
 */
export function WorkflowTimeline({ run }: { run: Pick<WorkflowRun, 'stages' | 'aiModel' | 'currentStage' | 'status'> }) {
  return (
    <div className="flex flex-col gap-2">
      <p className="sr-only" aria-live="polite">
        {run.currentStage ? `Running: ${run.currentStage}` : `Workflow ${run.status.toLowerCase()}`}
      </p>
      <ol className="flex flex-col gap-2" aria-label="Workflow stages">
        {toBlocks(run.stages).map((block) =>
          block.kind === 'single' ? (
            <StageItem key={block.stage.id} stage={block.stage} model={run.aiModel} />
          ) : (
            <li key={block.group} className="rounded-lg border border-dashed p-2">
              <span className="mb-2 flex items-center gap-1.5 px-1 text-xs font-medium text-muted-foreground">
                <GitFork aria-hidden className="size-3.5" />
                Runs in parallel
              </span>
              <ol className="grid items-start gap-2 md:grid-cols-2">
                {block.stages.map((stage) => (
                  <StageItem key={stage.id} stage={stage} model={run.aiModel} />
                ))}
              </ol>
            </li>
          ),
        )}
      </ol>
    </div>
  )
}
