import { keepPreviousData, useQuery, type QueryClient } from '@tanstack/react-query'
import { fetchCatalog, fetchCatalogItem, fetchDuplicates, fetchRecommendations, type CatalogParams } from '@/api/assets'
import { fetchMetadata, fetchSchema } from '@/api/metadata'
import { queryKeys } from '@/api/queryKeys'
import type { AssetType } from '@/types/api'

export function useCatalog(params: CatalogParams) {
  return useQuery({
    queryKey: queryKeys.catalog(params as Record<string, unknown>),
    queryFn: ({ signal }) => fetchCatalog(params, signal),
    placeholderData: keepPreviousData,
  })
}

export function useCatalogItem(assetId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.asset(assetId ?? ''),
    queryFn: ({ signal }) => fetchCatalogItem(assetId!, signal),
    enabled: Boolean(assetId),
  })
}

export function useAssetMetadata(assetId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.metadata(assetId ?? ''),
    queryFn: ({ signal }) => fetchMetadata(assetId!, signal),
    enabled: Boolean(assetId),
  })
}

export function useSchema(assetType: AssetType | undefined) {
  return useQuery({
    queryKey: queryKeys.schema(assetType ?? ''),
    queryFn: ({ signal }) => fetchSchema(assetType!, signal),
    enabled: Boolean(assetType),
    staleTime: 5 * 60_000,
  })
}

export function useDuplicates(assetId: string) {
  return useQuery({ queryKey: queryKeys.duplicates(assetId), queryFn: ({ signal }) => fetchDuplicates(assetId, signal) })
}

export function useRecommendations(assetId: string) {
  return useQuery({
    queryKey: queryKeys.recommendations(assetId),
    queryFn: ({ signal }) => fetchRecommendations(assetId, signal),
  })
}

/** Refreshes everything derived from an asset's metadata after the backend changed it. */
export async function invalidateAsset(client: QueryClient, assetId?: string): Promise<void> {
  const keys: readonly unknown[][] = [
    [...queryKeys.assets],
    [...queryKeys.proposals],
    [...queryKeys.dashboard],
    [...queryKeys.workflows],
    ['audit'],
    ['taxonomy', 'usage'],
  ]
  const assetKeys: readonly unknown[][] = assetId
    ? [
        [...queryKeys.asset(assetId)],
        [...queryKeys.metadata(assetId)],
        ['quality', assetId],
        ['history', assetId],
        [...queryKeys.duplicates(assetId)],
        [...queryKeys.recommendations(assetId)],
      ]
    : []
  await Promise.all([...keys, ...assetKeys].map((queryKey) => client.invalidateQueries({ queryKey })))
}
