import { cn } from '@/lib/utils'
import { formatPercent } from '@/utils/format'

interface ScoreMeterProps {
  label: string
  /** 0..100 */
  value: number | null | undefined
  hint?: string
  size?: 'sm' | 'lg'
  className?: string
}

function band(value: number): { text: string; bar: string; word: string } {
  if (value >= 90) {
    return { text: 'text-emerald-800 dark:text-emerald-300', bar: 'bg-emerald-600', word: 'Good' }
  }
  if (value >= 70) {
    return { text: 'text-amber-900 dark:text-amber-300', bar: 'bg-amber-500', word: 'Fair' }
  }
  return { text: 'text-red-800 dark:text-red-300', bar: 'bg-red-500', word: 'Poor' }
}

/** Deterministic score (quality, completeness) as number + word + bar; computed by the backend in Java. */
export function ScoreMeter({ label, value, hint, size = 'sm', className }: ScoreMeterProps) {
  const hasValue = value !== null && value !== undefined
  const tone = hasValue ? band(value) : null
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-xs text-muted-foreground">{label}</span>
        {tone && <span className={cn('text-[11px] font-medium', tone.text)}>{tone.word}</span>}
      </div>
      <span className={cn('font-semibold tabular', size === 'lg' ? 'text-2xl' : 'text-lg', tone?.text)}>
        {formatPercent(value)}
      </span>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={hasValue ? value : undefined}
        aria-valuetext={hasValue ? `${formatPercent(value)} (${tone?.word})` : 'Not available'}
        className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
      >
        <div className={cn('h-full', tone?.bar)} style={{ width: hasValue ? `${Math.min(100, value)}%` : 0 }} />
      </div>
      {hint && <span className="text-[11px] text-muted-foreground">{hint}</span>}
    </div>
  )
}
