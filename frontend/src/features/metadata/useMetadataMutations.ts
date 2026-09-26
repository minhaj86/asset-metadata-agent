import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage, errorTitle } from '@/api/errors'
import { removeMetadataValue, setMetadataValue, validateAsset } from '@/api/metadata'
import { invalidateAsset } from '@/features/assets/assetQueries'
import { humanize } from '@/utils/format'

export function useValidateAsset(assetId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => validateAsset(assetId),
    onSuccess: (result) => {
      void invalidateAsset(client, assetId)
      toast.success('Validation completed', {
        description: result.valid
          ? `Metadata is valid · ${result.warnings.length} warning(s)`
          : `${result.errors.length} error(s), ${result.warnings.length} warning(s)`,
      })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}

export interface SetValueInput {
  field: string
  value: unknown
  reason?: string
}

/** Manual edit. Errors are returned to the form (validation issues) and announced as a toast. */
export function useSetMetadataValue(assetId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ field, value, reason }: SetValueInput) => setMetadataValue(assetId, field, value, reason),
    onSuccess: (entry) => {
      void invalidateAsset(client, assetId)
      toast.success('Metadata saved', { description: `${humanize(entry.field)} updated and audited` })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}

export function useRemoveMetadataValue(assetId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ field, reason }: { field: string; reason?: string }) => removeMetadataValue(assetId, field, reason),
    onSuccess: (_, { field }) => {
      void invalidateAsset(client, assetId)
      toast.success('Metadata value removed', { description: `${humanize(field)} cleared and audited` })
    },
    onError: (error) => toast.error(errorTitle(error), { description: errorMessage(error) }),
  })
}
