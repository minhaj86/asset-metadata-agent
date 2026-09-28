import { Link } from 'react-router'
import { AssetPreview } from '@/components/AssetPreview'
import { AssetStatusBadge, AssetTypeBadge } from '@/components/status/StatusBadges'
import { TaxonomyBreadcrumb } from '@/features/taxonomy/AssetTaxonomyPanel'
import type { SearchCriteria, SearchHit } from '@/types/api'
import { formatValue, humanize } from '@/utils/format'

const SUMMARY_FIELDS = ['productType', 'color', 'angleFacing', 'shotType', 'campaign', 'season']

/** Filters of the executed criteria that the hit satisfies (every hit matches all structured filters). */
function matchedFilters(criteria: SearchCriteria, hit: SearchHit): [string, string][] {
  return Object.entries(criteria.filters).filter(([field]) => hit.metadata[field] !== undefined && hit.metadata[field] !== null)
}

interface SearchResultsProps {
  hits: SearchHit[]
  criteria: SearchCriteria
  semanticApplied: boolean
  fieldLabel: (key: string) => string
}

export function SearchResults({ hits, criteria, semanticApplied, fieldLabel }: SearchResultsProps) {
  return (
    <ol className="flex flex-col gap-2" aria-label="Search results">
      {hits.map((hit, index) => {
        const title = hit.metadata.title
        const taxonomy = hit.metadata.taxonomyPath
        const matched = matchedFilters(criteria, hit)
        return (
          <li key={hit.id} className="flex gap-3 rounded-lg border bg-card p-3">
            <div className="size-20 shrink-0 overflow-hidden rounded-md border">
              <AssetPreview assetUrl={hit.assetUrl} mimeType={hit.mimeType} alt={hit.filename} compact />
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span className="text-xs text-muted-foreground tabular">#{index + 1}</span>
                <Link to={`/assets/${hit.id}`} className="font-mono text-sm font-medium text-primary hover:underline">
                  {hit.externalAssetId}
                </Link>
                <AssetTypeBadge type={hit.type} />
                <AssetStatusBadge status={hit.status} />
                {semanticApplied && hit.similarity !== null && (
                  <span className="ml-auto text-sm" title="Cosine similarity between the query and the asset's metadata embedding">
                    <span className="font-semibold tabular">{Math.round(hit.similarity * 100)}%</span>{' '}
                    <span className="text-xs text-muted-foreground">similarity</span>
                  </span>
                )}
              </div>
              <span className="text-sm">{title ? formatValue(title) : <span className="text-muted-foreground">{hit.filename}</span>}</span>
              <span className="flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
                {SUMMARY_FIELDS.filter((field) => hit.metadata[field] !== undefined).map((field) => (
                  <span key={field}>
                    {fieldLabel(field)}: <span className="text-foreground">{humanize(formatValue(hit.metadata[field]))}</span>
                  </span>
                ))}
              </span>
              {matched.length > 0 && (
                <span className="flex flex-wrap items-center gap-1 text-xs">
                  <span className="text-muted-foreground">Matched:</span>
                  {matched.map(([field, value]) => (
                    <span key={field} className="rounded border border-emerald-200 bg-emerald-50 px-1.5 py-0.5 text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
                      {fieldLabel(field)}: {value}
                    </span>
                  ))}
                </span>
              )}
              {typeof taxonomy === 'string' && <TaxonomyBreadcrumb path={taxonomy} />}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
