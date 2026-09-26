import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bot, CheckCircle2, Loader2, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { Section } from '@/components/PageHeader'
import { ScoreMeter } from '@/components/ScoreMeter'
import { ErrorState } from '@/components/States'
import { SeverityBadge, type Severity } from '@/components/status/StatusBadges'
import { fetchQuality } from '@/api/metadata'
import { queryKeys } from '@/api/queryKeys'
import { isAiAvailable, useSystemStatus } from '@/hooks/useSystemStatus'
import type { FieldDefinition, Issue } from '@/types/api'
import { formatDateTime, humanize } from '@/utils/format'

interface QualityReportViewProps {
  assetId: string
  fields: FieldDefinition[] | undefined
}

interface Finding {
  severity: Severity
  field: string | null
  message: string
  detail?: string | null
  category?: string
}

function fieldLabel(fields: FieldDefinition[] | undefined, key: string | null): string {
  return key ? (fields?.find((field) => field.key === key)?.displayName ?? key) : 'Asset'
}

function FindingItems({ findings, fields, empty }: { findings: Finding[]; fields: FieldDefinition[] | undefined; empty: string }) {
  if (findings.length === 0) {
    return (
      <p className="flex items-center gap-2 px-4 py-3 text-sm text-muted-foreground">
        <CheckCircle2 aria-hidden className="size-4 text-emerald-600" />
        {empty}
      </p>
    )
  }
  return (
    <ul className="divide-y">
      {findings.map((finding, index) => (
        <li key={`${finding.field}-${index}`} className="flex flex-col gap-1 px-4 py-2.5 sm:flex-row sm:items-start sm:gap-3">
          <div className="flex w-56 shrink-0 items-center gap-2">
            <SeverityBadge severity={finding.severity} />
            <span className="truncate text-sm font-medium">{fieldLabel(fields, finding.field)}</span>
          </div>
          <div className="min-w-0 text-sm">
            <p>{finding.message}</p>
            {finding.detail && <p className="text-muted-foreground">Suggested: {finding.detail}</p>}
            {finding.category && <p className="text-xs text-muted-foreground">{humanize(finding.category)}</p>}
          </div>
        </li>
      ))}
    </ul>
  )
}

function FindingList({ title, findings, fields, empty }: { title: string; findings: Finding[]; fields: FieldDefinition[] | undefined; empty: string }) {
  return (
    <Section title={`${title} (${findings.length})`} contentClassName="p-0">
      <FindingItems findings={findings} fields={fields} empty={empty} />
    </Section>
  )
}

const toFinding = (issue: Issue): Finding => ({ severity: issue.severity, field: issue.field, message: issue.message })

/**
 * Deterministic quality (score, completeness, validation findings computed in Java) plus optional semantic review
 * by the MetadataQualityAgent. AI suggestions never change the deterministic score.
 */
export function QualityReportView({ assetId, fields }: QualityReportViewProps) {
  const [includeAi, setIncludeAi] = useState(false)
  const { data: status } = useSystemStatus()
  const deterministic = useQuery({ queryKey: queryKeys.quality(assetId, false), queryFn: ({ signal }) => fetchQuality(assetId, false, signal) })
  const withAi = useQuery({
    queryKey: queryKeys.quality(assetId, true),
    queryFn: ({ signal }) => fetchQuality(assetId, true, signal),
    enabled: includeAi,
    staleTime: Infinity,
  })
  const report = withAi.data ?? deterministic.data

  if (deterministic.isPending) {
    return <Skeleton className="h-64 w-full" aria-label="Loading quality report" />
  }
  if (deterministic.isError) {
    return <ErrorState error={deterministic.error} onRetry={() => void deterministic.refetch()} />
  }
  if (!report) {
    return null
  }
  const errors = report.deterministicErrors.map(toFinding)
  const warnings = report.warnings.map(toFinding)
  const suggestions: Finding[] = (withAi.data?.aiSuggestions ?? []).map((suggestion) => ({
    severity: 'SUGGESTION',
    field: suggestion.field,
    message: suggestion.message,
    detail: suggestion.suggestedImprovement,
    category: suggestion.category,
  }))

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
        <ScoreMeter label="Overall quality" value={report.qualityScore} size="lg" hint="Deterministic (Java)" />
        <ScoreMeter label="Completeness" value={report.completenessPercentage} size="lg" hint="Required + recommended" />
        <ScoreMeter label="Required fields" value={report.requiredCompletenessPercentage} size="lg" hint={`${report.missingRequiredFields.length} missing`} />
        <div className="flex flex-col gap-1">
          <span className="text-xs text-muted-foreground">Validity</span>
          <span className={errors.length ? 'text-lg font-semibold text-red-700 dark:text-red-300' : 'text-lg font-semibold text-emerald-700 dark:text-emerald-300'}>
            {errors.length ? `${errors.length} error(s)` : 'Valid'}
          </span>
          <span className="text-[11px] text-muted-foreground">{warnings.length} warning(s) · assessed {formatDateTime(report.assessedAt)}</span>
        </div>
      </div>

      <FindingList title="Errors" findings={errors} fields={fields} empty="No validation errors." />
      <FindingList title="Warnings" findings={warnings} fields={fields} empty="No warnings." />

      <Section
        title="AI quality suggestions"
        description="Soft criteria judged by the MetadataQualityAgent (weak descriptions, poor alt text, irrelevant keywords). Suggestions do not change the score."
        actions={
          <Button size="sm" variant="outline" onClick={() => (includeAi ? void withAi.refetch() : setIncludeAi(true))} disabled={withAi.isFetching || !isAiAvailable(status)}>
            {withAi.isFetching ? <Loader2 aria-hidden className="animate-spin" /> : <Sparkles aria-hidden />}
            {withAi.data ? 'Re-run semantic review' : 'Run semantic review'}
          </Button>
        }
        contentClassName="p-0"
      >
        {!isAiAvailable(status) && !withAi.data ? (
          <p className="flex items-center gap-2 px-4 py-3 text-sm text-muted-foreground">
            <Bot aria-hidden className="size-4" />
            AI service unavailable. Deterministic quality checks above remain fully available.
          </p>
        ) : withAi.isError ? (
          <ErrorState className="m-4" error={withAi.error} onRetry={() => void withAi.refetch()} aiHint="Deterministic quality checks remain available." />
        ) : !withAi.data ? (
          <p className="px-4 py-3 text-sm text-muted-foreground">Run the semantic review to get AI suggestions for this asset.</p>
        ) : withAi.data.aiStatus === 'UNAVAILABLE' ? (
          <p className="px-4 py-3 text-sm text-muted-foreground">The semantic review was unavailable for this request; deterministic results are shown.</p>
        ) : (
          <div>
            <div className="flex flex-wrap items-center gap-3 border-b px-4 py-2.5 text-sm">
              {withAi.data.aiSummary && <span>{withAi.data.aiSummary}</span>}
              {withAi.data.confidence !== null && <ConfidenceIndicator confidence={withAi.data.confidence} />}
              {withAi.data.aiModel && <span className="font-mono text-xs text-muted-foreground">{withAi.data.aiModel}</span>}
            </div>
            <FindingItems findings={suggestions} fields={fields} empty="No AI suggestions — the metadata reads well." />
          </div>
        )}
      </Section>
    </div>
  )
}
