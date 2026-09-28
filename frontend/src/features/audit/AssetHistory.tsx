import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { History } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { FilterSelect } from '@/components/FilterSelect'
import { EmptyState, ErrorState } from '@/components/States'
import { fetchHistory } from '@/api/metadata'
import { queryKeys } from '@/api/queryKeys'
import type { FieldDefinition } from '@/types/api'
import { AuditTimeline } from './AuditTimeline'

/** Asset history tab; `?field=` (e.g. from the field inspector's "View history") narrows it to one field. */
export function AssetHistory({ assetId, fields }: { assetId: string; fields: FieldDefinition[] }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const field = searchParams.get('field') ?? undefined
  const history = useQuery({
    queryKey: queryKeys.history(assetId, field),
    queryFn: ({ signal }) => fetchHistory(assetId, field, signal),
  })
  const label = (key: string) => fields.find((definition) => definition.key === key)?.displayName ?? key

  return (
    <div className="flex flex-col gap-4">
      <FilterSelect
        label="Field"
        value={field}
        anyLabel="All fields"
        className="w-64"
        options={fields.map((definition) => ({ value: definition.key, label: definition.displayName }))}
        onChange={(next) =>
          setSearchParams(
            (previous) => {
              const params = new URLSearchParams(previous)
              if (next) {
                params.set('field', next)
              } else {
                params.delete('field')
              }
              return params
            },
            { replace: true },
          )
        }
      />
      {history.isPending ? (
        <Skeleton className="h-48 w-full" aria-label="Loading history" />
      ) : history.isError ? (
        <ErrorState error={history.error} onRetry={() => void history.refetch()} />
      ) : history.data.length === 0 ? (
        <EmptyState icon={History} title="No changes recorded" description={field ? `No audit events for ${label(field)}.` : undefined} />
      ) : (
        <AuditTimeline events={history.data} fieldLabel={label} />
      )}
    </div>
  )
}
