import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ErrorState } from '@/components/States'
import { fetchProposals } from '@/api/approvals'
import { queryKeys } from '@/api/queryKeys'
import { AssetHeader } from '@/features/assets/AssetHeader'
import { AssetOverview } from '@/features/assets/AssetOverview'
import { AssetSummaryPanel } from '@/features/assets/AssetSummaryPanel'
import { useAssetMetadata, useCatalogItem, useSchema } from '@/features/assets/assetQueries'
import { AssetProposals } from '@/features/approvals/AssetProposals'
import { AssetHistory } from '@/features/audit/AssetHistory'
import { MetadataEditor } from '@/features/metadata/MetadataEditor'
import { QualityReportView } from '@/features/metadata/QualityReportView'
import { AssetTaxonomyPanel } from '@/features/taxonomy/AssetTaxonomyPanel'

const TABS = ['overview', 'metadata', 'suggestions', 'quality', 'taxonomy', 'history'] as const
type Tab = (typeof TABS)[number]

export default function AssetDetailPage() {
  const { assetId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('tab') as Tab | null
  const tab: Tab = requested && TABS.includes(requested) ? requested : 'overview'

  const asset = useCatalogItem(assetId)
  const metadata = useAssetMetadata(assetId)
  const schema = useSchema(asset.data?.type)
  const openParams = { assetId, status: 'PROPOSED' as const, limit: 200 }
  const openProposals = useQuery({
    queryKey: queryKeys.proposalList(openParams),
    queryFn: ({ signal }) => fetchProposals(openParams, signal),
    enabled: Boolean(assetId),
  })
  const openCount = openProposals.data?.length ?? 0
  const taxonomyProposal = useMemo(() => openProposals.data?.find((proposal) => proposal.field === 'taxonomyPath'), [openProposals.data])

  if (asset.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label="Loading asset">
        <Skeleton className="h-14 w-1/2" />
        <div className="grid gap-4 md:grid-cols-[320px_1fr]">
          <Skeleton className="aspect-[4/5]" />
          <Skeleton className="h-64" />
        </div>
      </div>
    )
  }
  if (asset.isError) {
    return <ErrorState error={asset.error} onRetry={() => void asset.refetch()} />
  }

  const changeTab = (next: string) =>
    setSearchParams(
      (previous) => {
        const params = new URLSearchParams(previous)
        params.set('tab', next)
        if (next !== 'history') {
          params.delete('field')
        }
        return params
      },
      { replace: true },
    )

  const fields = schema.data?.fields ?? []
  const loadingContent = metadata.isPending || schema.isPending

  return (
    <div className="flex flex-col gap-4">
      <AssetHeader asset={asset.data} />
      <AssetSummaryPanel asset={asset.data} fields={schema.data?.fields} />

      <Tabs value={tab} onValueChange={changeTab} className="gap-4">
        <TabsList className="flex-wrap">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="metadata">Metadata</TabsTrigger>
          <TabsTrigger value="suggestions">
            AI Suggestions
            {openCount > 0 && (
              <span className="ml-1 rounded-full bg-amber-100 px-1.5 text-[11px] font-semibold text-amber-900 tabular dark:bg-amber-900 dark:text-amber-100">
                {openCount}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="quality">Quality</TabsTrigger>
          <TabsTrigger value="taxonomy">Taxonomy</TabsTrigger>
          <TabsTrigger value="history">History</TabsTrigger>
        </TabsList>

        {metadata.isError ? (
          <ErrorState error={metadata.error} onRetry={() => void metadata.refetch()} />
        ) : schema.isError ? (
          <ErrorState error={schema.error} onRetry={() => void schema.refetch()} />
        ) : loadingContent ? (
          <Skeleton className="h-64 w-full" aria-label="Loading metadata" />
        ) : (
          <>
            <TabsContent value="overview">
              <AssetOverview asset={asset.data} metadata={metadata.data} fields={fields} />
            </TabsContent>
            <TabsContent value="metadata">
              <MetadataEditor asset={asset.data} schema={schema.data} metadata={metadata.data} openProposals={openProposals.data ?? []} />
            </TabsContent>
            <TabsContent value="suggestions">
              <AssetProposals asset={asset.data} />
            </TabsContent>
            <TabsContent value="quality">
              <QualityReportView assetId={asset.data.id} fields={fields} />
            </TabsContent>
            <TabsContent value="taxonomy">
              <AssetTaxonomyPanel entry={metadata.data.metadata.find((entry) => entry.field === 'taxonomyPath')} proposal={taxonomyProposal} />
            </TabsContent>
            <TabsContent value="history">
              <AssetHistory assetId={asset.data.id} fields={fields} />
            </TabsContent>
          </>
        )}
      </Tabs>
    </div>
  )
}
