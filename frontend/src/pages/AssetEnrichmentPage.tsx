import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { ArrowLeft } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader, Section } from '@/components/PageHeader'
import { ErrorState } from '@/components/States'
import { RunStatusBadge } from '@/components/status/StatusBadges'
import { fetchWorkflowPlan, fetchWorkflows, isTerminal } from '@/api/enrichment'
import { queryKeys } from '@/api/queryKeys'
import { useCatalogItem } from '@/features/assets/assetQueries'
import { RunEnrichmentButton } from '@/features/enrichment/RunEnrichmentButton'
import { RunProposals } from '@/features/enrichment/RunProposals'
import { WorkflowRunSummary } from '@/features/enrichment/WorkflowRunSummary'
import { WorkflowTimeline } from '@/features/enrichment/WorkflowTimeline'
import { planToStages } from '@/features/enrichment/planStages'
import { useWorkflowRun } from '@/features/enrichment/useWorkflowRun'
import { cn } from '@/lib/utils'
import { formatRelative } from '@/utils/format'

export default function AssetEnrichmentPage() {
  const { assetId } = useParams()
  const [searchParams] = useSearchParams()
  const asset = useCatalogItem(assetId)
  const runsParams = { assetId, limit: 10 }
  const runs = useQuery({
    queryKey: queryKeys.workflowList(runsParams),
    queryFn: ({ signal }) => fetchWorkflows(runsParams, signal),
    enabled: Boolean(assetId),
  })
  const runId = searchParams.get('run') ?? runs.data?.[0]?.workflowId
  const run = useWorkflowRun(runId)
  const plan = useQuery({ queryKey: queryKeys.workflowPlan, queryFn: ({ signal }) => fetchWorkflowPlan(signal), enabled: !runId, staleTime: Infinity })

  if (asset.isPending) {
    return <Skeleton className="h-64 w-full" aria-label="Loading" />
  }
  if (asset.isError) {
    return <ErrorState error={asset.error} onRetry={() => void asset.refetch()} />
  }
  const plannedStages = planToStages(plan.data ?? [])
  // While the displayed run is in progress this page is the progress view; starting another run is offered afterwards.
  const viewingActiveRun = Boolean(run.data && !isTerminal(run.data.status))

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        eyebrow={
          <Link to={`/assets/${asset.data.id}`} className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
            <ArrowLeft aria-hidden className="size-3" />
            {asset.data.externalAssetId}
          </Link>
        }
        title={`AI enrichment · ${asset.data.externalAssetId}`}
        description="LangChain4j Agentic workflow: agents reason and propose; deterministic Spring services validate, apply policy and persist."
        actions={viewingActiveRun ? undefined : <RunEnrichmentButton asset={asset.data} label={runId ? 'Run again' : 'Run AI enrichment'} />}
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
        <div className="flex flex-col gap-4">
          {!runId ? (
            <Section title="Workflow plan" description="This asset has not been enriched yet. These stages will run when you start enrichment.">
              {plan.isPending ? <Skeleton className="h-48" /> : plan.isError ? <ErrorState error={plan.error} /> : <WorkflowTimeline run={{ stages: plannedStages, aiModel: null, currentStage: null, status: 'QUEUED' }} />}
            </Section>
          ) : run.isPending ? (
            <Skeleton className="h-64 w-full" aria-label="Loading workflow run" />
          ) : run.isError ? (
            <ErrorState error={run.error} onRetry={() => void run.refetch()} />
          ) : (
            <>
              <WorkflowRunSummary run={run.data} />
              <Section
                title="Agentic workflow"
                description={run.live ? 'Live updates via server-sent events' : 'Updating by polling'}
                actions={<RunStatusBadge status={run.data.status} />}
              >
                <WorkflowTimeline run={run.data} />
              </Section>
              {run.data.status === 'COMPLETED' && <RunProposals workflowId={run.data.workflowId} assetId={asset.data.id} />}
            </>
          )}
        </div>

        <Section title="Run history" className="xl:self-start" contentClassName="p-0">
          {runs.isPending ? (
            <Skeleton className="m-4 h-24" />
          ) : !runs.data?.length ? (
            <p className="px-4 py-3 text-sm text-muted-foreground">No runs yet.</p>
          ) : (
            <ul className="divide-y">
              {runs.data.map((item) => (
                <li key={item.workflowId}>
                  <Link
                    to={`?run=${item.workflowId}`}
                    aria-current={item.workflowId === runId ? 'true' : undefined}
                    className={cn('flex flex-col gap-1 px-4 py-2.5 text-sm hover:bg-muted/50', item.workflowId === runId && 'bg-muted')}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <RunStatusBadge status={item.status} />
                      <span className="text-xs text-muted-foreground">{formatRelative(item.createdAt)}</span>
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {item.summary ? `${item.summary.autoApplied} applied · ${item.summary.pendingReview} for review` : item.error?.message ?? item.currentStage ?? 'Queued'}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
    </div>
  )
}
