import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Download, ShieldCheck, Sparkles, X } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { errorMessage, errorTitle } from '@/api/errors'
import { exportMetadata, validateAssets } from '@/api/metadata'
import { useStartBulkEnrichment } from '@/features/enrichment/useStartEnrichment'
import { aiUnavailableReason, isAiAvailable, useSystemStatus } from '@/hooks/useSystemStatus'
import { saveFile } from '@/utils/download'
import { invalidateAsset } from './assetQueries'

const MAX_BULK_ENRICHMENT = 25

interface BulkActionBarProps {
  selectedIds: string[]
  onClear: () => void
}

/** Actions on selected assets. Bulk AI enrichment only creates proposals; backend policy governs what is applied. */
export function BulkActionBar({ selectedIds, onClear }: BulkActionBarProps) {
  const client = useQueryClient()
  const { data: status } = useSystemStatus()
  const [confirmEnrichment, setConfirmEnrichment] = useState(false)
  const bulkEnrichment = useStartBulkEnrichment()
  const aiAvailable = isAiAvailable(status)

  const validation = useMutation({
    mutationFn: () => validateAssets(selectedIds),
    onSuccess: (results) => {
      void invalidateAsset(client)
      const valid = results.filter((result) => result.valid).length
      toast.success('Validation completed', {
        description: `${valid} valid, ${results.length - valid} with errors`,
      })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })

  const exporting = useMutation({
    mutationFn: (format: 'csv' | 'json') => exportMetadata(selectedIds, format),
    onSuccess: ({ blob, filename }) => {
      saveFile(blob, filename)
      toast.success('Metadata exported', { description: filename })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })

  if (selectedIds.length === 0) {
    return null
  }
  const tooMany = selectedIds.length > MAX_BULK_ENRICHMENT
  const enrichmentDisabledReason = !aiAvailable
    ? aiUnavailableReason(status)
    : tooMany
      ? `At most ${MAX_BULK_ENRICHMENT} assets per bulk enrichment`
      : null

  return (
    <div role="region" aria-label="Bulk actions" className="sticky bottom-3 z-20 flex flex-wrap items-center gap-2 rounded-lg border bg-popover px-3 py-2 shadow-md">
      <span className="text-sm font-medium tabular">{selectedIds.length} selected</span>
      <Button size="sm" variant="outline" onClick={() => validation.mutate()} disabled={validation.isPending}>
        <ShieldCheck aria-hidden />
        Validate metadata
      </Button>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={enrichmentDisabledReason ? 0 : -1}>
            <Button size="sm" variant="outline" onClick={() => setConfirmEnrichment(true)} disabled={Boolean(enrichmentDisabledReason) || bulkEnrichment.isPending}>
              <Sparkles aria-hidden />
              Start AI enrichment
            </Button>
          </span>
        </TooltipTrigger>
        {enrichmentDisabledReason && <TooltipContent>{enrichmentDisabledReason}</TooltipContent>}
      </Tooltip>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button size="sm" variant="outline" disabled={exporting.isPending}>
            <Download aria-hidden />
            Export metadata
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem onSelect={() => exporting.mutate('csv')}>CSV (one row per value)</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => exporting.mutate('json')}>JSON (with provenance)</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Button size="sm" variant="ghost" className="ml-auto" onClick={onClear}>
        <X aria-hidden />
        Clear selection
      </Button>

      <AlertDialog open={confirmEnrichment} onOpenChange={setConfirmEnrichment}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Start AI enrichment for {selectedIds.length} assets?</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <p>
                  The agentic workflow creates <strong>AI suggestions</strong> for each asset. The backend approval policy decides
                  what may be applied automatically; sensitive fields (taxonomy, rights, copyright, product associations) always
                  wait for a human decision in the approval queue.
                </p>
                <p>Runs execute in the background with limited concurrency. Progress is shown under AI Enrichment.</p>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => bulkEnrichment.mutate(selectedIds, { onSuccess: onClear })}
            >
              Start enrichment
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
