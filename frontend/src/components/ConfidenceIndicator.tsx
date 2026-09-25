import { Info } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useSystemStatus } from '@/hooks/useSystemStatus'
import { cn } from '@/lib/utils'
import { CONFIDENCE_DISCLAIMER, confidenceLevel, type ConfidenceLevel } from '@/utils/confidence'
import { formatConfidence } from '@/utils/format'

const LEVELS: Record<ConfidenceLevel, { label: string; bar: string; text: string }> = {
  high: { label: 'High confidence', bar: 'bg-emerald-600', text: 'text-emerald-800 dark:text-emerald-300' },
  medium: { label: 'Medium confidence', bar: 'bg-amber-500', text: 'text-amber-900 dark:text-amber-300' },
  low: { label: 'Low confidence', bar: 'bg-red-500', text: 'text-red-800 dark:text-red-300' },
}

interface ConfidenceIndicatorProps {
  confidence: number
  /** compact: percentage + short label only */
  variant?: 'full' | 'compact'
  className?: string
}

/**
 * AI confidence as percentage + text band + bar. Bands use the backend's governance thresholds. The tooltip makes
 * explicit that confidence is not correctness.
 */
export function ConfidenceIndicator({ confidence, variant = 'full', className }: ConfidenceIndicatorProps) {
  const { data: status } = useSystemStatus()
  const level = confidenceLevel(confidence, status?.governance)
  const config = level ? LEVELS[level] : null
  const percent = formatConfidence(confidence)
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          tabIndex={0}
          aria-label={`${percent} ${config?.label ?? 'confidence'}. ${CONFIDENCE_DISCLAIMER}`}
          className={cn(
            'inline-flex items-center gap-1.5 rounded-sm text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring',
            className,
          )}
        >
          <span className="font-semibold tabular">{percent}</span>
          {variant === 'full' && (
            <span aria-hidden className="h-1.5 w-12 overflow-hidden rounded-full bg-muted">
              <span className={cn('block h-full', config?.bar ?? 'bg-muted-foreground')} style={{ width: percent }} />
            </span>
          )}
          {config && <span className={cn('whitespace-nowrap', config.text)}>{variant === 'full' ? config.label : config.label.split(' ')[0]}</span>}
          <Info aria-hidden className="size-3 text-muted-foreground" />
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-64">{CONFIDENCE_DISCLAIMER}</TooltipContent>
    </Tooltip>
  )
}
