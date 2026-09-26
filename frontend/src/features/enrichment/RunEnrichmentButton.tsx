import { Link } from 'react-router'
import { Loader2, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { aiUnavailableReason, isAiAvailable, useSystemStatus } from '@/hooks/useSystemStatus'
import type { CatalogItem } from '@/types/api'
import { useStartEnrichment } from './useStartEnrichment'

interface RunEnrichmentButtonProps {
  asset: Pick<CatalogItem, 'id' | 'aiStatus' | 'lastEnrichment'>
  size?: 'sm' | 'default'
  label?: string
}

/**
 * Starts the agentic workflow. Disabled with an explanation when the AI capability is unavailable; manual
 * metadata management stays fully usable.
 */
export function RunEnrichmentButton({ asset, size = 'sm', label = 'Run AI enrichment' }: RunEnrichmentButtonProps) {
  const { data: status } = useSystemStatus()
  const start = useStartEnrichment()
  const running = asset.aiStatus === 'RUNNING' || asset.aiStatus === 'QUEUED'

  if (running && asset.lastEnrichment) {
    return (
      <Button asChild size={size} variant="outline">
        <Link to={`/assets/${asset.id}/enrich?run=${asset.lastEnrichment.workflowId}`}>
          <Loader2 aria-hidden className="animate-spin" />
          Enrichment running — view progress
        </Link>
      </Button>
    )
  }
  const available = isAiAvailable(status)
  const button = (
    <Button size={size} onClick={() => start.mutate(asset.id)} disabled={!available || start.isPending}>
      {start.isPending ? <Loader2 aria-hidden className="animate-spin" /> : <Sparkles aria-hidden />}
      {label}
    </Button>
  )
  if (available) {
    return button
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0} aria-label={`${label} unavailable: ${aiUnavailableReason(status)}`}>
          {button}
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-72">
        AI service unavailable. {aiUnavailableReason(status)} The asset can still be edited manually.
      </TooltipContent>
    </Tooltip>
  )
}
