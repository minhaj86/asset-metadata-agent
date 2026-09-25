import { cn } from '@/lib/utils'
import type { MetadataValue } from '@/types/api'
import { formatDate, isEmptyValue } from '@/utils/format'

interface MetadataValueViewProps {
  value: MetadataValue | undefined
  dataType?: string | null
  className?: string
  /** clamp long text to a few lines */
  clamp?: boolean
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/** Renders a metadata value by type: lists as chips, booleans as Yes/No, dates formatted, empty as "Not set". */
export function MetadataValueView({ value, dataType, className, clamp }: MetadataValueViewProps) {
  if (isEmptyValue(value)) {
    return <span className={cn('text-muted-foreground italic', className)}>Not set</span>
  }
  if (Array.isArray(value)) {
    return (
      <span className={cn('flex flex-wrap gap-1', className)}>
        {value.map((item, index) => (
          <span key={`${String(item)}-${index}`} className="rounded border bg-muted/50 px-1.5 py-0.5 text-xs">
            {String(item)}
          </span>
        ))}
      </span>
    )
  }
  if (typeof value === 'boolean') {
    return <span className={className}>{value ? 'Yes' : 'No'}</span>
  }
  const text = String(value)
  if (dataType === 'DATE' || (typeof value === 'string' && ISO_DATE.test(text))) {
    return <time dateTime={text} className={cn('tabular', className)}>{formatDate(text)}</time>
  }
  if (dataType === 'URL') {
    return (
      <a href={text} target="_blank" rel="noreferrer noopener" className={cn('break-all text-primary underline-offset-2 hover:underline', className)}>
        {text}
      </a>
    )
  }
  return <span className={cn('break-words', clamp && 'line-clamp-3', className)}>{text}</span>
}
