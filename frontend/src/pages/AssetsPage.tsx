import { useMemo, useState } from 'react'
import { Images, LayoutGrid, List } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState } from '@/components/States'
import { AssetFilters } from '@/features/assets/AssetFilters'
import { AssetGrid } from '@/features/assets/AssetGrid'
import { AssetTable } from '@/features/assets/AssetTable'
import { BulkActionBar } from '@/features/assets/BulkActionBar'
import { useCatalog, useSchema } from '@/features/assets/assetQueries'
import { toCatalogParams, useCatalogParams } from '@/features/assets/useCatalogParams'
import { formatNumber } from '@/utils/format'

export default function AssetsPage() {
  const { filters, update, clearFilters, activeFilterCount } = useCatalogParams()
  const params = useMemo(() => toCatalogParams(filters), [filters])
  const catalog = useCatalog(params)
  const schema = useSchema(filters.type ?? 'PRODUCT_IMAGE')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const items = catalog.data?.items ?? []

  const toggle = (assetId: string) =>
    setSelected((previous) => {
      const next = new Set(previous)
      if (next.has(assetId)) {
        next.delete(assetId)
      } else {
        next.add(assetId)
      }
      return next
    })

  const toggleAll = (checked: boolean) =>
    setSelected((previous) => {
      const next = new Set(previous)
      items.forEach((item) => (checked ? next.add(item.id) : next.delete(item.id)))
      return next
    })

  const sortBy = (column: string) =>
    update({
      sort: column,
      direction: filters.sort === column && filters.direction === 'asc' ? 'desc' : 'asc',
    })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Assets"
        description="Asset references with their metadata summary. Binaries stay in the DAM; previews use the DAM rendition URL."
        actions={
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={filters.view}
            onValueChange={(view) => view && update({ view: view === 'table' ? undefined : view, page: filters.page })}
            aria-label="View"
          >
            <ToggleGroupItem value="table" aria-label="Table view">
              <List aria-hidden />
            </ToggleGroupItem>
            <ToggleGroupItem value="grid" aria-label="Grid view">
              <LayoutGrid aria-hidden />
            </ToggleGroupItem>
          </ToggleGroup>
        }
      />
      <AssetFilters
        filters={filters}
        schema={schema.data}
        activeFilterCount={activeFilterCount}
        onChange={update}
        onClear={clearFilters}
      />

      {catalog.isPending ? (
        <div className="space-y-2" aria-busy="true" aria-label="Loading assets">
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={index} className="h-12 w-full" />
          ))}
        </div>
      ) : catalog.isError ? (
        <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState
          icon={Images}
          title="No assets match these filters"
          description={activeFilterCount > 0 ? 'Try removing some filters.' : 'Register assets through the API to see them here.'}
        />
      ) : (
        <>
          <p className="sr-only" aria-live="polite">
            {formatNumber(catalog.data.totalItems)} assets found
          </p>
          {filters.view === 'grid' ? (
            <AssetGrid items={items} selected={selected} onToggle={toggle} />
          ) : (
            <AssetTable
              items={items}
              sort={{ sort: filters.sort, direction: filters.direction }}
              onSort={sortBy}
              selected={selected}
              onToggle={toggle}
              onToggleAll={toggleAll}
            />
          )}
          <Pagination
            page={catalog.data.page}
            totalPages={catalog.data.totalPages}
            totalItems={catalog.data.totalItems}
            pageSize={catalog.data.size}
            onPageChange={(page) => update({ page })}
            label="assets"
          />
        </>
      )}
      <BulkActionBar selectedIds={[...selected]} onClear={() => setSelected(new Set())} />
    </div>
  )
}
