import { useId } from 'react'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

const ANY = '__any__'

export interface FilterOption {
  value: string
  label: string
}

interface FilterSelectProps {
  label: string
  value: string | undefined
  options: FilterOption[]
  onChange: (value: string | undefined) => void
  anyLabel?: string
  /** offer an "any" option that maps to `undefined` (default true) */
  allowAny?: boolean
  className?: string
  /** hide the visible label (still announced to screen readers) */
  hideLabel?: boolean
}

/** Labelled select; the optional "any" option maps to `undefined`. */
export function FilterSelect({ label, value, options, onChange, anyLabel = 'Any', allowAny = true, className, hideLabel }: FilterSelectProps) {
  const id = useId()
  return (
    <div className={cn('flex min-w-36 flex-col gap-1', className)}>
      <Label htmlFor={id} className={cn('text-xs text-muted-foreground', hideLabel && 'sr-only')}>
        {label}
      </Label>
      <Select value={value ?? ANY} onValueChange={(next) => onChange(next === ANY ? undefined : next)}>
        <SelectTrigger id={id} size="sm" className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {allowAny && <SelectItem value={ANY}>{anyLabel}</SelectItem>}
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
