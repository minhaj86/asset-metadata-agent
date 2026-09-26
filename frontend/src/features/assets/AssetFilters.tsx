import { useEffect, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FilterSelect } from '@/components/FilterSelect'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { ASSET_TYPES, type MetadataSchema } from '@/types/api'
import { humanize } from '@/utils/format'
import { COMPLETENESS_BUCKETS, type CatalogFilters } from './useCatalogParams'

interface AssetFiltersProps {
  filters: CatalogFilters
  schema: MetadataSchema | undefined
  activeFilterCount: number
  onChange: (changes: Partial<Record<keyof CatalogFilters, string | number | boolean | undefined>>) => void
  onClear: () => void
}

function vocabulary(schema: MetadataSchema | undefined, key: string) {
  return (schema?.fields.find((field) => field.key === key)?.allowedValues ?? []).map((value) => ({
    value,
    label: humanize(value),
  }))
}

/** Catalog filters; vocabularies (category, colour) come from the backend schema. */
export function AssetFilters({ filters, schema, activeFilterCount, onChange, onClear }: AssetFiltersProps) {
  const [text, setText] = useState(filters.q)
  const [productId, setProductId] = useState(filters.productId ?? '')
  const debouncedText = useDebouncedValue(text)
  const debouncedProductId = useDebouncedValue(productId)

  useEffect(() => {
    if (debouncedText !== filters.q) {
      onChange({ q: debouncedText })
    }
  }, [debouncedText]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (debouncedProductId !== (filters.productId ?? '')) {
      onChange({ productId: debouncedProductId })
    }
  }, [debouncedProductId]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-card p-3" role="search" aria-label="Filter assets">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex min-w-64 flex-1 flex-col gap-1">
          <Label htmlFor="asset-search" className="text-xs text-muted-foreground">
            Search
          </Label>
          <div className="relative">
            <Search aria-hidden className="pointer-events-none absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="asset-search"
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder="Asset ID, filename or any metadata value"
              className="h-7 pl-7"
            />
          </div>
        </div>
        <FilterSelect
          label="Asset type"
          value={filters.type}
          options={ASSET_TYPES.map((type) => ({ value: type, label: humanize(type) }))}
          onChange={(type) => onChange({ type })}
        />
        <FilterSelect label="Category" value={filters.category} options={vocabulary(schema, 'category')} onChange={(category) => onChange({ category })} />
        <FilterSelect label="Colour" value={filters.color} options={vocabulary(schema, 'color')} onChange={(color) => onChange({ color })} />
        <div className="flex w-32 flex-col gap-1">
          <Label htmlFor="product-id-filter" className="text-xs text-muted-foreground">
            Product ID
          </Label>
          <Input id="product-id-filter" value={productId} onChange={(event) => setProductId(event.target.value)} className="h-7" inputMode="numeric" />
        </div>
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <FilterSelect
          label="Completeness"
          value={filters.completeness}
          options={Object.entries(COMPLETENESS_BUCKETS).map(([value, bucket]) => ({ value, label: bucket.label }))}
          onChange={(completeness) => onChange({ completeness })}
        />
        <FilterSelect
          label="Metadata status"
          value={filters.status}
          options={[
            { value: 'DRAFT', label: 'Draft (incomplete or invalid)' },
            { value: 'IN_REVIEW', label: 'In review' },
            { value: 'READY', label: 'Ready' },
            { value: 'ARCHIVED', label: 'Archived' },
          ]}
          onChange={(status) => onChange({ status })}
        />
        <FilterSelect
          label="AI status"
          value={filters.aiStatus}
          options={[
            { value: 'NOT_ENRICHED', label: 'Not enriched' },
            { value: 'RUNNING', label: 'Running' },
            { value: 'REVIEW_PENDING', label: 'Review pending' },
            { value: 'ENRICHED', label: 'Enriched' },
            { value: 'FAILED', label: 'Failed' },
          ]}
          onChange={(aiStatus) => onChange({ aiStatus })}
        />
        <FilterSelect
          label="AI-generated values"
          value={filters.aiEnriched}
          options={[
            { value: 'yes', label: 'Has AI-generated values' },
            { value: 'no', label: 'No AI-generated values' },
          ]}
          onChange={(aiEnriched) => onChange({ aiEnriched })}
        />
        <FilterSelect
          label="Usage rights"
          value={filters.rightsStatus}
          options={[
            { value: 'VALID', label: 'Valid' },
            { value: 'EXPIRING', label: 'Expiring soon' },
            { value: 'EXPIRED', label: 'Expired' },
            { value: 'MISSING', label: 'Missing' },
          ]}
          onChange={(rightsStatus) => onChange({ rightsStatus })}
        />
        <div className="flex h-7 items-center gap-2">
          <Checkbox
            id="missing-required"
            checked={filters.missingRequired}
            onCheckedChange={(checked) => onChange({ missingRequired: checked === true })}
          />
          <Label htmlFor="missing-required" className="text-sm font-normal">
            Missing required fields
          </Label>
        </div>
        {filters.taxonomyPath && (
          <span className="inline-flex h-7 items-center gap-1 rounded-md border bg-muted/50 px-2 text-xs">
            Taxonomy: {filters.taxonomyPath}
            <button type="button" aria-label="Remove taxonomy filter" onClick={() => onChange({ taxonomyPath: undefined })} className="rounded p-0.5 hover:bg-muted">
              <X aria-hidden className="size-3" />
            </button>
          </span>
        )}
        {activeFilterCount > 0 && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setText('')
              setProductId('')
              onClear()
            }}
          >
            <X aria-hidden />
            Clear filters ({activeFilterCount})
          </Button>
        )}
      </div>
    </div>
  )
}
