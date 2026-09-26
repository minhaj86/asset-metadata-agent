import { Link } from 'react-router'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AssetPreview } from '@/components/AssetPreview'
import { AiStatusBadge, AssetStatusBadge, RightsStatusBadge } from '@/components/status/StatusBadges'
import { MetadataValueView } from '@/components/MetadataValueView'
import { cn } from '@/lib/utils'
import type { CatalogItem } from '@/types/api'
import { formatPercent, formatRelative, humanize } from '@/utils/format'

interface SortState {
  sort: string
  direction: 'asc' | 'desc'
}

interface AssetTableProps {
  items: CatalogItem[]
  sort: SortState
  onSort: (sort: string) => void
  selected: Set<string>
  onToggle: (assetId: string) => void
  onToggleAll: (checked: boolean) => void
}

function SortHeader({ column, label, sort, onSort, className }: { column: string; label: string; sort: SortState; onSort: (column: string) => void; className?: string }) {
  const active = sort.sort === column
  const Icon = !active ? ArrowUpDown : sort.direction === 'asc' ? ArrowUp : ArrowDown
  return (
    <TableHead aria-sort={active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'} className={className}>
      <button
        type="button"
        onClick={() => onSort(column)}
        className="inline-flex items-center gap-1 rounded-sm font-medium outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
      >
        {label}
        <Icon aria-hidden className={cn('size-3', !active && 'opacity-40')} />
      </button>
    </TableHead>
  )
}

export function CompletenessCell({ value, missing }: { value: number | undefined; missing: number }) {
  if (value === undefined) {
    return <span className="text-muted-foreground">—</span>
  }
  const bar = value >= 90 ? 'bg-emerald-600' : value >= 70 ? 'bg-amber-500' : 'bg-red-500'
  return (
    <div className="flex min-w-24 flex-col gap-1">
      <div className="flex items-baseline gap-1.5 text-xs">
        <span className="font-medium tabular">{formatPercent(value)}</span>
        {missing > 0 && <span className="text-red-700 dark:text-red-300">{missing} required missing</span>}
      </div>
      <div aria-hidden className="h-1 w-full overflow-hidden rounded-full bg-muted">
        <div className={cn('h-full', bar)} style={{ width: `${value}%` }} />
      </div>
    </div>
  )
}

export function AssetTable({ items, sort, onSort, selected, onToggle, onToggleAll }: AssetTableProps) {
  const allSelected = items.length > 0 && items.every((item) => selected.has(item.id))
  const someSelected = items.some((item) => selected.has(item.id))

  return (
    <div className="overflow-x-auto rounded-lg border">
      <Table className="text-[13px]">
        <TableHeader className="bg-muted/40">
          <TableRow>
            <TableHead className="w-9">
              <Checkbox
                aria-label="Select all assets on this page"
                checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                onCheckedChange={(checked) => onToggleAll(checked === true)}
              />
            </TableHead>
            <TableHead className="w-14">
              <span className="sr-only">Thumbnail</span>
            </TableHead>
            <SortHeader column="externalAssetId" label="Asset ID" sort={sort} onSort={onSort} />
            <SortHeader column="filename" label="Filename" sort={sort} onSort={onSort} />
            <SortHeader column="type" label="Type" sort={sort} onSort={onSort} />
            <SortHeader column="productId" label="Product ID" sort={sort} onSort={onSort} />
            <SortHeader column="category" label="Category" sort={sort} onSort={onSort} />
            <SortHeader column="completeness" label="Completeness" sort={sort} onSort={onSort} />
            <SortHeader column="qualityScore" label="Quality" sort={sort} onSort={onSort} />
            <TableHead>AI status</TableHead>
            <SortHeader column="updatedAt" label="Last modified" sort={sort} onSort={onSort} />
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((item) => (
            <TableRow key={item.id} data-state={selected.has(item.id) ? 'selected' : undefined}>
              <TableCell>
                <Checkbox
                  aria-label={`Select ${item.externalAssetId}`}
                  checked={selected.has(item.id)}
                  onCheckedChange={() => onToggle(item.id)}
                />
              </TableCell>
              <TableCell className="py-1">
                <div className="size-10 overflow-hidden rounded border">
                  <AssetPreview assetUrl={item.assetUrl} mimeType={item.mimeType} alt={item.filename} compact />
                </div>
              </TableCell>
              <TableCell>
                <Link to={`/assets/${item.id}`} className="font-mono text-xs font-medium text-primary underline-offset-2 hover:underline">
                  {item.externalAssetId}
                </Link>
              </TableCell>
              <TableCell className="max-w-60">
                <div className="flex flex-col gap-1">
                  <span className="truncate" title={item.filename}>
                    {item.filename}
                  </span>
                  <span className="flex flex-wrap gap-1">
                    <AssetStatusBadge status={item.status} />
                    {item.rightsStatus !== 'VALID' && <RightsStatusBadge status={item.rightsStatus} until={item.summary?.usageRightsEnd} />}
                  </span>
                </div>
              </TableCell>
              <TableCell className="whitespace-nowrap">{humanize(item.type)}</TableCell>
              <TableCell className="font-mono text-xs"><MetadataValueView value={item.keyMetadata.productId} /></TableCell>
              <TableCell><MetadataValueView value={item.keyMetadata.category} /></TableCell>
              <TableCell>
                <CompletenessCell value={item.summary?.completeness} missing={item.summary?.missingRequiredFields.length ?? 0} />
              </TableCell>
              <TableCell className="tabular">{item.summary ? item.summary.qualityScore : '—'}</TableCell>
              <TableCell>
                <AiStatusBadge status={item.aiStatus} />
              </TableCell>
              <TableCell className="whitespace-nowrap text-muted-foreground" title={item.updatedAt}>
                {formatRelative(item.updatedAt)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
