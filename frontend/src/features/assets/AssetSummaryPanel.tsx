import { Link } from 'react-router'
import { AssetPreview } from '@/components/AssetPreview'
import { ScoreMeter } from '@/components/ScoreMeter'
import { RunStatusBadge } from '@/components/status/StatusBadges'
import type { CatalogItem, FieldDefinition } from '@/types/api'
import { formatDate, formatRelative } from '@/utils/format'

interface AssetSummaryPanelProps {
  asset: CatalogItem
  fields: FieldDefinition[] | undefined
}

function Metric({ label, value, hint, to }: { label: string; value: number; hint?: string; to?: string }) {
  const content = (
    <>
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-lg font-semibold tabular">{value}</span>
      {hint && <span className="text-[11px] text-muted-foreground">{hint}</span>}
    </>
  )
  return to && value > 0 ? (
    <Link to={to} className="flex flex-col gap-0.5 rounded-md p-1 -m-1 outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
      {content}
    </Link>
  ) : (
    <div className="flex flex-col gap-0.5">{content}</div>
  )
}

/** Preview + deterministic metadata summary computed by the backend. */
export function AssetSummaryPanel({ asset, fields }: AssetSummaryPanelProps) {
  const summary = asset.summary
  const missing = summary?.missingRequiredFields ?? []
  const label = (key: string) => fields?.find((field) => field.key === key)?.displayName ?? key
  const openSuggestions = (summary?.pendingReviewCount ?? 0) + (summary?.lowConfidenceCount ?? 0)

  return (
    <div className="grid gap-4 md:grid-cols-[minmax(0,320px)_1fr]">
      <div className="aspect-[4/5] overflow-hidden rounded-lg border">
        <AssetPreview assetUrl={asset.assetUrl} mimeType={asset.mimeType} alt={`Preview of ${asset.filename}`} />
      </div>
      <section aria-label="Metadata summary" className="flex flex-col gap-4 rounded-lg border bg-card p-4">
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
          <ScoreMeter label="Metadata quality" value={summary?.qualityScore} size="lg" hint="Deterministic score" />
          <ScoreMeter label="Completeness" value={summary?.completeness} size="lg" hint="Required + recommended fields" />
          <ScoreMeter label="Required fields" value={summary?.requiredCompleteness} size="lg" hint="Share of required fields set" />
        </div>
        <div className="grid grid-cols-2 gap-4 border-t pt-4 sm:grid-cols-4">
          <Metric label="Missing required" value={missing.length} to={`?tab=metadata`} />
          <Metric label="AI suggestions" value={openSuggestions} hint="Open proposals" to="?tab=suggestions" />
          <Metric label="Pending approval" value={summary?.pendingReviewCount ?? 0} hint="Human review" to="?tab=suggestions" />
          <Metric label="AI-generated values" value={summary?.aiValueCount ?? 0} hint="Applied by policy or approved" />
        </div>
        {missing.length > 0 && (
          <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
            <span className="font-medium">Missing required:</span> {missing.map(label).join(', ')}
          </div>
        )}
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 border-t pt-3 text-xs sm:grid-cols-4">
          <div>
            <dt className="text-muted-foreground">Usage rights end</dt>
            <dd className="font-medium">{formatDate(summary?.usageRightsEnd)}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Last modified</dt>
            <dd className="font-medium" title={asset.updatedAt}>
              {formatRelative(asset.updatedAt)}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Last AI enrichment</dt>
            <dd>
              {asset.lastEnrichment ? (
                <Link to={`/assets/${asset.id}/enrich?run=${asset.lastEnrichment.workflowId}`} className="inline-flex items-center gap-1.5 hover:underline">
                  <RunStatusBadge status={asset.lastEnrichment.status} />
                  {formatRelative(asset.lastEnrichment.createdAt)}
                </Link>
              ) : (
                <span className="text-muted-foreground">Never</span>
              )}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Validation</dt>
            <dd className="font-medium">
              {summary ? `${summary.errorCount} error(s), ${summary.warningCount} warning(s)` : '—'}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  )
}
