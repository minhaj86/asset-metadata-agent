import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bot, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader, Section } from '@/components/PageHeader'
import { EmptyState, ErrorState } from '@/components/States'
import { AiStatusBadge } from '@/components/status/StatusBadges'
import { fetchWorkflowPlan, fetchWorkflows, isTerminal } from '@/api/enrichment'
import { queryKeys } from '@/api/queryKeys'
import { useCatalog } from '@/features/assets/assetQueries'
import { RunEnrichmentButton } from '@/features/enrichment/RunEnrichmentButton'
import { WorkflowRunsTable } from '@/features/enrichment/WorkflowRunsTable'
import { WorkflowTimeline } from '@/features/enrichment/WorkflowTimeline'
import { planToStages } from '@/features/enrichment/planStages'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'

export default function EnrichmentPage() {
  const runsParams = { limit: 50 }
  const runs = useQuery({
    queryKey: queryKeys.workflowList(runsParams),
    queryFn: ({ signal }) => fetchWorkflows(runsParams, signal),
    refetchInterval: (query) => (query.state.data?.some((run) => !isTerminal(run.status)) ? 2000 : false),
  })
  const plan = useQuery({ queryKey: queryKeys.workflowPlan, queryFn: ({ signal }) => fetchWorkflowPlan(signal), staleTime: Infinity })
  const [assetQuery, setAssetQuery] = useState('')
  const debouncedQuery = useDebouncedValue(assetQuery)
  const candidates = useCatalog({ q: debouncedQuery || undefined, size: 6, sort: 'externalAssetId', direction: 'asc' })

  const stages = planToStages(plan.data ?? [])

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="AI Enrichment"
        description="Agentic metadata enrichment runs. Agents propose metadata with confidence and evidence; the backend policy decides what is applied and what needs review."
      />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Section title="Workflow runs" description="Newest first · refreshes while runs are active" contentClassName="p-0">
          {runs.isPending ? (
            <Skeleton className="m-4 h-40" />
          ) : runs.isError ? (
            <ErrorState className="m-4" error={runs.error} onRetry={() => void runs.refetch()} />
          ) : runs.data.length === 0 ? (
            <EmptyState icon={Bot} title="No enrichment runs yet" description="Start enrichment for an asset to see the agentic workflow in action." />
          ) : (
            <WorkflowRunsTable runs={runs.data} />
          )}
        </Section>

        <div className="flex flex-col gap-4">
          <Section title="Start enrichment" description="Find an asset and run the workflow">
            <div className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <Label htmlFor="enrich-asset-search" className="text-xs text-muted-foreground">
                  Asset
                </Label>
                <div className="relative">
                  <Search aria-hidden className="pointer-events-none absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input id="enrich-asset-search" className="pl-7" value={assetQuery} onChange={(event) => setAssetQuery(event.target.value)} placeholder="e.g. DA-100001" />
                </div>
              </div>
              <ul className="flex flex-col divide-y rounded-md border" aria-label="Matching assets">
                {(candidates.data?.items ?? []).map((asset) => (
                  <li key={asset.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                    <span className="flex min-w-0 flex-col">
                      <span className="font-mono text-xs font-medium">{asset.externalAssetId}</span>
                      <span className="truncate text-xs text-muted-foreground">{asset.filename}</span>
                      <span className="mt-1">
                        <AiStatusBadge status={asset.aiStatus} />
                      </span>
                    </span>
                    <RunEnrichmentButton asset={asset} label="Run" />
                  </li>
                ))}
              </ul>
            </div>
          </Section>

          <Section
            title="How the agentic workflow works"
            description="LangChain4j Agentic supervisor: sequential, parallel and conditional stages sharing one agentic scope"
          >
            {plan.isPending ? (
              <Skeleton className="h-48" />
            ) : plan.isError ? (
              <ErrorState error={plan.error} aiHint="The workflow plan is published by the AI layer." />
            ) : (
              <WorkflowTimeline run={{ stages, aiModel: null, currentStage: null, status: 'QUEUED' }} />
            )}
          </Section>
        </div>
      </div>
    </div>
  )
}
