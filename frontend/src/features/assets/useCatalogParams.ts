import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { CatalogParams } from '@/api/assets'
import type { AiStatus, AssetStatus, AssetType, RightsStatus } from '@/types/api'

export type AssetView = 'table' | 'grid'

/** Completeness filter buckets offered by the UI (mapped to the backend's min/max parameters). */
export const COMPLETENESS_BUCKETS: Record<string, { label: string; min?: number; max?: number }> = {
  '95': { label: '95–100%', min: 95 },
  '80': { label: '80–94%', min: 80, max: 94.99 },
  '60': { label: '60–79%', min: 60, max: 79.99 },
  '0': { label: 'Below 60%', max: 59.99 },
}

export interface CatalogFilters {
  q: string
  type?: AssetType
  status?: AssetStatus
  aiStatus?: AiStatus
  rightsStatus?: RightsStatus
  missingRequired: boolean
  aiEnriched?: 'yes' | 'no'
  completeness?: string
  category?: string
  color?: string
  productId?: string
  taxonomyPath?: string
  sort: string
  direction: 'asc' | 'desc'
  page: number
  size: number
  view: AssetView
}

const FILTER_KEYS = ['q', 'type', 'status', 'aiStatus', 'rightsStatus', 'missingRequired', 'aiEnriched', 'completeness', 'category', 'color', 'productId', 'taxonomyPath'] as const

/** Catalog filters, sorting, paging and view live in the URL so that lists are shareable and survive reloads. */
export function useCatalogParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const filters: CatalogFilters = useMemo(() => {
    const get = (key: string) => searchParams.get(key) || undefined
    return {
      q: searchParams.get('q') ?? '',
      type: get('type') as AssetType | undefined,
      status: get('status') as AssetStatus | undefined,
      aiStatus: get('aiStatus') as AiStatus | undefined,
      rightsStatus: get('rightsStatus') as RightsStatus | undefined,
      missingRequired: searchParams.get('missingRequired') === 'true',
      aiEnriched: get('aiEnriched') as 'yes' | 'no' | undefined,
      completeness: get('completeness'),
      category: get('category'),
      color: get('color'),
      productId: get('productId'),
      taxonomyPath: get('taxonomyPath'),
      sort: searchParams.get('sort') ?? 'updatedAt',
      direction: searchParams.get('direction') === 'asc' ? 'asc' : 'desc',
      page: Math.max(0, Number(searchParams.get('page') ?? 0) || 0),
      size: Number(searchParams.get('size') ?? 25) || 25,
      view: searchParams.get('view') === 'grid' ? 'grid' : 'table',
    }
  }, [searchParams])

  /** Updates parameters; any filter change resets to the first page. */
  const update = useCallback(
    (changes: Partial<Record<keyof CatalogFilters, string | number | boolean | undefined>>) => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous)
          Object.entries(changes).forEach(([key, value]) => {
            if (value === undefined || value === '' || value === false) {
              next.delete(key)
            } else {
              next.set(key, String(value))
            }
          })
          if (!('page' in changes)) {
            next.delete('page')
          }
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const clearFilters = useCallback(() => {
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        FILTER_KEYS.forEach((key) => next.delete(key))
        next.delete('page')
        return next
      },
      { replace: true },
    )
  }, [setSearchParams])

  const activeFilterCount = FILTER_KEYS.filter((key) => {
    const value = filters[key]
    return value !== undefined && value !== '' && value !== false
  }).length

  return { filters, update, clearFilters, activeFilterCount }
}

/** Translates UI filters into backend catalog parameters. */
export function toCatalogParams(filters: CatalogFilters): CatalogParams {
  const bucket = filters.completeness ? COMPLETENESS_BUCKETS[filters.completeness] : undefined
  const fieldFilters = [
    filters.category && `category:${filters.category}`,
    filters.color && `color:${filters.color}`,
    filters.productId && `productId:${filters.productId}`,
  ].filter((value): value is string => Boolean(value))
  return {
    q: filters.q || undefined,
    type: filters.type,
    status: filters.status,
    aiStatus: filters.aiStatus,
    rightsStatus: filters.rightsStatus,
    missingRequired: filters.missingRequired || undefined,
    aiEnriched: filters.aiEnriched === undefined ? undefined : filters.aiEnriched === 'yes',
    completenessMin: bucket?.min,
    completenessMax: bucket?.max,
    taxonomyPath: filters.taxonomyPath,
    filter: fieldFilters.length ? fieldFilters : undefined,
    sort: filters.sort,
    direction: filters.direction,
    page: filters.page,
    size: filters.size,
  }
}
