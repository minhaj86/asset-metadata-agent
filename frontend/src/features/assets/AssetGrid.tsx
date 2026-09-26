import { Link } from 'react-router'
import { Checkbox } from '@/components/ui/checkbox'
import { AssetPreview } from '@/components/AssetPreview'
import { AiStatusBadge, AssetStatusBadge } from '@/components/status/StatusBadges'
import { cn } from '@/lib/utils'
import type { CatalogItem } from '@/types/api'
import { formatValue, humanize } from '@/utils/format'
import { CompletenessCell } from './AssetTable'

interface AssetGridProps {
  items: CatalogItem[]
  selected: Set<string>
  onToggle: (assetId: string) => void
}

export function AssetGrid({ items, selected, onToggle }: AssetGridProps) {
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6" aria-label="Assets">
      {items.map((item) => {
        const title = item.keyMetadata.title
        return (
          <li
            key={item.id}
            className={cn('group relative flex flex-col overflow-hidden rounded-lg border bg-card', selected.has(item.id) && 'ring-2 ring-ring')}
          >
            <div className="absolute top-2 left-2 z-10 rounded bg-background/90 p-0.5">
              <Checkbox aria-label={`Select ${item.externalAssetId}`} checked={selected.has(item.id)} onCheckedChange={() => onToggle(item.id)} />
            </div>
            <Link to={`/assets/${item.id}`} className="flex flex-1 flex-col outline-none focus-visible:ring-2 focus-visible:ring-ring">
              <div className="aspect-[4/5] border-b">
                <AssetPreview assetUrl={item.assetUrl} mimeType={item.mimeType} alt={item.filename} />
              </div>
              <div className="flex flex-1 flex-col gap-1.5 p-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs font-medium">{item.externalAssetId}</span>
                  <AssetStatusBadge status={item.status} />
                </div>
                <span className="line-clamp-1 text-sm" title={title ? formatValue(title) : item.filename}>
                  {title ? formatValue(title) : item.filename}
                </span>
                <span className="text-xs text-muted-foreground">{humanize(item.type)}</span>
                <CompletenessCell value={item.summary?.completeness} missing={item.summary?.missingRequiredFields.length ?? 0} />
                <div>
                  <AiStatusBadge status={item.aiStatus} />
                </div>
              </div>
            </Link>
          </li>
        )
      })}
    </ul>
  )
}
