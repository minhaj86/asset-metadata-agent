import { ArrowRight } from 'lucide-react'
import { MetadataValueView } from '@/components/MetadataValueView'
import { cn } from '@/lib/utils'
import type { MetadataDataType, MetadataValue } from '@/types/api'
import { formatValue } from '@/utils/format'

interface ProposalComparisonProps {
  current: MetadataValue
  proposed: MetadataValue
  applied?: MetadataValue
  dataType: MetadataDataType | null
}

function isLong(value: MetadataValue, dataType: MetadataDataType | null): boolean {
  return dataType === 'TEXT' || formatValue(value).length > 40
}

/**
 * What exists vs. what AI proposes. Long text (title, description, alt text) is shown side by side; short values
 * as "current → proposed".
 */
export function ProposalComparison({ current, proposed, applied, dataType }: ProposalComparisonProps) {
  const sideBySide = isLong(current, dataType) || isLong(proposed, dataType)
  const panel = (label: string, value: MetadataValue, tone: 'current' | 'proposed' | 'applied') => (
    <div
      className={cn(
        'flex min-w-0 flex-1 flex-col gap-1 rounded-md border p-2.5',
        tone === 'current' && 'bg-muted/40',
        tone === 'proposed' && 'border-violet-200 bg-violet-50/60 dark:border-violet-900 dark:bg-violet-950/30',
        tone === 'applied' && 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-900 dark:bg-emerald-950/30',
      )}
    >
      <span className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">{label}</span>
      <div className="text-sm">
        <MetadataValueView value={value} dataType={dataType} />
      </div>
    </div>
  )

  if (sideBySide) {
    return (
      <div className="grid gap-2 md:grid-cols-2">
        {panel('Current', current, 'current')}
        {panel('AI proposed', proposed, 'proposed')}
        {applied !== undefined && applied !== null && <div className="md:col-span-2">{panel('Applied (reviewer edit)', applied, 'applied')}</div>}
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
      {panel('Current', current, 'current')}
      <ArrowRight aria-hidden className="hidden size-4 shrink-0 self-center text-muted-foreground sm:block" />
      {panel('AI proposed', proposed, 'proposed')}
      {applied !== undefined && applied !== null && panel('Applied (reviewer edit)', applied, 'applied')}
    </div>
  )
}
