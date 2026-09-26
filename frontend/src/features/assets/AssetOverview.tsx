import { Link } from 'react-router'
import { Copy, Lightbulb } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { Section } from '@/components/PageHeader'
import { ErrorState } from '@/components/States'
import { AuthorityBadge, Pill, RunStatusBadge, SourceBadge } from '@/components/status/StatusBadges'
import type { AssetMetadata, CatalogItem, FieldDefinition } from '@/types/api'
import { formatRelative, humanize } from '@/utils/format'
import { useDuplicates, useRecommendations } from './assetQueries'

const CONTENT_FIELDS = ['title', 'description', 'altText', 'keywords', 'taxonomyPath']

interface AssetOverviewProps {
  asset: CatalogItem
  metadata: AssetMetadata
  fields: FieldDefinition[]
}

export function AssetOverview({ asset, metadata, fields }: AssetOverviewProps) {
  const duplicates = useDuplicates(asset.id)
  const recommendations = useRecommendations(asset.id)
  const entry = (key: string) => metadata.metadata.find((item) => item.field === key)
  const label = (key: string) => fields.find((field) => field.key === key)?.displayName ?? humanize(key)

  return (
    <div className="grid gap-4 xl:grid-cols-[1fr_380px]">
      <div className="flex flex-col gap-4">
        <Section title="Descriptive metadata" description="Content used for search, accessibility and publishing">
          <dl className="flex flex-col gap-3">
            {CONTENT_FIELDS.filter((key) => fields.some((field) => field.key === key)).map((key) => {
              const current = entry(key)
              return (
                <div key={key} className="grid gap-1 sm:grid-cols-[9rem_1fr]">
                  <dt className="text-sm text-muted-foreground">{label(key)}</dt>
                  <dd className="flex flex-col gap-1 text-sm">
                    <MetadataValueView value={current?.value} />
                    {current && (
                      <span className="flex flex-wrap gap-1.5">
                        <SourceBadge source={current.source} />
                        <AuthorityBadge authoritative={current.authoritative} />
                      </span>
                    )}
                  </dd>
                </div>
              )
            })}
          </dl>
        </Section>

        <Section title="Key metadata" contentClassName="p-0">
          <dl className="grid grid-cols-2 divide-x divide-y sm:grid-cols-4">
            {Object.entries(asset.keyMetadata).map(([key, value]) => (
              <div key={key} className="p-3">
                <dt className="text-xs text-muted-foreground">{label(key)}</dt>
                <dd className="text-sm font-medium">
                  <MetadataValueView value={value} />
                </dd>
              </div>
            ))}
          </dl>
        </Section>
      </div>

      <div className="flex flex-col gap-4">
        <Section title="Latest AI enrichment">
          {asset.lastEnrichment ? (
            <div className="flex flex-col gap-2 text-sm">
              <div className="flex items-center gap-2">
                <RunStatusBadge status={asset.lastEnrichment.status} />
                <span className="text-muted-foreground">{formatRelative(asset.lastEnrichment.createdAt)}</span>
              </div>
              <Link to={`/assets/${asset.id}/enrich?run=${asset.lastEnrichment.workflowId}`} className="text-primary hover:underline">
                View workflow run
              </Link>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">This asset has not been enriched yet.</p>
          )}
        </Section>

        <Section title="Similar assets" description="Vector similarity plus identical key fields">
          {duplicates.isPending ? (
            <Skeleton className="h-16 w-full" />
          ) : duplicates.isError ? (
            <ErrorState error={duplicates.error} />
          ) : duplicates.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">No similar assets found.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {duplicates.data.map((match) => (
                <li key={match.id} className="flex flex-col gap-1 rounded-md border p-2 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <Link to={`/assets/${match.id}`} className="font-mono text-xs font-medium text-primary hover:underline">
                      {match.externalAssetId}
                    </Link>
                    <span className="text-xs tabular">{Math.round(match.similarity * 100)}% similar</span>
                  </div>
                  <span className="truncate text-xs text-muted-foreground">{match.filename}</span>
                  <div className="flex flex-wrap gap-1">
                    {match.kind === 'PROBABLE_DUPLICATE' && (
                      <Pill tone="warning" icon={Copy}>
                        Probable duplicate
                      </Pill>
                    )}
                    {match.identicalFields.length > 0 && (
                      <span className="text-[11px] text-muted-foreground">Same: {match.identicalFields.map(label).join(', ')}</span>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Section>

        <Section title="Recommendations" description="Values shared by similar assets — read-only hints, never applied automatically">
          {recommendations.isPending ? (
            <Skeleton className="h-16 w-full" />
          ) : recommendations.isError ? (
            <ErrorState error={recommendations.error} />
          ) : recommendations.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">No recommendations.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {recommendations.data.map((recommendation) => (
                <li key={recommendation.field} className="flex flex-col gap-1 rounded-md border p-2 text-sm">
                  <div className="flex items-center gap-1.5">
                    <Lightbulb aria-hidden className="size-3.5 text-amber-600" />
                    <span className="font-medium">{label(recommendation.field)}</span>
                  </div>
                  <MetadataValueView value={recommendation.value} />
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <ConfidenceIndicator confidence={recommendation.confidence} variant="compact" />
                    <span>from {recommendation.supportingAssets} similar asset(s)</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
    </div>
  )
}
